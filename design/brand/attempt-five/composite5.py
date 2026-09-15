"""
Attempt five, drawn where an icon is actually seen, plus the one measurement this round is for.

Everything that measures the phone is imported from attempt three and attempt four rather than
rewritten: the drawer grid found by scanning the screenshot, the launcher mask lifted off the
Photos tile to sub-pixel accuracy, the drawer ground sampled from the gutter beside PlainTicker's
own slot, the SVG parser, the rasterizer, the 1:1 size strip and the safe-zone overlay. The circle
mask comes from design/brand/render_icons.py, which is where this repository already defines it.

What is new here is `edge_contrast`. Attempt three found that a Canvas tile has no edge against the
Seeker's wallpaper and attempt four repeated the failure six times out of seven, and both of those
findings were made by eye and by the compositor failing to locate the tile. This round was called
to settle the ground, so the ground is measured: for every variant, the finished drawer image is
sampled in a three-pixel ring just inside the launcher's own measured mask and in a three-pixel ring
just outside it, and the WCAG contrast ratio between the two means is printed. A tile with an edge
is a number above about 3; a tile with no edge is a number near 1. Two references are measured
beside the candidates so the number has a scale: PlainTicker's slot as it ships, and the Photos
tile two columns over, which is the brightest thing in the row.

Run:   PYTHONIOENCODING=utf-8 python design/brand/attempt-five/composite5.py
Needs: python -m pip install pillow numpy
Reads: design/brand/attempt-three/drawer.png, design/brand/attempt-five/svg/*.svg
Writes: design/brand/attempt-five/render/*.png and design/brand/attempt-five/gallery.html
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.insert(0, str(BRAND))
sys.path.insert(0, str(HERE))

import numpy as np  # noqa: E402

import composite as C3  # noqa: E402  the measuring rig, attempt three
import marks5  # noqa: E402
from render_icons import shape_mask  # noqa: E402  exponent 2 is a true circle

SVG_DIR = HERE / "svg"
RENDER = HERE / "render"
GALLERY = HERE / "gallery.html"
EDGES_JSON = HERE / "edge-contrast.json"
CRITIQUE = HERE / "critique"

VIEWPORT_108 = 324   # 108dp at 480dpi
BIG = 240            # the loose tiles in the gallery
RING = 3             # pixels each side of the tile boundary sampled by edge_contrast
NL = chr(10)

GUIDE_VISIBLE = (122, 133, 148)   # the 72 a launcher shows
GUIDE_SAFE = (90, 169, 230)       # the 66 circle every mask is guaranteed to keep
GUIDE_MASK = (217, 164, 65)       # the superellipse actually measured off the phone


def guides(image):
    """The 108 viewport with the three boundaries a mark has to answer to drawn on top of it."""
    size = image.width
    unit = size / 108.0
    out = image.convert("RGBA")
    over = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(over)

    visible = 18 * unit, 18 * unit, 90 * unit - 1, 90 * unit - 1
    draw.rectangle(visible, outline=GUIDE_VISIBLE + (170,), width=max(1, round(unit * 0.5)))

    safe = 21 * unit, 21 * unit, 87 * unit - 1, 87 * unit - 1
    draw.ellipse(safe, outline=GUIDE_SAFE + (200,), width=max(1, round(unit * 0.7)))

    points = []
    for step in range(361):
        angle = step * np.pi / 180.0
        cos, sin = np.cos(angle), np.sin(angle)
        x = 54 + 36 * np.sign(cos) * abs(cos) ** (2.0 / 3.05)
        y = 54 + 36 * np.sign(sin) * abs(sin) ** (2.0 / 3.05)
        points.append((x * unit, y * unit))
    draw.line(points, fill=GUIDE_MASK + (190,), width=max(1, round(unit * 0.6)))

    out.alpha_composite(over)
    return out.convert("RGB")


def on_plate(tile, pad, plate):
    out = Image.new("RGBA", (tile.width + pad * 2, tile.height + pad * 2), plate + (255,))
    out.alpha_composite(tile, (pad, pad))
    return out.convert("RGB")


# -- the measurement this round exists for --------------------------------------------------------

def luminance(rgb):
    """WCAG relative luminance of an sRGB triple given 0 to 255."""
    channels = np.asarray(rgb, dtype=float) / 255.0
    linear = np.where(channels <= 0.04045, channels / 12.92, ((channels + 0.055) / 1.055) ** 2.4)
    return float(0.2126 * linear[..., 0].mean() + 0.7152 * linear[..., 1].mean()
                 + 0.0722 * linear[..., 2].mean())


def boundary_bands(mask, pad):
    """Two boolean rings around the mask edge: RING px inside it and RING px outside it."""
    size = mask.width
    big = Image.new("L", (size + pad * 2, size + pad * 2), 0)
    big.paste(mask, (pad, pad))
    window = RING * 2 + 1
    eroded = np.asarray(big.filter(ImageFilter.MinFilter(window)))
    dilated = np.asarray(big.filter(ImageFilter.MaxFilter(window)))
    solid = np.asarray(big)
    inner = (solid >= 250) & (eroded < 250)
    outer = (solid <= 5) & (dilated > 5)
    return inner, outer


def edge_contrast(image, box, mask, pad=8):
    """
    Does this tile have an edge against the drawer it is sitting in?

    Sampled off the finished composite, not off the SVG, so the launcher's own mask, the drawer's
    wallpaper and the tile's own artwork are all in the number. Returns the WCAG contrast ratio
    between the mean of a three-pixel ring inside the mask boundary and the mean of a three-pixel
    ring outside it. Near 1 means the tile does not exist as an object; above 3 means it does.
    """
    x0, y0, size = box
    pixels = np.asarray(image.convert("RGB")).astype(float)
    patch = pixels[y0 - pad:y0 + size + pad, x0 - pad:x0 + size + pad]
    inner, outer = boundary_bands(mask, pad)
    lo, hi = sorted((luminance(patch[inner]), luminance(patch[outer])))
    return (hi + 0.05) / (lo + 0.05)


def main():
    RENDER.mkdir(parents=True, exist_ok=True)
    drawer = Image.open(C3.DRAWER).convert("RGBA")
    size, first_x, pitch, row_tops = C3.find_grid(drawer)

    row_top = [y for y in row_tops if 1700 < y < 1900][0]
    box = (first_x + pitch, row_top, size)
    photos = (first_x, row_top, size)
    edges, exponent = C3.measure_mask(drawer, photos)
    mask = C3.mask_image(edges, size)

    pixels = np.asarray(drawer.convert("RGB")).astype(float)
    ground = np.median(
        pixels[row_top:row_top + size, box[0] - 26:box[0] - 8].reshape(-1, 3), axis=0)
    ground_rgb = tuple(int(c) for c in ground)

    unit_px = size / C3.VISIBLE
    print("drawer {} x {}".format(drawer.width, drawer.height))
    print("tile   {} px, column pitch {} px, PlainTicker at ({}, {})".format(
        size, pitch, box[0], box[1]))
    print("       {} px is {:.1f}dp at 480dpi, and one 108-viewport unit is {:.2f} px".format(
        size, size / 3.0, unit_px))
    print("mask   measured off the Photos tile; best-fit superellipse exponent {:.2f}".format(
        exponent))
    print("ground sampled from the gutter: rgb{}".format(ground_rgb))

    references = {
        "the drawer as it is, PlainTicker's own slot": edge_contrast(drawer, box, mask),
        "Photos, the brightest tile in the same row": edge_contrast(drawer, photos, mask),
    }
    print("edge   reference readings, before any candidate is placed:")
    for name, value in references.items():
        print("         {:>5.2f} to 1   {}".format(value, name))

    entries = []
    for variant in marks5.VARIANTS + [marks5.SHIPPED]:
        key = variant.key
        colour_svg = SVG_DIR / (key + ".svg")
        flat_svg = SVG_DIR / (key + "-flat.svg")

        full = C3.place(drawer, colour_svg, box, mask, ground)
        contrast = edge_contrast(full, box, mask)
        full.convert("RGB").save(RENDER / (key + "-drawer.png"))
        crop = full.crop((24, row_top - 379, 24 + pitch * 4 - 48, row_top + size + 110))
        crop.convert("RGB").save(RENDER / (key + "-crop.png"))

        # The real thing: the measured 60.7dp tile, 182 device pixels, on the drawer's own ground.
        at60 = on_plate(C3.tile_for(colour_svg, size, mask), 18, ground_rgb)
        at60.save(RENDER / (key + "-60dp.png"))

        sheet108 = guides(C3.render_svg(colour_svg, VIEWPORT_108, crop_to_visible=False))
        sheet108.save(RENDER / (key + "-108dp-safe.png"))

        mono = on_plate(C3.tile_for(flat_svg, BIG, C3.scaled_mask(edges, size, BIG),
                                    recolour=C3.THEMED_FG, plate=C3.THEMED_BG), 18, C3.PLATE)
        mono.save(RENDER / (key + "-monochrome.png"))

        measured = on_plate(C3.tile_for(colour_svg, BIG, C3.scaled_mask(edges, size, BIG)),
                            18, ground_rgb)
        measured.save(RENDER / (key + "-measured.png"))

        fitted = on_plate(C3.tile_for(colour_svg, BIG, shape_mask(BIG, 3.05)), 18, ground_rgb)
        fitted.save(RENDER / (key + "-superellipse.png"))

        circle = on_plate(C3.tile_for(colour_svg, BIG, shape_mask(BIG, 2)), 18, ground_rgb)
        circle.save(RENDER / (key + "-circle.png"))

        entries.append({
            "variant": variant,
            "contrast": contrast,
            "crop": crop.convert("RGB"),
            "measured": measured,
            "fitted": fitted,
            "circle": circle,
            "mono": mono,
            "at60": at60,
            "guides": sheet108,
            "small": C3.small_strip(colour_svg, flat_svg, edges, size),
        })
        print("  {:<34} edge {:>5.2f} to 1   -> -60dp, -108dp-safe, -monochrome, -measured, "
              "-superellipse, -circle, -drawer, -crop".format(key, contrast))

    sheet = contact_sheet(entries)
    sheet.save(RENDER / "drawer-contact-sheet.png")
    print("sheet  {} ({} x {})".format(RENDER / "drawer-contact-sheet.png",
                                       sheet.width, sheet.height))

    readings = {"references": references,
                "candidates": {e["variant"].key: e["contrast"] for e in entries}}
    EDGES_JSON.write_text(json.dumps(readings, indent=2), encoding="utf-8", newline=NL)

    GALLERY.write_text(gallery_html(entries, references, size, exponent, box, unit_px),
                       encoding="utf-8", newline=NL)
    print("gallery {} ({:.1f} KB)".format(GALLERY, GALLERY.stat().st_size / 1024))


def contact_sheet(entries):
    """Every variant in the real drawer, in PlainTicker's own slot, two across. No captions."""
    cell_w, cell_h = entries[0]["crop"].width, entries[0]["crop"].height
    label = 34
    rows = (len(entries) + 1) // 2
    sheet = Image.new("RGB", (cell_w * 2 + 30, (cell_h + label + 20) * rows + 10), (12, 14, 17))
    draw = ImageDraw.Draw(sheet)
    for index, entry in enumerate(entries):
        variant = entry["variant"]
        col, row = index % 2, index // 2
        x = 10 + col * (cell_w + 10)
        y = 10 + row * (cell_h + label + 20)
        draw.text((x + 2, y + 6), "{}   edge {:.2f} to 1".format(variant.key, entry["contrast"]),
                  fill=(200, 210, 222))
        sheet.paste(entry["crop"], (x, y + label))
    return sheet


# -- the gallery -----------------------------------------------------------------------------------

def gallery_html(entries, references, tile_px, exponent, box, unit_px):
    by_key = {e["variant"].key: e for e in entries}
    sections = []
    for family, members in (("The live bar anchor", marks5.BAR_FAMILY),
                            ("The disconnected quote", marks5.QUOTE_FAMILY)):
        cards = "".join(card_html(by_key[v.key]) for v in members)
        sections.append(FAMILY.format(family=C3.escape(family),
                                      blurb=FAMILY_BLURB[family], cards=cards))
    sections.append(FAMILY.format(family="The mark on the phone now",
                                  blurb="<p>Not a candidate. It is here so both families are "
                                        "judged against the thing they would replace rather than "
                                        "against nothing.</p>",
                                  cards=card_html(by_key["as-shipped"])))

    ranked = sorted(entries, key=lambda e: -e["contrast"])
    rows = "".join(
        '<tr class="{cls}"><td>{key}</td><td>{treatment}</td><td class="n">{value}</td></tr>'.format(
            cls="inv" if e["variant"].treatment == "inverted" else "",
            key=C3.escape(e["variant"].key), treatment=C3.escape(e["variant"].treatment),
            value="{:.2f}".format(e["contrast"])) for e in ranked)
    refs = "".join(
        '<tr class="ref"><td colspan="2">{name}</td><td class="n">{value}</td></tr>'.format(
            name=C3.escape(name), value="{:.2f}".format(value))
        for name, value in references.items())

    return PAGE.format(
        sections="".join(sections), rows=rows, refs=refs,
        tile_px=tile_px, dp="{:.1f}".format(tile_px / 3.0), unit="{:.2f}".format(unit_px),
        unit_dp="{:.3f}".format(marks5.UNIT_DP), exponent="{:.2f}".format(exponent),
        x=box[0], y=box[1],
        antigravity=paragraphs((CRITIQUE / "antigravity.txt").read_text(encoding="utf-8")),
        codex=paragraphs((CRITIQUE / "codex.txt").read_text(encoding="utf-8")),
        verdict=paragraphs(VERDICT))


def card_html(entry):
    variant = entry["variant"]
    dp, px = variant.thinnest_on_phone()
    return CARD.format(
        title=C3.escape(variant.title),
        key=C3.escape(variant.key),
        varies=C3.escape(variant.varies),
        caption=C3.escape(variant.caption),
        argument=C3.escape(variant.argument),
        breaks=C3.escape(variant.breaks),
        treatment=C3.escape(variant.treatment),
        contrast="{:.2f}".format(entry["contrast"]),
        thin="{:g} units, {:.1f}dp, {:.0f} device pixels".format(variant.thinnest(), dp, px),
        gaps=C3.escape(", ".join("{} {:g}".format(k, v) for k, v in variant.gaps.items()) or "-"),
        radius="{:.1f}".format(variant.radius()),
        measured=C3.data_uri(entry["measured"]),
        fitted=C3.data_uri(entry["fitted"]),
        circle=C3.data_uri(entry["circle"]),
        mono=C3.data_uri(entry["mono"]),
        at60=C3.data_uri(entry["at60"]),
        guides=C3.data_uri(entry["guides"], "JPEG", quality=92, subsampling=0),
        small=C3.data_uri(entry["small"]),
        crop=C3.data_uri(entry["crop"], "JPEG", quality=92, subsampling=0),
        crop_w=entry["crop"].width,
    )


def paragraphs(text):
    """Prose to HTML: blank lines split, **bold** is bold, single newlines break."""
    out = []
    for block in text.replace(chr(13), "").split(NL + NL):
        body = C3.escape(block.strip())
        while "**" in body:
            body = body.replace("**", "<strong>", 1).replace("**", "</strong>", 1)
        out.append("<p>" + body.replace(NL, "<br>") + "</p>")
    return "".join(out)


VERDICT = """The two critics agree on more than they disagree on, and where they agree they are
worth acting on. Both, independently, read the inverted live bar the same way: the knocked-out bar
splits the tile into two regions and what comes out is a pane divider. Antigravity called it a
physical cut separating the field into two pieces; Codex called it a sidebar and said the hamburger
was traded rather than escaped. Both also dropped the defence of the dot. Antigravity: at that
scale and exposure a small isolated unit reads as a speck. Codex: eighty dollars and eight million
dollars would get the same dot, so the explanation names a referent without making it visible.

Where they split is which family survives. Antigravity would ship the disconnected quote and drop
the live bar outright, because the vertical void cuts the tile into columns that do not register as
one object. Codex, forced to pick one, picked live-bar-true-weight-inverted while saying its worst
fault is that it identifies a generic sidebar more readily than this app, and argued that neither
family should ship on its present reasoning because neither draws the refusal, which is the app's
one distinctive act.

Followed: Antigravity on which family, Codex on both of the specific changes Antigravity asked for.
Each critic asked for one concrete move and both moves are drawn here rather than summarised.
Antigravity wanted the bar two units right so the Accent band beside it reads as a margin rather
than a sliver; that is live-bar-wide-gutter-inverted, and in the drawer strip the wider band reads
as more of a second pane, not less, which is Codex's objection confirmed on sight. Antigravity
wanted the pool six units closer to the bracket; that is quote-tight-gulf-inverted, and at 14 units
the pool starts to attach to the bracket as a component of one control, which is the reading Codex
warned about. Both asks are drawn and neither is adopted.

What is being shipped: quote-square-pool-inverted. What is not: the live bar family, on the finding
that its misread and its edge cannot both be solved. Escaping the hamburger needed the bar to bleed
past the mask; getting an edge needed the field inverted; and the two together manufacture the pane
divider that both critics saw. The Canvas-ground live bar keeps its meaning, the blue member is the
live one, and scores 1.39 to 1 on the edge, which is not a tile.

Codex's deepest objection is not answered by this round and is left standing: neither family draws
the refusal. The mark that did draw it was attempt four's withheld number, and the founder did not
keep it."""


FAMILY_BLURB = {
    "The live bar anchor": (
        "<p>Antigravity's concept 2. A vertical member on the left margin with horizontal readings "
        "held off it: the bar that means live, and the readings that hang off it. Two problems "
        "were put to this round. Its nearest system glyph is a hamburger menu, which Antigravity "
        "named itself, and its 12-unit bar is 10.1dp on the Seeker's real tile where the app's own "
        "live bar is a 2dp rule.</p>"
        "<p>The misread is answered structurally. A menu glyph is inset from every edge of its box "
        "and spreads equal rows over the full height; these bars run off the top and the bottom so "
        "the mask cuts them, and the readings sit below the middle so the upper third is bar and "
        "nothing else. The scale is answered as far as it can be: 2dp of a 48dp tile is 4.5 units "
        "and under the floor, so absolute width cannot be kept, and the true-weight variants keep "
        "the bar's aspect instead, 8 across the 72 a launcher shows, which is the 1 to 9 it has "
        "beside its label in the app.</p>"),
    "The disconnected quote": (
        "<p>Antigravity's concept 6. A monospace right bracket at the right boundary, one small "
        "unit stranded at the left, and the whole middle of the tile empty: the quote on one side, "
        "the $80 pool it was quoted off on the other. The problem put to this round was the dot. "
        "Eight units is 6.7dp on the Seeker's real tile, the smallest element anywhere in attempt "
        "four; it is a circle, which breaks section 4's radius 0; and section 8 bans coloured dots "
        "as decoration. The defence on record is that the dot is not decoration, it is the "
        "$80.</p>"
        "<p>The defence is tested rather than repeated. Squaring the pool puts the shape lock back "
        "and takes the mark out of the coloured-dot clause at once, and a square the width of the "
        "bracket's own stroke reads as one unit of the thing across the tile rather than as a "
        "speck. The 8-unit pool is drawn again at the doubted size so the threshold can be looked "
        "at, and the round pool is drawn at the square's size so the shape lock is a comparison "
        "and not an assertion.</p>"),
}


CARD = """
<section class="card">
  <header>
    <h3>{title}</h3>
    <p class="key">{key}</p>
    <p class="varies">varies: {varies}</p>
    <p class="caption">{caption}</p>
  </header>
  <div class="body">
    <div class="marks">
      <figure><img class="tile" src="{measured}" alt=""><figcaption>the launcher's own mask, lifted
        off this phone</figcaption></figure>
      <figure><img class="tile" src="{fitted}" alt=""><figcaption>the fitted superellipse, exponent
        3.05</figcaption></figure>
      <figure><img class="tile" src="{circle}" alt=""><figcaption>a true circle
        mask</figcaption></figure>
      <figure><img class="tile" src="{mono}" alt=""><figcaption>the monochrome layer, in
        launcher-supplied colours</figcaption></figure>
    </div>
    <figure class="strip">
      <img src="{at60}" alt="">
      <figcaption>60.7dp on the Seeker, 182 device pixels, 1:1, on the drawer's own
        ground</figcaption>
    </figure>
    <figure class="strip">
      <img src="{small}" alt="">
      <figcaption>16, 24, 32 and 48 px at 1:1 &mdash; colour, then flattened</figcaption>
    </figure>
    <figure class="guides">
      <img src="{guides}" alt="">
      <figcaption>the whole 108 viewport. Grey square: the central 72 a launcher shows. Blue
        circle: the 66 guaranteed under any mask. Amber outline: the superellipse measured off this
        phone.</figcaption>
    </figure>
    <dl class="facts">
      <dt>edge in the drawer</dt><dd class="big">{contrast} to 1</dd>
      <dt>treatment</dt><dd>{treatment}</dd>
      <dt>thinnest shape</dt><dd>{thin}</dd>
      <dt>negative space</dt><dd>{gaps} units</dd>
      <dt>furthest corner</dt><dd>{radius} of the 33-unit circle-mask guarantee</dd>
      <dt>breaks</dt><dd>{breaks}</dd>
      <dt>why this drawing</dt><dd class="words">{argument}</dd>
    </dl>
  </div>
  <figure class="drawer">
    <img src="{crop}" width="{crop_w}" alt="">
    <figcaption>the real drawer, this variant in PlainTicker's slot, under the launcher's own
      mask</figcaption>
  </figure>
</section>
"""

FAMILY = """
<section class="family">
  <h2>{family}</h2>
  <div class="blurb">{blurb}</div>
  {cards}
</section>
"""

PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>PlainTicker app icon, attempt five</title>
<style>
  :root {{
    --canvas:#0B0F14; --elevated:#121820; --ink:#E8ECF1; --ink2:#B4BCC8;
    --muted:#7F8A99; --line:rgba(232,236,241,.10); --strong:rgba(232,236,241,.22); --accent:#5AA9E6;
  }}
  * {{ box-sizing:border-box; }}
  body {{
    margin:0; background:var(--canvas); color:var(--ink);
    font:15px/1.55 "Segoe UI", system-ui, -apple-system, sans-serif;
    -webkit-font-smoothing:antialiased;
  }}
  .wrap {{ max-width:1180px; margin:0 auto; padding:0 24px 96px; }}
  header.top {{ padding:56px 0 28px; border-bottom:1px solid var(--strong); }}
  h1 {{ font-size:30px; line-height:1.2; margin:0 0 14px; font-weight:600; letter-spacing:-.01em; }}
  .lede {{ max-width:70ch; color:var(--ink2); margin:0 0 18px; }}
  .lede strong {{ color:var(--ink); font-weight:600; }}
  .meta {{ font:12px/1.7 ui-monospace, "JetBrains Mono", Consolas, monospace; color:var(--muted); }}
  .panel {{ background:var(--elevated); border:1px solid var(--line); padding:22px 26px; margin:26px 0 0; }}
  .panel h3 {{ margin:0 0 10px; font-size:15px; font-weight:600; }}
  .panel p {{ margin:0 0 12px; color:var(--ink2); max-width:72ch; }}
  .panel p:last-child {{ margin-bottom:0; }}
  table.edges {{ border-collapse:collapse; font:13px/1.6 ui-monospace, Consolas, monospace;
                 margin:14px 0 0; width:100%; max-width:620px; }}
  table.edges td {{ padding:5px 10px; border-bottom:1px solid var(--line); color:var(--ink2); }}
  table.edges td.n {{ text-align:right; color:var(--ink); width:84px; }}
  table.edges tr.inv td {{ color:var(--ink); }}
  table.edges tr.inv td.n {{ color:var(--accent); }}
  table.edges tr.ref td {{ color:var(--muted); }}
  .family {{ padding:52px 0 0; }}
  .family > h2 {{ font-size:24px; margin:0 0 10px; font-weight:600; letter-spacing:-.01em; }}
  .blurb p {{ color:var(--ink2); max-width:72ch; margin:0 0 12px; }}
  .card {{ padding:40px 0; border-bottom:1px solid var(--line); }}
  .card > header h3 {{ font-size:19px; margin:0; font-weight:600; }}
  .key {{ margin:5px 0 0; font:12px/1.5 ui-monospace, Consolas, monospace; color:var(--muted); }}
  .varies {{ margin:8px 0 0; color:var(--accent); font-size:13px; max-width:66ch; }}
  .caption {{ margin:6px 0 0; color:var(--ink2); max-width:66ch; }}
  .body {{ display:grid; grid-template-columns:minmax(0,1fr) 330px; gap:26px 32px; margin:24px 0 0;
           align-items:start; }}
  .marks {{ display:flex; gap:20px; flex-wrap:wrap; }}
  .facts {{ grid-row:1 / span 4; }}
  figure {{ margin:0; }}
  figcaption {{ margin-top:9px; font-size:11.5px; color:var(--muted); max-width:32ch; }}
  img.tile {{ width:120px; height:120px; display:block; }}
  .strip img {{ display:block; max-width:100%; }}
  .guides img {{ display:block; width:300px; max-width:100%; border:1px solid var(--line); }}
  .facts {{ display:grid; grid-template-columns:124px 1fr; gap:9px 16px; margin:0; font-size:13px;
            align-content:start; }}
  .facts dt {{ color:var(--muted); }}
  .facts dd {{ margin:0; color:var(--ink2); }}
  .facts dd.big {{ color:var(--accent); font:15px/1.4 ui-monospace, Consolas, monospace; }}
  .facts dd.words {{ color:var(--ink); }}
  .drawer {{ margin:26px 0 0; }}
  .drawer img {{ display:block; max-width:100%; height:auto; border:1px solid var(--line); }}
  #actual:checked ~ .wrap .drawer img {{ max-width:none; width:auto; }}
  #actual:checked ~ .wrap .drawer {{ overflow-x:auto; }}
  #hidecap:checked ~ .wrap .caption,
  #hidecap:checked ~ .wrap .varies,
  #hidecap:checked ~ .wrap .facts,
  #hidecap:checked ~ .wrap .key,
  #hidecap:checked ~ .wrap .card header h3 {{ visibility:hidden; }}
  .controls {{ display:flex; gap:22px; align-items:center; padding:22px 0 0; font-size:13px;
               color:var(--ink2); flex-wrap:wrap; }}
  .controls label {{ display:flex; gap:8px; align-items:center; cursor:pointer; }}
  .box {{ width:15px; height:15px; border:1px solid var(--strong); display:inline-block; }}
  #actual:checked ~ .wrap label[for=actual] .box,
  #hidecap:checked ~ .wrap label[for=hidecap] .box {{ background:var(--accent); border-color:var(--accent); }}
  @media (max-width:900px) {{
    .body {{ grid-template-columns:1fr; }}
    .facts {{ grid-row:auto; }}
    .guides img {{ width:100%; }}
  }}
</style>
</head>
<body>
<input type="checkbox" id="actual" hidden>
<input type="checkbox" id="hidecap" hidden>
<div class="wrap">
  <header class="top">
    <h1>PlainTicker app icon &mdash; attempt five, the two marks that were kept</h1>
    <p class="lede">Attempt four put seven concepts into the real drawer and two came back:
    number 2, the live bar anchor, and number 6, the disconnected quote. This round develops those
    two and nothing else. Each family is drawn six ways, and every variant is named for the one
    thing it changes rather than for its place in a list, so the sheet is a set of comparisons and
    not another shortlist.</p>
    <p class="lede">Both marks arrived on a Canvas ground, and a Canvas tile has no edge against
    this phone's wallpaper. That is the failure attempt three measured and attempt four repeated,
    and it is what this round was called to settle, so <strong>every family carries inverted
    variants and the edge is measured rather than judged</strong>: a three-pixel ring inside the
    launcher's own mask against a three-pixel ring outside it, in the finished drawer image, as a
    contrast ratio. Near 1 means the tile is not an object. The table is below and the number is on
    every card.</p>
    <p class="meta">Seeker, 1200 x 2670 at 480dpi &middot; drawer tile {tile_px} px, which is
    {dp}dp, not the 48dp an icon brief assumes &middot; one 108-viewport unit is {unit} device
    pixels and {unit_dp}dp &middot; launcher mask measured off the Photos tile, best-fit
    superellipse exponent {exponent} &middot; PlainTicker's slot at ({x}, {y})</p>
    <div class="panel">
      <h3>Does the tile have an edge? Measured in the drawer, best first</h3>
      <table class="edges">
        <tbody>{rows}{refs}</tbody>
      </table>
      <p style="margin-top:14px">Inverted variants are the ones printed in Ink with a blue number.
      A reading near 1 is a tile whose boundary cannot be seen at all: the wallpaper and the tile
      are the same luminance and what the eye gets is a mark floating on the wallpaper with no
      object under it.</p>
    </div>
    <div class="panel">
      <h3>Google Antigravity, gemini-3.1-pro-high, effort high, plan mode, on these fourteen
      drawings. Verbatim.</h3>
      {antigravity}
    </div>
    <div class="panel">
      <h3>Codex, codex-cli 0.154.0, asked to argue against the same fourteen with the tiles and the
      drawer strips in front of it. Verbatim.</h3>
      {codex}
    </div>
    <div class="panel">
      <h3>Where they disagreed, and which was followed</h3>
      {verdict}
    </div>
    <div class="controls">
      <label for="actual"><span class="box"></span> drawer strips at 1:1 device pixels</label>
      <label for="hidecap"><span class="box"></span> hide names and reasoning</label>
    </div>
  </header>
  {sections}
</div>
</body>
</html>
"""


if __name__ == "__main__":
    main()

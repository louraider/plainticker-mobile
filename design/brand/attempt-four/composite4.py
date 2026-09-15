"""
Draw Antigravity's seven where an icon is actually seen, and build the gallery the founder opens.

Everything that measures the phone is imported from design/brand/attempt-three/composite.py rather
than rewritten: the drawer grid found by scanning the screenshot, the launcher mask lifted off the
Photos tile to sub-pixel accuracy, the drawer ground sampled from the gutter beside PlainTicker's
own slot, the SVG parser and the rasterizer. The circle and squircle masks come from
design/brand/render_icons.py, which is where this repository already defines them (exponent 2 is
the circle, exponent 4 the squircle a Pixel launcher applies). Nothing about the device is assumed
here that attempt three did not already measure.

What is new is what gets written out. Attempt three produced the drawer strip and the size strip.
This adds the three layers a launcher can substitute without asking: the 108 viewport with the safe
zone drawn over it, so a mark that is only safe under the measured superellipse is visibly only
safe under the measured superellipse; the same mark under a true circle mask and under a squircle;
and the monochrome layer in launcher-supplied colours, which is the layer that killed the first
construction of attempt two.

Run:   PYTHONIOENCODING=utf-8 python design/brand/attempt-four/composite4.py
Needs: python -m pip install pillow numpy
Reads: design/brand/attempt-three/drawer.png, design/brand/attempt-four/svg/*.svg
Writes: design/brand/attempt-four/render/*.png and design/brand/attempt-four/gallery.html
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.insert(0, str(BRAND))
sys.path.insert(0, str(HERE))

import numpy as np  # noqa: E402

import composite as C3  # noqa: E402  the measuring rig, attempt three
import marks4  # noqa: E402
from render_icons import shape_mask  # noqa: E402  circle is exponent 2, squircle is 4

SVG_DIR = HERE / "svg"
RENDER = HERE / "render"
GALLERY = HERE / "gallery.html"

DENSITY = 3          # the Seeker is 480dpi, so 48dp is 144 px and 108dp is 324 px
TILE_48 = 48 * DENSITY
VIEWPORT_108 = 108 * DENSITY
BIG = 240            # the loose tiles in the gallery
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

    print("drawer {} x {}".format(drawer.width, drawer.height))
    print("tile   {} px, column pitch {} px, PlainTicker at ({}, {})".format(
        size, pitch, box[0], box[1]))
    print("       {} px is {:.2f}dp at 480dpi, and one 108-viewport unit is {:.2f} px".format(
        size, size / 3.0, size / C3.VISIBLE))
    print("mask   measured off the Photos tile; best-fit superellipse exponent {:.2f}".format(
        exponent))
    print("ground sampled from the gutter: rgb{}".format(ground_rgb))

    entries = []
    for concept in marks4.CONCEPTS + [marks4.SHIPPED]:
        key = concept.key
        colour_svg = SVG_DIR / (key + ".svg")
        flat_svg = SVG_DIR / (key + "-flat.svg")

        full = C3.place(drawer, colour_svg, box, mask, ground)
        full.convert("RGB").save(RENDER / (key + "-drawer.png"))
        crop = full.crop((24, row_top - 379, 24 + pitch * 4 - 48, row_top + size + 110))
        crop.convert("RGB").save(RENDER / (key + "-crop.png"))

        at48 = on_plate(C3.tile_for(colour_svg, TILE_48, C3.scaled_mask(edges, size, TILE_48)),
                        18, ground_rgb)
        at48.save(RENDER / (key + "-48dp.png"))

        sheet108 = guides(C3.render_svg(colour_svg, VIEWPORT_108, crop_to_visible=False))
        sheet108.save(RENDER / (key + "-108dp-safe.png"))

        mono = on_plate(C3.tile_for(flat_svg, BIG, C3.scaled_mask(edges, size, BIG),
                                    recolour=C3.THEMED_FG, plate=C3.THEMED_BG), 18, C3.PLATE)
        mono.save(RENDER / (key + "-monochrome.png"))

        circle = on_plate(C3.tile_for(colour_svg, BIG, shape_mask(BIG, 2)), 18, ground_rgb)
        circle.save(RENDER / (key + "-circle.png"))

        squircle = on_plate(C3.tile_for(colour_svg, BIG, shape_mask(BIG, 4)), 18, ground_rgb)
        squircle.save(RENDER / (key + "-squircle.png"))

        entries.append({
            "concept": concept,
            "crop": crop.convert("RGB"),
            "tile": C3.tile_for(colour_svg, BIG, C3.scaled_mask(edges, size, BIG)),
            "mono": mono,
            "circle": circle,
            "squircle": squircle,
            "at48": at48,
            "guides": sheet108,
            "small": C3.small_strip(colour_svg, flat_svg, edges, size),
        })
        print("  {:>4}  {:<26} -> {}-48dp.png, -108dp-safe, -monochrome, -circle, -squircle, "
              "-drawer, -crop".format(concept.rank or "-", concept.title, key))

    sheet = contact_sheet(entries)
    sheet.save(RENDER / "drawer-seven.png")
    print("sheet  {} ({} x {})".format(RENDER / "drawer-seven.png", sheet.width, sheet.height))

    GALLERY.write_text(gallery_html(entries, size, exponent, box), encoding="utf-8", newline=NL)
    print("gallery {} ({:.1f} KB)".format(GALLERY, GALLERY.stat().st_size / 1024))


def contact_sheet(entries):
    """The seven and the mark they would replace, in the real drawer, two across."""
    cell_w, cell_h = entries[0]["crop"].width, entries[0]["crop"].height
    label = 34
    sheet = Image.new("RGB", (cell_w * 2 + 30, (cell_h + label + 20) * 4 + 10), (12, 14, 17))
    draw = ImageDraw.Draw(sheet)
    for index, entry in enumerate(entries):
        concept = entry["concept"]
        col, row = index % 2, index // 2
        x = 10 + col * (cell_w + 10)
        y = 10 + row * (cell_h + label + 20)
        head = ("{}  {}".format(concept.rank, concept.title) if concept.rank
                else "     {}, not a candidate".format(concept.title))
        draw.text((x + 2, y + 6), head, fill=(200, 210, 222))
        sheet.paste(entry["crop"], (x, y + label))
    return sheet


# -- the gallery ----------------------------------------------------------------------------------

def gallery_html(entries, tile_px, exponent, box):
    cards = []
    for entry in entries:
        concept = entry["concept"]
        baseline = not concept.rank
        cards.append(CARD.format(
            klass=" shipped" if baseline else "",
            rank="" if baseline else str(concept.rank),
            title=C3.escape(concept.title),
            caption=C3.escape(concept.caption),
            words=C3.escape(concept.words),
            drawn=C3.escape(concept.drawn),
            breaks=C3.escape(concept.breaks),
            treatment=C3.escape(concept.treatment),
            gaps=C3.escape(", ".join("{} {:g}".format(k, v)
                                     for k, v in concept.gaps.items()) or "-"),
            radius="{:.1f}".format(concept.radius()),
            tile=C3.data_uri(entry["tile"]),
            mono=C3.data_uri(entry["mono"]),
            circle=C3.data_uri(entry["circle"]),
            squircle=C3.data_uri(entry["squircle"]),
            at48=C3.data_uri(entry["at48"]),
            guides=C3.data_uri(entry["guides"], "JPEG", quality=94, subsampling=0),
            small=C3.data_uri(entry["small"]),
            crop=C3.data_uri(entry["crop"], "JPEG", quality=94, subsampling=0),
            crop_w=entry["crop"].width,
        ))
    return PAGE.format(
        cards="".join(cards), tile_px=tile_px, dp="{:.0f}".format(tile_px / 3.0),
        unit="{:.2f}".format(tile_px / C3.VISIBLE), exponent="{:.2f}".format(exponent),
        x=box[0], y=box[1], model=C3.escape(marks4.MODEL),
        ranking=paragraphs(marks4.RANKING_WORDS), ship=paragraphs(marks4.SHIP_WORDS),
        contradictions=paragraphs(marks4.CONTRADICTIONS))


def paragraphs(text):
    """Markdown-ish prose to HTML: blank lines split, **bold** is bold, single newlines break."""
    out = []
    for block in text.split(NL + NL):
        body = C3.escape(block.strip())
        while "**" in body:
            body = body.replace("**", "<strong>", 1).replace("**", "</strong>", 1)
        out.append("<p>" + body.replace(NL, "<br>") + "</p>")
    return "".join(out)


CARD = """
<section class="card{klass}">
  <header>
    <span class="no">{rank}</span>
    <h2>{title}</h2>
    <p class="caption">{caption}</p>
  </header>
  <div class="body">
    <div class="marks">
      <figure><img class="tile" src="{tile}" alt=""><figcaption>the tile, {treatment}, under the
        launcher's own measured mask</figcaption></figure>
      <figure><img class="tile" src="{mono}" alt=""><figcaption>the monochrome layer, in
        launcher-supplied colours</figcaption></figure>
    </div>
    <div class="marks">
      <figure><img class="tile" src="{circle}" alt=""><figcaption>circle mask</figcaption></figure>
      <figure><img class="tile" src="{squircle}" alt=""><figcaption>squircle mask</figcaption></figure>
    </div>
    <figure class="strip">
      <img src="{small}" alt="">
      <figcaption>16, 24, 32 and 48 px at 1:1 &mdash; colour, then flattened</figcaption>
    </figure>
    <figure class="strip">
      <img src="{at48}" alt="">
      <figcaption>48dp on the Seeker, 144 device pixels, on the drawer's own ground</figcaption>
    </figure>
    <figure class="guides">
      <img src="{guides}" alt="">
      <figcaption>the whole 108 viewport at 108dp. Grey square: the central 72 a launcher shows.
        Blue circle: the 66 guaranteed under any mask. Amber outline: the superellipse measured off
        this phone. Anything between the blue and the amber survives here and not everywhere.</figcaption>
    </figure>
    <dl class="facts">
      <dt>Antigravity</dt><dd class="words">{words}</dd>
      <dt>drawn</dt><dd>{drawn}</dd>
      <dt>breaks</dt><dd>{breaks}</dd>
      <dt>negative space</dt><dd>{gaps} units</dd>
      <dt>furthest corner</dt><dd>{radius} of the 33-unit circle-mask guarantee</dd>
    </dl>
  </div>
  <figure class="drawer">
    <img src="{crop}" width="{crop_w}" alt="">
    <figcaption>the real drawer, this candidate in PlainTicker's slot, under the launcher's own
      mask</figcaption>
  </figure>
</section>
"""

PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>PlainTicker app icon, attempt four</title>
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
  .panel h3 {{ margin:0 0 10px; font-size:15px; font-weight:600; color:var(--ink); }}
  .panel p {{ margin:0 0 12px; color:var(--ink2); max-width:72ch; }}
  .panel p:last-child {{ margin-bottom:0; }}
  .panel strong {{ color:var(--ink); font-weight:600; }}
  .controls {{ display:flex; gap:22px; align-items:center; padding:22px 0 0; font-size:13px; color:var(--ink2); }}
  .controls label {{ display:flex; gap:8px; align-items:center; cursor:pointer; }}
  .card {{ padding:44px 0; border-bottom:1px solid var(--line); }}
  .card.shipped {{ opacity:.85; }}
  .card > header {{ display:grid; grid-template-columns:64px 1fr; gap:0 18px; align-items:baseline; }}
  .no {{ font:600 30px/1 ui-monospace, "JetBrains Mono", Consolas, monospace; color:var(--muted); }}
  .card h2 {{ font-size:21px; margin:0; font-weight:600; letter-spacing:-.01em; }}
  .caption {{ grid-column:2; margin:6px 0 0; color:var(--ink2); max-width:66ch; }}
  .card.shipped .no::after {{ content:"\\2014"; }}
  .body {{ display:grid; grid-template-columns:300px 1fr; gap:26px 32px; margin:26px 0 0 82px; align-items:start; }}
  .marks {{ display:flex; gap:24px; }}
  .facts {{ grid-row:1 / span 5; }}
  figure {{ margin:0; }}
  figcaption {{ margin-top:9px; font-size:11.5px; color:var(--muted); letter-spacing:.01em; max-width:38ch; }}
  img.tile {{ width:138px; height:138px; display:block; image-rendering:auto; }}
  .strip img {{ display:block; }}
  .guides img {{ display:block; width:300px; height:300px; border:1px solid var(--line); }}
  .facts {{ display:grid; grid-template-columns:118px 1fr; gap:9px 16px; margin:0; font-size:13px;
            align-content:start; }}
  .facts dt {{ color:var(--muted); }}
  .facts dd {{ margin:0; color:var(--ink2); }}
  .facts dd.words {{ color:var(--ink); }}
  .drawer {{ margin:28px 0 0 82px; }}
  .drawer img {{ display:block; max-width:100%; height:auto; border:1px solid var(--line); }}
  #actual:checked ~ .wrap .drawer img {{ max-width:none; width:auto; }}
  #actual:checked ~ .wrap .drawer {{ overflow-x:auto; }}
  #hidecap:checked ~ .wrap .caption,
  #hidecap:checked ~ .wrap .facts,
  #hidecap:checked ~ .wrap .no,
  #hidecap:checked ~ .wrap .card h2 {{ visibility:hidden; }}
  .box {{ width:15px; height:15px; border:1px solid var(--strong); display:inline-block; }}
  #actual:checked ~ .wrap label[for=actual] .box,
  #hidecap:checked ~ .wrap label[for=hidecap] .box {{ background:var(--accent); border-color:var(--accent); }}
  @media (max-width:900px) {{
    .body {{ grid-template-columns:1fr; margin-left:0; }}
    .facts {{ grid-row:auto; }}
    .drawer {{ margin-left:0; }}
    .card > header {{ grid-template-columns:44px 1fr; }}
    .guides img {{ width:100%; height:auto; }}
  }}
</style>
</head>
<body>
<input type="checkbox" id="actual" hidden>
<input type="checkbox" id="hidecap" hidden>
<div class="wrap">
  <header class="top">
    <h1>PlainTicker app icon &mdash; attempt four, seven concepts from Antigravity</h1>
    <p class="lede">Three attempts were drawn in this repository and all three were rejected. The
    thing that changed here is not the argument, it is <strong>who had the idea</strong>. These
    seven came back from Google Antigravity as prose, before any geometry existed, against a brief
    that carried the product, the design system, the launcher constraints and a written diagnosis of
    why the first three failed. The words under each mark are Antigravity's, verbatim. The drawing
    is this repository's, and every departure from the paragraph is written down beside it.</p>
    <p class="lede">Every mark is shown four ways it can be substituted without being asked:
    the launcher's own measured mask, a circle mask, a squircle mask, and the monochrome layer,
    which is the layer that killed the first construction of attempt two. Then at 48dp on the
    drawer's own ground, then in the real drawer beside real neighbours, which is the only place
    that decides.</p>
    <p class="meta">Concepts: {model} &middot; Seeker, 1200 x 2670 at 480dpi &middot; drawer tile
    {tile_px} px, which is {dp}dp &middot; one 108-viewport unit is {unit} px &middot; launcher mask
    measured off the Photos tile, best-fit superellipse exponent {exponent} &middot; PlainTicker's
    slot at ({x}, {y})</p>
    <div class="panel">
      <h3>Antigravity's ranking, in its own words</h3>
      {ranking}
    </div>
    <div class="panel">
      <h3>Antigravity's pick, in its own words</h3>
      {ship}
    </div>
    <div class="panel">
      <h3>Where Antigravity contradicted the product or the design system</h3>
      {contradictions}
    </div>
    <div class="controls">
      <label for="actual"><span class="box"></span> drawer strips at 1:1 device pixels</label>
      <label for="hidecap"><span class="box"></span> hide names, ranks and reasoning</label>
    </div>
  </header>
  {cards}
</div>
</body>
</html>
"""


if __name__ == "__main__":
    main()

"""
The chosen mark on four grounds, composited into the real Seeker drawer and measured there.

Nothing in this file measures the phone again. `composite5.edge_contrast` is the function attempt
five was called to write and it is imported, along with attempt three's rig: the drawer grid found
by scanning the screenshot, the launcher mask lifted off the Photos tile to sub-pixel accuracy, the
ground sampled from the gutter beside PlainTicker's own slot, the SVG parser and the rasterizer.
`render_icons.shape_mask` is where this repository already defines a superellipse. What is added
here is the rounded-square mask, which no earlier round drew, and the size strip at the four sizes
this round was asked for.

Two questions, both answered with numbers rather than with an argument:

  The ground. The founder chose the arrangement, not the ground, and the ground is what four
  rejected attempts got wrong. Four treatments of the same four rectangles go into PlainTicker's
  own slot in the real drawer and the boundary of each is sampled three pixels inside the
  launcher's measured mask against three pixels outside it. Near 1 is a tile that does not exist as
  an object; Photos, the brightest tile in that row, is the top of the scale.

  The masks. The mark's outermost corners sit 39.6 units from the centre and the circle-mask
  guarantee this repository has enforced since attempt one is 33, so the guarantee is broken and
  the question is by how much and against what. Each tile is cut three ways: the superellipse of
  exponent 3.05 measured off this phone, a true circle, and a rounded square. The circle is the one
  that costs something and the cost is printed.

Run:    PYTHONIOENCODING=utf-8 python design/brand/two-corners/measure.py
Needs:  python -m pip install pillow numpy
Reads:  design/brand/attempt-three/drawer.png, design/brand/two-corners/icon/*.svg
Writes: design/brand/two-corners/render/*.png, edge-contrast.json, gallery.html
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.insert(0, str(BRAND / "attempt-five"))
sys.path.insert(0, str(BRAND / "logo"))
sys.path.insert(0, str(BRAND))
sys.path.insert(0, str(HERE))

import numpy as np                      # noqa: E402

import composite as C3                  # noqa: E402  the measuring rig, attempt three
import composite5 as C5                 # noqa: E402  the edge measurement, attempt five
import marks_two_corners as M           # noqa: E402
from render_icons import shape_mask     # noqa: E402  exponent 2 is a true circle

ICON_DIR = HERE / "icon"
RENDER = HERE / "render"
GALLERY = HERE / "gallery.html"
EDGES_JSON = HERE / "edge-contrast.json"

BIG = 240
NL = chr(10)

# The four sizes this round was asked for, in dp, drawn at one device pixel per dp so the strip is
# the worst case rather than a flattering one. 60.7dp is the tile the Seeker actually draws.
SIZES_DP = (16, 24, 48, 60.7)

# A rounded square at quarter radius: the loosest of the three masks, and the one some launchers
# still offer. The circle is the tightest, the measured superellipse sits between them.
ROUND_SQUARE_RADIUS = 0.25

MEASURED_EXPONENT = 3.05


def rounded_square_mask(size, radius=ROUND_SQUARE_RADIUS):
    """A rounded-square launcher mask, corner radius as a fraction of the tile."""
    big = size * C3.SS
    canvas = Image.new("L", (big, big), 0)
    ImageDraw.Draw(canvas).rounded_rectangle(
        (0, 0, big - 1, big - 1), radius=big * radius, fill=255)
    return canvas.resize((size, size), Image.LANCZOS)


def size_strip(colour_svg, flat_svg, edges, source_size):
    """
    The mark at 16, 24, 48 and 60.7dp, one device pixel per dp, colour then flattened.

    The flattened row is beside it because the themed icon and the 24dp notification silhouette
    have no colour to lean on, and 16dp is smaller than Android will ever draw this: a mark that
    survives 16 has mass, which is the thing three of the four rejected attempts lacked.
    """
    sizes = [int(round(dp)) for dp in SIZES_DP]
    gap, pad, tall = 10, 10, max(sizes)
    width = pad * 2 + sum(sizes) + gap * (len(sizes) - 1)
    strip = Image.new("RGBA", (width * 2 + gap * 3, tall + pad * 2), C3.PLATE + (255,))
    x = pad
    for s in sizes:
        strip.alpha_composite(
            C3.tile_for(colour_svg, s, C3.scaled_mask(edges, source_size, s)), (x, pad + tall - s))
        x += s + gap
    x += gap * 2
    for s in sizes:
        strip.alpha_composite(
            C3.tile_for(flat_svg, s, C3.scaled_mask(edges, source_size, s),
                        recolour=C3.THEMED_FG, plate=C3.THEMED_BG), (x, pad + tall - s))
        x += s + gap
    return strip


def main():
    RENDER.mkdir(parents=True, exist_ok=True)
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    for mark in M.TREATMENTS:
        (ICON_DIR / (mark.key + ".svg")).write_text(mark.icon_svg(), encoding="utf-8", newline=NL)
        (ICON_DIR / (mark.key + "-flat.svg")).write_text(mark.icon_flat_svg(), encoding="utf-8",
                                                         newline=NL)

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

    print("drawer  {} x {}, tile {} px ({:.1f}dp at 480dpi), one viewport unit {:.2f} px".format(
        drawer.width, drawer.height, size, size / 3.0, size / C3.VISIBLE))
    print("mask    measured off the Photos tile, best-fit superellipse exponent {:.2f}".format(
        exponent))
    print("ground  sampled from the gutter beside PlainTicker's own slot: rgb{}".format(ground_rgb))

    geometry = M.TREATMENTS[0]
    thin_dp, thin_px = geometry.on_phone()
    clipped = geometry.clipped_by_circle()
    print("mark    furthest corner {:.2f} units, {:+.2f} inside the measured superellipse, "
          "{:+.2f} inside the circle".format(
              geometry.radius(), geometry.mask_clearance(MEASURED_EXPONENT),
              geometry.mask_clearance(2.0)))
    print("        a true circle clips {:.1f}% of the mark's area".format(clipped * 100))

    references = {
        "the drawer as it is, PlainTicker's own slot": C5.edge_contrast(drawer, box, mask),
        "Photos, the brightest tile in the same row": C5.edge_contrast(drawer, photos, mask),
    }
    for name, value in references.items():
        print("  reference   {:>5.2f} to 1   {}".format(value, name))

    entries = []
    for mark in M.TREATMENTS:
        key = mark.key
        colour_svg = ICON_DIR / (key + ".svg")
        flat_svg = ICON_DIR / (key + "-flat.svg")

        full = C3.place(drawer, colour_svg, box, mask, ground)
        contrast = C5.edge_contrast(full, box, mask)
        crop = full.crop((24, row_top - 379, 24 + pitch * 4 - 48, row_top + size + 110))
        crop.convert("RGB").save(RENDER / (key + "-crop.png"))

        at60 = C5.on_plate(C3.tile_for(colour_svg, size, mask), 18, ground_rgb)
        at60.save(RENDER / (key + "-60dp.png"))
        guides = C5.guides(C3.render_svg(colour_svg, 324, crop_to_visible=False))
        guides.save(RENDER / (key + "-108dp-safe.png"))
        mono = C5.on_plate(C3.tile_for(flat_svg, BIG, C3.scaled_mask(edges, size, BIG),
                                       recolour=C3.THEMED_FG, plate=C3.THEMED_BG), 18, C3.PLATE)
        mono.save(RENDER / (key + "-monochrome.png"))
        measured = C5.on_plate(C3.tile_for(colour_svg, BIG, C3.scaled_mask(edges, size, BIG)),
                               18, ground_rgb)
        measured.save(RENDER / (key + "-measured.png"))
        ellipse = C5.on_plate(C3.tile_for(colour_svg, BIG, shape_mask(BIG, MEASURED_EXPONENT)),
                              18, ground_rgb)
        ellipse.save(RENDER / (key + "-superellipse.png"))
        circle = C5.on_plate(C3.tile_for(colour_svg, BIG, shape_mask(BIG, 2)), 18, ground_rgb)
        circle.save(RENDER / (key + "-circle.png"))
        square = C5.on_plate(C3.tile_for(colour_svg, BIG, rounded_square_mask(BIG)), 18, ground_rgb)
        square.save(RENDER / (key + "-rounded-square.png"))
        strip = size_strip(colour_svg, flat_svg, edges, size)
        strip.save(RENDER / (key + "-sizes.png"))

        entries.append({"mark": mark, "contrast": contrast, "crop": crop.convert("RGB"),
                        "at60": at60, "guides": guides, "mono": mono, "measured": measured,
                        "ellipse": ellipse, "circle": circle, "square": square, "sizes": strip})
        print("  {:<22} edge {:>5.2f} to 1   ink {:.1f}% of the visible 72".format(
            key, contrast, mark.ink_share() * 100))

    sheet = contact_sheet(entries)
    sheet.save(RENDER / "drawer-contact-sheet.png")
    print("sheet   {} ({} x {})".format(RENDER / "drawer-contact-sheet.png",
                                        sheet.width, sheet.height))

    readings = {
        "references": references,
        "treatments": {e["mark"].key: e["contrast"] for e in entries},
        "chosen": M.CHOSEN,
        "geometry": {
            "furthest corner, units from centre": round(geometry.radius(), 2),
            "circle-mask guarantee, units": 33.0,
            "clearance inside the measured superellipse 3.05, units":
                round(geometry.mask_clearance(MEASURED_EXPONENT), 2),
            "clearance inside the rounded-down superellipse 3.0, units":
                round(geometry.mask_clearance(3.0), 2),
            "clearance inside a true circle, units": round(geometry.mask_clearance(2.0), 2),
            "share of the mark a true circle clips": round(clipped, 4),
            "thinnest arm, units": geometry.thinnest(),
            "thinnest arm on the Seeker tile, dp": round(thin_dp, 1),
        },
    }
    EDGES_JSON.write_text(json.dumps(readings, indent=2), encoding="utf-8", newline=NL)
    print("edges   {}".format(EDGES_JSON))

    GALLERY.write_text(
        gallery_html(entries, references, size, exponent, box, geometry, clipped),
        encoding="utf-8", newline=NL)
    print("gallery {} ({:.1f} KB)".format(GALLERY, GALLERY.stat().st_size / 1024))


def contact_sheet(entries):
    """Every treatment in the real drawer, in PlainTicker's own slot, two across. No captions."""
    cell_w, cell_h = entries[0]["crop"].width, entries[0]["crop"].height
    label = 34
    rows = (len(entries) + 1) // 2
    sheet = Image.new("RGB", (cell_w * 2 + 30, (cell_h + label + 20) * rows + 10), (12, 14, 17))
    draw = ImageDraw.Draw(sheet)
    for index, entry in enumerate(entries):
        col, row = index % 2, index // 2
        x = 10 + col * (cell_w + 10)
        y = 10 + row * (cell_h + label + 20)
        draw.text((x + 2, y + 6), "{}   edge {:.2f} to 1".format(entry["mark"].key,
                                                                 entry["contrast"]),
                  fill=(200, 210, 222))
        sheet.paste(entry["crop"], (x, y + label))
    return sheet


# -- the gallery ----------------------------------------------------------------------------------

def gallery_html(entries, references, tile_px, exponent, box, geometry, clipped):
    ranked = sorted(entries, key=lambda e: -e["contrast"])
    rows = "".join(
        '<tr class="{cls}"><td>{key}</td><td>{field}</td><td class="n">{value}</td></tr>'.format(
            cls="win" if e["mark"].key == M.CHOSEN else "",
            key=C3.escape(e["mark"].key), field=C3.escape(e["mark"].field),
            value="{:.2f}".format(e["contrast"])) for e in ranked)
    refs = "".join(
        '<tr class="ref"><td colspan="2">{name}</td><td class="n">{value}</td></tr>'.format(
            name=C3.escape(name), value="{:.2f}".format(value))
        for name, value in references.items())
    cards = "".join(card_html(e) for e in entries)
    thin_dp, thin_px = geometry.on_phone()

    return PAGE.format(
        cards=cards, rows=rows, refs=refs,
        tile_px=tile_px, dp="{:.1f}".format(tile_px / 3.0),
        unit="{:.2f}".format(tile_px / C3.VISIBLE), exponent="{:.2f}".format(exponent),
        x=box[0], y=box[1],
        radius="{:.2f}".format(geometry.radius()),
        ellipse_room="{:+.2f}".format(geometry.mask_clearance(MEASURED_EXPONENT)),
        tight_room="{:+.2f}".format(geometry.mask_clearance(3.0)),
        circle_room="{:+.2f}".format(geometry.mask_clearance(2.0)),
        clipped="{:.1f}".format(clipped * 100),
        thin="{:g} units, {:.1f}dp, {:.0f} device pixels".format(
            geometry.thinnest(), thin_dp, thin_px),
        chosen=C3.escape(M.CHOSEN),
        square="{:.0f}".format(ROUND_SQUARE_RADIUS * 100),
    )


def card_html(entry):
    mark = entry["mark"]
    return CARD.format(
        title=C3.escape(mark.title),
        key=C3.escape(mark.key),
        klass=" win" if mark.key == M.CHOSEN else "",
        field=C3.escape(mark.field),
        breaks=C3.escape(mark.breaks),
        contrast="{:.2f}".format(entry["contrast"]),
        ink="{:.1f}".format(mark.ink_share() * 100),
        measured=C3.data_uri(entry["measured"]),
        ellipse=C3.data_uri(entry["ellipse"]),
        circle=C3.data_uri(entry["circle"]),
        square=C3.data_uri(entry["square"]),
        mono=C3.data_uri(entry["mono"]),
        at60=C3.data_uri(entry["at60"]),
        sizes=C3.data_uri(entry["sizes"]),
        guides=C3.data_uri(entry["guides"], "JPEG", quality=92, subsampling=0),
        crop=C3.data_uri(entry["crop"], "JPEG", quality=92, subsampling=0),
        crop_w=entry["crop"].width,
    )


CARD = """
<section class="card{klass}">
  <header>
    <h3>{title}</h3>
    <p class="key">{key} &middot; field {field}</p>
  </header>
  <div class="body">
    <div class="marks">
      <figure><img class="tile" src="{measured}" alt=""><figcaption>the launcher's own mask, lifted
        off this phone</figcaption></figure>
      <figure><img class="tile" src="{ellipse}" alt=""><figcaption>the measured superellipse,
        exponent 3.05</figcaption></figure>
      <figure><img class="tile" src="{circle}" alt=""><figcaption>a true circle &mdash; look at the
        outer point of each corner</figcaption></figure>
      <figure><img class="tile" src="{square}" alt=""><figcaption>a rounded square, quarter
        radius</figcaption></figure>
      <figure><img class="tile" src="{mono}" alt=""><figcaption>the monochrome layer, in
        launcher-supplied colours</figcaption></figure>
    </div>
    <dl class="facts">
      <dt>edge in the drawer</dt><dd class="big">{contrast} to 1</dd>
      <dt>ink share</dt><dd>{ink}% of the visible 72</dd>
      <dt>breaks</dt><dd class="words">{breaks}</dd>
    </dl>
    <figure class="strip">
      <img src="{at60}" alt="">
      <figcaption>60.7dp on the Seeker, 182 device pixels, 1:1, on the drawer's own
        ground</figcaption>
    </figure>
    <figure class="strip">
      <img src="{sizes}" alt="">
      <figcaption>16, 24, 48 and 60.7dp at one device pixel per dp &mdash; colour, then the
        monochrome layer in launcher colours</figcaption>
    </figure>
    <figure class="guides">
      <img src="{guides}" alt="">
      <figcaption>the whole 108 viewport. Grey square: the central 72 a launcher shows. Blue
        circle: the 66 this repository has called guaranteed since attempt one. Amber outline: the
        superellipse measured off this phone. The corners sit outside the blue and inside the
        amber, and that is the whole mask question.</figcaption>
    </figure>
  </div>
  <figure class="drawer">
    <img src="{crop}" width="{crop_w}" alt="">
    <figcaption>the real drawer, this treatment in PlainTicker's slot, under the launcher's own
      mask</figcaption>
  </figure>
</section>
"""

PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>PlainTicker app icon &mdash; two corners, and which ground it stands on</title>
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
                 margin:14px 0 0; width:100%; max-width:640px; }}
  table.edges td {{ padding:5px 10px; border-bottom:1px solid var(--line); color:var(--ink2); }}
  table.edges td.n {{ text-align:right; color:var(--ink); width:84px; }}
  table.edges tr.win td {{ color:var(--ink); }}
  table.edges tr.win td.n {{ color:var(--accent); }}
  table.edges tr.ref td {{ color:var(--muted); }}
  .card {{ padding:44px 0; border-bottom:1px solid var(--line); }}
  .card.win {{ border-left:2px solid var(--accent); padding-left:22px; }}
  .card > header h3 {{ font-size:20px; margin:0; font-weight:600; letter-spacing:-.01em; }}
  .key {{ margin:5px 0 0; font:12px/1.5 ui-monospace, Consolas, monospace; color:var(--muted); }}
  .body {{ display:grid; grid-template-columns:minmax(0,1fr) 330px; gap:26px 32px; margin:24px 0 0;
           align-items:start; }}
  .marks {{ display:flex; gap:20px; flex-wrap:wrap; }}
  .facts {{ grid-row:1 / span 4; }}
  figure {{ margin:0; }}
  figcaption {{ margin-top:9px; font-size:11.5px; color:var(--muted); max-width:32ch; }}
  img.tile {{ width:118px; height:118px; display:block; }}
  .strip img {{ display:block; max-width:100%; }}
  .guides img {{ display:block; width:300px; max-width:100%; border:1px solid var(--line); }}
  .facts {{ display:grid; grid-template-columns:118px 1fr; gap:9px 16px; margin:0; font-size:13px;
            align-content:start; }}
  .facts dt {{ color:var(--muted); }}
  .facts dd {{ margin:0; color:var(--ink2); }}
  .facts dd.big {{ color:var(--accent); font:16px/1.4 ui-monospace, Consolas, monospace; }}
  .facts dd.words {{ color:var(--ink2); }}
  .drawer {{ margin:26px 0 0; }}
  .drawer img {{ display:block; max-width:100%; height:auto; border:1px solid var(--line); }}
  #actual:checked ~ .wrap .drawer img {{ max-width:none; width:auto; }}
  #actual:checked ~ .wrap .drawer {{ overflow-x:auto; }}
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
    <h1>Two corners &mdash; the arrangement is chosen, this is the ground</h1>
    <p class="lede">The founder picked cell <strong>4a, "Two corners"</strong>: two registration
    corners on the 108 viewport, top-left and bottom-right, with an empty centre between them.
    Those four rectangles are transcribed verbatim and nothing on this page moves them. Every tile
    below is the same drawing.</p>
    <p class="lede">What is open is the ground, and the ground is what four rejected attempts got
    wrong. <strong>PlainTicker's tile as it ships has no boundary at all: 1.03 to 1.</strong> The
    number under each tile is that same measurement &mdash; a three-pixel ring inside the launcher's
    own measured mask against a three-pixel ring outside it, in the finished drawer image, as a WCAG
    contrast ratio. Near 1 is a tile that is not an object. Photos, the brightest tile in that row,
    is the top of the scale at 18.06.</p>
    <p class="meta">Seeker, 1200 x 2670 at 480dpi &middot; drawer tile {tile_px} px, which is
    {dp}dp, not the 48dp an icon brief assumes &middot; one 108-viewport unit is {unit} device
    pixels &middot; launcher mask measured off the Photos tile, best-fit superellipse exponent
    {exponent} &middot; PlainTicker's slot at ({x}, {y})</p>
    <div class="panel">
      <h3>Does the tile have an edge? Measured in the real drawer, best first</h3>
      <table class="edges">
        <tbody>{rows}{refs}</tbody>
      </table>
      <p style="margin-top:14px">The recommendation is <strong>{chosen}</strong>: it reads within a
      point of the cool off-white and is built from two colours DESIGN.md section 2 already has, so
      the test can pin the icon to the Kotlin tokens and a palette change cannot leave it behind.
      The off-white costs a tenth and an eleventh colour to buy a difference no eye will find in a
      drawer.</p>
    </div>
    <div class="panel">
      <h3>The masks, and what a circle does to these corners</h3>
      <p>The outermost point of each corner sits <strong>{radius} units</strong> from the centre.
      The circle-mask guarantee this repository has enforced since attempt one is 33 units, so this
      mark breaks it, and the question is against what.</p>
      <p>Against the superellipse of exponent 3.05 measured off this phone, the tightest corner has
      <strong>{ellipse_room} units</strong> of room; against the 3.0 this repository rounds down to,
      <strong>{tight_room} units</strong>. That clears, but it is not roomy: 0.8 of a unit is two
      device pixels on the Seeker's tile and two thirds of a dp, so the drawing is at the edge of
      the mask rather than comfortably inside it, and it must not be pushed out any further.</p>
      <p>Against a true circle the same corner is <strong>{circle_room} units</strong>, which is
      outside. A circular mask clips <strong>{clipped}%</strong> of the mark's area &mdash; a small
      number that lands in exactly the wrong place, because what it takes is the outer right-angle
      vertex of both corners. The circle bevels the two points that make the shape read as a
      registration corner and leaves two thick chevrons. The measured mask on this phone is not a
      circle and no Pixel-family launcher cuts one, but a launcher that does will soften this mark;
      that is the cost of the arrangement as drawn, and it is a picture on every card below rather
      than a sentence.</p>
      <p>The rounded square, at a corner radius of {square}% of the tile, is the loosest of the
      three and takes nothing.</p>
    </div>
    <div class="panel">
      <h3>The arrangement, once, because every treatment is the same four rectangles</h3>
      <p>Thinnest arm {thin}. Empty centre 28 by 28 units, dead centre. Eight units of clear space
      between the two corners in both axes. The mark is 180 degrees rotationally symmetric about
      the centre and is not mirror-symmetric about either axis, which is what keeps the flattened
      silhouette off the plus sign that killed the first gauge.</p>
    </div>
    <div class="controls">
      <label for="actual"><span class="box"></span> drawer strips at 1:1 device pixels</label>
      <label for="hidecap"><span class="box"></span> hide names and numbers</label>
    </div>
  </header>
  {cards}
</div>
</body>
</html>
"""


if __name__ == "__main__":
    main()

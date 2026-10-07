"""
Put every candidate where an icon is actually seen, which is the part both previous attempts skipped.

Attempt one was judged on the App info screen, where the icon is large. Attempt two was judged on a
neutral comparison sheet, where the icon has no neighbours. An icon is never seen in either place.
It is seen in a launcher drawer at about 48dp, beside thirty saturated tiles, for half a second,
and the founder rejected both marks within seconds of seeing them exactly there. So this script
takes the real drawer off the real phone and puts each candidate into the tile PlainTicker actually
occupies, at the real pixel size, under the launcher's own mask.

Nothing here is assumed that could be measured:

  the tile rectangle   found by scanning the screenshot for the drawer grid, not typed in. The
                       Seeker draws a drawer tile at 182 x 182 device pixels on a 1200 x 2670
                       screen at 480dpi, so one 108-viewport unit is 2.53 px and 48dp would be 144:
                       these crops are the size test itself, not a proxy for it.
  the launcher mask    lifted off a neighbouring tile. Photos has a white ground that bleeds to the
                       mask edge, so the alpha of that tile against the drawer background IS the
                       mask, per row, to sub-pixel accuracy. A superellipse exponent is fitted to
                       it and printed, but the measured edges are what gets used.
  the drawer ground    sampled from the gutter beside the tile, so the candidate sits on the same
                       pixels its neighbours sit on rather than on a guess at the wallpaper.

The SVG files are the source of truth and are parsed here rather than re-derived from marks3, so a
candidate that renders is a candidate that really is drawn in those files. Supported primitives are
the ones the marks are made of and no more: rect, circle, rectilinear path, and one mask element
for negative space. Anything else raises.

Run:   PYTHONIOENCODING=utf-8 python design/brand/attempt-three/composite.py
Needs: python -m pip install pillow numpy defusedxml
Reads: design/brand/attempt-three/drawer.png, design/brand/attempt-three/svg/*.svg
Writes: design/brand/attempt-three/render/*.png and design/brand/attempt-three/gallery.html
"""
import base64
import io
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

try:
    import defusedxml.ElementTree as ET  # refuses external entities and entity expansion
except ImportError as missing:
    raise ImportError(
        "design/brand/attempt-three/composite.py parses XML with defusedxml, which is not installed. "
        "Install it with: python -m pip install defusedxml"
    ) from missing

HERE = Path(__file__).resolve().parent
sys.path.append(str(HERE.parent))  # design/brand, for xml_ns

from xml_ns import SVG_NS  # noqa: E402
SVG_DIR = HERE / "svg"
RENDER = HERE / "render"
DRAWER = HERE / "drawer.png"
GALLERY = HERE / "gallery.html"

SS = 4                       # supersampling factor
VIEWPORT = 108.0
VISIBLE = 72.0               # a launcher shows the central 72 of the 108 viewport

# A themed icon has no colours of its own: the launcher supplies both. These are Android 13+
# defaults for a dark system theme, used only so the flattened layer is judged the way it is drawn.
THEMED_BG = (43, 53, 65)
THEMED_FG = (220, 231, 244)
PLATE = (24, 27, 32)         # the gallery's own plate behind a loose tile


# -- reading the SVG ----------------------------------------------------------------------------

PATH_TOKEN = re.compile(r"[MHVLZ]|-?(?:\d*\.\d+|\d+)")


def tag(element):
    return element.tag.replace(SVG_NS, "")


def parse_svg(path):
    """
    (ordered [(fill, contours)], is_masked) from one of our SVGs.

    Two shapes of document, and only two. A colour drawing is a flat list of primitives painted in
    order, where a Canvas-coloured primitive is a knockout by overpainting. A flattened drawing is
    a <mask> of white and black primitives applied to one filled rect, because in the monochrome
    layer there is no Canvas to overpaint with and negative space has to be a real hole.
    """
    root = ET.parse(path).getroot()
    mask = root.find(SVG_NS + "mask")
    if mask is None:
        return [(el.get("fill"), contours(el)) for el in root if tag(el) != "mask"], False
    holder = [el for el in root if el.get("mask")]
    assert len(holder) == 1, "a flattened drawing applies its mask to exactly one shape: " + str(path)
    return [(el.get("fill"), contours(el)) for el in mask], True


def contours(element):
    """A primitive as a list of point lists, in user units."""
    kind = tag(element)
    if kind == "rect":
        x, y = float(element.get("x")), float(element.get("y"))
        w, h = float(element.get("width")), float(element.get("height"))
        return [[(x, y), (x + w, y), (x + w, y + h), (x, y + h)]]
    if kind == "circle":
        cx, cy, r = (float(element.get(k)) for k in ("cx", "cy", "r"))
        steps = 256
        return [[(cx + r * math.cos(2 * math.pi * i / steps),
                  cy + r * math.sin(2 * math.pi * i / steps)) for i in range(steps)]]
    if kind == "path":
        return [path_points(element.get("d"))]
    raise AssertionError("unsupported primitive <{}>: these marks are rects, circles and "
                         "rectilinear paths only".format(kind))


def path_points(data):
    tokens = PATH_TOKEN.findall(data)
    assert "".join(tokens) == re.sub(r"[\s,]", "", data), "unparsed characters in " + data
    points, x, y, command, i = [], 0.0, 0.0, "", 0

    def take():
        nonlocal i
        i += 1
        return float(tokens[i - 1])

    while i < len(tokens):
        if tokens[i][0].isalpha():
            command = tokens[i]
            i += 1
            continue
        if command == "M":
            x, y = take(), take()
        elif command == "L":
            x, y = take(), take()
        elif command == "H":
            x = take()
        elif command == "V":
            y = take()
        else:
            raise AssertionError("unsupported path command " + command)
        points.append((x, y))
    return points


def rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


# -- rasterizing --------------------------------------------------------------------------------

def coverage(contour_list, size, scale, offset):
    """An 8-bit coverage mask for one primitive, supersampled then box-filtered down."""
    big = size * SS
    canvas = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(canvas)
    for contour in contour_list:
        draw.polygon([((px - offset) * scale * SS, (py - offset) * scale * SS)
                      for px, py in contour], fill=255)
    return canvas.resize((size, size), Image.LANCZOS)


def render_svg(path, size, crop_to_visible=True, recolour=None):
    """
    The drawing as RGBA at `size` px.

    crop_to_visible reproduces what a launcher does: the 108 viewport is scaled so its central 72
    fills the tile, and everything outside that 72 is thrown away before the mask is applied. That
    is why a knockout can legally run off the edge, and why the mark has to earn its mass inside 72
    rather than inside 108.
    """
    paints, is_masked = parse_svg(path)
    span = VISIBLE if crop_to_visible else VIEWPORT
    offset = (VIEWPORT - span) / 2
    scale = size / span

    if is_masked:
        alpha = Image.new("L", (size, size), 0)
        for fill, contour_list in paints:
            cover = coverage(contour_list, size, scale, offset)
            solid = Image.new("L", (size, size), 255 if rgb(fill)[0] > 127 else 0)
            alpha = Image.composite(solid, alpha, cover)
        out = Image.new("RGBA", (size, size), (recolour or (255, 255, 255)) + (255,))
        out.putalpha(alpha)
        return out

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for fill, contour_list in paints:
        cover = coverage(contour_list, size, scale, offset)
        flat = Image.new("RGBA", (size, size), (recolour or rgb(fill)) + (255,))
        out = Image.composite(flat, out, cover)
    return out


# -- the launcher's own mask, measured off the phone ---------------------------------------------

def find_grid(drawer):
    """
    The drawer grid, by scanning: (tile size, first column x, column pitch, [row y]) in pixels.

    A tile shows up as a run of rows, and of columns, that differ from the drawer ground. Two things
    make this less trivial than it sounds, and both are worth knowing rather than hiding.

    PlainTicker's own tile is invisible to the scan. Its Canvas ground is within a few units of the
    drawer's background, so it has no edge at all, and the scan finds only the pale part of the mark
    floating inside the slot. That is the finding, not a bug: it is the reason this attempt exists.

    Some neighbours are nearly as dark, MattleFun and Pyra among them, so no single row contains four
    clean columns either. So column starts are collected from every tile row in the screenshot and
    clustered; a real column appears in most rows and survives, a stray run does not. PlainTicker's
    slot is then the second column, interpolated like any other.
    """
    pixels = np.asarray(drawer.convert("RGB")).astype(int)
    ground = np.median(pixels[1700:1790, 0:30].reshape(-1, 3), axis=0)
    mask = np.abs(pixels - ground).sum(axis=2) > 40

    rows = [r for r in runs(mask.sum(axis=1), 30) if abs(r[1] - r[0] + 1 - 182) <= 6]
    assert rows, "no drawer rows of tile height found"
    size = rows[0][1] - rows[0][0] + 1

    starts = []
    for top, bottom in rows:
        band = mask[top:bottom + 1]
        starts += [c[0] for c in runs(band.sum(axis=0), 3) if c[1] - c[0] + 1 >= size - 4]
    clusters = []
    for x in sorted(starts):
        if clusters and x - clusters[-1][-1] <= 8:
            clusters[-1].append(x)
        else:
            clusters.append([x])
    columns = [round(sum(c) / len(c)) for c in clusters if len(c) >= 3]
    assert len(columns) >= 2, "could not read the column grid: {}".format(clusters)
    pitch = round((columns[-1] - columns[0]) / (len(columns) - 1))
    return size, columns[0], pitch, [r[0] for r in rows]


def runs(profile, floor):
    out, start, inside = [], 0, False
    for i, value in enumerate(profile):
        if value > floor and not inside:
            start, inside = i, True
        elif value <= floor and inside:
            out.append((start, i - 1))
            inside = False
    if inside:
        out.append((start, len(profile) - 1))
    return out


def measure_mask(drawer, box):
    """
    The launcher's mask, lifted from a tile whose ground is white and bleeds to the mask edge.

    Photos is that tile. Against the known drawer background its per-pixel alpha is the mask itself,
    and because the shape is convex the per-row left and right edges, taken at half coverage with
    linear interpolation, describe it to sub-pixel accuracy. Those edges are what is used. An
    exponent is fitted to them only so the number can be printed and sanity-checked against the
    superellipse a Pixel-family launcher is documented to cut.
    """
    x0, y0, size = box
    pixels = np.asarray(drawer.convert("RGB")).astype(float)
    ground = np.median(pixels[y0:y0 + size, max(0, x0 - 28):x0 - 8].reshape(-1, 3), axis=0)
    tile = pixels[y0:y0 + size, x0:x0 + size]
    alpha = np.clip(((tile - ground) / (255.0 - ground)).max(axis=2), 0.0, 1.0)

    edges = []
    for row in alpha:
        hot = np.where(row >= 0.5)[0]
        if len(hot) == 0:
            edges.append(None)
            continue
        left, right = hot[0], hot[-1]
        if left > 0:
            a, b = row[left - 1], row[left]
            left = left - (b - 0.5) / max(b - a, 1e-6)
        if right < size - 1:
            a, b = row[right], row[right + 1]
            right = right + (a - 0.5) / max(a - b, 1e-6)
        edges.append((float(left), float(right) + 1.0))

    exponent = fit_exponent(edges, size)
    return edges, exponent


def fit_exponent(edges, size):
    best, best_error = None, None
    for n in np.arange(2.0, 8.01, 0.05):
        error = 0.0
        for index, edge in enumerate(edges):
            if edge is None:
                continue
            y = abs((index + 0.5) - size / 2) / (size / 2)
            if y >= 1:
                continue
            want = (1 - min(y, 1.0) ** n) ** (1 / n) * (size / 2) + size / 2
            error += (want - edge[1]) ** 2
        if best_error is None or error < best_error:
            best, best_error = float(n), error
    return best


def mask_image(edges, size):
    """The measured edges as an 8-bit alpha image, supersampled vertically and horizontally."""
    big = size * SS
    canvas = Image.new("L", (big, big), 0)
    draw = ImageDraw.Draw(canvas)
    for index, edge in enumerate(edges):
        if edge is None:
            continue
        left, right = edge[0] * SS, edge[1] * SS
        draw.rectangle([left, index * SS, right - 1, index * SS + SS - 1], fill=255)
    return canvas.resize((size, size), Image.LANCZOS)


def scaled_mask(edges, source_size, size):
    """The same measured mask at another size, for the loose tiles in the gallery."""
    return mask_image(edges, source_size).resize((size, size), Image.LANCZOS)


# -- putting a candidate in the drawer -----------------------------------------------------------

def tile_for(svg_path, size, mask, recolour=None, plate=None):
    """One finished tile: the drawing, optionally on a launcher-supplied plate, cut by the mask."""
    art = render_svg(svg_path, size, recolour=recolour)
    if plate is None:
        tile = art
    else:
        tile = Image.new("RGBA", (size, size), plate + (255,))
        tile.alpha_composite(art)
    own = np.asarray(tile.getchannel("A")).astype(np.uint16)
    cut = np.asarray(mask).astype(np.uint16)
    tile.putalpha(Image.fromarray(((own * cut + 127) // 255).astype(np.uint8)))
    return tile


def place(drawer, svg_path, box, mask, ground):
    """The drawer with the candidate in PlainTicker's slot and the shipped tile painted out."""
    x0, y0, size = box
    out = drawer.copy().convert("RGBA")
    out.paste(Image.new("RGBA", (size, size), tuple(int(c) for c in ground) + (255,)), (x0, y0))
    out.alpha_composite(tile_for(svg_path, size, mask, plate=None), (x0, y0))
    return out


def main():
    RENDER.mkdir(parents=True, exist_ok=True)
    drawer = Image.open(DRAWER).convert("RGBA")
    size, first_x, pitch, row_tops = find_grid(drawer)

    # PlainTicker is the second column of the row it shares with Photos, Play Store and pump.fun.
    row_top = [y for y in row_tops if 1700 < y < 1900][0]
    box = (first_x + pitch, row_top, size)
    photos = (first_x, row_top, size)
    edges, exponent = measure_mask(drawer, photos)
    mask = mask_image(edges, size)

    pixels = np.asarray(drawer.convert("RGB")).astype(float)
    ground = np.median(pixels[row_top:row_top + size, box[0] - 26:box[0] - 8].reshape(-1, 3), axis=0)

    print("drawer {} x {}".format(drawer.width, drawer.height))
    print("tile   {} px, column pitch {} px, PlainTicker at ({}, {})".format(size, pitch, box[0], box[1]))
    print("       {} px is {:.2f}dp at 480dpi, and one 108-viewport unit is {:.2f} px".format(
        size, size / 3.0, size / VISIBLE))
    print("mask   measured off the Photos tile; best-fit superellipse exponent {:.2f}".format(exponent))
    print("ground sampled from the gutter: rgb{}".format(tuple(int(c) for c in ground)))

    import marks3
    entries = []
    for concept in marks3.CONCEPTS + [marks3.SHIPPED]:
        stem = "{:02d}-{}".format(concept.number, concept.key)
        colour_svg = SVG_DIR / (stem + ".svg")
        flat_svg = SVG_DIR / (stem + "-flat.svg")

        full = place(drawer, colour_svg, box, mask, ground)
        full.convert("RGB").save(RENDER / (stem + "-drawer.png"))

        # Two full rows and their labels: the candidate with eight real neighbours around it.
        crop = full.crop((24, row_top - 379, 24 + pitch * 4 - 48, row_top + size + 110))
        crop.convert("RGB").save(RENDER / (stem + "-crop.png"))

        entries.append({
            "concept": concept,
            "crop": crop.convert("RGB"),
            "tile": tile_for(colour_svg, 320, scaled_mask(edges, size, 320)),
            "flat": tile_for(flat_svg, 320, scaled_mask(edges, size, 320),
                             recolour=THEMED_FG, plate=THEMED_BG),
            "small": small_strip(colour_svg, flat_svg, edges, size),
            "big": tile_for(colour_svg, 400, scaled_mask(edges, size, 400)),
        })
        print("  {:02d} {:<32} -> {}-drawer.png, {}-crop.png".format(
            concept.number, concept.title, stem, stem))

    sheet = contact_sheet(entries)
    sheet.save(RENDER / "drawer-eight.png")
    print("sheet  {} ({} x {})".format(RENDER / "drawer-eight.png", sheet.width, sheet.height))

    GALLERY.write_text(gallery_html(entries, size, exponent, box), encoding="utf-8", newline=chr(10))
    print("gallery {} ({:.1f} KB)".format(GALLERY, GALLERY.stat().st_size / 1024))


def small_strip(colour_svg, flat_svg, edges, source_size):
    """
    The size test, at 1:1 and with nowhere to hide.

    16 px is smaller than anything Android will ever draw this at; it is in the gallery because a
    mark that survives 16 px has mass, and mass is the thing both previous attempts lacked. The
    flattened layer is drawn at the same sizes beside it because the themed icon and the 24dp
    notification silhouette have no colour to lean on.
    """
    sizes = (16, 24, 32, 48)
    gap, pad = 10, 8
    width = pad * 2 + sum(sizes) + gap * (len(sizes) - 1)
    strip = Image.new("RGBA", (width * 2 + gap * 3, 48 + pad * 2), PLATE + (255,))
    x = pad
    for s in sizes:
        strip.alpha_composite(tile_for(colour_svg, s, scaled_mask(edges, source_size, s)),
                              (x, pad + 48 - s))
        x += s + gap
    x += gap * 2
    for s in sizes:
        strip.alpha_composite(tile_for(flat_svg, s, scaled_mask(edges, source_size, s),
                                       recolour=THEMED_FG, plate=THEMED_BG), (x, pad + 48 - s))
        x += s + gap
    return strip


def contact_sheet(entries):
    """All eight in the real drawer, two across, at full device resolution. No ranking, no captions."""
    rows = [e for e in entries if e["concept"].number <= 8]
    cell_w, cell_h = rows[0]["crop"].width, rows[0]["crop"].height
    label = 34
    sheet = Image.new("RGB", (cell_w * 2 + 30, (cell_h + label + 20) * 4 + 10), (12, 14, 17))
    draw = ImageDraw.Draw(sheet)
    for index, entry in enumerate(rows):
        col, row = index % 2, index // 2
        x = 10 + col * (cell_w + 10)
        y = 10 + row * (cell_h + label + 20)
        draw.text((x + 2, y + 6), "{:02d}  {}".format(entry["concept"].number,
                                                      entry["concept"].title), fill=(200, 210, 222))
        sheet.paste(entry["crop"], (x, y + label))
    return sheet


# -- the gallery --------------------------------------------------------------------------------

def data_uri(image, fmt="PNG", **kw):
    buffer = io.BytesIO()
    image.save(buffer, fmt, **kw)
    return "data:image/{};base64,{}".format(
        fmt.lower(), base64.b64encode(buffer.getvalue()).decode("ascii"))


def gallery_html(entries, tile_px, exponent, box):
    cards = []
    for entry in entries:
        concept = entry["concept"]
        shipped = concept.number > 8
        cards.append(CARD.format(
            number="" if shipped else "{:02d}".format(concept.number),
            klass=" shipped" if shipped else "",
            title=escape(concept.title),
            caption=escape(concept.caption),
            treatment=escape(concept.treatment),
            breaks=escape(concept.breaks),
            gaps=escape(", ".join("{} {:g}".format(k, v) for k, v in concept.gaps.items()) or "-"),
            radius="{:.1f}".format(concept.radius()),
            tile=data_uri(entry["tile"]),
            flat=data_uri(entry["flat"]),
            small=data_uri(entry["small"]),
            crop=data_uri(entry["crop"], "JPEG", quality=94, subsampling=0),
            crop_w=entry["crop"].width,
        ))
    return PAGE.format(cards="".join(cards), tile_px=tile_px, dp="{:.0f}".format(tile_px / 3.0),
                       unit="{:.2f}".format(tile_px / VISIBLE), exponent="{:.2f}".format(exponent),
                       x=box[0], y=box[1])


def escape(text):
    return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


CARD = """
<section class="card{klass}">
  <header>
    <span class="no">{number}</span>
    <h2>{title}</h2>
    <p class="caption">{caption}</p>
  </header>
  <div class="body">
    <div class="marks">
      <figure><img class="tile" src="{tile}" alt=""><figcaption>the tile, {treatment}</figcaption></figure>
      <figure><img class="tile" src="{flat}" alt=""><figcaption>flattened to one colour</figcaption></figure>
    </div>
    <figure class="strip">
      <img src="{small}" alt="">
      <figcaption>16, 24, 32 and 48 px at 1:1 &mdash; colour, then flattened</figcaption>
    </figure>
    <dl class="facts">
      <dt>breaks</dt><dd>{breaks}</dd>
      <dt>negative space</dt><dd>{gaps} units</dd>
      <dt>furthest corner</dt><dd>{radius} of the 33-unit circle-mask guarantee</dd>
    </dl>
  </div>
  <figure class="drawer">
    <img src="{crop}" width="{crop_w}" alt="">
    <figcaption>the real drawer, this candidate in PlainTicker's slot, under the launcher's own mask</figcaption>
  </figure>
</section>
"""

PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>PlainTicker app icon, attempt three</title>
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
  .controls {{ display:flex; gap:22px; align-items:center; padding:18px 0 0; font-size:13px; color:var(--ink2); }}
  .controls label {{ display:flex; gap:8px; align-items:center; cursor:pointer; }}
  .card {{ padding:44px 0; border-bottom:1px solid var(--line); }}
  .card.shipped {{ opacity:.85; }}
  .card > header {{ display:grid; grid-template-columns:64px 1fr; gap:0 18px; align-items:baseline; }}
  .no {{ font:600 30px/1 ui-monospace, "JetBrains Mono", Consolas, monospace; color:var(--muted); }}
  .card h2 {{ font-size:21px; margin:0; font-weight:600; letter-spacing:-.01em; }}
  .caption {{ grid-column:2; margin:6px 0 0; color:var(--ink2); max-width:66ch; }}
  .card.shipped .no::after {{ content:"\\2014"; }}
  .body {{ display:grid; grid-template-columns:352px 1fr; gap:28px 32px; margin:26px 0 0 82px; align-items:start; }}
  .marks {{ display:flex; gap:32px; }}
  figure {{ margin:0; }}
  figcaption {{ margin-top:9px; font-size:11.5px; color:var(--muted); letter-spacing:.01em; }}
  img.tile {{ width:160px; height:160px; display:block; image-rendering:auto; }}
  .strip img {{ display:block; }}
  .facts {{ display:grid; grid-template-columns:118px 1fr; gap:7px 16px; margin:0; font-size:12.5px; }}
  .facts dt {{ color:var(--muted); }}
  .facts dd {{ margin:0; color:var(--ink2); }}
  .drawer {{ margin:28px 0 0 82px; }}
  .drawer img {{ display:block; max-width:100%; height:auto; border:1px solid var(--line); }}
  #actual:checked ~ .wrap .drawer img {{ max-width:none; width:auto; }}
  #actual:checked ~ .wrap .drawer {{ overflow-x:auto; }}
  #hidecap:checked ~ .wrap .caption,
  #hidecap:checked ~ .wrap .facts,
  #hidecap:checked ~ .wrap .card h2 {{ visibility:hidden; }}
  .box {{ width:15px; height:15px; border:1px solid var(--strong); display:inline-block; }}
  #actual:checked ~ .wrap label[for=actual] .box,
  #hidecap:checked ~ .wrap label[for=hidecap] .box {{ background:var(--accent); border-color:var(--accent); }}
  @media (max-width:900px) {{
    .body {{ grid-template-columns:1fr; margin-left:0; }}
    .drawer {{ margin-left:0; }}
    .card > header {{ grid-template-columns:44px 1fr; }}
  }}
</style>
</head>
<body>
<input type="checkbox" id="actual" hidden>
<input type="checkbox" id="hidecap" hidden>
<div class="wrap">
  <header class="top">
    <h1>PlainTicker app icon &mdash; eight ideas, attempt three</h1>
    <p class="lede">Eight different ideas, not eight weights of one. Each is drawn from primitive
    shapes, each is shown flattened to one colour because the themed icon and the notification
    silhouette have no colour to lean on, and each is composited into the real app drawer, in the
    tile PlainTicker occupies on the attached Seeker, under the launcher's own measured mask.</p>
    <p class="lede"><strong>Nothing here is ranked and nothing is recommended.</strong> The last two
    attempts handed over a conclusion instead of a choice, and both were rejected on sight. The
    order is the order they were drawn in. Judge them in the drawer strip first, with the captions
    hidden if you like: a mark that needs its explanation has already lost.</p>
    <p class="meta">Seeker SM02E4072810430 &middot; 1200 x 2670 at 480dpi &middot; drawer tile
    {tile_px} px, which is {dp}dp &middot; one 108-viewport unit is {unit} px &middot; launcher mask
    measured off the Photos tile, best-fit superellipse exponent {exponent} &middot; PlainTicker's
    slot at ({x}, {y})</p>
    <div class="controls">
      <label for="actual"><span class="box"></span> drawer strips at 1:1 device pixels</label>
      <label for="hidecap"><span class="box"></span> hide names and captions</label>
    </div>
  </header>
  {cards}
</div>
</body>
</html>
"""


if __name__ == "__main__":
    main()

"""
Render the icon candidates at the sizes an icon is actually seen, and build the comparison sheet.

An Android vector drawable is the source of truth, so this reads the drawable XML rather than a
parallel copy of the geometry: it parses the absolute path commands, rasterizes them with an
even-odd fill (so the counter of a traced letterform is a hole, which is how the mark this
replaces is drawn), applies the launcher mask, and composites on a launcher background.

What it produces, per candidate, one row of the sheet:
  48dp, 72dp and 108dp, circle mask and squircle mask, on a light and a dark launcher ground
  the monochrome layer alone, themed light and themed dark
  the 24dp notification silhouette alone, white on the status bar

The last two rows are frozen in candidates/*.xml so the sheet keeps comparing against them after
res/drawable has moved on: the first construction of the chosen mark, rejected at review because
every one of its shapes was centred on y 54 and the silhouette was therefore a plus sign, and the
"P" that shipped in DT3.

48dp comes first in the row because 48dp is where an icon lives, and the monochrome and 24dp
columns are the ones that decide: colour can separate two shapes, a silhouette cannot.

It also writes the two images the Solana dApp Store listing needs (docs/dapp-store-publishing.md),
out of that same rasterizer and the same shipped drawable: a flat 512 icon and a 1024x500 banner.
The store gets no vector, so the PNG is rendered from the file the launcher draws rather than
traced from a screenshot of it, and the tile ground is chased through @color/ink to its literal
value instead of being retyped here.

Run:   PYTHONIOENCODING=utf-8 python design/brand/render_icons.py
Needs: python -m pip install pillow numpy
Writes: design/brand/candidates/*.xml, design/brand/icon-candidates.png,
        design/brand/store/icon-512.png and design/brand/store/banner-1024x500.png
"""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont, PngImagePlugin

import marks as M

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
CANDIDATES = HERE / "candidates"
STORE = HERE / "store"
FONTS = ROOT / "app/src/main/res/font"
SHEET = HERE / "icon-candidates.png"

# What the phone actually draws, and what the store images are rendered from.
SHIPPED_FOREGROUND = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"
SHIPPED_BACKGROUND = ROOT / "app/src/main/res/values/ic_launcher_background.xml"
COLORS = ROOT / "app/src/main/res/values/colors.xml"
STORE_ICON = STORE / "icon-512.png"
STORE_BANNER = STORE / "banner-1024x500.png"

NS = "{http://schemas.android.com/apk/res/android}"
SS = 4              # supersampling factor for the rasterizer
DENSITY = 3         # the sheet is drawn at 3x, so 48dp is the 144px the Seeker shows

CANVAS = "#0B0F14"           # the adaptive icon background layer, an alias of the Canvas token
GROUND_DARK = "#15181D"      # a dark launcher wallpaper
GROUND_LIGHT = "#ECEEF1"     # a light launcher wallpaper
THEMED_LIGHT_BG = "#D5E2F0"  # a themed icon on a light system theme: launcher-supplied colors
THEMED_LIGHT_FG = "#182430"
THEMED_DARK_BG = "#2B3541"
THEMED_DARK_FG = "#DCE7F4"
STATUS_BAR = "#0E1116"
SHEET_BG = "#1B1E23"
SHEET_INK = "#E8ECF1"
SHEET_MUTED = "#8C97A5"
SHEET_RULE = (58, 64, 73)


# -- reading the drawable -----------------------------------------------------------------------

def read_vector(path):
    """(viewport, [(rgb tuple, [contour, ...])]) from an Android vector drawable."""
    root = ET.parse(path).getroot()
    viewport = float(root.get(NS + "viewportWidth"))
    return viewport, [
        (hex_rgb(el.get(NS + "fillColor")), contours(el.get(NS + "pathData")))
        for el in root.iter("path")
    ]


def hex_rgb(value):
    value = value.lstrip("#")
    if len(value) == 8:  # AARRGGBB
        value = value[2:]
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def shipped_ground():
    """
    The literal colour the launcher tile is, chased through the resource reference.

    res/values/ic_launcher_background.xml holds `@color/ink` rather than a hex, so anything that
    retyped Ink could drift from the tile on the phone with nothing failing. This reads the two
    files the launcher itself reads, and then checks the answer against the ground the chosen mark
    carries, which is the value glyph.py wrote that reference from.
    """
    reference = ET.parse(SHIPPED_BACKGROUND).getroot().find("color").text.strip()
    value = reference
    if value.startswith("@color/"):
        name = value.split("/", 1)[1]
        value = next(el.text.strip() for el in ET.parse(COLORS).getroot().iter("color")
                     if el.get("name") == name)
    assert re.fullmatch(r"#[0-9A-Fa-f]{6}", value), "unresolved tile ground: {}".format(reference)
    ground = M.MARKS[M.CHOSEN].ground
    assert value.upper() == ground.upper(), (
        "the shipped tile draws {} (via {}) and the mark carries {}: run glyph.py".format(
            value, reference, ground))
    return value


TOKEN = re.compile(r"[MLHVQCZ]|-?(?:\d*\.\d+|\d+)")


def contours(data):
    """Flatten absolute path data into a list of point lists, curves subdivided."""
    tokens = TOKEN.findall(data)
    assert "".join(tokens) == re.sub(r"[\s,]", "", data), "unparsed characters in " + data
    out, current = [], []
    x = y = start_x = start_y = 0.0
    command = ""
    i = 0

    def take():
        nonlocal i
        i += 1
        return float(tokens[i - 1])

    while i < len(tokens):
        if tokens[i][0].isalpha():
            command = tokens[i]
            i += 1
            if command == "Z":
                if current:
                    out.append(current)
                current = []
                x, y = start_x, start_y
            continue
        if command == "M":
            if current:
                out.append(current)
            x, y = take(), take()
            start_x, start_y = x, y
            current = [(x, y)]
        elif command == "L":
            x, y = take(), take()
            current.append((x, y))
        elif command == "H":
            x = take()
            current.append((x, y))
        elif command == "V":
            y = take()
            current.append((x, y))
        elif command == "Q":
            cx, cy, ex, ey = take(), take(), take(), take()
            for step in range(1, 17):
                t = step / 16
                u = 1 - t
                current.append((u * u * x + 2 * u * t * cx + t * t * ex,
                                u * u * y + 2 * u * t * cy + t * t * ey))
            x, y = ex, ey
        elif command == "C":
            c1x, c1y, c2x, c2y, ex, ey = (take() for _ in range(6))
            for step in range(1, 25):
                t = step / 24
                u = 1 - t
                current.append((u ** 3 * x + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t ** 3 * ex,
                                u ** 3 * y + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t ** 3 * ey))
            x, y = ex, ey
        else:
            raise AssertionError("unsupported path command " + command)
    if current:
        out.append(current)
    return out


# -- rasterizing --------------------------------------------------------------------------------

def fill_mask(contour_list, viewport, size):
    """An 8-bit coverage mask: contours XORed, which is the even-odd rule for simple contours."""
    big = size * SS
    scale = big / viewport
    total = np.zeros((big, big), dtype=bool)
    for contour in contour_list:
        if len(contour) < 3:
            continue
        one = Image.new("1", (big, big), 0)
        ImageDraw.Draw(one).polygon([(px * scale, py * scale) for px, py in contour], fill=1)
        total ^= np.asarray(one, dtype=bool)
    return Image.fromarray((total * 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)


def render_layer(vector_path, size, recolor=None):
    """The drawable as an RGBA image of `size` px, optionally forcing one color."""
    viewport, paths = read_vector(vector_path)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for color, contour_list in paths:
        flat = Image.new("RGBA", (size, size), (recolor or color) + (255,))
        out = Image.composite(flat, out, fill_mask(contour_list, viewport, size))
    return out


# -- the launcher mask --------------------------------------------------------------------------

def shape_mask(size, exponent):
    """A superellipse: exponent 2 is the circle mask, 4 the squircle a Pixel launcher applies."""
    axis = (np.arange(size * SS) + 0.5) / (size * SS) * 2 - 1
    gx, gy = np.meshgrid(axis, axis)
    inside = (np.abs(gx) ** exponent + np.abs(gy) ** exponent) <= 1.0
    return Image.fromarray((inside * 255).astype(np.uint8)).resize((size, size), Image.LANCZOS)


def flat_tile(size, foreground_path=SHIPPED_FOREGROUND, background=None):
    """
    The adaptive icon composited and left square: the 108 layers scaled so their central 72 fills
    the tile, the background layer under the foreground, no mask and no corner radius.

    This is the launcher tile up to the cut, so it is both what launcher_icon masks and what a
    store listing wants: the store applies its own mask, and a PNG that arrives pre-rounded is
    rounded twice.
    """
    layer = round(size * 108 / 72)
    tile = Image.new("RGBA", (layer, layer), hex_rgb(background or shipped_ground()) + (255,))
    tile.alpha_composite(render_layer(foreground_path, layer))
    inset = (layer - size) // 2
    return tile.crop((inset, inset, inset + size, inset + size))


def launcher_icon(foreground_path, size, exponent, background=CANVAS):
    """
    The adaptive icon as a launcher draws it: the flat tile, cut by the mask.
    """
    tile = flat_tile(size, foreground_path, background)
    tile.putalpha(shape_mask(size, exponent))
    return tile


def themed_icon(monochrome_path, size, exponent, background, foreground):
    """A themed icon: the monochrome layer in launcher-supplied colors, nothing else to lean on."""
    tile = Image.new("RGBA", (size, size), hex_rgb(background) + (255,))
    layer = round(size * 108 / 72)
    glyph = render_layer(monochrome_path, layer, recolor=hex_rgb(foreground))
    inset = (layer - size) // 2
    tile.alpha_composite(glyph.crop((inset, inset, inset + size, inset + size)))
    tile.putalpha(shape_mask(size, exponent))
    return tile


def on_ground(icon, ground, pad):
    plate = Image.new("RGBA", (icon.width + pad * 2, icon.height + pad * 2), hex_rgb(ground) + (255,))
    plate.alpha_composite(icon, (pad, pad))
    return plate.convert("RGB")


# -- the store listing images ---------------------------------------------------------------------
#
# The dApp Store takes no vector, so the two images it asks for (docs/dapp-store-publishing.md, the
# asset table) are rendered here from the same drawable the launcher draws and the same rasterizer
# the candidate sheet uses. Neither is traced from the icon: the icon is the one below, flattened.

BANNER_SIZE = (1024, 500)
STORE_ICON_SIZE = 512
WORDMARK = "PlainTicker"
# The listing's own short description, docs/dapp-store-publishing.md. No sentence is invented for
# an image: the one line on the banner is a line the store already carries under the name.
BANNER_LINE = "Read the xStock, then swap"
BANNER_MIN_MARGIN = 72      # the least the group may come to an edge a store card can crop
BANNER_MARK = 168           # the mark's own block, edge to edge
BANNER_GUTTER = 72
WORDMARK_SIZE = 92          # Outfit 600, the TopBar's own face for the app name
WORDMARK_TRACKING = -0.01   # DESIGN.md section 3: negative on headings, never positive
BANNER_LINE_SIZE = 30       # Outfit 400, body
BANNER_LEAD = 58            # wordmark baseline to line baseline


def bundled_font(name, size):
    """A face the app itself ships in res/font. No system fallback: DESIGN.md section 8 bans the
    two faces a fallback would reach for."""
    path = FONTS / name
    assert path.exists(), "{} is not bundled in res/font, see docs/fonts.md".format(name)
    return ImageFont.truetype(str(path), size)


def mark_bounds(foreground_path=SHIPPED_FOREGROUND):
    """The block the mark occupies, in viewport units, read off the shipped drawable itself."""
    _, paths = read_vector(foreground_path)
    points = [point for _, contour_list in paths for contour in contour_list for point in contour]
    return (min(p[0] for p in points), min(p[1] for p in points),
            max(p[0] for p in points), max(p[1] for p in points))


def mark_block(size, foreground_path=SHIPPED_FOREGROUND):
    """The mark alone, cropped to its own bounds, `size` px across, on transparent."""
    viewport, _ = read_vector(foreground_path)
    x0, y0, x1, y1 = mark_bounds(foreground_path)
    scale = size / max(x1 - x0, y1 - y0)
    art = render_layer(foreground_path, round(viewport * scale))
    return art.crop((round(x0 * scale), round(y0 * scale), round(x1 * scale), round(y1 * scale)))


def tracked_text(draw, x, baseline, text, face, fill, tracking=0.0):
    """One line set on a baseline, letter by letter, so tracking can be negative."""
    for character in text:
        draw.text((x, baseline), character, font=face, fill=fill, anchor="ls")
        x += draw.textlength(character, font=face) + tracking
    return x - tracking


def banner_layout():
    """
    Where the mark and the type sit on the banner, in one place so the run can check its own work.

    The stack is left-aligned to itself and the group is centred on the field: a listing card can
    be cropped from either edge, and a group hard against one of them loses the mark or the name.
    """
    width, height = BANNER_SIZE
    word_face = bundled_font("outfit_semibold.ttf", WORDMARK_SIZE)
    line_face = bundled_font("outfit_regular.ttf", BANNER_LINE_SIZE)
    tracking = WORDMARK_TRACKING * WORDMARK_SIZE
    word_width = word_face.getlength(WORDMARK) + tracking * (len(WORDMARK) - 1)
    margin = round((width - (BANNER_MARK + BANNER_GUTTER
                             + max(word_width, line_face.getlength(BANNER_LINE)))) / 2)
    assert margin >= BANNER_MIN_MARGIN, "the group is wider than the field leaves room for"
    # Vertical: the wordmark's cap height down to the line's baseline, centred as one block.
    top = word_face.getbbox(WORDMARK, anchor="ls")[1]          # negative: cap height above baseline
    bottom = line_face.getbbox(BANNER_LINE, anchor="ls")[3]
    return {
        "word_face": word_face,
        "line_face": line_face,
        "tracking": tracking,
        "mark": (margin, round((height - BANNER_MARK) / 2)),
        "text_left": margin + BANNER_MARK + BANNER_GUTTER,
        "baseline": round((height - (BANNER_LEAD + bottom - top)) / 2 - top),
    }


def store_banner():
    """
    The 1024x500 listing banner: the icon's own field, widened.

    Nothing here is a second design. The ground is the tile's Ink, the mark is the same Canvas
    rectangles out of the same drawable at the same proportions, and the type is the app's own face
    saying the name and the short description the listing already carries. Seen small beside other
    entries it is the icon with a name next to it, which is what a reader needs it to be.
    """
    banner = Image.new("RGB", BANNER_SIZE, hex_rgb(shipped_ground()))
    on_tile = hex_rgb(M.CANVAS)     # the mark's colour, and so the type's
    where = banner_layout()

    mark = mark_block(BANNER_MARK)
    banner.paste(mark, where["mark"], mark)

    draw = ImageDraw.Draw(banner)
    tracked_text(draw, where["text_left"], where["baseline"], WORDMARK,
                 where["word_face"], on_tile, where["tracking"])
    tracked_text(draw, where["text_left"], where["baseline"] + BANNER_LEAD, BANNER_LINE,
                 where["line_face"], on_tile)
    return banner


def stamp(source):
    """The 'generated by, do not edit by hand' line the drawables carry, for a format with no
    comments in it."""
    info = PngImagePlugin.PngInfo()
    info.add_text("Software", "design/brand/render_icons.py")
    info.add_text("Comment", "Generated by design/brand/render_icons.py from {}, "
                             "do not edit by hand.".format(source))
    return info


def write_store():
    """Both listing images, and the pixels that say the mark is on them."""
    STORE.mkdir(parents=True, exist_ok=True)
    ground = shipped_ground()
    source = "{} over {}".format(SHIPPED_FOREGROUND.relative_to(ROOT).as_posix(),
                                 SHIPPED_BACKGROUND.relative_to(ROOT).as_posix())
    icon = flat_tile(STORE_ICON_SIZE).convert("RGB")
    icon.save(STORE_ICON, pnginfo=stamp(source))
    banner = store_banner()
    banner.save(STORE_BANNER, pnginfo=stamp(source))

    # Three probes each: the field, the empty centre of the mark, and one arm of it, addressed in
    # viewport units. The first two are the tile ground and the third is the mark, so a blank
    # image, an inverted one or a mark that missed its ground cannot pass this.
    block_x, block_y, block_x1, block_y1 = mark_bounds()
    unit = STORE_ICON_SIZE / M.VISIBLE                  # one viewport unit in the icon's pixels
    arm = BANNER_MARK / max(block_x1 - block_x, block_y1 - block_y)     # and in the banner's
    edge = (M.VIEWPORT - M.VISIBLE) / 2                 # the icon shows the central 72 of the 108
    mark_x, mark_y = banner_layout()["mark"]

    def icon_at(x, y):
        return round((x - edge) * unit), round((y - edge) * unit)

    def banner_at(x, y):
        return round(mark_x + (x - block_x) * arm), round(mark_y + (y - block_y) * arm)

    for image, path, at in ((icon, STORE_ICON, icon_at), (banner, STORE_BANNER, banner_at)):
        print("wrote {} ({} x {} {}) on @color/ink = {}, mark {}".format(
            path.relative_to(ROOT).as_posix(), image.width, image.height, image.mode,
            ground, M.CANVAS))
        for what, point in (("field", (0, 0)),
                            ("mark centre", at(54, 54)),
                            ("top-left arm", at(33, 33))):
            found = "#%02X%02X%02X" % image.getpixel(point)
            expected = ground if what != "top-left arm" else M.CANVAS
            assert found.upper() == expected.upper(), (
                "{} at {} is {}, expected {}".format(what, point, found, expected))
            print("    {:14} {} {}".format(what, point, found))


# -- the sheet ----------------------------------------------------------------------------------

def font(name, size):
    try:
        return ImageFont.truetype(str(FONTS / name), size)
    except OSError:
        return ImageFont.load_default()


NL = chr(10)
COLUMNS = [
    ("48dp circle" + NL + "dark ground", 48, 2, GROUND_DARK),
    ("48dp circle" + NL + "light ground", 48, 2, GROUND_LIGHT),
    ("48dp squircle" + NL + "dark ground", 48, 4, GROUND_DARK),
    ("48dp squircle" + NL + "light ground", 48, 4, GROUND_LIGHT),
    ("72dp squircle" + NL + "dark ground", 72, 4, GROUND_DARK),
    ("108dp squircle" + NL + "light ground", 108, 4, GROUND_LIGHT),
]
EXTRA = [
    "mono layer" + NL + "light theme",
    "mono layer" + NL + "dark theme",
    "24dp icon" + NL + "notification",
]


def build_sheet(rows):
    label = font("outfit_semibold.ttf", 13 * DENSITY)
    title = font("outfit_semibold.ttf", 17 * DENSITY)
    meta = font("jetbrains_mono_regular.ttf", 7 * DENSITY)
    pad = 6 * DENSITY
    gutter = 10 * DENSITY
    name_width = 150 * DENSITY

    cells = [size * DENSITY + pad * 2 for _, size, _, _ in COLUMNS] + [48 * DENSITY + pad * 2] * 3
    headers = [c[0] for c in COLUMNS] + EXTRA
    row_height = max(cells) + 16 * DENSITY
    top = 56 * DENSITY
    width = name_width + sum(cells) + gutter * (len(cells) + 1)
    height = top + row_height * len(rows) + 14 * DENSITY

    sheet = Image.new("RGB", (width, height), hex_rgb(SHEET_BG))
    draw = ImageDraw.Draw(sheet)
    draw.text((gutter, 11 * DENSITY), "PlainTicker app icon: candidates at the sizes they are seen",
              font=title, fill=hex_rgb(SHEET_INK))

    x = name_width + gutter
    for header, cell in zip(headers, cells):
        draw.multiline_text((x + cell / 2, 33 * DENSITY), header, font=meta, fill=hex_rgb(SHEET_MUTED),
                            anchor="ma", align="center", spacing=3 * DENSITY)
        x += cell + gutter

    for index, row in enumerate(rows):
        y = top + index * row_height
        draw.line([(gutter, y), (width - gutter, y)], fill=SHEET_RULE, width=1)
        draw.text((gutter, y + 10 * DENSITY), row["title"], font=label, fill=hex_rgb(SHEET_INK))
        draw.multiline_text((gutter, y + 27 * DENSITY), row["note"], font=meta,
                            fill=hex_rgb(SHEET_MUTED), spacing=4 * DENSITY)
        x = name_width + gutter
        for (_, size, exponent, ground), cell in zip(COLUMNS, cells):
            # The mark's own ground, not a hardcoded Canvas. Four rejected icons all put a Canvas
            # tile on a near-black wallpaper, and a sheet that draws every candidate on Canvas
            # cannot show that: the chosen mark is Canvas-coloured over an Ink tile and would come
            # out of this renderer invisible.
            icon = launcher_icon(row["foreground"], size * DENSITY, exponent,
                                 background=row.get("ground", CANVAS))
            sheet.paste(on_ground(icon, ground, pad), (x, y + 10 * DENSITY))
            x += cell + gutter
        for background, foreground, ground in (
            (THEMED_LIGHT_BG, THEMED_LIGHT_FG, GROUND_LIGHT),
            (THEMED_DARK_BG, THEMED_DARK_FG, GROUND_DARK),
        ):
            tile = themed_icon(row["monochrome"], 48 * DENSITY, 4, background, foreground)
            sheet.paste(on_ground(tile, ground, pad), (x, y + 10 * DENSITY))
            x += cells[-1] + gutter
        strip = Image.new("RGBA", (48 * DENSITY + pad * 2, 48 * DENSITY + pad * 2), hex_rgb(STATUS_BAR) + (255,))
        strip.alpha_composite(render_layer(row["stat"], 24 * DENSITY), (pad + 12 * DENSITY, pad + 12 * DENSITY))
        sheet.paste(strip.convert("RGB"), (x, y + 10 * DENSITY))
    return sheet


def write_candidates():
    """Every candidate as real drawables on disk, which is also what the sheet reads."""
    CANDIDATES.mkdir(parents=True, exist_ok=True)
    rows = []
    for mark in M.MARKS.values():
        files = {}
        for layer, text in (("foreground", mark.foreground()),
                            ("monochrome", mark.monochrome()),
                            ("stat", mark.stat())):
            target = CANDIDATES / "{}_{}.xml".format(mark.key, layer)
            target.write_text(text, newline=NL)
            files[layer] = target
        x0, y0, x1, y1 = mark.bounds()
        note = NL.join([
            "x {} to {}".format(M.number(x0), M.number(x1)),
            "y {} to {}".format(M.number(y0), M.number(y1)),
            "r {:.2f}, mask {:+.2f}".format(mark.radius(), mark.mask_clearance()),
            "  ".join("{:g}x{:g}".format(r.width, r.height) for r in mark.rects),
        ])
        rows.append(dict(title=mark.title, note=note, ground=mark.ground, **files))
    return rows


def main():
    rows = write_candidates()
    rows.append({
        "title": "Gauge, first cut",
        "note": NL.join(["rejected: every", "shape on y 54, so", "in one colour the", "mark is a plus"]),
        "foreground": CANDIDATES / "crossed_foreground.xml",
        "monochrome": CANDIDATES / "crossed_monochrome.xml",
        "stat": CANDIDATES / "crossed_stat.xml",
    })
    rows.append({
        "title": "Shipped (P)",
        "note": NL.join(["the mark being", "replaced: cap 40,", "accent tick 2 high"]),
        "foreground": CANDIDATES / "shipped_foreground.xml",
        "monochrome": CANDIDATES / "shipped_monochrome.xml",
        "stat": CANDIDATES / "shipped_stat.xml",
    })
    sheet = build_sheet(rows)
    sheet.save(SHEET)
    print("wrote {} ({} x {})".format(SHEET, sheet.width, sheet.height))
    for row in rows:
        print("  row {:12} <- {}".format(row["title"], Path(row["foreground"]).name))
    write_store()


if __name__ == "__main__":
    main()

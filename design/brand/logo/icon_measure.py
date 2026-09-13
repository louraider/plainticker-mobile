"""
The derived launcher icon, composited into the real Seeker drawer and measured there.

Nothing here re-measures the phone. `composite5.edge_contrast` is the measurement attempt five was
called to make and it is imported rather than rewritten, along with attempt three's rig: the drawer
grid found by scanning the screenshot, the launcher mask lifted off the Photos tile to sub-pixel
accuracy, the ground sampled from the gutter beside PlainTicker's own slot, the rasterizer and the
1:1 size strip. The only thing this file adds is which drawings go through it.

Every candidate is measured in all three treatments, because the ground question is the one attempt
five settled with a number and the icon is the one surface where it decides the answer. A Canvas
tile reads 1.03 to 1 against the drawer, which is no boundary at all. An Accent field with the mark
knocked out reads about 7.27, and pays for it by flattening a two-colour mark into one silhouette.
A cool off-white field reads about 16.89, against Photos's 18.06, and is the only treatment that
keeps the mark's two-colour structure. No earlier attempt measured the third one.

Run:   PYTHONIOENCODING=utf-8 python design/brand/logo/icon_measure.py
Needs: python -m pip install pillow numpy
Reads: design/brand/attempt-three/drawer.png, design/brand/logo/icon/*.svg
Writes: design/brand/logo/render/*.png, design/brand/logo/edge-contrast.json
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.insert(0, str(BRAND / "attempt-five"))
sys.path.insert(0, str(HERE))

import numpy as np                         # noqa: E402

import composite as C3                     # noqa: E402  the measuring rig, attempt three
import composite5 as C5                    # noqa: E402  the edge measurement, attempt five
import marks_logo                          # noqa: E402
import render_logo as R                    # noqa: E402
import wordmark as W                       # noqa: E402
from marks3 import GROUND                  # noqa: E402
from render_icons import shape_mask        # noqa: E402

ICON_DIR = HERE / "icon"
RENDER = HERE / "render"
EDGES_JSON = HERE / "edge-contrast.json"
BIG = 240
NL = chr(10)


def variants():
    """Every candidate in both treatments, then the control. Keys match the SVG filenames."""
    out = []
    for mark in marks_logo.CANDIDATES:
        out.append(mark)
        if mark.treatment == GROUND:
            out.append(marks_logo.inverted_of(mark))
            out.append(marks_logo.paper_of(mark))
    for mark in marks_logo.ASKED:
        out.append(mark)
        out.append(marks_logo.paper_of(mark))
    out.append(marks_logo.CONTROL)
    return out


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

    print("drawer {} x {}, tile {} px ({:.1f}dp at 480dpi), mask exponent {:.2f}".format(
        drawer.width, drawer.height, size, size / 3.0, exponent))
    print("ground sampled from the gutter beside PlainTicker's own slot: rgb{}".format(ground_rgb))

    references = {
        "the drawer as it is, PlainTicker's own slot": C5.edge_contrast(drawer, box, mask),
        "Photos, the brightest tile in the same row": C5.edge_contrast(drawer, photos, mask),
    }
    for name, value in references.items():
        print("  reference  {:>5.2f} to 1   {}".format(value, name))

    entries = []
    for mark in variants():
        key = mark.key
        colour_svg = ICON_DIR / (key + ".svg")
        flat_svg = ICON_DIR / (key + "-flat.svg")
        if not colour_svg.exists():
            colour_svg.write_text(mark.icon_svg(), encoding="utf-8", newline=NL)
            flat_svg.write_text(mark.icon_flat_svg(), encoding="utf-8", newline=NL)

        full = C3.place(drawer, colour_svg, box, mask, ground)
        contrast = C5.edge_contrast(full, box, mask)
        crop = full.crop((24, row_top - 379, 24 + pitch * 4 - 48, row_top + size + 110))
        crop.convert("RGB").save(RENDER / (key + "-crop.png"))

        C5.on_plate(C3.tile_for(colour_svg, size, mask), 18, ground_rgb).save(
            RENDER / (key + "-60dp.png"))
        C5.guides(C3.render_svg(colour_svg, 324, crop_to_visible=False)).save(
            RENDER / (key + "-108dp-safe.png"))
        C5.on_plate(C3.tile_for(flat_svg, BIG, C3.scaled_mask(edges, size, BIG),
                                recolour=C3.THEMED_FG, plate=C3.THEMED_BG),
                    18, C3.PLATE).save(RENDER / (key + "-monochrome.png"))
        C5.on_plate(C3.tile_for(colour_svg, BIG, C3.scaled_mask(edges, size, BIG)),
                    18, ground_rgb).save(RENDER / (key + "-measured.png"))
        C5.on_plate(C3.tile_for(colour_svg, BIG, shape_mask(BIG, 2)), 18, ground_rgb).save(
            RENDER / (key + "-circle.png"))
        C3.small_strip(colour_svg, flat_svg, edges, size).save(RENDER / (key + "-small.png"))

        entries.append({"key": key, "contrast": contrast, "crop": crop.convert("RGB")})
        print("  {:<28} edge {:>5.2f} to 1".format(key, contrast))

    sheet = contact_sheet(entries)
    sheet.save(RENDER / "drawer-contact-sheet.png")
    print("sheet  {} ({} x {})".format(RENDER / "drawer-contact-sheet.png",
                                       sheet.width, sheet.height))

    readings = {"references": references,
                "candidates": {e["key"]: e["contrast"] for e in entries},
                "chosen": marks_logo.CHOSEN + "-paper"}
    EDGES_JSON.write_text(json.dumps(readings, indent=2), encoding="utf-8", newline=NL)
    print("edges  {}".format(EDGES_JSON))


def contact_sheet(entries):
    """Every candidate in the real drawer, in PlainTicker's own slot, two across."""
    cell_w, cell_h = entries[0]["crop"].width, entries[0]["crop"].height
    label = 34
    rows = (len(entries) + 1) // 2
    sheet = Image.new("RGB", (cell_w * 2 + 30, (cell_h + label + 20) * rows + 10), (12, 14, 17))
    draw = ImageDraw.Draw(sheet)
    font = R.pil_font(W.OUTFIT, 17)
    for index, entry in enumerate(entries):
        col, row = index % 2, index // 2
        x = 10 + col * (cell_w + 10)
        y = 10 + row * (cell_h + label + 20)
        draw.text((x + 2, y + 6), "{}   edge {:.2f} to 1".format(entry["key"], entry["contrast"]),
                  font=font, fill=(200, 210, 222))
        sheet.paste(entry["crop"], (x, y + label))
    return sheet


if __name__ == "__main__":
    main()

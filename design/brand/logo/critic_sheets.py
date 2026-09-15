"""
The three sheets the second critic is shown, because Codex is given pixels rather than geometry.

Attempt five learned that the two critics are only worth running together if they are looking at
different things: Antigravity gets the numbers and argues about the idea, Codex gets the rendered
article and argues about what an eye actually receives. These are the renders for the second one.

Run:   PYTHONIOENCODING=utf-8 python design/brand/logo/critic_sheets.py
Writes: design/brand/logo/critique/sheet-*.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.insert(0, str(HERE))

import marks_logo as M          # noqa: E402
import render_logo as R         # noqa: E402
import wordmark as W            # noqa: E402

OUT = HERE / "critique"
RENDER = HERE / "render"
PLATE = (16, 19, 23)
LABEL = (176, 188, 202)


def label_font(px):
    return R.pil_font(W.OUTFIT, px)


def sheet_marks():
    """Every candidate at the measured drawer size, 1:1, and again at 24dp, on the drawer ground."""
    marks = M.ALL
    tile, small, pad, gap, lab = 182, 72, 26, 30, 26
    cols = 4
    rows = (len(marks) + cols - 1) // cols
    cell_w = tile + gap + small
    cell_h = tile + lab
    img = Image.new("RGB", (pad + cols * (cell_w + pad), pad + rows * (cell_h + pad + 14)),
                    R.DARK)
    draw = ImageDraw.Draw(img)
    font = label_font(16)
    for index, mark in enumerate(marks):
        col, row = index % cols, index // cols
        x = pad + col * (cell_w + pad)
        y = pad + row * (cell_h + pad + 14)
        big = R.mark_image(mark, tile, R.DARK, pad_ratio=0.0, cropped=False)
        img.paste(big.resize((tile, tile), Image.LANCZOS), (x, y))
        tiny = R.mark_image(mark, small, R.DARK, pad_ratio=0.0, cropped=False)
        img.paste(tiny.resize((small, small), Image.LANCZOS), (x + tile + gap, y + tile - small))
        draw.text((x, y + tile + 6), mark.key, font=font, fill=LABEL)
    return img


def sheet_lockups():
    """The three wordmark settings, then the horizontal lockup with each mark."""
    pad = 22
    rows = []
    for setting in W.SETTINGS:
        rows.append(("wordmark, " + setting.key, R.wordmark_image(setting, 46, R.DARK)))
    for mark in M.ALL:
        rows.append(("lockup, " + mark.key,
                     R.lockup_image(mark, W.chosen_setting(), "horizontal", 42, R.DARK)))
    rows.append(("stacked, " + M.CHOSEN,
                 R.lockup_image(M.chosen(), W.chosen_setting(), "stacked", 42, R.DARK)))
    width = max(i.width for _, i in rows) + pad * 2 + 210
    height = sum(i.height for _, i in rows) + pad * (len(rows) + 1)
    img = Image.new("RGB", (width, height), R.DARK)
    draw = ImageDraw.Draw(img)
    font = label_font(15)
    y = pad
    for name, image in rows:
        img.paste(image, (pad, y))
        draw.text((width - pad, y + image.height // 2), name, font=font, fill=LABEL, anchor="rm")
        y += image.height + pad
    return img


def sheet_drawer():
    """The chosen mark's three grounds in the real drawer, with the measured edge contrast."""
    keys = ["{}".format(M.CHOSEN), "{}-inverted".format(M.CHOSEN), "{}-paper".format(M.CHOSEN),
            "short-measure-paper", "overhang-paper", "gauge-inverted"]
    import json
    edges = json.loads((HERE / "edge-contrast.json").read_text(encoding="utf-8"))
    crops = [(k, Image.open(RENDER / (k + "-crop.png"))) for k in keys
             if (RENDER / (k + "-crop.png")).exists()]
    pad, lab = 12, 30
    cell_w, cell_h = crops[0][1].width, crops[0][1].height
    scale = 0.62
    cw, ch = int(cell_w * scale), int(cell_h * scale)
    cols = 2
    rows = (len(crops) + cols - 1) // cols
    img = Image.new("RGB", (pad + cols * (cw + pad), pad + rows * (ch + lab + pad)), PLATE)
    draw = ImageDraw.Draw(img)
    font = label_font(16)
    for index, (key, crop) in enumerate(crops):
        col, row = index % cols, index // cols
        x = pad + col * (cw + pad)
        y = pad + row * (ch + lab + pad)
        draw.text((x + 2, y + 4), "{}   edge {:.2f} to 1".format(
            key, edges["candidates"].get(key, float("nan"))), font=font, fill=LABEL)
        img.paste(crop.resize((cw, ch), Image.LANCZOS), (x, y + lab))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, image in (("sheet-marks.png", sheet_marks()),
                        ("sheet-lockups.png", sheet_lockups()),
                        ("sheet-drawer.png", sheet_drawer())):
        path = OUT / name
        image.convert("RGB").save(path, quality=92)
        print("  {}  {} x {}".format(path, image.width, image.height))


if __name__ == "__main__":
    main()

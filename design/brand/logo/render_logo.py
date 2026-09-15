"""
The logo drawn where it is actually looked at: a headline, a row of text, and four small sizes.

Nothing here parses the SVGs. The wordmark is drawn from the same fonts, at the same pen positions
wordmark.py computes, and the mark is drawn from the same rectangles marks_logo.py holds, so a
render and a deliverable cannot drift apart. Everything is drawn at SS times the final size and
filtered down, which is what the phone's own rasterizer does and what decides whether a 6-unit
shape exists at 16dp.

Run:   PYTHONIOENCODING=utf-8 python design/brand/logo/render_logo.py
Needs: python -m pip install pillow
Writes: design/brand/logo/render/*.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
REPO = BRAND.parent.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.insert(0, str(HERE))

import marks_logo   # noqa: E402
import wordmark as W  # noqa: E402
from marks3 import ACCENT, CANVAS, INK, INVERTED  # noqa: E402
from composite import rgb  # noqa: E402  the repository's one hex-to-RGB
from marks_logo import INK_LIGHT, PAPER  # noqa: E402

RENDER = HERE / "render"
SS = 4
DPI = 3.0            # the Seeker is 480dpi, so 1dp is 3 device pixels

DARK = rgb(CANVAS)
LIGHT = rgb(PAPER)
ONE_DARK = (255, 255, 255)
ONE_LIGHT = (0, 0, 0)


def palette(ground, one_colour=False):
    """(ink, accent, hole) for a ground. One colour means exactly that: no accent left."""
    if one_colour:
        ink = ONE_DARK if ground == DARK else ONE_LIGHT
        return ink, ink, ground
    if ground == LIGHT:
        return rgb(INK_LIGHT), rgb(ACCENT), LIGHT
    return rgb(INK), rgb(ACCENT), DARK


# -- the wordmark ---------------------------------------------------------------------------------

_FONT_CACHE = {}


def pil_font(face, px):
    key = (face.path, round(px, 2))
    if key not in _FONT_CACHE:
        _FONT_CACHE[key] = ImageFont.truetype(str(face.path), max(1, int(round(px))))
    return _FONT_CACHE[key]


def draw_wordmark(draw, setting, cap_px, x, baseline, colour):
    """The setting at a given cap height, glyph by glyph, at wordmark.py's own pen positions."""
    scale = cap_px / W.CAP
    items, _ = setting.glyphs()
    for face, char, size, pen_x in items:
        draw.text((x + pen_x * scale, baseline), char,
                  font=pil_font(face, size * scale), fill=colour, anchor="ls")


def wordmark_image(setting, cap_px, ground, one_colour=False, margin=None):
    ink, _, _ = palette(ground, one_colour)
    x0, y0, x1, y1 = setting.box()
    scale = cap_px / W.CAP
    pad = margin if margin is not None else cap_px * 0.42
    width = int(round((x1 - x0) * scale + pad * 2))
    height = int(round((y1 - y0) * scale + pad * 2))
    big = Image.new("RGB", (width * SS, height * SS), ground)
    draw = ImageDraw.Draw(big)
    draw_wordmark(draw, setting, cap_px * SS, (pad - x0 * scale) * SS, (pad - y0 * scale) * SS, ink)
    return big.resize((width, height), Image.LANCZOS)


def inline_row(setting, body_px, ground, one_colour=False):
    """
    The wordmark inside a line of running text, which is where a logo is usually smallest.

    The sentence is the product's own: README's first line, cut to length. The wordmark is set to
    the cap height of the body face beside it, because that is what a page will do to it.
    """
    ink, _, _ = palette(ground, one_colour)
    body = pil_font(W.OUTFIT, body_px)
    cap_px = body_px * W.OUTFIT.cap / W.OUTFIT.upem
    before, after = "Read the token first with ", ", before the swap."
    pad = int(round(body_px * 0.7))
    scale = cap_px / W.CAP
    x0, _, x1, _ = setting.box()
    word_w = (x1 - x0) * scale
    w_before = body.getlength(before)
    width = int(round(pad * 2 + w_before + word_w + body.getlength(after)))
    height = int(round(body_px * 2.2))
    baseline = int(round(height * 0.62))
    big = Image.new("RGB", (width * SS, height * SS), ground)
    draw = ImageDraw.Draw(big)
    big_body = pil_font(W.OUTFIT, body_px * SS)
    draw.text((pad * SS, baseline * SS), before, font=big_body, fill=ink, anchor="ls")
    draw_wordmark(draw, setting, cap_px * SS, (pad + w_before - x0 * scale) * SS, baseline * SS,
                  ink)
    draw.text(((pad + w_before + word_w) * SS, baseline * SS), after, font=big_body, fill=ink,
              anchor="ls")
    return big.resize((width, height), Image.LANCZOS)


# -- the mark -------------------------------------------------------------------------------------

def draw_mark(draw, mark, box, ground, one_colour=False, cropped=True):
    """
    The mark into a box. `cropped` draws the mark's own bounding box; otherwise the 108 viewport.
    """
    ink, accent, hole = palette(ground, one_colour)
    x, y, size_w, size_h = box
    if cropped:
        bx0, by0, bx1, by1 = mark.bbox()
        pad = mark.FIELD_PAD if mark.treatment == INVERTED else 0.0
        bx0, by0, bx1, by1 = bx0 - pad, by0 - pad, bx1 + pad, by1 + pad
    else:
        bx0, by0, bx1, by1 = 0.0, 0.0, 108.0, 108.0
    sx, sy = size_w / (bx1 - bx0), size_h / (by1 - by0)

    def place(shape):
        return [x + (shape.x - bx0) * sx, y + (shape.y - by0) * sy,
                x + (shape.x + shape.w - bx0) * sx - 1, y + (shape.y + shape.h - by0) * sy - 1]

    if mark.treatment == INVERTED:
        field = accent if not one_colour else ink
        draw.rectangle([x, y, x + size_w - 1, y + size_h - 1], fill=field)
        for shape in mark.marks():
            if shape.cut:
                draw.rectangle(place(shape), fill=hole)
        return
    for shape in mark.marks():
        if shape.cut:
            continue
        draw.rectangle(place(shape), fill=ink if shape.colour == INK else accent)


def mark_image(mark, height_px, ground, one_colour=False, pad_ratio=0.5, cropped=True):
    if cropped:
        bx0, by0, bx1, by1 = mark.bbox()
        extra = mark.FIELD_PAD * 2 if mark.treatment == INVERTED else 0.0
        ratio = (bx1 - bx0 + extra) / (by1 - by0 + extra)
    else:
        ratio = 1.0
    width = int(round(height_px * ratio))
    pad = int(round(height_px * pad_ratio))
    canvas = Image.new("RGB", (width + pad * 2, height_px + pad * 2), ground)
    big = canvas.resize(((width + pad * 2) * SS, (height_px + pad * 2) * SS), Image.NEAREST)
    draw = ImageDraw.Draw(big)
    draw_mark(draw, mark, (pad * SS, pad * SS, width * SS, height_px * SS), ground, one_colour,
              cropped)
    return big.resize((width + pad * 2, height_px + pad * 2), Image.LANCZOS)


def size_strip(mark, ground, one_colour=False):
    """
    The mark at 16, 24, 48 and 60.7dp on one baseline, at 1:1 device pixels, nowhere to hide.

    60.7dp is the measured Seeker drawer tile; 48dp is what an icon brief assumes; 24dp is the
    notification size; 16dp is a favicon and the smallest a logo is ever asked to be.
    """
    sizes = [16.0, 24.0, 48.0, 60.7]
    bx0, by0, bx1, by1 = mark.bbox()
    extra = mark.FIELD_PAD * 2 if mark.treatment == INVERTED else 0.0
    ratio = (bx1 - bx0 + extra) / (by1 - by0 + extra)
    gap, pad = 26, 22
    heights = [int(round(dp * DPI)) for dp in sizes]
    widths = [int(round(h * ratio)) for h in heights]
    width = pad * 2 + sum(widths) + gap * (len(sizes) - 1)
    height = pad * 2 + max(heights) + 26
    big = Image.new("RGB", (width * SS, height * SS), ground)
    draw = ImageDraw.Draw(big)
    label = pil_font(W.OUTFIT, 11 * SS)
    ink, _, _ = palette(ground, one_colour)
    x = pad
    base = pad + max(heights)
    for dp, h, w in zip(sizes, heights, widths):
        draw_mark(draw, mark, (x * SS, (base - h) * SS, w * SS, h * SS), ground, one_colour)
        draw.text(((x + w / 2) * SS, (base + 16) * SS), "{:g}dp".format(dp), font=label,
                  fill=tuple(int(c * 0.62 + 0.38 * g) for c, g in zip(ink, ground)),
                  anchor="ms")
        x += w + gap
    return big.resize((width, height), Image.LANCZOS)


# -- lockups --------------------------------------------------------------------------------------

def lockup_image(mark, setting, layout, cap_px, ground, one_colour=False, caps=None):
    ink, _, _ = palette(ground, one_colour)
    bx0, by0, bx1, by1 = mark.bbox()
    pad_units = mark.FIELD_PAD if mark.treatment == INVERTED else 0.0
    mw = (bx1 - bx0 + pad_units * 2)
    mh = (by1 - by0 + pad_units * 2)
    on_baseline = caps is None and mark.baseline is not None and mark.cap
    if on_baseline:
        mark_h = cap_px * mh / mark.cap
    else:
        mark_h = cap_px * (W.MARK_CAPS if caps is None else caps)
    mark_w = mark_h * mw / mh
    scale = cap_px / W.CAP
    unit_px = mark_h / mh
    to_baseline = ((mark.baseline - (by0 - pad_units)) * unit_px) if on_baseline else mark_h / 2.0
    wx0, wy0, wx1, wy1 = setting.box()
    word_w, word_h = (wx1 - wx0) * scale, (wy1 - wy0) * scale
    gap = W.GAP_EMS * W.SIZE * scale
    pad = int(round(cap_px * 0.45))

    if layout == "horizontal":
        width = int(round(mark_w + gap + word_w)) + pad * 2
        height = int(round(max(mark_h, word_h))) + pad * 2
        baseline = pad - wy0 * scale
        mark_top = baseline - to_baseline if on_baseline else             baseline + wy0 * scale + (word_h - mark_h) / 2.0
        lift = min(0.0, mark_top - pad)
        baseline, mark_top = baseline - lift, mark_top - lift
        height = int(round(max(baseline + wy1 * scale, mark_top + mark_h))) + pad
        mark_box = (pad, mark_top, mark_w, mark_h)
        word_x = pad + mark_w + gap
    else:
        stack_gap = W.STACK_EMS * W.SIZE * scale
        width = int(round(max(mark_w, word_w))) + pad * 2
        height = int(round(mark_h + stack_gap + word_h)) + pad * 2
        mark_box = (pad, pad, mark_w, mark_h)
        baseline = pad + mark_h + stack_gap - wy0 * scale
        word_x = pad

    big = Image.new("RGB", (width * SS, height * SS), ground)
    draw = ImageDraw.Draw(big)
    draw_mark(draw, mark, tuple(v * SS for v in mark_box), ground, one_colour)
    draw_wordmark(draw, setting, cap_px * SS, (word_x - wx0 * scale) * SS, baseline * SS, ink)
    return big.resize((width, height), Image.LANCZOS)


# -- the sheet ------------------------------------------------------------------------------------

def main():
    RENDER.mkdir(parents=True, exist_ok=True)
    written = []

    def save(image, name):
        path = RENDER / name
        image.save(path)
        written.append((name, image.size))

    for setting in W.SETTINGS:
        save(wordmark_image(setting, 64, DARK), "wordmark-{}-headline-dark.png".format(setting.key))
        save(wordmark_image(setting, 64, LIGHT), "wordmark-{}-headline-light.png".format(
            setting.key))
        save(wordmark_image(setting, 64, DARK, one_colour=True),
             "wordmark-{}-headline-onecolour.png".format(setting.key))
        save(inline_row(setting, 17, DARK), "wordmark-{}-inline-dark.png".format(setting.key))
        save(inline_row(setting, 17, LIGHT), "wordmark-{}-inline-light.png".format(setting.key))

    for mark in marks_logo.ALL:
        key = mark.key
        save(mark_image(mark, 168, DARK), "mark-{}-dark.png".format(key))
        save(mark_image(mark, 168, LIGHT), "mark-{}-light.png".format(key))
        save(mark_image(mark, 168, DARK, one_colour=True), "mark-{}-onecolour.png".format(key))
        save(size_strip(mark, DARK), "mark-{}-sizes-dark.png".format(key))
        save(size_strip(mark, LIGHT), "mark-{}-sizes-light.png".format(key))
        save(size_strip(mark, DARK, one_colour=True), "mark-{}-sizes-onecolour.png".format(key))

    mark = marks_logo.chosen()
    setting = W.chosen_setting()
    for layout in ("horizontal", "stacked"):
        save(lockup_image(mark, setting, layout, 54, DARK),
             "lockup-{}-dark.png".format(layout))
        save(lockup_image(mark, setting, layout, 54, LIGHT),
             "lockup-{}-light.png".format(layout))
        save(lockup_image(mark, setting, layout, 54, DARK, one_colour=True),
             "lockup-{}-onecolour.png".format(layout))
    # Codex's one asked-for change, drawn exactly as stated so it can be looked at beside the rule.
    save(lockup_image(mark, setting, "horizontal", 54, DARK, caps=W.MARK_CAPS_CODEX),
         "lockup-horizontal-codex-dark.png")

    for name, size in written:
        print("  {:<44} {} x {}".format(name, size[0], size[1]))
    print("  {} files in {}".format(len(written), RENDER))


if __name__ == "__main__":
    main()

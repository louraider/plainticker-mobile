"""
The wordmark, and the lockups it makes with the mark.

A logo is a wordmark plus a mark, and the wordmark is where this product's own rule can be applied
to its own name. DESIGN.md section 3 is not a preference, it is a law the app keeps everywhere:
words are Outfit, and every number, ticker symbol, hash and timestamp is JetBrains Mono. "Numbers
never appear in Outfit." The name PlainTicker is one word made of two kinds of object, a plain word
and a ticker, so the wordmark sets each half in the face its own design system assigns it. That is
not two fonts in a logo for decoration. It is the product stating its typographic law on the one
string it owns.

Two other settings are drawn beside it so the choice is a choice: the whole name in Outfit, which
is what the TopBar ships today, and the whole name in JetBrains Mono, which turns the name into a
symbol. All three come from the fonts bundled in the app (app/src/main/res/font), outlined with
fontTools, so the wordmark on the web and the label in the TopBar are the same letterforms rather
than two near-misses.

Optical work that is in here rather than assumed:
  cap-height match   Outfit SemiBold caps are 703/1000 em and JetBrains Mono Medium's are 730, so
                     the mono half is set at 96.3 percent of the Outfit size. Set at the same
                     nominal size the two halves would not share a cap line, which is the usual
                     reason a two-face wordmark reads as a mistake.
  the join           mono carries a fixed advance with side bearings inside it, so "nT" opens a
                     gap Outfit would never leave. JOIN closes it by eye, in em units.
  tracking           Outfit at -0.01em (DESIGN.md's section-heading tracking), mono at -0.02em.

Run:   PYTHONIOENCODING=utf-8 python design/brand/logo/wordmark.py
Writes: design/brand/logo/svg/wordmark-*.svg and lockup-*.svg
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
REPO = BRAND.parent.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.insert(0, str(HERE))
sys.path.append(str(BRAND))  # for xml_ns

from fontTools.pens.svgPathPen import SVGPathPen        # noqa: E402
from fontTools.pens.transformPen import TransformPen    # noqa: E402
from fontTools.ttLib import TTFont                      # noqa: E402

import marks_logo                                       # noqa: E402
from marks3 import ACCENT, CANVAS, INK, INVERTED, num    # noqa: E402
from marks_logo import INK_LIGHT, PAPER                  # noqa: E402
from xml_ns import SVG_NS_URI                            # noqa: E402

FONT_DIR = REPO / "app" / "src" / "main" / "res" / "font"
SVG_DIR = HERE / "svg"
NL = chr(10)

EM = 1000.0            # every face here is a 1000-unit em
SIZE = 100.0           # the wordmark is drawn at cap-relative units; SIZE is the Outfit em
TRACK_OUTFIT = -0.010  # em
TRACK_MONO = -0.020    # em
JOIN = -0.055          # em, closing the mono side bearing at the "nT" join


class Face:
    def __init__(self, filename):
        self.path = FONT_DIR / filename
        self.font = TTFont(self.path)
        self.glyphs = self.font.getGlyphSet()
        self.cmap = self.font.getBestCmap()
        self.hmtx = self.font["hmtx"]
        self.upem = float(self.font["head"].unitsPerEm)
        self.cap = float(self.font["OS/2"].sCapHeight)
        self.xheight = float(self.font["OS/2"].sxHeight)

    def name(self, char):
        return self.cmap[ord(char)]

    def advance(self, char):
        return self.hmtx[self.name(char)][0] / self.upem

    def outline(self, char, scale, pen_x, baseline):
        """The glyph as one SVG path in the wordmark's own coordinates, y already flipped."""
        pen = SVGPathPen(self.glyphs, ntos=lambda v: num(round(v, 2)))
        unit = scale / self.upem
        self.glyphs[self.name(char)].draw(
            TransformPen(pen, (unit, 0, 0, -unit, pen_x, baseline)))
        return pen.getCommands()


OUTFIT = Face("outfit_semibold.ttf")
MONO = Face("jetbrains_mono_medium.ttf")

# The mono em that puts both caps on one line.
MONO_SIZE = SIZE * (OUTFIT.cap / OUTFIT.upem) / (MONO.cap / MONO.upem)
CAP = SIZE * OUTFIT.cap / OUTFIT.upem


class Setting:
    """One way of setting the name: a list of (face, size, tracking, text) runs."""

    def __init__(self, key, title, argument, runs, joins=()):
        self.key = key
        self.title = title
        self.argument = argument
        self.runs = runs
        self.joins = dict(joins)      # index of a run -> em correction applied before it

    def glyphs(self):
        """[(face, char, scale, pen_x)] plus the advance width, in wordmark units."""
        out, pen = [], 0.0
        for index, (face, size, track, text) in enumerate(self.runs):
            pen += self.joins.get(index, 0.0) * SIZE
            for char in text:
                out.append((face, char, size, pen))
                pen += face.advance(char) * size + track * SIZE
        return out, pen - self.runs[-1][2] * SIZE

    def box(self):
        """(x0, y0, x1, y1) of the drawn ink, baseline at y 0, caps negative."""
        items, _ = self.glyphs()
        xs, ys = [], []
        for face, char, size, pen_x in items:
            glyph = face.font["glyf"][face.name(char)] if "glyf" in face.font else None
            if glyph is None or not glyph.numberOfContours:
                continue
            unit = size / face.upem
            xs += [pen_x + glyph.xMin * unit, pen_x + glyph.xMax * unit]
            ys += [-glyph.yMax * unit, -glyph.yMin * unit]
        return min(xs), min(ys), max(xs), max(ys)

    def paths(self, baseline=0.0, x=0.0):
        items, _ = self.glyphs()
        return [face.outline(char, size, x + pen_x, baseline)
                for face, char, size, pen_x in items]


PLAIN_TICKER = Setting(
    "split", "Plain in Outfit, Ticker in JetBrains Mono",
    "The app's own law on its own name: words are Outfit, tickers are mono. The name is one word "
    "made of two kinds of object and it is set as two kinds of object. Cap heights are matched so "
    "the two halves share one cap line rather than reading as an accident.",
    [(OUTFIT, SIZE, TRACK_OUTFIT, "Plain"),
     (MONO, MONO_SIZE, TRACK_MONO, "Ticker")],
    joins={1: JOIN},
)

ALL_OUTFIT = Setting(
    "outfit", "The whole name in Outfit",
    "What the TopBar ships today at 15/600. One face, no argument, and nothing in it says what "
    "the product does.",
    [(OUTFIT, SIZE, TRACK_OUTFIT, "PlainTicker")],
)

ALL_MONO = Setting(
    "mono", "The whole name in JetBrains Mono",
    "The other end: the name treated as a ticker symbol throughout. It reads as data rather than "
    "as a company, and it puts the plain half in the numeral face, which is the one thing "
    "DESIGN.md section 3 forbids in reverse.",
    [(MONO, MONO_SIZE, TRACK_MONO, "PlainTicker")],
)

SETTINGS = [PLAIN_TICKER, ALL_OUTFIT, ALL_MONO]
CHOSEN_SETTING = "outfit"


def chosen_setting():
    return next(s for s in SETTINGS if s.key == CHOSEN_SETTING)


# -- documents -----------------------------------------------------------------------------------

def doc(view, width, height, body, note):
    return (
        '<svg xmlns="{svg_ns}" viewBox="{view}" width="{w}" height="{h}">'
        '{nl}{note}{nl}{body}{nl}</svg>{nl}'
    ).format(svg_ns=SVG_NS_URI,
             view=view, w=num(round(width, 2)), h=num(round(height, 2)), nl=NL, note=note,
             body=body)


def wordmark_note(setting, colourway):
    x0, y0, x1, y1 = setting.box()
    return (
        "<!--{nl}"
        "  PlainTicker wordmark: {title}{nl}"
        "  {argument}{nl}"
        "  Colourway: {way}.{nl}"
        "  Cap height {cap} units; Outfit em {size}, JetBrains Mono em {mono} so both caps{nl}"
        "  land on one line (Outfit caps are {ocap}/1000 em, mono's are {mcap}/1000).{nl}"
        "  Tracking: Outfit {to}em, mono {tm}em, join correction {join}em.{nl}"
        "  Drawn ink {w} x {h} units. Outlines from the fonts bundled in the app,{nl}"
        "  app/src/main/res/font, so the wordmark and the TopBar label are one shape.{nl}"
        "  Generated by design/brand/logo/wordmark.py, do not edit by hand.{nl}"
        "-->"
    ).format(nl=NL, title=setting.title, argument=setting.argument, way=colourway,
             cap=num(round(CAP, 2)), size=num(SIZE), mono=num(round(MONO_SIZE, 2)),
             ocap=num(OUTFIT.cap), mcap=num(MONO.cap),
             to=TRACK_OUTFIT, tm=TRACK_MONO, join=setting.joins.get(1, 0.0),
             w=num(round(x1 - x0, 2)), h=num(round(y1 - y0, 2)))


COLOURS = {"colour": INK, "light": INK_LIGHT, "mono": "currentColor", "knockout": CANVAS}


def wordmark_svg(setting, colourway, pad=0.0):
    x0, y0, x1, y1 = setting.box()
    fill = COLOURS[colourway]
    body = NL.join('  <path d="{}" fill="{}"/>'.format(d, fill) for d in setting.paths())
    if colourway == "knockout":
        plate = ('  <rect x="{}" y="{}" width="{}" height="{}" fill="{}"/>'.format(
            num(round(x0 - pad, 2)), num(round(y0 - pad, 2)), num(round(x1 - x0 + pad * 2, 2)),
            num(round(y1 - y0 + pad * 2, 2)), ACCENT))
        body = plate + NL + body
    view = "{} {} {} {}".format(num(round(x0 - pad, 2)), num(round(y0 - pad, 2)),
                                num(round(x1 - x0 + pad * 2, 2)), num(round(y1 - y0 + pad * 2, 2)))
    return doc(view, x1 - x0 + pad * 2, y1 - y0 + pad * 2, body,
               wordmark_note(setting, colourway))


# -- lockups -------------------------------------------------------------------------------------
#
# The mark is placed against the cap band rather than against the wordmark's bounding box, because
# the cap line is the only horizontal both halves of the wordmark actually share. MARK_CAPS is how
# many cap heights tall the mark stands: the mark is wider than it is high, so matching its height
# to the caps would make it longer than the word.

# How the mark meets the word.
#
# A mark whose parts are figures is set like figures: its figure blocks stand at the wordmark's cap
# height and its registers hang below the wordmark's baseline, the way a descender does. That is a
# rule with a reason rather than a ratio picked by eye, and it answers Codex's complaint that the
# mark had more authority than its meaning earns without splitting the difference with anybody. A
# mark that declares no baseline falls back to standing MARK_CAPS cap heights and centring on the
# cap band.
MARK_CAPS = 1.32        # the fallback, and the lockup as drawn before the critics saw it
MARK_CAPS_CODEX = 1.00  # Codex's one asked-for change, drawn exactly as stated
GAP_EMS = 0.42          # clear space between the mark and the word, in Outfit ems
STACK_EMS = 0.40        # clear space under the mark in the stacked lockup


def mark_geometry(mark):
    x0, y0, x1, y1 = mark.bbox()
    return x0, y0, x1 - x0, y1 - y0


def lockup_svg(mark, setting, layout, colourway, caps=None):
    """
    mark left of the word (horizontal) or over it (stacked, left aligned per DESIGN.md section 5).
    """
    mx, my, mw, mh = mark_geometry(mark)
    inverted = mark.treatment == INVERTED
    pad = mark.FIELD_PAD if (inverted or colourway == "knockout") else 0.0
    on_baseline = caps is None and mark.baseline is not None and mark.cap
    if on_baseline:
        scale = CAP / mark.cap                       # the mark's figures at the word's cap height
        caps = mh / mark.cap
    else:
        caps = MARK_CAPS if caps is None else caps
        scale = (CAP * caps) / mh
    plate_w, plate_h = (mw + pad * 2) * scale, (mh + pad * 2) * scale
    # Where the mark's own baseline lands, measured down from the top of its plate.
    to_baseline = ((mark.baseline - (my - pad)) * scale) if on_baseline else plate_h / 2.0

    wx0, wy0, wx1, wy1 = setting.box()
    word_w, word_h = wx1 - wx0, wy1 - wy0
    gap = GAP_EMS * SIZE

    if layout == "horizontal":
        # The mark sits on the wordmark's own baseline when it has one, and is centred on the cap
        # band when it does not.
        mark_y = (0.0 - to_baseline) if on_baseline else (-CAP / 2.0 - plate_h / 2.0)
        mark_x = 0.0
        word_x, word_baseline = plate_w + gap, 0.0
        left, top = 0.0, min(mark_y, wy0)
        right, bottom = word_x + word_w, max(mark_y + plate_h, wy1)
    else:
        mark_x, mark_y = 0.0, -CAP - STACK_EMS * SIZE - plate_h
        word_x, word_baseline = 0.0, 0.0
        left, top = 0.0, mark_y
        right, bottom = max(plate_w, word_w), wy1

    parts = []
    unit = "translate({} {}) scale({}) translate({} {})".format(
        num(round(mark_x, 3)), num(round(mark_y, 3)), num(round(scale, 5)),
        num(round(-(mx - pad), 3)), num(round(-(my - pad), 3)))
    ink = {"colour": INK, "light": INK_LIGHT, "mono": "currentColor", "knockout": CANVAS}[colourway]
    hole = {"colour": CANVAS, "light": PAPER, "mono": None, "knockout": CANVAS}[colourway]

    shapes = []
    if inverted or colourway == "knockout":
        shapes.append('<rect x="{}" y="{}" width="{}" height="{}" fill="{}"/>'.format(
            num(mx - pad), num(my - pad), num(mw + pad * 2), num(mh + pad * 2), ACCENT))
        for shape in mark.marks():
            if inverted and not shape.cut:
                continue
            shapes.append(shape.svg(hole or CANVAS))
    else:
        for shape in mark.marks():
            if shape.cut:
                continue
            colour = ink if shape.colour == INK else (
                ACCENT if colourway != "mono" else "currentColor")
            shapes.append(shape.svg(colour))
    parts.append('  <g transform="{}">{nl}    {body}{nl}  </g>'.format(
        unit, nl=NL, body=(NL + "    ").join(shapes)))

    for d in setting.paths(baseline=word_baseline, x=word_x - wx0):
        parts.append('  <path d="{}" fill="{}"/>'.format(d, ink))

    if colourway == "knockout" and not inverted:
        pass  # the word is already Canvas on the Accent plate below

    width, height = right - left, bottom - top
    if colourway == "knockout":
        plate = '  <rect x="{}" y="{}" width="{}" height="{}" fill="{}"/>'.format(
            num(round(left - gap, 2)), num(round(top - gap, 2)),
            num(round(width + gap * 2, 2)), num(round(height + gap * 2, 2)), ACCENT)
        parts.insert(0, plate)
        left, top, width, height = left - gap, top - gap, width + gap * 2, height + gap * 2

    view = "{} {} {} {}".format(num(round(left, 2)), num(round(top, 2)),
                                num(round(width, 2)), num(round(height, 2)))
    note = (
        "<!--{nl}"
        "  PlainTicker lockup: {layout}, {way}.{nl}"
        "  Mark: {mark}. Wordmark: {word}.{nl}"
        "  {rule}{nl}"
        "  The mark stands {caps} cap heights overall. Clear space {gap} of the Outfit em on{nl}"
        "  every side; never set the word and the mark closer than that.{nl}"
        "  Generated by design/brand/logo/wordmark.py, do not edit by hand.{nl}"
        "-->"
    ).format(nl=NL, layout=layout, way=colourway, mark=mark.title, word=setting.title,
             caps="{:.3f}".format(caps), gap=GAP_EMS,
             rule=("Its figure blocks stand at the wordmark's cap height and its registers hang "
                   "below the wordmark's baseline, the way a descender does."
                   if on_baseline else
                   "It is centred on the wordmark's cap band, which is the horizontal the two "
                   "share."))
    return doc(view, width, height, NL.join(parts), note)


def main():
    SVG_DIR.mkdir(parents=True, exist_ok=True)
    written = []
    for setting in SETTINGS:
        for way in ("colour", "light", "mono", "knockout"):
            path = SVG_DIR / "wordmark-{}-{}.svg".format(setting.key, way)
            path.write_text(wordmark_svg(setting, way, pad=0.32 * SIZE if way == "knockout" else 0),
                            encoding="utf-8", newline=NL)
            written.append(path)

    mark = marks_logo.chosen()
    setting = chosen_setting()
    for layout in ("horizontal", "stacked"):
        for way in ("colour", "light", "mono", "knockout"):
            path = SVG_DIR / "lockup-{}-{}.svg".format(layout, way)
            path.write_text(lockup_svg(mark, setting, layout, way), encoding="utf-8", newline=NL)
            written.append(path)
    path = SVG_DIR / "lockup-horizontal-codex-colour.svg"
    path.write_text(lockup_svg(mark, setting, "horizontal", "colour", caps=MARK_CAPS_CODEX),
                    encoding="utf-8", newline=NL)
    written.append(path)

    for path in written:
        print("  {}".format(path.relative_to(REPO)))
    print()
    print("  Outfit cap {:.0f}/1000 em, JetBrains Mono cap {:.0f}/1000 em".format(
        OUTFIT.cap, MONO.cap))
    print("  mono set at {:.2f} of the Outfit size so both caps land on one line".format(
        MONO_SIZE / SIZE))
    for setting in SETTINGS:
        x0, y0, x1, y1 = setting.box()
        print("  {:<8} ink {:7.2f} x {:6.2f} units, cap {:.2f}, ratio {:.2f} to 1".format(
            setting.key, x1 - x0, y1 - y0, CAP, (x1 - x0) / CAP))


if __name__ == "__main__":
    main()

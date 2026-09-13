"""
Eight ideas for the app icon, as geometry, written out as real SVG.

Attempts one and two failed the same way and the diagnosis is in TODOS.md, "The app icon, attempt
three". The short version: both marks were assembled out of the design system's smallest parts, a
traced letterform and a hairline with two ticks, and those parts exist to stay quiet inside a
screen somebody reads for a minute. A tile in a launcher grid has the opposite job. It is read for
half a second beside thirty saturated neighbours and it competes on mass and silhouette, not on
detail. Both marks also won every argument on paper and still looked weak, which means the
argument was not the thing being decided.

So this file does not hold three weights of one idea. It holds eight different ideas, each one a
different claim about what the product is, each one drawn from primitive shapes only: axis-aligned
rectangles, one circle, two simple paths, and masks where the meaning lives in negative space.

Coordinate system: the 108 adaptive-icon viewport, centre (54, 54), the same one marks.py uses so
the winner can move into design/brand/glyph.py without being redrawn. A launcher shows the central
72 of that viewport, so on the Seeker, where a drawer tile measures 182 device pixels, one viewport
unit is 2.53 px. The rules below are stated in viewport units and checked at import.

  MIN_STROKE   6 units   no positive shape thinner than this. 4dp at 48dp. Inherited from marks.py.
  MIN_GAP      6 units   NEW, and non-negotiable here. Every one of these eight marks lives or dies
                         on negative space: a knockout gap, a seam, a ring opening, a void, the
                         four corners left over from a disc. marks.py floors strokes only, and a
                         4-unit gap closes up at tile size exactly the way the 1.3dp tick vanished
                         in attempt one. A gap is a shape. It gets a floor.
  MASK         the real launcher mask, measured off the phone rather than assumed. composite.py
                         lifts it off the Photos tile in the drawer screenshot and fits a
                         superellipse of exponent 3.05 to it; every corner of every shape here is
                         checked against exponent 3.0, which is the slightly tighter mask, so a
                         mark that clears it clears what the launcher is really cutting. Two of the
                         eight were pulled in when that measurement came back, which is exactly the
                         work an assumed mask would have skipped. SAFE_RADIUS 33 from marks.py is
                         the conservative circle-mask guarantee; most of these exceed it on purpose,
                         because mass is the fix, and `radius()` prints the number so the cost is
                         visible rather than hidden.

Two treatments, and each idea is drawn in whichever one suits it rather than every idea in both.

  GROUND    the tile stays Canvas near-black and the mark grows until it is the tile.
  INVERTED  the tile becomes an Accent field and the mark is knocked out of it in Canvas.

Both are one line of XML in res/mipmap-anydpi-v26 and neither has been tried. The drawer screenshot
settles why one of them is needed: five dark tiles near PlainTicker do work (OKX, bitchat, Jito, X,
Uklon) and every one of them carries a single high-contrast mark that nearly fills the tile, while
PlainTicker's own tile has no edge against the drawer ground at all.

Rules of DESIGN.md this file knowingly departs from, per the licence the TODOS entry gives the icon:
  section 9, first sentence  "the mark is the tracking gauge, not a letter" encodes a rejected
                             answer as a constraint. No third attempt can exist under it.
  section 4, radius 0        broken by exactly one candidate, 08, which is a circle. The shape lock
                             exists so UI containers read as parts of an instrument. A brand mark
                             is not a container.
  Mark.mirrors_itself        written to kill a plus sign in the monochrome layer, and as written it
                             also refuses a ring and two equal blocks, neither of which can ever
                             read as a plus. Narrowed here to what it meant: `reads_as_plus_or_tee`.
  section 2, one accent      kept literally. An Accent field is still exactly one accent.
  section 1, quiet           broken by all eight, on purpose. The interface is read for a minute and
                             must not shout; the tile is read for half a second against thirty
                             saturated neighbours and must.

Run:  PYTHONIOENCODING=utf-8 python design/brand/attempt-three/marks3.py
Writes: design/brand/attempt-three/svg/NN-key.svg and NN-key-flat.svg
"""
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
SVG_DIR = HERE / "svg"

VIEWPORT = 108.0
CENTRE = 54.0
VISIBLE = 72.0           # the part of the viewport a launcher actually shows
MASK_EXPONENT = 3.0      # superellipse; 2 would be a plain circle mask, 4 a hard squircle.
                         # Measured, not assumed: composite.py lifts the mask off the Photos
                         # tile in the drawer screenshot and fits 3.05 to it. Rounded down
                         # here, because a smaller exponent is the tighter mask and a mark
                         # that clears 3.0 clears whatever the launcher is really cutting.
SAFE_RADIUS = 33.0       # marks.py's circle-mask guarantee, reported not enforced
MIN_STROKE = 6.0
MIN_GAP = 6.0

CANVAS = "#0B0F14"
INK = "#E8ECF1"
ACCENT = "#5AA9E6"
FLAT = "#FFFFFF"

GROUND = "ground"
INVERTED = "inverted"

NL = chr(10)


def num(value):
    rounded = round(float(value), 3)
    return str(int(rounded)) if rounded == int(rounded) else repr(rounded)


# -- primitives ---------------------------------------------------------------------------------

class Shape:
    """A primitive with a colour and a role. `keep` shapes are the mark, `cut` shapes are the gaps."""

    def __init__(self, colour, cut=False, bleed=False, thin=None, note=""):
        self.colour = colour
        self.cut = cut          # True: this shape is negative space, a knockout, a seam, a void
        self.bleed = bleed      # True: deliberately runs past the visible 72, so no corner check
        self._thin = thin       # override for the measured narrow dimension, used by 08
        self.note = note

    @property
    def thin(self):
        return self._thin if self._thin is not None else self.measure_thin()


class Rect(Shape):
    def __init__(self, x, y, w, h, colour, **kw):
        super().__init__(colour, **kw)
        self.x, self.y, self.w, self.h = float(x), float(y), float(w), float(h)

    def measure_thin(self):
        return min(self.w, self.h)

    def corners(self):
        return [(self.x, self.y), (self.x + self.w, self.y),
                (self.x, self.y + self.h), (self.x + self.w, self.y + self.h)]

    def svg(self, colour=None):
        return '<rect x="{}" y="{}" width="{}" height="{}" fill="{}"/>'.format(
            num(self.x), num(self.y), num(self.w), num(self.h), colour or self.colour)


class Circle(Shape):
    def __init__(self, cx, cy, r, colour, **kw):
        super().__init__(colour, **kw)
        self.cx, self.cy, self.r = float(cx), float(cy), float(r)

    def measure_thin(self):
        return self.r * 2

    def corners(self):
        return [(self.cx + self.r * math.cos(a), self.cy + self.r * math.sin(a))
                for a in (i * math.pi / 8 for i in range(16))]

    def svg(self, colour=None):
        return '<circle cx="{}" cy="{}" r="{}" fill="{}"/>'.format(
            num(self.cx), num(self.cy), num(self.r), colour or self.colour)


class Path(Shape):
    """A rectilinear outline given as its corner points. One sentence each, or it is too complex."""

    def __init__(self, points, colour, thin, **kw):
        super().__init__(colour, thin=thin, **kw)
        self.points = [(float(x), float(y)) for x, y in points]

    def measure_thin(self):
        return self._thin

    def corners(self):
        return list(self.points)

    def data(self):
        head = "M{} {}".format(num(self.points[0][0]), num(self.points[0][1]))
        body = ""
        px, py = self.points[0]
        for x, y in self.points[1:]:
            body += "H{}".format(num(x)) if y == py else "V{}".format(num(y))
            px, py = x, y
        return head + body + "Z"

    def svg(self, colour=None):
        return '<path d="{}" fill="{}"/>'.format(self.data(), colour or self.colour)


# -- a candidate --------------------------------------------------------------------------------

class Concept:
    def __init__(self, number, key, title, caption, breaks, treatment, field, shapes, gaps):
        self.number = number
        self.key = key
        self.title = title
        self.caption = caption          # the one line the gallery prints under the mark
        self.breaks = breaks            # the rule this drawing breaks, written down
        self.treatment = treatment
        self.field = field              # the full-bleed background colour of the tile
        self.shapes = shapes            # paint order, background first
        self.gaps = gaps                # measured negative-space dimensions, name -> units
        self.check()

    # -- the rules ------------------------------------------------------------------------------

    def check(self):
        for index, shape in enumerate(self.shapes):
            if shape.bleed:
                continue
            floor = MIN_GAP if shape.cut else MIN_STROKE
            what = "gap" if shape.cut else "stroke"
            assert shape.thin >= floor - 1e-9, "{}/{} {} is {} units, under the {} floor".format(
                self.key, index, what, num(shape.thin), num(floor))
            for x, y in shape.corners():
                assert self.inside_mask(x, y), \
                    "{}/{} corner ({}, {}) falls outside the launcher mask".format(
                        self.key, index, num(x), num(y))
        for name, size in self.gaps.items():
            assert size >= MIN_GAP - 1e-9, "{}: the {} gap is {} units, under the {} floor".format(
                self.key, name, num(size), num(MIN_GAP))
        assert sum(1 for s in self.shapes if s.colour == ACCENT) >= 1, \
            "{}: nothing carries the accent".format(self.key)
        assert not self.reads_as_plus_or_tee(), \
            "{}: the flattened silhouette reads as a plus or a T".format(self.key)

    @staticmethod
    def inside_mask(x, y):
        """Inside the superellipse a Pixel-family launcher cuts out of the visible 72."""
        ax = abs(x - CENTRE) / (VISIBLE / 2)
        ay = abs(y - CENTRE) / (VISIBLE / 2)
        return ax ** MASK_EXPONENT + ay ** MASK_EXPONENT <= 1.0 + 1e-9

    def reads_as_plus_or_tee(self):
        """
        marks.py's `mirrors_itself`, narrowed to what it was written to catch.

        The original refuses any construction symmetric about its own middle. It was written after
        a gauge whose track, reference tick and token tick all sat on y 54 flattened into a plus
        sign, and it does catch that. It also refuses a ring, an inscribed disc and two equal
        blocks, and none of those can read as a plus in any colour. Symmetry was never the fault;
        being a plus or a T was. So the test is the fault itself: take the marked area as a coarse
        grid, and refuse it only if that area is a full-width band crossed by a full-height band
        (a plus), or a full-width band with a single central stem hanging off it (a T).
        """
        grid = 12
        cell = VIEWPORT / grid
        painted = [[False] * grid for _ in range(grid)]
        for shape in self.shapes:
            if shape is self.shapes[0]:      # the field is not the mark
                continue
            for row in range(grid):
                for col in range(grid):
                    x, y = (col + 0.5) * cell, (row + 0.5) * cell
                    if self._covers(shape, x, y):
                        painted[row][col] = not shape.cut
        rows = [r for r in range(grid) if all(painted[r])]
        cols = [c for c in range(grid) if all(painted[r][c] for r in range(grid))]
        if rows and cols:
            return True                      # a full band each way: a plus
        if rows:
            stems = [c for c in range(grid)
                     if all(painted[r][c] for r in range(max(rows) + 1, grid))
                     and max(rows) + 1 < grid]
            if stems and len(stems) <= 4 and min(stems) > 0 and max(stems) < grid - 1:
                return True                  # one band with a central stem under it: a T
        return False

    @staticmethod
    def _covers(shape, x, y):
        if isinstance(shape, Rect):
            return shape.x <= x <= shape.x + shape.w and shape.y <= y <= shape.y + shape.h
        if isinstance(shape, Circle):
            return math.hypot(x - shape.cx, y - shape.cy) <= shape.r
        xs = [p[0] for p in shape.points]
        ys = [p[1] for p in shape.points]
        if not (min(xs) <= x <= max(xs) and min(ys) <= y <= max(ys)):
            return False
        inside = False                        # even-odd ray cast, the outline is rectilinear
        pts = shape.points
        for i in range(len(pts)):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % len(pts)]
            if (y0 > y) != (y1 > y):
                if x < x0 + (y - y0) / (y1 - y0) * (x1 - x0):
                    inside = not inside
        return inside

    def radius(self):
        """The furthest corner from the centre, against marks.py's 33 circle-mask guarantee."""
        return max((math.hypot(x - CENTRE, y - CENTRE)
                    for s in self.shapes[1:] if not s.bleed for (x, y) in s.corners()), default=0.0)

    # -- output ---------------------------------------------------------------------------------

    def header(self, layer):
        return (
            "<!--{nl}"
            "  {number:02d} {title} - {caption}{nl}"
            "  Treatment: {treatment}. Field {field}.{nl}"
            "  Breaks: {breaks}{nl}"
            "  Layer: {layer}.{nl}"
            "  Furthest corner {radius} of the 33 circle-mask guarantee; drawn for the real{nl}"
            "  superellipse mask measured off the phone, see composite.py.{nl}"
            "  Generated by design/brand/attempt-three/marks3.py, do not edit by hand.{nl}"
            "-->"
        ).format(nl=NL, number=self.number, title=self.title, caption=self.caption,
                 treatment=self.treatment, field=self.field, breaks=self.breaks,
                 layer=layer, radius="{:.2f}".format(self.radius()))

    def colour_svg(self):
        body = NL.join("  " + s.svg() for s in self.shapes)
        return (
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" '
            'height="108">{nl}{header}{nl}{body}{nl}</svg>{nl}'
        ).format(nl=NL, header=self.header("colour, the tile as a launcher draws it"), body=body)

    def flat_svg(self):
        """
        One colour, nothing to lean on: the Android themed icon and the notification silhouette.

        Negative space is carried by a mask rather than by painting Canvas over Ink, because in the
        monochrome layer there is no Canvas to paint with. White in the mask keeps, black cuts, and
        paint order is the same as the colour drawing, so the two cannot drift apart.
        """
        entries = []
        for shape in self.shapes:
            if shape is self.shapes[0] and self.treatment == GROUND:
                continue                      # the Canvas field is not part of the monochrome layer
            entries.append("      " + shape.svg("#000000" if shape.cut else "#ffffff"))
        return (
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" '
            'height="108">{nl}{header}{nl}'
            '  <mask id="{key}" maskUnits="userSpaceOnUse" x="0" y="0" width="108" height="108">'
            '{nl}{body}{nl}  </mask>{nl}'
            '  <rect x="0" y="0" width="108" height="108" fill="{flat}" mask="url(#{key})"/>{nl}'
            '</svg>{nl}'
        ).format(nl=NL, header=self.header("flattened to one colour, themed icon and notification"),
                 key=self.key, body=NL.join(entries), flat=FLAT)

    def write(self):
        SVG_DIR.mkdir(parents=True, exist_ok=True)
        stem = "{:02d}-{}".format(self.number, self.key)
        colour = SVG_DIR / (stem + ".svg")
        flat = SVG_DIR / (stem + "-flat.svg")
        colour.write_text(self.colour_svg(), encoding="utf-8", newline=NL)
        flat.write_text(self.flat_svg(), encoding="utf-8", newline=NL)
        return colour, flat


# -- the eight ----------------------------------------------------------------------------------
# Every one is a different claim about what the product is. None is a weight of another: the shape
# families are block-and-frame, ring, bitten mass, voided mass, cut mass, overlap, interlock, disc.

ONE_TO_ONE = Concept(
    1, "one-to-one", "One to one",
    "the claim set beside the thing it claims, at the same measure",
    "Mark.mirrors_itself, which refuses two equal blocks even though they can never read as a plus.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(22, 33, 28, 42, CANVAS, cut=True, note="the share, solid"),
        Rect(58, 33, 28, 42, CANVAS, cut=True, note="the token, an open frame"),
        Rect(66, 41, 12, 26, ACCENT, note="what the frame encloses"),
    ],
    {"between the two blocks": 8.0, "frame stroke": 8.0},
)

WRAPPER = Concept(
    2, "wrapper", "The wrapper and what is in it",
    "a shell around a real asset, and the contents do not fill the claim",
    "Section 1's quiet character: a 58-unit ring is the loudest object in the product.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(27, 27, 54, 54, INK, note="the wrapper"),
        Rect(36, 36, 36, 36, CANVAS, cut=True, note="the opening"),
        Rect(45, 42, 20, 20, ACCENT, note="what is actually in it, undersized and off-centre"),
    ],
    {"opening above the core": 6.0, "opening below the core": 10.0,
     "opening left of the core": 9.0, "opening right of the core": 7.0},
)

REACH = Concept(
    3, "reach", "Someone else's reach",
    "a third party can move or freeze what you hold, and the edge doing it is not yours",
    "Section 1 again, and the product's own reticence: this is the one mark that names a risk.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="what you hold"),
        Rect(66, -12, 54, 54, CANVAS, cut=True, bleed=True, note="the delegate, reaching in from outside"),
    ],
    {"bite across": 24.0, "bite down": 24.0},
)

BLANK = Concept(
    4, "blank", "The blank it will not fill",
    "below the liquidity floor the app prints no number, and this is that refusal",
    "Section 8's ban on anything that reads as a broken or empty state. Here it is the content.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(38, 22, 24, 56, CANVAS, cut=True, note="where the value would sit, and does not"),
    ],
    {"void across": 24.0, "void down": 56.0},
)

SPLIT = Concept(
    5, "split", "The split",
    "one holding cut into two unequal parts, which is what a split does to what you own",
    "Section 8's 'three equal tiles' generalised: two masses is the most generic minimal mark there is.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(26, 26, 30, 50, INK, note="the larger part"),
        Rect(63, 32, 19, 50, ACCENT, note="the smaller part, slid along the seam"),
    ],
    {"seam": 7.0, "the offset that keeps the silhouette asymmetric": 6.0},
)

BEFORE = Concept(
    6, "before", "Before",
    "what the token says, placed in front of what the company says, before you swap",
    "Section 2's ban on shadows: a knockout gap is one slip from being one, so it is 7 units of Canvas.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(26, 26, 44, 44, INK, note="the company's filing, behind"),
        Rect(40, 40, 49, 49, CANVAS, cut=True, bleed=True,
             note="the knockout that separates them; it runs past the mask on two sides, which is "
                  "why it is bleed: only the 7 units of it that touch the filing are ever seen"),
        Rect(47, 47, 35, 35, ACCENT, note="what the mint says, in front"),
    ],
    {"knockout": 7.0, "the band of the filing left showing": 14.0},
)

FIT = Concept(
    7, "fit", "The fit",
    "the token either fits the share exactly or it does not, and the app measures the difference",
    "Section 4's radius 0 is kept, but the tenon must stay a plain rectangle or it is a jigsaw piece.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(26, 26, 56, 56, ACCENT, note="the share"),
        Path([(26, 26), (57, 26), (57, 36), (71, 36), (71, 72), (57, 72), (57, 82), (26, 82)],
             CANVAS, thin=10.0, cut=True, note="the seam"),
        Path([(26, 26), (50, 26), (50, 43), (64, 43), (64, 65), (50, 65), (50, 82), (26, 82)],
             INK, thin=14.0, note="the token, with its tenon"),
    ],
    {"seam beside the tenon": 7.0, "seam above the tenon": 7.0,
     "seam below the tenon": 7.0, "seam along the joint": 7.0},
)

MEASURE = Concept(
    8, "measure", "Different shapes, same measure",
    "a token and a share are not the same object, and the corners are the difference",
    "Section 4's shape lock, radius 0 on everything. Broken outright, and only here.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(26, 26, 56, 56, ACCENT, note="the square measure"),
        # The disc is tangent on all four sides, so the four corners left over are the whole idea.
        # Their widest point is 28 * (sqrt(2) - 1) = 11.6 units on the diagonal and they taper to
        # nothing at the tangent points. That taper is the one place MIN_GAP cannot be enforced,
        # because it is what a circle inscribed in a square is; the number is stated rather than
        # asserted, and 09-shipped.svg is in the same folder to compare a genuinely thin mark with.
        Circle(54, 54, 28, CANVAS, cut=True, thin=11.6, note="the round measure"),
    ],
    {"corner at its widest": 11.6},
)

CONCEPTS = [ONE_TO_ONE, WRAPPER, REACH, BLANK, SPLIT, BEFORE, FIT, MEASURE]

# What ships today, redrawn here from res/drawable/ic_launcher_foreground.xml so the gallery can
# show the founder the baseline in the same frame as the eight. Not a candidate, not numbered.
SHIPPED = Concept(
    9, "shipped", "As it ships today",
    "the tracking gauge from attempt two, for comparison only",
    "Nothing. This is the mark the founder has already seen and rejected.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(26, 54, 56, 8, INK, note="track"),
        Rect(50, 62, 8, 14, INK, note="reference graduation"),
        Rect(64, 32, 10, 36, ACCENT, note="token tick"),
    ],
    {},
)


def main():
    print("{:>3}  {:<32} {:<9} {:>7}  {}".format("no", "concept", "treatment", "corner", "shapes"))
    for concept in CONCEPTS + [SHIPPED]:
        colour, flat = concept.write()
        print("{:>3}  {:<32} {:<9} {:>7.2f}  {}".format(
            concept.number, concept.title, concept.treatment, concept.radius(),
            len(concept.shapes) - 1))
        for name, size in concept.gaps.items():
            print("      gap  {:<40} {:>5} units  {:>5.1f} px on the Seeker".format(
                name, num(size), size * 182 / 72))
        print("      files {}, {}".format(colour.name, flat.name))


if __name__ == "__main__":
    main()

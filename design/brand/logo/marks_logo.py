"""
The logo, attempt six: the mark is designed first and the launcher icon is cut out of it.

Three icon attempts failed and attempt four wrote down why: a composition of blank rectangles does
not read as the meaning its author assigned it, it reads as whichever Android system glyph already
owns that geometry, and a Canvas tile has no edge at all against the Seeker's drawer wallpaper
(1.03 to 1, measured). Attempt five settled the ground with a number and left one objection
standing, Codex's, which nothing in that round answered:

    "These marks show two things sitting apart. They do not show that one cannot justify the
    other."

That sentence is the brief for this file. The product's distinctive act is refusal: it will not
print a tracking figure when the pool behind it cannot carry one. Every mark here has to draw the
*reason* for the refusal, not just an absence, and has to say which system glyph it is nearest to
and why it is not that glyph.

Geometry lives in the 108 adaptive-icon viewport for one reason: so the logo mark and the launcher
icon are the same drawing rather than two drawings that resemble each other. A launcher shows the
central 72; the logo crops to the mark's own bounding box and the drawing does not change. That is
what "the icon is derived from the logo" has to mean if it is to mean anything.

Run:   PYTHONIOENCODING=utf-8 python design/brand/logo/marks_logo.py
Writes: design/brand/logo/svg/mark-*.svg and design/brand/logo/icon/*.svg
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.append(str(BRAND))  # for xml_ns

import marks3  # noqa: E402
from marks3 import ACCENT, CANVAS, GROUND, INK, INVERTED, Rect  # noqa: E402
from xml_ns import SVG_NS_URI  # noqa: E402

SVG_DIR = HERE / "svg"
ICON_DIR = HERE / "icon"
NL = chr(10)

# The Seeker, measured rather than assumed (attempt five).
TILE_PX = 182.0
TILE_DP = TILE_PX / 3.0                       # 60.7dp, not the 48dp an icon brief assumes
UNIT_DP = TILE_DP / marks3.VISIBLE
UNIT_PX = TILE_PX / marks3.VISIBLE

INK_LIGHT = "#0E141B"      # the mark on a cool off-white, DESIGN.md section 2's deferred light end
PAPER = "#F2F5F8"


class Mark(marks3.Concept):
    """
    One logo mark. Everything attempt four learned is a required field rather than a caption.

    `system_glyph` and `apart` are the test attempt three never applied: name the Android glyph
    this geometry is nearest to, then say what keeps it off that glyph. `says` has to be a sentence
    about this product that would be false about a different one.
    """

    def __init__(self, number, key, title, says, system_glyph, apart, refusal, breaks,
                 treatment, field, shapes, gaps, risk="", waiver="", baseline=None, cap=None):
        self.baseline = baseline        # the y in the 108 viewport that is this mark's baseline
        self.cap = cap                  # the height, in units, of the part that is a figure
        self.waiver = waiver            # a floor this drawing breaks on instruction, and the cost
        self.says = says
        self.system_glyph = system_glyph
        self.apart = apart
        self.refusal = refusal          # how this drawing carries the one act nobody else does
        self.risk = risk                # the thing most likely to kill it, written by me not them
        super().__init__(number, key, title, says, breaks, treatment, field, shapes, gaps)

    def check(self):
        """
        The floors, unless a critic's instruction is being carried out literally.

        A waiver does not make the drawing legal, it makes the cost visible: the waived mark is
        drawn, rendered and measured beside the one that keeps the floor, and the reader decides
        from the pictures rather than from the argument.
        """
        if self.waiver:
            return
        super().check()

    # -- measurements the gallery prints -----------------------------------------------------

    def marks(self):
        """The shapes that are the mark, which is everything but the field."""
        return self.shapes[1:]

    def bbox(self):
        xs, ys = [], []
        for shape in self.marks():
            for x, y in shape.corners():
                xs.append(x)
                ys.append(y)
        return min(xs), min(ys), max(xs), max(ys)

    def thinnest(self):
        return min((s.thin for s in self.marks() if not s.bleed), default=0.0)

    def on_phone(self):
        thin = self.thinnest()
        return thin * UNIT_DP, thin * UNIT_PX

    def ink_share(self):
        """
        How much of the visible 72 the drawing covers, as a fraction. Emptiness, as a number.

        For a ground mark that is the mark itself against the Canvas. For an inverted mark the
        field is the drawing, so the share starts at one and the knockouts take it back down. Both
        answer the same question: how much of the tile is something rather than nothing.
        """
        step, covered, total = 0.5, 0, 0
        y = 18.0
        while y < 90.0:
            x = 18.0
            while x < 90.0:
                total += 1
                painted = self.treatment == INVERTED
                for shape in self.marks():
                    if self._covers(shape, x, y):
                        painted = not shape.cut
                covered += 1 if painted else 0
                x += step
            y += step
        return covered / float(total)

    # -- the three colourways a logo has to ship ----------------------------------------------

    def _doc(self, body, width, height, view, comment):
        return (
            '<svg xmlns="{svg_ns}" viewBox="{view}" width="{w}" '
            'height="{h}">{nl}{comment}{nl}{body}{nl}</svg>{nl}'
        ).format(svg_ns=SVG_NS_URI,
                 view=view, w=marks3.num(width), h=marks3.num(height), nl=NL,
                 comment=comment, body=body)

    def note(self, layer):
        dp, px = self.on_phone()
        x0, y0, x1, y1 = self.bbox()
        return (
            "<!--{nl}"
            "  PlainTicker, mark {number:02d}: {title}{nl}"
            "  Says: {says}{nl}"
            "  Nearest system glyph: {glyph}{nl}"
            "  Not that glyph because: {apart}{nl}"
            "  The refusal: {refusal}{nl}"
            "  Breaks: {breaks}{nl}"
            "  Layer: {layer}{nl}"
            "  Thinnest positive shape {thin} units, {dp}dp and {px} device pixels on the{nl}"
            "  Seeker's measured 60.7dp drawer tile. Ink share of the visible 72: {ink}%.{nl}"
            "  Mark box {bw} x {bh} units at ({bx}, {by}) in the 108 adaptive viewport.{nl}"
            "  Furthest corner {radius} of the 33 circle-mask guarantee; drawn against the{nl}"
            "  superellipse measured off the phone, see attempt-three/composite.py.{nl}"
            "  Generated by design/brand/logo/marks_logo.py, do not edit by hand.{nl}"
            "-->"
        ).format(nl=NL, number=self.number, title=self.title, says=self.says,
                 glyph=self.system_glyph, apart=self.apart, refusal=self.refusal,
                 breaks=self.breaks, layer=layer,
                 thin=marks3.num(self.thinnest()), dp="{:.1f}".format(dp), px="{:.0f}".format(px),
                 ink="{:.1f}".format(self.ink_share() * 100),
                 bw=marks3.num(x1 - x0), bh=marks3.num(y1 - y0),
                 bx=marks3.num(x0), by=marks3.num(y0),
                 radius="{:.2f}".format(self.radius()))

    def crop_view(self, pad=0.0):
        x0, y0, x1, y1 = self.bbox()
        return "{} {} {} {}".format(marks3.num(x0 - pad), marks3.num(y0 - pad),
                                    marks3.num(x1 - x0 + pad * 2),
                                    marks3.num(y1 - y0 + pad * 2))

    FIELD_PAD = 10.0     # the clear space a knocked-out mark keeps inside its own field

    def logo_svg(self, colourway):
        """
        The mark on its own, cropped to itself. No tile, no launcher, no 108 box.

        colourway: "colour" (Ink and Accent on a dark ground), "light" (the same on a cool
        off-white), "mono" (one colour through currentColor), "knockout" (cut out of an Accent
        field). An inverted mark has no positive shapes to paint, so for it "colour" and
        "knockout" are the same drawing and "mono" becomes a mask, exactly as the themed launcher
        icon does it: there is no Canvas to overpaint with, so a hole has to be a real hole.
        """
        x0, y0, x1, y1 = self.bbox()
        w, h = x1 - x0, y1 - y0
        pad = self.FIELD_PAD
        inverted = self.treatment == INVERTED

        if inverted or colourway == "knockout":
            hole = {"light": PAPER, "mono": None}.get(colourway, CANVAS) if inverted else CANVAS
            plate = Rect(x0 - pad, y0 - pad, w + pad * 2, h + pad * 2, ACCENT)
            if colourway == "mono" and inverted:
                return self._masked(plate, self.marks(), w + pad * 2, h + pad * 2, pad)
            cuts = [s for s in self.marks() if s.cut] if inverted else \
                   [s for s in self.marks() if not s.cut]
            body = NL.join(["  " + plate.svg()] + ["  " + s.svg(hole) for s in cuts])
            return self._doc(body, w + pad * 2, h + pad * 2, self.crop_view(pad),
                             self.note("knocked out of an Accent field"))

        if colourway == "mono":
            body = NL.join("  " + s.svg("currentColor") for s in self.marks() if not s.cut)
            return self._doc(body, w, h, self.crop_view(), self.note("one colour, currentColor"))
        swap = {INK: INK_LIGHT} if colourway == "light" else {}
        body = NL.join("  " + s.svg(swap.get(s.colour, s.colour))
                       for s in self.marks() if not s.cut)
        layer = "full colour on Canvas" if colourway == "colour" else "full colour on paper"
        return self._doc(body, w, h, self.crop_view(), self.note(layer))

    def _masked(self, plate, shapes, width, height, pad):
        """An inverted mark in one colour: the field keeps, the knockouts cut, nothing overpaints."""
        entries = ["      " + plate.svg("#ffffff")]
        entries += ["      " + s.svg("#000000" if s.cut else "#ffffff") for s in shapes]
        return (
            '<svg xmlns="{svg_ns}" viewBox="{view}" width="{w}" '
            'height="{h}">{nl}{note}{nl}'
            '  <mask id="{key}" maskUnits="userSpaceOnUse">{nl}{body}{nl}  </mask>{nl}'
            '  <g mask="url(#{key})">{nl}    {plate}{nl}  </g>{nl}'
            '</svg>{nl}'
        ).format(svg_ns=SVG_NS_URI,
                 view=self.crop_view(pad), w=marks3.num(width), h=marks3.num(height), nl=NL,
                 note=self.note("one colour, currentColor, holes are real holes"),
                 key=self.key + "-mono", body=NL.join(entries),
                 plate=plate.svg("currentColor"))

    # -- the launcher icon, which is the same drawing inside the tile it has to survive --------

    def icon_svg(self):
        body = NL.join("  " + s.svg() for s in self.shapes)
        return self._doc(body, 108, 108, "0 0 108 108",
                         self.note("the tile as a launcher draws it"))

    def icon_flat_svg(self):
        entries = []
        for shape in self.shapes:
            if shape is self.shapes[0] and self.treatment == GROUND:
                continue
            entries.append("      " + shape.svg("#000000" if shape.cut else "#ffffff"))
        return (
            '<svg xmlns="{svg_ns}" viewBox="0 0 108 108" width="108" '
            'height="108">{nl}{note}{nl}'
            '  <mask id="{key}" maskUnits="userSpaceOnUse" x="0" y="0" width="108" height="108">'
            '{nl}{body}{nl}  </mask>{nl}'
            '  <rect x="0" y="0" width="108" height="108" fill="#FFFFFF" mask="url(#{key})"/>{nl}'
            '</svg>{nl}'
        ).format(svg_ns=SVG_NS_URI,
                 nl=NL, note=self.note("flattened to one colour, themed icon and notification"),
                 key=self.key, body=NL.join(entries))

    def write(self):
        SVG_DIR.mkdir(parents=True, exist_ok=True)
        ICON_DIR.mkdir(parents=True, exist_ok=True)
        written = []
        for way in ("colour", "light", "mono", "knockout"):
            path = SVG_DIR / "mark-{}-{}.svg".format(self.key, way)
            path.write_text(self.logo_svg(way), encoding="utf-8", newline=NL)
            written.append(path)
        icon = ICON_DIR / "{}.svg".format(self.key)
        icon.write_text(self.icon_svg(), encoding="utf-8", newline=NL)
        flat = ICON_DIR / "{}-flat.svg".format(self.key)
        flat.write_text(self.icon_flat_svg(), encoding="utf-8", newline=NL)
        return written + [icon, flat]


def field(colour):
    """The tile's own ground. It bleeds past the mask by definition, so no corner check."""
    return Rect(0, 0, 108, 108, colour, bleed=True)


# =================================================================================================
# The overhang. A claim resting on a footing that cannot carry it.
#
# This is the family that answers Codex directly. Every earlier mark drew two things apart; this
# draws one thing standing on another and running out past it. A slab with forty units of itself
# hanging over nothing is not a diagram of separation, it is a diagram of insufficiency, and
# insufficiency is the only relation this product has that its neighbours do not. UBERx quotes a
# 152.13 percent premium on a pool holding $80: that is a claim 64 units wide on a footing 14 wide.
# =================================================================================================

OVERHANG = Mark(
    number=1, key="overhang",
    title="The overhang",
    says="a printed figure standing on a pool too narrow to carry it, most of it over nothing",
    system_glyph="a T, a map pin, and the text-underline button, all of which are a bar with a "
                 "stem",
    apart="every one of those centres its stem. This one is 15 units off centre on a 64-unit bar, "
          "so the bar is not balanced on it and the eye reads an overhang rather than a stem. "
          "marks3.reads_as_plus_or_tee refuses a central stem and this drawing passes it.",
    refusal="the figure is the thing the app will not print when the block under it is this "
            "narrow. The mark draws the reason rather than the blank.",
    breaks="Nothing in DESIGN.md. It draws section 1.1, the liquidity floor, which is the one "
           "rule the product is built around.",
    treatment=GROUND, field=CANVAS,
    shapes=[field(CANVAS),
            Rect(22, 38, 64, 12, INK, note="the quoted figure"),
            Rect(30, 50, 12, 22, ACCENT, note="the pool it is quoted off")],
    gaps={"the overhang, right of the footing": 44.0, "the overhang, left of it": 8.0},
    risk="a bar with a stem under it is a busy neighbourhood. The whole mark rests on the offset "
         "being read as offset at 60.7dp.",
)

OVERHANG_FLUSH = Mark(
    number=2, key="overhang-flush",
    title="The overhang, flush",
    says="the same claim with its whole length hanging one way, nothing under the far end",
    system_glyph="a reversed L, and the 'align left' button",
    apart="an L has two arms of comparable length; here the horizontal is 64 units and the "
          "vertical 20, and the vertical is at the very end rather than forming a corner with "
          "room on both sides.",
    refusal="the same, taken to the limit: fifty units of figure with nothing at all beneath it.",
    breaks="Nothing in DESIGN.md.",
    treatment=GROUND, field=CANVAS,
    shapes=[field(CANVAS),
            Rect(22, 38, 64, 12, INK, note="the quoted figure"),
            Rect(22, 50, 14, 20, ACCENT, note="the pool, at the near end")],
    gaps={"the overhang": 50.0},
    risk="corners are punctuation. Codex killed the bracket family for exactly this and a flush "
         "footing makes a corner.",
)

# =================================================================================================
# The floor. The one number in the codebase, drawn as a line that is not reached.
#
# $10,000 of pool depth lives in exactly one place, TrackingQuality, and it decides whether the
# signature element of the product is drawn at all. Above the line the app prints; below it the app
# says what the pool holds and prints nothing. So the line is drawn full width and the measure
# stops short of it, and the space above the line is empty because that is where the figure would
# have been.
# =================================================================================================

FLOOR = Mark(
    number=3, key="floor",
    title="The floor",
    says="the threshold a reading has to clear, and a reading that does not clear it",
    system_glyph="the download glyph without its arrowhead, and a vertical progress bar",
    apart="a download glyph's line is under the mass and this line is over it; a progress bar's "
          "column touches its track and this one stops 12 units short, which is the subject of "
          "the drawing rather than a gap in it.",
    refusal="everything above the line is empty because nothing is printed up there. The void is "
            "two thirds of the visible tile and it is the withheld figure's own room.",
    breaks="Section 8 bans filled progress bars. This is a column that fails to reach a line "
           "rather than a track being filled, which is the distinction the mark rests on and it "
           "is declared rather than smuggled.",
    treatment=GROUND, field=CANVAS,
    shapes=[field(CANVAS),
            Rect(20, 44, 68, 8, INK, note="the $10,000 floor"),
            Rect(28, 64, 12, 20, ACCENT, note="the pool, short of it")],
    gaps={"between the column and the floor": 12.0, "above the floor": 26.0},
    risk="a gap has to be read as deliberate. At 60.7dp, 12 units is 30 device pixels, which is "
         "large, but a line over a block is still a lot of phone vocabulary.",
)

# =================================================================================================
# The kept place. The figure's own slot in a row of figures, held open and left empty.
#
# Of 160 analyzed tokens Jupiter reports a depth for only 53. The app does not close the gap up and
# it does not filter the row out: the place stays in the list and the number is simply not there.
# This is the most literal drawing of the refusal in the set and the most exposed to misreading.
# =================================================================================================

KEPT_PLACE = Mark(
    number=4, key="kept-place",
    title="The kept place",
    says="three registered places, two of them filled, the third kept and left blank",
    system_glyph="a bar chart with a missing bar, and the battery glyph",
    apart="bars vary in height to carry a quantity and these are two identical blocks over three "
          "identical registers, so they are places rather than values; the third register is "
          "drawn and empty, which is a reserved slot and not a missing bar. A battery has one "
          "container, not three, and no chart draws the register of a value it does not have.",
    refusal="drawn directly. The third place is not closed up and not removed, which is the "
            "difference between disclosure and curation, and the accent is on the empty place "
            "rather than on the filled ones.",
    breaks="Section 2 lists what may carry the Accent and a blank is not on the list. Declared "
           "rather than smuggled: a launcher tile carries exactly one accent, the subject of this "
           "mark is the place that was not filled, so the accent is on that place. Section 8's ban "
           "on three equal tiles is about layout containers and these are glyph places on a rule, "
           "but the resemblance is close enough to write down.",
    treatment=GROUND, field=CANVAS,
    shapes=[field(CANVAS),
            Rect(26, 32, 13, 34, INK, note="a place with a figure in it"),
            Rect(47, 32, 13, 34, INK, note="a place with a figure in it"),
            Rect(25, 66, 15, 10, INK, note="the register that place sits in"),
            Rect(46, 66, 15, 10, INK, note="the register that place sits in"),
            Rect(67, 66, 15, 10, ACCENT, note="the register of the place that was not filled")],
    gaps={"between the two filled places": 8.0, "between registers": 6.0,
          "the kept place itself, over its own register": 34.0},
    baseline=66.0, cap=34.0,
    risk="three blocks in a row is the most owned geometry on the phone. It lives or dies on the "
         "baseline being seen.",
)

# =================================================================================================
# The short measure. The place kept, with what is actually behind it drawn to a fraction of the
# height a figure needs. This is the merge: absence and insufficiency in one drawing, which is what
# Codex asked for and what no earlier mark contained.
# =================================================================================================

SHORT_MEASURE = Mark(
    number=5, key="short-measure",
    title="The short measure",
    says="two figures printed, and in the third place a pool a quarter of the height a figure "
         "needs",
    system_glyph="a bar chart, and the signal-strength meter",
    apart="nothing here varies smoothly: two blocks are identical and full height and the third "
          "is a stub on the same row, so it reads as a place that could not be filled rather "
          "than as a third measurement. The row rule under all three is a baseline, which no "
          "signal meter has.",
    refusal="the drawing states the reason and the result at once. The stub is why the figure is "
            "not printed; the empty space over it is the figure that is not printed.",
    breaks="Nothing in DESIGN.md. The ratio is a lie of scale and it is declared: $80 against a "
           "$10,000 floor is 1 to 125, which cannot be drawn, so the stub is 1 to 4.",
    treatment=GROUND, field=CANVAS,
    shapes=[field(CANVAS),
            Rect(26, 34, 13, 32, INK, note="a figure"),
            Rect(47, 34, 13, 32, INK, note="a figure"),
            Rect(68, 58, 13, 8, ACCENT, note="the pool, in the place a third figure would hold"),
            Rect(24, 66, 60, 8, INK, note="the row, which is not shortened")],
    gaps={"between the filled places": 8.0, "between the second and the stub": 8.0,
          "over the stub, where the figure is not": 24.0},
    risk="a chart is the default shape of every finance app on the phone, and this is one step "
         "away from being one.",
)

# =================================================================================================
# The withheld print. Emptiness at the limit.
#
# The founder liked three of attempt four's seven and what all three had in common was emptiness
# used deliberately. This is that taken as far as it goes: a field that is all reading, with the
# one place it will not fill cut out of it. It is in the set as the boundary of the idea, so the
# founder can see where the direction stops working rather than being told.
# =================================================================================================

WITHHELD_PRINT = Mark(
    number=6, key="withheld-print",
    title="The withheld print",
    says="everything the app read, with the one figure it would not print cut out of it",
    system_glyph="a battery terminal, and a 1 in a tile",
    apart="a terminal sits on an edge and this is inset on all four sides; the cut is 16 by 40, "
          "which is a monospace figure's proportion rather than a stroke's.",
    refusal="the mark is the refusal and nothing else. There is no gauge, no reading and no "
            "figure: only the shape of the place where one would have gone.",
    breaks="Nothing. Section 2's one accent is kept by inverting rather than by adding.",
    treatment=INVERTED, field=ACCENT,
    shapes=[field(ACCENT),
            Rect(62, 34, 16, 40, CANVAS, cut=True, note="the figure that was not printed")],
    gaps={"the cut": 16.0},
    risk="a single notch in a field may read as damage or as a stray 1. Antigravity warned in "
         "attempt five that an isolated small unit reads as a speck, and this is that warning "
         "taken on deliberately.",
)

# =================================================================================================
# The gauge, inverted. Not a candidate: the control.
#
# .agents/product-marketing.md claims "the brand mark is the tracking gauge itself rather than a
# letter, because it is the one mark nobody else has and it says what the app does." That mark
# shipped and has been rejected three times. It is drawn here inverted, so it gets the one thing
# attempt five proved it never had, an edge, and so the claim is tested against a drawing instead
# of being argued about.
# =================================================================================================

GAUGE_INVERTED = Mark(
    number=7, key="gauge-inverted",
    title="The tracking gauge, inverted",
    says="the mark on the phone now, given the edge the measurement says it never had",
    system_glyph="a UI slider, which is what Codex called it in attempt five",
    apart="it is not. A slider is exactly what a track with a thumb crossing it is.",
    refusal="none. The gauge is the product's output and it is the thing the product withholds "
            "below the floor, so it is drawn on the screens where a figure is allowed and absent "
            "on the ones where it is not.",
    breaks="Nothing. This is DESIGN.md section 9 as written, inverted.",
    treatment=INVERTED, field=ACCENT,
    shapes=[field(ACCENT),
            Rect(26, 54, 56, 8, CANVAS, cut=True, note="the track"),
            Rect(50, 62, 8, 14, CANVAS, cut=True, note="the reference graduation"),
            Rect(64, 38, 10, 36, CANVAS, cut=True, note="the token tick")],
    gaps={"the track": 8.0, "the reference": 8.0, "the token tick": 10.0},
    risk="it has already been rejected three times. It is here as the control, not as a "
         "candidate.",
)

# =================================================================================================
# The two critics' asked-for changes, carried out literally.
#
# Attempt five's rule stands: each critic gets exactly one concrete change, it is drawn exactly as
# stated whether or not it is right, and the drawing rather than the argument decides. Codex's
# change is to the lockup and lives in wordmark.py; Antigravity's is here.
# =================================================================================================

SHORT_MEASURE_AGY = Mark(
    number=8, key="short-measure-agy",
    title="The short measure, with Antigravity's stub",
    says="Antigravity's one change, drawn exactly: the Accent stub at 4 units instead of 8",
    system_glyph="a bar chart, and the signal-strength meter, which is what Antigravity itself "
                 "named as this drawing's own misread",
    apart="at 4 units it is no longer a stub in a place, it is a hairline under one.",
    refusal="the same as short-measure, if the stub is still there to carry it.",
    breaks="MIN_STROKE. 4 units is 3.4dp and 10 device pixels on the Seeker tile, under the 6-unit "
           "floor this repository has enforced since attempt one, whose 2-unit tick was 1.3dp and "
           "simply was not present on the phone.",
    treatment=GROUND, field=CANVAS,
    shapes=[field(CANVAS),
            Rect(26, 34, 13, 32, INK, note="a figure"),
            Rect(47, 34, 13, 32, INK, note="a figure"),
            Rect(68, 62, 13, 4, ACCENT, note="the pool, at the height Antigravity asked for"),
            Rect(24, 66, 60, 8, INK, note="the row")],
    gaps={"over the stub": 28.0},
    risk="halving the one element that carries the meaning is the attempt-one failure with a "
         "different number on it.",
    waiver="the 6-unit stroke floor, on Antigravity's instruction, so the cost can be looked at "
           "rather than argued about.",
)

CANDIDATES = [OVERHANG, OVERHANG_FLUSH, FLOOR, KEPT_PLACE, SHORT_MEASURE,
              WITHHELD_PRINT]
ASKED = [SHORT_MEASURE_AGY]     # drawn on instruction, judged from the drawing
CONTROL = GAUGE_INVERTED
ALL = CANDIDATES + ASKED + [CONTROL]

# The mark the round ships, set after the two critics reported. Read by icon_measure.py and by
# wordmark.py so the lockups and the launcher icon cannot drift from the adjudication.
CHOSEN = "kept-place"


def chosen():
    return next(m for m in ALL if m.key == CHOSEN)


PAPER_FIELD = "paper"


def paper_of(mark):
    """
    The same drawing on a cool off-white field, which is the third treatment nobody measured.

    Attempts three, four and five only ever asked one ground question: Canvas or an Accent field.
    Canvas has no edge in the drawer (1.03 to 1) and an Accent field has one (about 7.27), so every
    round took the Accent field and paid for it by knocking the mark out, which flattens a two-
    colour mark into one silhouette. On a mark whose whole argument is that one part is Ink and one
    part is Accent, that is not a treatment, it is an amputation.

    A paper field is the way out and it costs nothing the design system has not already conceded:
    DESIGN.md section 2 says a light variant keeps the hierarchy rather than inverting it, and the
    canvas becomes a cool off-white. The mark keeps both colours, the tile gets an edge, and the
    launcher icon becomes the logo's own light colourway rather than a different drawing.
    """
    if mark.treatment == INVERTED:
        return mark
    shapes = [field(PAPER)] + [
        Rect(s.x, s.y, s.w, s.h, INK_LIGHT if s.colour == INK else s.colour, note=s.note)
        for s in mark.marks()]
    return Mark(mark.number, mark.key + "-paper", mark.title + ", on paper",
                mark.says, mark.system_glyph, mark.apart, mark.refusal,
                "Section 2 keeps Canvas as the page background and defers the light variant. A "
                "launcher tile is not a page and the drawer is mostly light tiles; the hierarchy "
                "is kept rather than inverted, which is the condition section 2 sets.",
                PAPER_FIELD, PAPER, shapes, mark.gaps, mark.risk, mark.waiver,
                mark.baseline, mark.cap)


def inverted_of(mark):
    """
    The same drawing knocked out of an Accent field.

    Not a separate mark and not a second idea: attempt five measured that a Canvas tile has no
    edge in the Seeker's drawer (1.03 to 1) and an Accent field has one (6.6 to 7.3 to 1), so the
    launcher icon of a ground-treatment mark is that mark inverted. The geometry is untouched.
    """
    if mark.treatment == INVERTED:
        return mark
    shapes = [field(ACCENT)] + [Rect(s.x, s.y, s.w, s.h, CANVAS, cut=True, note=s.note)
                                for s in mark.marks()]
    return Mark(mark.number, mark.key + "-inverted", mark.title + ", inverted",
                mark.says, mark.system_glyph, mark.apart, mark.refusal,
                "Nothing. Section 2's one accent is kept by inverting rather than by adding.",
                INVERTED, ACCENT, shapes, mark.gaps, mark.risk, mark.waiver,
                mark.baseline, mark.cap)


def main():
    for mark in ALL:
        for path in mark.write():
            print("  {}".format(path.relative_to(BRAND.parent.parent)))
    for mark in CANDIDATES:
        if mark.treatment == GROUND:
            inv = inverted_of(mark)
            ICON_DIR.mkdir(parents=True, exist_ok=True)
            (ICON_DIR / (inv.key + ".svg")).write_text(inv.icon_svg(), encoding="utf-8",
                                                       newline=NL)
            (ICON_DIR / (inv.key + "-flat.svg")).write_text(inv.icon_flat_svg(),
                                                            encoding="utf-8", newline=NL)
            print("  icon/{}.svg and -flat".format(inv.key))
    print()
    print("  {:<22} {:>6} {:>8} {:>7} {:>8}".format("mark", "thin", "on phone", "ink", "corner"))
    for mark in ALL:
        dp, px = mark.on_phone()
        print("  {:<22} {:>4}u {:>6.1f}dp {:>6.1f}% {:>7.2f}".format(
            mark.key, marks3.num(mark.thinnest()), dp, mark.ink_share() * 100, mark.radius()))


if __name__ == "__main__":
    main()

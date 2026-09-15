"""
The mark the founder chose, drawn in every ground it could sit on.

The arrangement is settled and is not reopened here. "Two corners", 4a in the founder's own
selection gallery: two registration corners on the 108 adaptive-icon viewport, top-left and
bottom-right, with an empty centre between them. The four rectangles below are transcribed from
that gallery verbatim and nothing in this file may move them.

    rect x=26 y=26 w=34 h=14      top-left, horizontal arm
    rect x=26 y=26 w=14 h=34      top-left, vertical arm
    rect x=48 y=68 w=34 h=14      bottom-right, horizontal arm
    rect x=68 y=48 w=14 h=34      bottom-right, vertical arm

What is open is the ground, and the ground is what four rejected attempts got wrong. Attempt five
measured it: PlainTicker's tile as it ships reads 1.03 to 1 across its own boundary in the real
drawer, which is not a boundary. So the same four rectangles are built here on four grounds and
measure.py reads each one off the phone:

  two-corners             the founder's pick as picked. Ink corners on Canvas.
  two-corners-inverted    an Accent field with the corners knocked out of it in Canvas. This is
                          what the founder's gallery calls Field, and what cell 4f draws.
  two-corners-paper       the cool off-white the logo round introduced, #F2F5F8, with the corners
                          in a near-black that is not a token either.
  two-corners-ink         the same light idea built out of the palette instead of beside it: an
                          Ink field with the corners in Canvas. Two of the nine tokens of
                          DESIGN.md section 2 and no tenth colour.

Geometry, floors and the mask test are imported from attempt three and the drawing machinery from
the logo round. One rule of marks3 is deliberately not enforced and is reported instead: the mark
carries no Accent at all, because the founder's drawing has none. `check` says so out loud rather
than letting it pass by accident.

Run:    PYTHONIOENCODING=utf-8 python design/brand/two-corners/marks_two_corners.py
Writes: design/brand/two-corners/icon/<key>.svg and <key>-flat.svg
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BRAND = HERE.parent
sys.path.insert(0, str(BRAND / "attempt-three"))
sys.path.insert(0, str(BRAND / "logo"))

import marks3  # noqa: E402
import marks_logo  # noqa: E402
from marks3 import ACCENT, CANVAS, GROUND, INK, INVERTED, Rect  # noqa: E402
from marks_logo import INK_LIGHT, PAPER, field  # noqa: E402

ICON_DIR = HERE / "icon"
NL = chr(10)

PAPER_FIELD = marks_logo.PAPER_FIELD     # "paper"
INK_FIELD = "ink field"

# The four rectangles of gallery cell 4a, as (x, y, w, h, what it is). Transcribed, not derived.
CORNERS = (
    (26, 26, 34, 14, "top-left, horizontal arm"),
    (26, 26, 14, 34, "top-left, vertical arm"),
    (48, 68, 34, 14, "bottom-right, horizontal arm"),
    (68, 48, 14, 34, "bottom-right, vertical arm"),
)

SAYS = ("two registration corners and the empty centre between them: the frame the app keeps "
        "around a figure it has not printed")
NEAREST = ("the crop and scan-frame reticle, and the fullscreen-expand pair; both are corner "
           "brackets on a square")
APART = ("those put their brackets hard against the box edge, keep them thin, and draw all four "
         "or draw arrowheads. These are 14 units thick, inset 26 from the viewport edge, and only "
         "two of the four are there, so what the eye is given is the diagonal between them rather "
         "than a frame around something")
REFUSAL = ("the centre is drawn as a kept place and left empty, which is what the app does below "
           "the liquidity floor of DESIGN.md section 1.1")
RISK = ("a corner reticle is a busy neighbourhood on a phone. The whole mark rests on the two "
        "corners reading as a pair across an empty middle rather than as half a crop tool")


class Corners(marks_logo.Mark):
    """
    The two-corner mark on one ground.

    marks3's floors all apply and are run. Its accent rule does not: it asserts that something in
    the drawing carries Accent, and the founder's 4a carries none. Skipping that silently would
    hide the fact, so `check` states the weaker rule DESIGN.md section 2 actually sets, which is at
    most one accent, and the Accent-field treatment is the only one of the four that has any.
    """

    def check(self):
        for index, shape in enumerate(self.shapes):
            if shape.bleed:
                continue
            floor = marks3.MIN_GAP if shape.cut else marks3.MIN_STROKE
            assert shape.thin >= floor - 1e-9, "{}/{} is {} units, under the {} floor".format(
                self.key, index, marks3.num(shape.thin), marks3.num(floor))
            for x, y in shape.corners():
                assert marks3.Concept.inside_mask(x, y), (
                    "{}/{} corner ({}, {}) falls outside the superellipse the launcher cuts"
                    .format(self.key, index, marks3.num(x), marks3.num(y)))
        for name, size in self.gaps.items():
            assert size >= marks3.MIN_GAP - 1e-9, "{}: the {} gap is {} units, under {}".format(
                self.key, name, marks3.num(size), marks3.num(marks3.MIN_GAP))
        assert not self.reads_as_plus_or_tee(), \
            "{}: the flattened silhouette reads as a plus or a T".format(self.key)
        accents = sum(1 for s in self.shapes if s.colour == ACCENT)
        assert accents <= 1, "{}: DESIGN.md section 2 allows at most one accent".format(self.key)

    # -- what the masks do to it, which is the other measured fact of this round ----------------

    def clipped_by_circle(self):
        """
        The fraction of the mark's own area a true circular mask would cut off.

        Sampled on a half-unit grid over the visible 72 rather than solved, because the answer
        wanted is how much of the drawing disappears, not where the boundary runs. The circle is
        the one inscribed in the visible 72, radius 36 from the centre, which is what a launcher
        that cuts circles applies.
        """
        step, painted, lost = 0.5, 0, 0
        y = 18.0
        while y < 90.0:
            x = 18.0
            while x < 90.0:
                on = False
                for shape in self.marks():
                    if self._covers(shape, x, y):
                        on = not shape.cut
                if on:
                    painted += 1
                    if math.hypot(x - marks3.CENTRE, y - marks3.CENTRE) > marks3.VISIBLE / 2:
                        lost += 1
                x += step
            y += step
        return lost / float(painted) if painted else 0.0

    def icon_flat_svg(self):
        """
        The flattened layer: the four corner rectangles in one colour, and nothing else.

        marks_logo keeps the field in the flattened drawing for every treatment that is not the
        Canvas ground, because in that round every treatment that was not the ground was an
        inverted one whose mark is a hole in a field. Two of the four treatments here are a dark
        mark on a light field, and for those the field is not part of the drawing at all: Android's
        monochrome layer is one colour on a plate the launcher supplies, so the only thing that can
        carry the mark is the mark. Keeping the field there flattens the whole tile to solid, which
        is what it did before this override. The founder's gallery draws exactly this as cell 4a's
        flat variant: four rectangles in currentColor.
        """
        keep = self.shapes if self.treatment == INVERTED else self.marks()
        entries = ["      " + s.svg("#000000" if s.cut else "#ffffff") for s in keep]
        return (
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" '
            'height="108">{nl}{note}{nl}'
            '  <mask id="{key}" maskUnits="userSpaceOnUse" x="0" y="0" width="108" height="108">'
            '{nl}{body}{nl}  </mask>{nl}'
            '  <rect x="0" y="0" width="108" height="108" fill="#FFFFFF" mask="url(#{key})"/>{nl}'
            '</svg>{nl}'
        ).format(nl=NL, note=self.note("flattened to one colour, themed icon and notification"),
                 key=self.key, body=NL.join(entries))

    def mask_clearance(self, exponent):
        """
        How much room the tightest corner has inside a superellipse of this exponent, in units.

        Positive is inside. Measured along the ray the corner actually sits on, so the number is
        the distance the drawing could still be pushed outward before the mask starts eating it.
        """
        worst = None
        half = marks3.VISIBLE / 2
        for shape in self.marks():
            if shape.bleed:
                continue
            for x, y in shape.corners():
                ax, ay = abs(x - marks3.CENTRE), abs(y - marks3.CENTRE)
                here = math.hypot(ax, ay)
                if here < 1e-9:
                    continue
                ux, uy = ax / here, ay / here
                edge = 1.0 / ((ux / half) ** exponent + (uy / half) ** exponent) ** (1.0 / exponent)
                room = edge - here
                worst = room if worst is None else min(worst, room)
        return worst


def mark_rects(colour):
    return [Rect(x, y, w, h, colour, note=note) for x, y, w, h, note in CORNERS]


GAPS = {
    "between the two corners, across": 8.0,
    "between the two corners, down": 8.0,
    "the empty centre, square": 28.0,
}


def on_canvas():
    """Cell 4a exactly as the founder picked it: Ink corners on the Canvas ground."""
    return Corners(
        number=1, key="two-corners", title="Two corners, Canvas ground",
        says=SAYS, system_glyph=NEAREST, apart=APART, refusal=REFUSAL,
        breaks="Nothing in section 2, and nothing in section 9 but the sentence saying the mark is "
               "the gauge. It carries no Accent, which section 2 permits.",
        treatment=GROUND, field=CANVAS,
        shapes=[field(CANVAS)] + mark_rects(INK), gaps=GAPS, risk=RISK)


def on_accent():
    """What the founder's gallery calls Field: an Accent tile with the corners cut out of it."""
    return Corners(
        number=2, key="two-corners-inverted", title="Two corners, Accent field",
        says=SAYS, system_glyph=NEAREST, apart=APART, refusal=REFUSAL,
        breaks="Nothing. Section 2's one accent is kept by inverting the tile rather than by "
               "adding a second coloured shape.",
        treatment=INVERTED, field=ACCENT,
        shapes=[field(ACCENT)] + [Rect(x, y, w, h, CANVAS, cut=True, note=note)
                                  for x, y, w, h, note in CORNERS],
        gaps=GAPS, risk=RISK)


def on_paper():
    """The cool off-white the logo round measured at 16.89 to 1, and the two colours it costs."""
    return Corners(
        number=3, key="two-corners-paper", title="Two corners, cool off-white field",
        says=SAYS, system_glyph=NEAREST, apart=APART, refusal=REFUSAL,
        breaks="Two colours that are not tokens. #F2F5F8 and #0E141B appear nowhere in section "
               "2's nine, so BrandAssetsTest cannot pin the icon to the Kotlin palette and a "
               "token change could leave the icon behind.",
        treatment=PAPER_FIELD, field=PAPER,
        shapes=[field(PAPER)] + mark_rects(INK_LIGHT), gaps=GAPS, risk=RISK)


def on_ink():
    """The same light idea out of the palette: an Ink field, the corners in Canvas."""
    return Corners(
        number=4, key="two-corners-ink", title="Two corners, Ink field",
        says=SAYS, system_glyph=NEAREST, apart=APART, refusal=REFUSAL,
        breaks="Section 2 keeps Canvas as the page background and defers the light variant. A "
               "launcher tile is not a page, and this inverts nothing in the hierarchy: it is the "
               "same Ink-and-Canvas pair section 2 already rates at 15.9 to 1, with the tile "
               "taking the Ink side.",
        treatment=INK_FIELD, field=INK,
        shapes=[field(INK)] + mark_rects(CANVAS), gaps=GAPS, risk=RISK)


TREATMENTS = [on_canvas(), on_accent(), on_paper(), on_ink()]

# The ground the round ships, set after measure.py read all four off the phone. glyph.py and
# BrandAssetsTest take the geometry from design/brand/marks.py; this names the ground that won.
CHOSEN = "two-corners-ink"


def chosen():
    return next(m for m in TREATMENTS if m.key == CHOSEN)


def main():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    for mark in TREATMENTS:
        (ICON_DIR / (mark.key + ".svg")).write_text(mark.icon_svg(), encoding="utf-8", newline=NL)
        (ICON_DIR / (mark.key + "-flat.svg")).write_text(mark.icon_flat_svg(), encoding="utf-8",
                                                         newline=NL)
        print("  icon/{}.svg and -flat.svg".format(mark.key))
    print()
    first = TREATMENTS[0]
    dp, px = first.on_phone()
    print("  the arrangement, once, since every treatment is the same four rectangles:")
    print("    thinnest arm      {:g} units, {:.1f}dp and {:.0f} device pixels on the Seeker tile"
          .format(first.thinnest(), dp, px))
    print("    empty centre      28 x 28 units, dead centre")
    print("    furthest corner   {:.2f} units from the centre, against a 33-unit circle guarantee"
          .format(first.radius()))
    for exponent in (3.05, 3.0):
        print("    superellipse {:.2f}  clearance {:+.2f} units at the tightest corner"
              .format(exponent, first.mask_clearance(exponent)))
    print("    a true circle     clips {:.1f}% of the mark's area"
          .format(first.clipped_by_circle() * 100))
    print()
    print("  {:<22} {:>10} {:>8}".format("treatment", "field", "ink"))
    for mark in TREATMENTS:
        print("  {:<22} {:>10} {:>7.1f}%".format(mark.key, mark.field, mark.ink_share() * 100))


if __name__ == "__main__":
    main()

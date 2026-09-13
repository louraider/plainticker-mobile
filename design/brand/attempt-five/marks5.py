"""
Attempt five: the two marks the founder kept, developed rather than replaced.

Attempt four put seven concepts from Antigravity into the real drawer. The founder kept two of
them, number 2 (the live bar anchor) and number 6 (the disconnected quote), and asked for another
round on those two and nothing else. So this file holds two families rather than a fresh set of
ideas, and every variant in a family is an answer to a named problem with that family.

The problems are already known and are not rediscovered here.

  The ground. Canvas is #0B0F14 and the Seeker's drawer wallpaper is within a few units of it. A
  Canvas tile has no edge at all: attempt three's compositor cannot even find PlainTicker's own
  slot by scanning for a tile against the wallpaper. Attempt three died of this and attempt four
  repeated it six times out of seven, both marks kept here among them. Every family below carries
  at least one inverted variant, where the tile is a solid Accent field and the mark is knocked out
  of it in Canvas, and composite5.py measures the contrast across the tile boundary in the real
  drawer for every variant so the question is settled by a number rather than by taste.

  The live bar's misread and its scale. Antigravity named the hamburger menu as this mark's nearest
  system glyph itself. A hamburger is three equal full-width rows evenly distributed over the
  height of a square, inset from every edge; it has no vertical member at all. The corrections here
  are structural, not cosmetic: the bar bleeds off the top and the bottom of the viewport so the
  mask cuts it, which no system glyph ever does, and the readings are pushed below the middle so
  the upper third of the tile is bar and nothing else. The scale complaint is real and only half
  soluble. The app's live bar is a 2dp rule; 2dp of a 48dp tile is 4.5 viewport units, under the
  6-unit floor, and on the Seeker's 60.7dp tile it is 3.8 units, still under it. What can be kept
  is the bar's proportion rather than its width, and the true-weight variants do that: 8 units
  across the 72 a launcher shows is 1 to 9, which is the aspect the bar has beside its own label in
  the app. The heavy variants keep Antigravity's 12 and the two are drawn side by side.

  The quote's dot. Eight units is 5.3dp at 48dp and 6.7dp on the Seeker's real 60.7dp tile, and it
  is a circle, which breaks section 4's radius 0, and section 8 bans coloured dots as decoration.
  The defence on record is that the dot is not decoration, it is the $80. That defence is tested
  here rather than repeated: the square-pool variants square it, which is what the shape lock asks
  for and also what makes it read as a measured unit rather than a speck, and the small-pool
  variant keeps it at 8 units so the threshold can be looked at rather than argued about. A round
  variant at the same size as the square one is drawn so circle against square is a comparison and
  not an assertion.

Both concepts as Antigravity proposed them are redrawn here, keyed with -as-proposed, so every
development is judged against the thing it develops and not against nothing. One repair was made to
the quote while redrawing it: the bracket's arms now overlap its spine instead of butting against
it, because the original left a visible hairline seam at the join at every size. Nothing else
changed in either control.

Two more drawings arrived after the two critics read the sheet, one requested by each of them, and
they are keyed -wide-gutter- and -tight-gulf-. Both are somebody else's instruction carried out
exactly rather than adopted, and CRITIQUE.md says which was followed and why.

Geometry, floors and checks are imported from attempt three rather than copied: MIN_STROKE 6 units,
MIN_GAP 6 units, the superellipse mask of exponent 3.0 (the measured one is 3.05, and the tighter
number is checked), one accent, and reads_as_plus_or_tee.

Run:  PYTHONIOENCODING=utf-8 python design/brand/attempt-five/marks5.py
Writes: design/brand/attempt-five/svg/<key>.svg and <key>-flat.svg
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "attempt-three"))

import marks3  # noqa: E402
from marks3 import ACCENT, CANVAS, GROUND, INK, INVERTED, Circle, Rect  # noqa: E402

SVG_DIR = HERE / "svg"
NL = chr(10)

# The Seeker, measured on the drawer screenshot rather than assumed.
TILE_PX = 182.0          # a drawer tile is 182 device pixels
DPI_SCALE = 3.0          # 480dpi
TILE_DP = TILE_PX / DPI_SCALE                 # 60.7dp, not the 48dp an icon brief assumes
UNIT_DP = TILE_DP / marks3.VISIBLE            # one viewport unit, in dp, on the real tile
UNIT_PX = TILE_PX / marks3.VISIBLE            # one viewport unit, in device pixels

FAMILY_BAR = "The live bar anchor"
FAMILY_QUOTE = "The disconnected quote"


class Variant(marks3.Concept):
    """One drawing in a family, named for what it changes rather than for its place in a list."""

    def __init__(self, number, family, key, title, varies, caption, breaks, treatment, field,
                 shapes, gaps, argument=""):
        self.family = family
        self.varies = varies        # the one axis this variant moves: ground, weight, proportion
        self.argument = argument    # why this drawing answers the problem the family has
        super().__init__(number, key, title, caption, breaks, treatment, field, shapes, gaps)

    @property
    def rank(self):
        return self.number

    def thinnest(self):
        """The narrowest positive dimension in the mark, in units."""
        return min((s.thin for s in self.shapes[1:] if not s.bleed), default=0.0)

    def thinnest_on_phone(self):
        """That dimension where it is actually read: dp and device pixels on a Seeker tile."""
        thin = self.thinnest()
        return thin * UNIT_DP, thin * UNIT_PX

    def header(self, layer):
        dp, px = self.thinnest_on_phone()
        return (
            "<!--{nl}"
            "  {family}: {title}{nl}"
            "  Varies: {varies}{nl}"
            "  {caption}{nl}"
            "  Treatment: {treatment}. Field {field}.{nl}"
            "  Breaks: {breaks}{nl}"
            "  Layer: {layer}.{nl}"
            "  Thinnest positive shape {thin} units, {dp}dp and {px} device pixels on the{nl}"
            "  Seeker's measured 60.7dp drawer tile.{nl}"
            "  Furthest corner {radius} of the 33 circle-mask guarantee; drawn for the real{nl}"
            "  superellipse mask measured off the phone, see attempt-three/composite.py.{nl}"
            "  Generated by design/brand/attempt-five/marks5.py, do not edit by hand.{nl}"
            "-->"
        ).format(nl=NL, family=self.family, title=self.title, varies=self.varies,
                 caption=self.caption, treatment=self.treatment, field=self.field,
                 breaks=self.breaks, layer=layer,
                 thin="{:g}".format(self.thinnest()), dp="{:.1f}".format(dp),
                 px="{:.0f}".format(px), radius="{:.2f}".format(self.radius()))

    def write(self):
        SVG_DIR.mkdir(parents=True, exist_ok=True)
        colour = SVG_DIR / (self.key + ".svg")
        flat = SVG_DIR / (self.key + "-flat.svg")
        colour.write_text(self.colour_svg(), encoding="utf-8", newline=NL)
        flat.write_text(self.flat_svg(), encoding="utf-8", newline=NL)
        return colour, flat


# -- family one: the live bar anchor -------------------------------------------------------------
#
# What the family is: a vertical member on the left margin with horizontal readings held off it.
# What it claims about this product: the bar that means live is the thing the app is built on, and
# the readings hang off it rather than standing on their own. When the bar stops, nothing hangs.

BAR_AS_PROPOSED = Variant(
    1, FAMILY_BAR, "live-bar-as-proposed", "As proposed",
    "nothing, this is attempt four redrawn",
    "the bar contained inside the mask, two dashes level with the middle",
    "Section 1's quiet character, and the live bar's own scale: it is a 2dp rule inside the app and "
    "12 units here. Its nearest system glyph is a hamburger menu and the drawing does not answer "
    "that yet.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(26, 26, 12, 56, ACCENT, note="the live bar, contained"),
        Rect(46, 38, 38, 10, INK, note="the long reading"),
        Rect(46, 60, 22, 10, INK, note="the short reading"),
    ],
    {"bar to readings": 8.0, "between the readings": 12.0},
    argument="The control. Every other drawing in this family is judged against this one.",
)

BAR_BLEED_GROUND = Variant(
    2, FAMILY_BAR, "live-bar-bleed-ground", "Bleeding bar, Canvas ground",
    "the bar runs off the top and the bottom, the readings drop below the middle",
    "the bar is no longer an object in a box, it is the edge of one",
    "Section 1's quiet character. The scale of the live bar is still Antigravity's 12 units, "
    "which is 10.1dp on the Seeker's tile against a 2dp rule in the app.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(32, 0, 12, 108, ACCENT, bleed=True, note="the live bar, running past both edges"),
        Rect(52, 44, 32, 10, INK, note="the long reading"),
        Rect(52, 66, 18, 10, INK, note="the short reading"),
    ],
    {"bar to readings": 8.0, "between the readings": 12.0,
     "empty above the first reading": 44.0},
    argument="Two structural changes, both aimed at the hamburger. A menu glyph is inset from "
            "every edge of its box and its rows are evenly spread over the full height; this bar "
            "is cut by the mask at both ends and the whole upper third of the tile is bar and "
            "nothing else. What is left is a margin with readings hanging off it, which is a "
            "document, not a control.",
)

BAR_BLEED_INVERTED = Variant(
    3, FAMILY_BAR, "live-bar-bleed-inverted", "Bleeding bar, Accent field",
    "the ground, from Canvas to a solid Accent field with the mark knocked out",
    "the tile is the live field and the reading is cut into it",
    "Section 1's quiet character, and the same 12-unit scale. Section 2's one accent is kept by "
    "inverting rather than by adding.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(32, 0, 12, 108, CANVAS, cut=True, bleed=True, note="the live bar, as a cut"),
        Rect(52, 44, 32, 10, CANVAS, cut=True, note="the long reading"),
        Rect(52, 66, 18, 10, CANVAS, cut=True, note="the short reading"),
    ],
    {"bar to readings": 8.0, "between the readings": 12.0,
     "empty above the first reading": 44.0},
    argument="The same drawing with the one change that decides the round. A knocked-out mark is "
            "also the second answer to the hamburger: no system glyph in Android is a knockout "
            "from a saturated field, they are all strokes on nothing.",
)

BAR_TRUE_WEIGHT_GROUND = Variant(
    4, FAMILY_BAR, "live-bar-true-weight-ground", "True weight, Canvas ground",
    "the weight, from Antigravity's 12 units to the 8 that keeps the app's own proportion",
    "the bar at the aspect it has in the app, 1 to 9 across the 72 a launcher shows",
    "Section 1's quiet character. The scale lie is answered as far as it can be: 2dp of a 48dp "
    "tile is 4.5 units, under the 6-unit floor, so the absolute width cannot be kept and the "
    "proportion is kept instead.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(34, 0, 8, 108, ACCENT, bleed=True, note="the live bar at the app's own aspect"),
        Rect(50, 44, 34, 8, INK, note="the long reading"),
        Rect(50, 66, 20, 8, INK, note="the short reading"),
    ],
    {"bar to readings": 8.0, "between the readings": 14.0,
     "empty above the first reading": 44.0},
    argument="8 units is 6.7dp on the Seeker's tile and 20 device pixels, comfortably above the "
            "floor, and 8 across 72 is the 1 to 9 the bar has beside its label in the app. This is "
            "the variant that can be defended to somebody who has the app open beside the drawer.",
)

BAR_TRUE_WEIGHT_INVERTED = Variant(
    5, FAMILY_BAR, "live-bar-true-weight-inverted", "True weight, Accent field",
    "the ground and the weight together",
    "the app's own proportion, cut out of a field that has an edge",
    "Section 1's quiet character only. This is the variant that breaks the least.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(34, 0, 8, 108, CANVAS, cut=True, bleed=True, note="the live bar, as a cut"),
        Rect(50, 44, 34, 8, CANVAS, cut=True, note="the long reading"),
        Rect(50, 66, 20, 8, CANVAS, cut=True, note="the short reading"),
    ],
    {"bar to readings": 8.0, "between the readings": 14.0,
     "empty above the first reading": 44.0},
    argument="Both corrections at once. The risk it carries is the opposite of the family's usual "
            "one: a thin cut in a bright field can fill in at 16 px in a way a thin bar on black "
            "does not, and the size strip in the gallery is where that is checked.",
)

BAR_ONE_READING = Variant(
    6, FAMILY_BAR, "live-bar-one-reading-inverted", "One reading, Accent field",
    "the count, from two readings to one",
    "the bar and the single reading it is holding up",
    "Section 1's quiet character. Nothing else. With one row there is no list left to mistake for "
    "a menu.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(32, 0, 12, 108, CANVAS, cut=True, bleed=True, note="the live bar, as a cut"),
        Rect(52, 58, 32, 12, CANVAS, cut=True, note="the one reading, below the middle"),
    ],
    {"bar to reading": 8.0, "empty above the reading": 58.0},
    argument="The blunt answer to the misread: a hamburger needs rows, and two is already a short "
            "list. One reading off one bar cannot be a menu at any size. What it costs is the "
            "plural in the idea, the app pulls several readings off a mint, and what it buys is "
            "that the mark stops resembling a control at all.",
)


# -- family two: the disconnected quote ----------------------------------------------------------
#
# What the family is: a monospace right bracket at one boundary and a single small unit at the
# other, with the whole middle of the tile empty.
# What it claims about this product: the quote and the pool it was quoted off are not the same size
# and the app is the thing that shows the distance between them. A 152 percent premium on a pool
# holding $80 is this drawing.

QUOTE_AS_PROPOSED = Variant(
    7, FAMILY_QUOTE, "quote-as-proposed", "As proposed",
    "nothing, this is attempt four redrawn",
    "a 12-unit bracket on the right, an 8-unit round dot stranded on the left",
    "Section 4's radius 0, broken by the dot, and section 8's ban on coloured dots as decoration. "
    "The dot is 8 units, which is 6.7dp on the Seeker's tile and the smallest element anywhere in "
    "attempt four.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(70, 30, 12, 48, INK, note="the bracket, spine"),
        Rect(54, 30, 28, 12, INK, note="the bracket, upper arm"),
        Rect(54, 66, 28, 12, INK, note="the bracket, lower arm"),
        Circle(28, 54, 4, ACCENT, note="the pool the quote came off"),
    ],
    {"the gulf, pool to bracket": 22.0, "the bracket's opening": 24.0},
    argument="The control. The dot is the thing under test and it is drawn exactly as it was.",
)

QUOTE_SQUARE_POOL_GROUND = Variant(
    8, FAMILY_QUOTE, "quote-square-pool-ground", "Square pool, Canvas ground",
    "the pool, from a round 8-unit dot to a square 12-unit unit",
    "the pool as a measured unit, one stroke of the bracket wide",
    "Nothing. Squaring the pool puts section 4's radius 0 back and takes the mark out of section "
    "8's coloured-dot clause at the same time.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(70, 30, 12, 48, INK, note="the bracket, spine"),
        Rect(54, 30, 28, 12, INK, note="the bracket, upper arm"),
        Rect(54, 66, 28, 12, INK, note="the bracket, lower arm"),
        Rect(22, 48, 12, 12, ACCENT, note="the pool, squared and sized to the bracket's stroke"),
    ],
    {"the gulf, pool to bracket": 20.0, "the bracket's opening": 24.0},
    argument="Two problems answered by one change. A circle in this system is always decoration "
            "because radius 0 is the shape lock, so a round dot reads as a bullet or a status "
            "light however it is captioned; a square of the same width as the bracket's own stroke "
            "reads as one unit of the thing on the other side of the tile, which is the "
            "comparison the mark exists to make. 12 units is 10.1dp on the Seeker's tile, up from "
            "6.7.",
)

QUOTE_SQUARE_POOL_INVERTED = Variant(
    9, FAMILY_QUOTE, "quote-square-pool-inverted", "Square pool, Accent field",
    "the ground, from Canvas to a solid Accent field with the mark knocked out",
    "the bracket and the pool cut out of a field that has an edge",
    "Nothing. Section 2's one accent is kept by inverting rather than by adding.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(70, 30, 12, 48, CANVAS, cut=True, note="the bracket, spine"),
        Rect(54, 30, 28, 12, CANVAS, cut=True, note="the bracket, upper arm"),
        Rect(54, 66, 28, 12, CANVAS, cut=True, note="the bracket, lower arm"),
        Rect(22, 48, 12, 12, CANVAS, cut=True, note="the pool, as a hole"),
    ],
    {"the gulf, pool to bracket": 20.0, "the bracket's opening": 24.0},
    argument="The pool becomes a hole rather than a dot, which is a better drawing of $80 than a "
            "lit mark was, and the tile gets the edge the family has never had.",
)

QUOTE_SMALL_POOL_INVERTED = Variant(
    10, FAMILY_QUOTE, "quote-small-pool-inverted", "Small pool, Accent field",
    "the proportion, the pool held at Antigravity's 8 units",
    "the same drawing with the pool left at the size that was doubted",
    "Nothing. This is the size test, not a proposal.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(70, 30, 12, 48, CANVAS, cut=True, note="the bracket, spine"),
        Rect(54, 30, 28, 12, CANVAS, cut=True, note="the bracket, upper arm"),
        Rect(54, 66, 28, 12, CANVAS, cut=True, note="the bracket, lower arm"),
        Rect(24, 50, 8, 8, CANVAS, cut=True, note="the pool at the doubted size, squared"),
    ],
    {"the gulf, pool to bracket": 22.0, "the bracket's opening": 24.0},
    argument="Drawn so the threshold can be looked at in the drawer strip beside the 12-unit one "
            "instead of being argued about. Everything else is held constant.",
)

QUOTE_HEAVY_BRACKET = Variant(
    11, FAMILY_QUOTE, "quote-heavy-bracket-inverted", "Heavy bracket, Accent field",
    "the weight, the bracket up to 14 units and the pool to 16",
    "both sides of the comparison made heavier, the gulf held",
    "Nothing. The mass answer, drawn once so it can be rejected on sight rather than in theory.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(68, 28, 14, 52, CANVAS, cut=True, note="the bracket, spine"),
        Rect(52, 28, 30, 12, CANVAS, cut=True, note="the bracket, upper arm"),
        Rect(52, 68, 30, 12, CANVAS, cut=True, note="the bracket, lower arm"),
        Rect(22, 46, 16, 16, CANVAS, cut=True, note="the pool, at mass"),
    ],
    {"the gulf, pool to bracket": 14.0, "the bracket's opening": 28.0},
    argument="Attempt three's lesson was that mass is not an idea, so this exists to be compared "
            "and most likely dropped. It does buy one thing: at 16 units the pool cannot be "
            "mistaken for an artefact at any size the phone draws.",
)

QUOTE_ROUND_POOL_GROUND = Variant(
    12, FAMILY_QUOTE, "quote-round-pool-ground", "Round pool, Canvas ground",
    "the shape of the pool only, a 12-unit circle against the 12-unit square",
    "the circle kept, at the square's size, so the shape lock is a comparison",
    "Section 4's radius 0 and section 8's coloured dots, both broken knowingly, which is the "
    "position attempt four took. Drawn at 12 units so the only difference from the square variant "
    "is the shape.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(70, 30, 12, 48, INK, note="the bracket, spine"),
        Rect(54, 30, 28, 12, INK, note="the bracket, upper arm"),
        Rect(54, 66, 28, 12, INK, note="the bracket, lower arm"),
        Circle(28, 54, 6, ACCENT, note="the pool, round, at the square's width"),
    ],
    {"the gulf, pool to bracket": 20.0, "the bracket's opening": 24.0},
    argument="If the round pool is going to break two rules it should have to win on sight against "
            "the square one at the same size, not against the 8-unit dot that was too small. This "
            "is that comparison and nothing else.",
)


# The mark on the phone right now, redrawn from res/drawable/ic_launcher_foreground.xml. Not a
# candidate: the thing both families would replace, in the same drawer, at the same size.
SHIPPED = Variant(
    0, "As it ships today", "as-shipped", "As it ships today",
    "nothing, this is the mark on the phone",
    "attempt two's tracking gauge, for comparison only",
    "Nothing. This is the mark that has already been rejected.",
    GROUND, CANVAS,
    [
        Rect(0, 0, 108, 108, CANVAS, bleed=True, note="the field"),
        Rect(26, 54, 56, 8, INK, note="the track"),
        Rect(50, 62, 8, 14, INK, note="the reference graduation, the NYSE close"),
        Rect(64, 38, 10, 36, ACCENT, note="the token tick, crossing the track"),
    ],
    {"reference to token": 6.0},
    argument="Not a candidate.",
)

# Added after the critics read the sheet. Antigravity asked for exactly this and nothing else in
# family one: the bar two units right, so the Accent left of it is 18 units and reads as a margin
# rather than as a sliver. Codex read the same band as a sidebar and would not widen it. Drawn so
# the two readings can be compared rather than summarised.
BAR_WIDE_GUTTER = Variant(
    13, FAMILY_BAR, "live-bar-wide-gutter-inverted", "Wide gutter, Accent field",
    "the gutter, from 14 units of Accent left of the bar to 18, at Antigravity's request",
    "the same true-weight drawing with the bar moved two units right",
    "Section 1's quiet character. Nothing else.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(36, 0, 8, 108, CANVAS, cut=True, bleed=True, note="the live bar, as a cut"),
        Rect(52, 44, 32, 8, CANVAS, cut=True, note="the long reading"),
        Rect(52, 66, 18, 8, CANVAS, cut=True, note="the short reading"),
    ],
    {"bar to readings": 8.0, "between the readings": 14.0,
     "the gutter, mask edge to bar": 18.0},
    argument="Antigravity's own correction, drawn to its instruction. Its reasoning is that the "
            "narrow band left of the bar reads as an accident and a wider one reads as a margin. "
            "Codex, reading the same drawing, said the band is not an accident, it is a sidebar, "
            "and that widening it makes the pane-layout reading stronger rather than weaker. Both "
            "cannot be right and the founder can see which.",
)

# Added after the critics read the sheet. Antigravity asked for the pool six units right, closing
# the gulf from 20 units to 14, to tighten the connection between the two shapes. Codex argued that
# the emptiness is the only thing the mark has and that closing it turns the pair into two
# components of one control. Drawn so that disagreement is a picture.
QUOTE_TIGHT_GULF = Variant(
    14, FAMILY_QUOTE, "quote-tight-gulf-inverted", "Tight gulf, Accent field",
    "the gulf, from 20 units to 14, at Antigravity's request",
    "the same drawing with the pool moved six units toward the bracket",
    "Nothing.",
    INVERTED, ACCENT,
    [
        Rect(0, 0, 108, 108, ACCENT, bleed=True, note="the field"),
        Rect(70, 30, 12, 48, CANVAS, cut=True, note="the bracket, spine"),
        Rect(54, 30, 28, 12, CANVAS, cut=True, note="the bracket, upper arm"),
        Rect(54, 66, 28, 12, CANVAS, cut=True, note="the bracket, lower arm"),
        Rect(28, 48, 12, 12, CANVAS, cut=True, note="the pool, moved in"),
    ],
    {"the gulf, pool to bracket": 14.0, "the bracket's opening": 24.0},
    argument="Antigravity's own correction, drawn to its instruction. The cost Codex named is "
            "visible here: at 14 units the pool and the bracket start to read as two parts of one "
            "control rather than as two things that cannot account for each other.",
)

BAR_FAMILY = [BAR_AS_PROPOSED, BAR_BLEED_GROUND, BAR_BLEED_INVERTED, BAR_TRUE_WEIGHT_GROUND,
              BAR_TRUE_WEIGHT_INVERTED, BAR_WIDE_GUTTER, BAR_ONE_READING]
QUOTE_FAMILY = [QUOTE_AS_PROPOSED, QUOTE_SQUARE_POOL_GROUND, QUOTE_SQUARE_POOL_INVERTED,
                QUOTE_SMALL_POOL_INVERTED, QUOTE_TIGHT_GULF, QUOTE_HEAVY_BRACKET, QUOTE_ROUND_POOL_GROUND]
VARIANTS = BAR_FAMILY + QUOTE_FAMILY


def main():
    print("one viewport unit on the Seeker's {:.1f}dp drawer tile: {:.3f}dp, {:.2f} device pixels"
          .format(TILE_DP, UNIT_DP, UNIT_PX))
    for variant in VARIANTS + [SHIPPED]:
        variant.write()
        dp, px = variant.thinnest_on_phone()
        print("  {:<34} thinnest {:>4g} units = {:>4.1f}dp = {:>3.0f}px   corner {:>5.1f}   {}"
              .format(variant.key, variant.thinnest(), dp, px, variant.radius(), variant.varies))
    print("wrote {} drawings, colour and flattened, to {}".format(len(VARIANTS) + 1, SVG_DIR))


if __name__ == "__main__":
    main()

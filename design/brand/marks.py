"""
The brand mark, as geometry (DESIGN.md section 9).

The launcher icon is not a picture, it is a handful of axis-aligned rectangles, so the mark lives
here as numbers and every drawable is written from them. `CHOSEN` names the one that ships and
design/brand/glyph.py writes it into res/drawable and res/values.

Coordinate system: the 108 adaptive-icon viewport. Center (54, 54). A launcher shows the central
72 and cuts a mask out of it, so where a shape may go is decided by that mask.

Two things changed on 2026-09-15, when the founder chose "Two corners" out of their own selection
gallery, and both are worth reading before changing anything here.

The mask rule is now the mask, not the circle. Every earlier mark was asserted inside the central
66 circle, radius 33, on the grounds that a launcher might cut a circle. The chosen mark's corners
sit 39.60 units out, so that rule would refuse it. The rule was always a proxy: the mask on the
phone this ships to was lifted off a neighbouring tile in the drawer screenshot and fits a
superellipse of exponent 3.05, and `MASK_EXPONENT` is that number rounded down to 3.0, because a
smaller exponent is the tighter shape and a mark that clears 3.0 clears whatever the launcher is
really cutting. The corners clear it by 0.81 of a unit, which is two device pixels and two thirds
of a dp on the Seeker's real tile: this mark is at the edge of the mask, not comfortably inside it,
and must not be pushed out any further. `SAFE_RADIUS` is kept and reported so the cost stays
visible in the file that used to enforce it. A launcher that cuts a true circle clips 2.1 percent
of the mark's area, and what it takes is the outer right-angle point off both corners;
design/brand/two-corners/gallery.html draws that.

The ground is now part of the mark. Four icon attempts were rejected and every one of them put a
Canvas tile on a near-black drawer wallpaper, where it has no boundary at all: 1.03 to 1, measured
across the tile edge in the real drawer by design/brand/attempt-five/composite5.py. So a Mark
carries the colour of its own background layer, glyph.py writes that colour into
res/values/ic_launcher_background.xml, and the tile cannot drift from the mark that sits on it.

2026-09-24: refit for Amber's tile, direction A of a three-direction audit (the comparison page
built for that audit, and DESIGN.md section 9's "Two corners, refit"). The audit found the drawing
itself fine but two things wrong with it: the tile carried Amber's colours without any amber in it
(one more white tile in the drawer instead of the one warm-yellow one), and the corners sat past
the plain 36-unit circle (VISIBLE / 2) a "Pixel-style" launcher cuts, not just the tighter
superellipse above. Both are fixed by numbers alone, not a redraw: the block the four rectangles
occupy pulled in from 26-to-82 to 31-to-77 and the arm thinned from 14 to 12 units, which moves the
furthest corner from 39.60 units out to 32.53, inside not only MASK_EXPONENT's superellipse but
also that plain 36-unit circle and even the deprecated SAFE_RADIUS (33) this file used to report
missing entirely. And `Mark.ground` moved off AMBER_INK onto `AMBER_ACTION` (#FFC247, Tokens.kt
`AmberPrimitive.fillDark` / `AmberDarkColors.actionFill`): same near-black corners
(`AMBER_GROUND`, unchanged), a warm amber tile under them instead of a cream one. Nothing about the
arrangement itself moved relative to its own logic (still two L-shaped corners on opposite
diagonals around an empty, kept centre, still rotationally symmetric about it and never mirrored
top to bottom): only how far out it reaches and what colour it sits on.

Why rectangles with this much mass: an icon is read at 48dp. The visible part of the 108 viewport
is its central 72, so one viewport unit is 48/72 = 0.667dp on a launcher grid. The tick that was
2 units tall in the icon this replaces measured 1.3dp there and vanished. Nothing below
MIN_STROKE units is drawn, which is 4dp at 48dp.

And why nothing here is symmetric about its own middle: a mark that is, collapses in one colour.
The first gauge drawn on 2026-09-13 put the track, the reference tick and the token tick all on
y=54, and the three of them were a cross. In colour the Accent tick separated and it read as a
gauge; flattened for the themed icon and for the 24 notification silhouette it read as a plus
sign. `Mark.mirrors_itself` is that lesson, and the rejected construction is kept for comparison
in design/brand/candidates/crossed_*.xml. Two corners passes it by being symmetric the other way:
turned 180 degrees about the centre it is itself, flipped top to bottom it is not.

Run:  PYTHONIOENCODING=utf-8 python design/brand/marks.py     (prints the geometry table)
"""
import math

VIEWPORT = 108
CENTER = VIEWPORT / 2
VISIBLE = 72.0
# The circle a launcher was once assumed to cut. Reported, no longer enforced: see the docstring.
SAFE_RADIUS = 33.0
# The superellipse a launcher really cuts. 3.05 was measured off the Seeker; 3.0 is the tighter
# shape, so a mark that clears this clears the phone.
MASK_EXPONENT = 3.0
# The smallest dimension any rectangle may have, in viewport units. 6 units is 4dp at 48dp.
MIN_STROKE = 6.0
# A 24 notification icon keeps 2 of padding on every side; the mark fills the 20 live box.
STAT_VIEWPORT = 24
STAT_LIVE = 20.0
STAT_MIN_STROKE = 2.0

# Amber's own dark-set ground and primary text (Tokens.kt AmberPrimitive.groundDark /
# text1Dark, read through the public AmberDarkColors.surfaceGround / textPrimary), not
# Instrument's retired Canvas (#0B0F14) and Ink (#E8ECF1): DESIGN.md section 9 found the mark's
# arrangement survived Amber but its colour pair was reasoned entirely inside Instrument's old,
# flat section 2 and never revisited, which left a cool tile and a cool-to-warm splash flash
# once Amber's own theme went live. Same roles (a near-black mark, a near-white tile/splash
# ink), Amber's own values. ACCENT stays Instrument's for now: it is used only by GAUGE/OFFSET/
# DATUM, the three rejected 2026-09-13 candidates kept here for the record, never written to a
# file (glyph.py only ever writes CHOSEN).
AMBER_GROUND = "#16130D"
AMBER_INK = "#F5EEDD"
# The launcher tile as of the 2026-09-24 refit (Tokens.kt AmberPrimitive.fillDark, read through
# the public AmberDarkColors.actionFill): the one warm-yellow tile in the drawer, replacing
# AMBER_INK in the CHOSEN mark's own `ground` role. Contrast against AMBER_GROUND is 11.5:1 by
# the same WCAG formula AmberContrastTest uses, which is that test's own pinned
# actionText-over-surfaceGround figure for this exact pair (EDGE_CONTRAST, below).
AMBER_ACTION = "#FFC247"
ACCENT = "#5AA9E6"
WHITE = "#FFFFFF"


def number(value, decimals=3):
    rounded = round(value, decimals)
    return str(int(rounded)) if rounded == int(rounded) else repr(rounded)


class Rect:
    """An axis-aligned rectangle in viewport units. Radius 0: the shape lock lives in the art."""

    def __init__(self, name, x0, y0, x1, y1, color):
        self.name = name
        self.x0, self.y0, self.x1, self.y1 = x0, y0, x1, y1
        self.color = color

    @property
    def width(self):
        return self.x1 - self.x0

    @property
    def height(self):
        return self.y1 - self.y0

    def corners(self):
        return [(x, y) for x in (self.x0, self.x1) for y in (self.y0, self.y1)]

    def scaled(self, factor, center, to_center):
        """The same rectangle in another viewport: scaled about `center`, re-centered on `to_center`."""
        return Rect(
            self.name,
            to_center + (self.x0 - center) * factor,
            to_center + (self.y0 - center) * factor,
            to_center + (self.x1 - center) * factor,
            to_center + (self.y1 - center) * factor,
            self.color,
        )

    def path(self):
        """Absolute commands only, so BrandAssetsTest can walk the coordinates."""
        return "M{} {}H{}V{}H{}Z".format(
            number(self.x0), number(self.y0), number(self.x1), number(self.y1), number(self.x0)
        )


class Mark:
    def __init__(self, key, title, idea, rects, ground=AMBER_GROUND):
        self.key = key
        self.title = title
        self.idea = idea
        self.rects = rects
        self.ground = ground        # the adaptive icon's background layer, written by glyph.py
        self.check()

    # -- geometry -------------------------------------------------------------------------------

    def bounds(self):
        return (
            min(r.x0 for r in self.rects),
            min(r.y0 for r in self.rects),
            max(r.x1 for r in self.rects),
            max(r.y1 for r in self.rects),
        )

    def radius(self):
        return max(math.hypot(x - CENTER, y - CENTER) for r in self.rects for (x, y) in r.corners())

    @staticmethod
    def inside_mask(x, y, exponent=MASK_EXPONENT):
        """Inside the superellipse the launcher cuts out of the visible 72."""
        ax = abs(x - CENTER) / (VISIBLE / 2)
        ay = abs(y - CENTER) / (VISIBLE / 2)
        return ax ** exponent + ay ** exponent <= 1.0 + 1e-9

    def mask_clearance(self, exponent=MASK_EXPONENT):
        """How far the tightest corner still is from that mask, in units. Positive is inside."""
        half = VISIBLE / 2
        worst = None
        for r in self.rects:
            for x, y in r.corners():
                ax, ay = abs(x - CENTER), abs(y - CENTER)
                here = math.hypot(ax, ay)
                if here < 1e-9:
                    continue
                ux, uy = ax / here, ay / here
                edge = 1.0 / ((ux / half) ** exponent + (uy / half) ** exponent) ** (1.0 / exponent)
                room = edge - here
                worst = room if worst is None else min(worst, room)
        return worst

    def check(self):
        for r in self.rects:
            thin = min(r.width, r.height)
            assert thin >= MIN_STROKE, "{}/{} is {} units, under the {} floor".format(self.key, r.name, thin, MIN_STROKE)
            for x, y in r.corners():
                assert self.inside_mask(x, y), (
                    "{}/{} corner ({}, {}) falls outside the superellipse of exponent {} that a "
                    "launcher cuts out of the visible 72".format(
                        self.key, r.name, number(x), number(y), number(MASK_EXPONENT))
                )
        x0, y0, x1, y1 = self.bounds()
        assert abs((x0 + x1) / 2 - CENTER) < 0.01, "{} is not centered across".format(self.key)
        assert abs((y0 + y1) / 2 - CENTER) < 0.01, "{} is not centered down".format(self.key)
        assert sum(1 for r in self.rects if r.color == ACCENT) <= 1, (
            "{}: DESIGN.md section 2 allows at most one accent shape".format(self.key)
        )
        assert not self.mirrors_itself(), (
            "{} is mirror-symmetric about its own horizontal middle, so in one color it collapses "
            "into a glyph (a plus, an equals, an H) instead of reading as itself".format(self.key)
        )

    def mirrors_itself(self):
        """
        True when the silhouette flipped top to bottom is the same silhouette.

        This is the rule the first gauge broke. Its track, reference tick and token tick were each
        centered on y=54, so the three of them together were a perfect cross: in color the Accent
        tick pulled away and the mark read as a gauge, but the monochrome layer and the 24
        notification silhouette have no color to pull with, and both read as a plus sign. Colour
        can separate two shapes; a silhouette can only be separated by where the shapes point.
        """
        here = sorted((r.x0, r.y0, r.x1, r.y1) for r in self.rects)
        flipped = sorted((r.x0, 2 * CENTER - r.y1, r.x1, 2 * CENTER - r.y0) for r in self.rects)
        return all(abs(a - b) < 1e-9 for row_a, row_b in zip(here, flipped) for a, b in zip(row_a, row_b))

    def stat_rects(self):
        """The same mark in the 24 notification viewport, fitted to the 20 live box."""
        x0, y0, x1, y1 = self.bounds()
        factor = STAT_LIVE / max(x1 - x0, y1 - y0)
        rects = [r.scaled(factor, CENTER, STAT_VIEWPORT / 2) for r in self.rects]
        for r in rects:
            edges = (r.x0, r.y0, r.x1, r.y1)
            assert all(2.0 - 1e-9 <= v <= 22.0 + 1e-9 for v in edges), \
                "{} leaves the 20 live area: {}".format(self.key, r.name)
            thin = min(r.width, r.height)
            assert thin >= STAT_MIN_STROKE, \
                "{}/{} is {:.2f} at 24, under {}".format(self.key, r.name, thin, STAT_MIN_STROKE)
        return rects

    # -- drawables ------------------------------------------------------------------------------

    def foreground(self):
        return vector(VIEWPORT, self.rects, None, self.header("foreground"))

    def monochrome(self):
        return vector(VIEWPORT, self.rects, WHITE, self.header("monochrome"))

    def stat(self):
        return vector(STAT_VIEWPORT, self.stat_rects(), WHITE, self.header("stat"))

    def tight_rects(self):
        """
        The same rectangles moved to the origin of a viewport exactly as big as the block they
        fill, for the TopBar lockup (2026-09-24). The launcher's 108 grid keeps the mark inside the
        central 72 and then inside a mask, so on that grid the glyph fills only 46 of 108 units:
        drawn at a text size, a 108-grid drawable renders the mark at about 43 percent of its own
        box, the lesson the web's TopNav learned first. Cropped here, the glyph is the box.
        """
        x0, y0, x1, y1 = self.bounds()
        assert (x1 - x0) == (y1 - y0), "{}: the block is not square, so no one size fits it".format(self.key)
        return [Rect(r.name, r.x0 - x0, r.y0 - y0, r.x1 - x0, r.y1 - y0, r.color) for r in self.rects]

    def tight(self):
        """The mark cropped to its own block, in white; the TopBar tints it with actionText."""
        x0, _, x1, _ = self.bounds()
        return vector(x1 - x0, self.tight_rects(), WHITE, self.header("tight"))

    def header(self, layer):
        x0, y0, x1, y1 = self.bounds()
        radius = self.radius()
        where = "\n".join([
            "  Block {} to {} across, {} to {} down.".format(
                number(x0), number(x1), number(y0), number(y1)),
            "  Furthest corner {:.2f} units from the center, {:+.2f} units inside the".format(
                radius, self.mask_clearance()),
            "  superellipse of exponent {} a launcher really cuts.".format(number(MASK_EXPONENT)),
        ] + ([
            "  That is past the {} the circle-mask guarantee once asked for: a launcher".format(
                number(SAFE_RADIUS)),
            "  cutting a true circle would bevel this mark rather than frame it.",
        ] if radius > SAFE_RADIUS else [
            "  It is also inside the {} the circle-mask guarantee asks for.".format(
                number(SAFE_RADIUS)),
        ]))
        if layer == "foreground":
            return (
                '  Brand mark, DESIGN.md section 9: "{}",\n'
                "  {}\n"
                "  Drawn over a {} background layer; glyph.py writes the chosen mark's own ground\n"
                "  into res/values/ic_launcher_background.xml, so the tile cannot drift from it.\n".format(
                    self.title, self.idea, self.ground)
                + where
            )
        if layer == "monochrome":
            return (
                "  Monochrome layer of the adaptive icon (themed icons, Android 13 and later): the same\n"
                "  rectangles as the foreground in one color, the launcher supplies the color. The\n"
                "  layer has no ground of its own, so the mark is the shapes and never the field.\n" + where
            )
        if layer == "tight":
            return (
                "  TopBar lockup: the mark cropped to its own block, so the glyph fills the box. White\n"
                "  on transparent; the TopBar tints it with the actionText token, so it follows dark and\n"
                "  light, and sizes it to the wordmark's cap height.\n" + where
            )
        return (
            "  Notification small icon: the mark in white on transparent, the system tints it.\n"
            "  The same rectangles fitted to the 20 live area of the 24 viewport."
        )


def background_resource(mark):
    """
    res/values/ic_launcher_background.xml, written from the mark rather than kept beside it.

    Four rejected attempts all put a Canvas tile (Instrument's own near-black, now retired) on a
    near-black drawer wallpaper, where it read 1.03 to 1 across its own edge and was not a tile at
    all. The ground is part of the mark now, so it is generated from the mark, and an icon cannot
    be redrawn without the background following.
    """
    alias = {
        AMBER_GROUND: "amber_ground", AMBER_INK: "amber_ink", AMBER_ACTION: "amber_action",
    }.get(mark.ground)
    assert alias, (
        "{}: the background layer is {}, which is not one of Amber's own tokens, so "
        "res/values/colors.xml has no name for it and BrandAssetsTest cannot pin it to the Kotlin "
        "palette".format(mark.key, mark.ground)
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!--\n"
        "  The adaptive icon's background layer, DESIGN.md section 9.\n"
        "  {} on the {} token. WCAG contrast {} (relative-luminance formula, matching\n"
        "  AmberContrastTest's own pinned actionText-over-surfaceGround figure for this exact\n"
        "  pair); not a fresh Seeker-drawer photo, which this task had no device to retake. The\n"
        "  amber_ink tile this replaced (2026-09-15 through 2026-09-22, ground on ink) measured\n"
        "  16.0 to 1 the same way; the Instrument pair before that (Canvas on an Ink tile) measured\n"
        "  15.48 to 1 offline in the real drawer, and the Canvas-only ground four attempts before\n"
        "  it shipped measured 1.03, which was no boundary at all.\n"
        "  Generated by design/brand/glyph.py, do not edit by hand.\n"
        "-->\n"
        "<resources>\n"
        '    <color name="ic_launcher_background">@color/{}</color>\n'
        "</resources>\n".format(mark.title, alias, EDGE_CONTRAST, alias)
    )


# WCAG contrast of AMBER_GROUND over AMBER_ACTION, computed the same way AmberContrastTest and
# contrast.py do (see design/brand/glyph.py's own note): equal to AmberDarkColors.actionText
# over AmberDarkColors.surfaceGround (the same pair, read either direction), which
# AmberContrastTest pins at 11.5:1 independently.
EDGE_CONTRAST = "11.5 to 1"


def vector(size, rects, override_color, comment):
    paths = "".join(
        "    <path\n"
        '        android:fillColor="{}"\n'
        '        android:pathData="{}" />\n'.format(override_color or r.color, r.path())
        for r in rects
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!--\n{}\n  Generated by design/brand/glyph.py, do not edit by hand.\n-->\n".format(comment)
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="{0}dp"\n'
        '    android:height="{0}dp"\n'
        '    android:viewportWidth="{0}"\n'
        '    android:viewportHeight="{0}">\n'.format(number(size))
        + paths
        + "</vector>\n"
    )


# -- the three candidates, drawn 2026-09-13 -----------------------------------------------------

GAUGE = Mark(
    "gauge",
    "Gauge",
    "the tracking gauge at icon weight: the scale, the reference graduation under it, the token over it.",
    [
        Rect("track", 26, 54, 82, 62, AMBER_INK),
        Rect("reference", 50, 62, 58, 76, AMBER_INK),
        Rect("token", 64, 32, 74, 68, ACCENT),
    ],
)

OFFSET = Mark(
    "offset",
    "Offset",
    "two rules of equal length, the NYSE close and the token, and the premium the token runs past it.",
    [
        Rect("close", 30, 39, 66, 51, AMBER_INK),
        Rect("token", 30, 57, 66, 69, AMBER_INK),
        Rect("premium", 66, 57, 78, 69, ACCENT),
    ],
)

DATUM = Mark(
    "datum",
    "Datum",
    "the quiet one: one reference rule and one reading held off it, nothing else.",
    [
        Rect("rule", 32, 62, 76, 70, AMBER_INK),
        Rect("reading", 60, 38, 76, 50, ACCENT),
    ],
)

# -- the mark the founder chose, 2026-09-15 -----------------------------------------------------
#
# "Two corners", cell 4a of the founder's own selection gallery, transcribed rather than derived:
# two registration corners on the 108 viewport, top-left and bottom-right, and an empty centre
# between them. Nothing about that arrangement may move (see "the refit changes reach, not the
# arrangement" below): which two rectangles, on which two opposite diagonals, around which empty
# centre, is still exactly the founder's own pick.
#
# The ground is the part this round decided, because the arrangement was the founder's and the
# ground is what four rejected attempts got wrong. Four treatments of these same rectangles were
# composited into the real Seeker drawer and measured across the tile boundary: Canvas 1.04 to 1,
# an Accent field 7.23, a cool off-white field 16.79, and this one, an Ink field with the corners
# in Canvas, 15.48. The off-white reads a point and a third higher and costs two colours that are
# not in DESIGN.md section 2; this one is the Ink-and-Canvas pair section 2 already rates at 15.9
# to 1, with the tile taking the Ink side, so BrandAssetsTest can pin the whole icon to the Kotlin
# tokens. design/brand/two-corners/gallery.html carries all four with the numbers under them.
#
# 2026-09-22: the arrangement above still stands (nothing about the four rectangles changed), but
# the colour pair it argued for was Canvas-and-Ink, Instrument's own flat section 2, decided over a
# week before the founder chose Amber and never revisited once Amber's own section 2 shipped
# (DESIGN.md section 9). That old pair is also, as of Amber, a colour nobody else in the app reads:
# Amber's real grounds are warm (#16130D dark, #FFFBF2 light) against Instrument's cool near-black
# and near-white, which is why the launcher tile stopped matching the app and the splash flashed
# cool-to-warm into Amber's light ground. AMBER_GROUND and AMBER_INK above replace CANVAS and INK
# with Amber's own dark-set values (Tokens.kt AmberPrimitive.groundDark / text1Dark) in the same
# two roles the old pair played: the mark stays AMBER_GROUND on an AMBER_INK tile, still the
# darker-figure-on-lighter-tile relationship the drawer measurement above picked, just carried in
# Amber's own hex now rather than Instrument's. The off-white candidate's objection ("costs two
# colours that are not in DESIGN.md section 2") no longer weighs against a warm option either,
# since Amber's section 2 already has a warm surface pair, but re-running the drawer composite is
# a founder decision against a concrete alternative, not something this fix reaches for; see
# DESIGN.md section 9, "What this is not."
#
# 2026-09-24: the refit changes reach, not the arrangement (DESIGN.md section 9, "Two corners,
# refit"; direction A of a three-direction audit, the founder's pick). Every rectangle below is
# still an "across" arm and a "down" arm meeting at one vertex per corner, top-left and
# bottom-right, on opposite diagonals, around an empty centre; what moved is how far out that
# vertex sits and what tile it sits on. The block pulled in from 26-to-82 to 31-to-77 and the
# 14-unit arm thinned to 12, which pulls the furthest corner from 39.60 units out to 32.53: inside
# the superellipse MASK_EXPONENT already cleared, and now also inside the plain 36-unit circle
# (VISIBLE / 2) a "Pixel-style" launcher cuts and the deprecated 33-unit SAFE_RADIUS this file used
# to report missing by nothing at all. `ground` moved off AMBER_INK onto AMBER_ACTION (#FFC247):
# the tile is Amber's own accent now, not its cream, so the app icon is the one warm-yellow tile in
# the drawer instead of one more white one holding Amber's colours without any amber in it. The
# corners stay AMBER_GROUND, the same near-black fill they always carried.

TWO_CORNERS = Mark(
    "two-corners",
    "Two corners",
    "two registration corners and the empty centre between them, which is the place the app keeps "
    "around a figure it has not printed.",
    [
        Rect("top-left arm, across", 31, 31, 59, 43, AMBER_GROUND),
        Rect("top-left arm, down", 31, 31, 43, 59, AMBER_GROUND),
        Rect("bottom-right arm, across", 49, 65, 77, 77, AMBER_GROUND),
        Rect("bottom-right arm, down", 65, 49, 77, 77, AMBER_GROUND),
    ],
    ground=AMBER_ACTION,
)

MARKS = {m.key: m for m in (TWO_CORNERS, GAUGE, OFFSET, DATUM)}
CHOSEN = "two-corners"


def main():
    for mark in MARKS.values():
        x0, y0, x1, y1 = mark.bounds()
        print("{:12} on {}, bounds x {} to {}, y {} to {}, corner radius {:.2f}, "
              "mask clearance {:+.2f}".format(
                  mark.key, mark.ground, x0, x1, y0, y1, mark.radius(), mark.mask_clearance()))
        for r in mark.rects:
            print("         {:10} x {} to {}, y {} to {}, {} x {} units, {:.2f} x {:.2f} dp at 48dp".format(
                r.name, r.x0, r.x1, r.y0, r.y1, r.width, r.height, r.width * 48 / 72, r.height * 48 / 72))
        for r in mark.stat_rects():
            print("    24dp {:10} x {:.2f} to {:.2f}, y {:.2f} to {:.2f}, {:.2f} x {:.2f}".format(
                r.name, r.x0, r.x1, r.y0, r.y1, r.width, r.height))


if __name__ == "__main__":
    main()

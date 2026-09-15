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

CANVAS = "#0B0F14"
INK = "#E8ECF1"
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
    def __init__(self, key, title, idea, rects, ground=CANVAS):
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

    def splash(self):
        """
        The mark in Ink on transparent, for the splash screen.

        The splash paints Canvas and then this vector over it, so it cannot be the foreground
        layer: the foreground is drawn for whatever ground the adaptive icon carries, and when that
        ground is light the foreground is a near-black mark that would be invisible on Canvas. Same
        rectangles, Ink, and nothing else.
        """
        return vector(VIEWPORT, self.rects, INK, self.header("splash"))

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
        if layer == "splash":
            return (
                "  Splash screen icon: the mark in Ink on transparent, over the Canvas window background.\n"
                "  The adaptive icon's own foreground is drawn for the launcher tile's ground, which is\n"
                "  not Canvas, so the splash needs the mark in the color the splash background can show.\n"
                + where
            )
        return (
            "  Notification small icon: the mark in white on transparent, the system tints it.\n"
            "  The same rectangles fitted to the 20 live area of the 24 viewport."
        )


def background_resource(mark):
    """
    res/values/ic_launcher_background.xml, written from the mark rather than kept beside it.

    Four rejected attempts all put a Canvas tile on a near-black drawer wallpaper, where it reads
    1.03 to 1 across its own edge and is not a tile at all. The ground is part of the mark now, so
    it is generated from the mark, and an icon cannot be redrawn without the background following.
    """
    alias = {CANVAS: "canvas", INK: "ink"}.get(mark.ground)
    assert alias, (
        "{}: the background layer is {}, which is not one of DESIGN.md section 2's tokens, so "
        "res/values/colors.xml has no name for it and BrandAssetsTest cannot pin it to the Kotlin "
        "palette".format(mark.key, mark.ground)
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!--\n"
        "  The adaptive icon's background layer, DESIGN.md section 9.\n"
        "  {} on the {} token. Measured at {} across the tile boundary in the real Seeker drawer;\n"
        "  the Canvas ground four attempts shipped measured 1.03, which is no boundary at all.\n"
        "  Generated by design/brand/glyph.py, do not edit by hand.\n"
        "-->\n"
        "<resources>\n"
        '    <color name="ic_launcher_background">@color/{}</color>\n'
        "</resources>\n".format(mark.title, alias, EDGE_CONTRAST, alias)
    )


# The edge this ground measures in the real drawer, from design/brand/two-corners/edge-contrast.json.
EDGE_CONTRAST = "15.48 to 1"


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
        Rect("track", 26, 54, 82, 62, INK),
        Rect("reference", 50, 62, 58, 76, INK),
        Rect("token", 64, 32, 74, 68, ACCENT),
    ],
)

OFFSET = Mark(
    "offset",
    "Offset",
    "two rules of equal length, the NYSE close and the token, and the premium the token runs past it.",
    [
        Rect("close", 30, 39, 66, 51, INK),
        Rect("token", 30, 57, 66, 69, INK),
        Rect("premium", 66, 57, 78, 69, ACCENT),
    ],
)

DATUM = Mark(
    "datum",
    "Datum",
    "the quiet one: one reference rule and one reading held off it, nothing else.",
    [
        Rect("rule", 32, 62, 76, 70, INK),
        Rect("reading", 60, 38, 76, 50, ACCENT),
    ],
)

# -- the mark the founder chose, 2026-09-15 -----------------------------------------------------
#
# "Two corners", cell 4a of the founder's own selection gallery, transcribed rather than derived:
# two registration corners on the 108 viewport, top-left and bottom-right, and an empty centre
# between them. Nothing may move these four rectangles.
#
# The ground is the part this round decided, because the arrangement was the founder's and the
# ground is what four rejected attempts got wrong. Four treatments of these same rectangles were
# composited into the real Seeker drawer and measured across the tile boundary: Canvas 1.04 to 1,
# an Accent field 7.23, a cool off-white field 16.79, and this one, an Ink field with the corners
# in Canvas, 15.48. The off-white reads a point and a third higher and costs two colours that are
# not in DESIGN.md section 2; this one is the Ink-and-Canvas pair section 2 already rates at 15.9
# to 1, with the tile taking the Ink side, so BrandAssetsTest can pin the whole icon to the Kotlin
# tokens. design/brand/two-corners/gallery.html carries all four with the numbers under them.

TWO_CORNERS = Mark(
    "two-corners",
    "Two corners",
    "two registration corners and the empty centre between them, which is the place the app keeps "
    "around a figure it has not printed.",
    [
        Rect("top-left arm, across", 26, 26, 60, 40, CANVAS),
        Rect("top-left arm, down", 26, 26, 40, 60, CANVAS),
        Rect("bottom-right arm, across", 48, 68, 82, 82, CANVAS),
        Rect("bottom-right arm, down", 68, 48, 82, 82, CANVAS),
    ],
    ground=INK,
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

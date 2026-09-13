"""
The brand mark, as geometry (DESIGN.md section 9).

The launcher icon is not a picture, it is two or three axis-aligned rectangles, so the mark lives
here as numbers and every drawable is written from them. Three candidates were drawn for the
2026-09-13 redraw; `CHOSEN` names the one that ships and design/brand/glyph.py writes it into
res/drawable.

Coordinate system: the 108 adaptive-icon viewport. Center (54, 54). The launcher mask can be a
circle, a squircle or a rounded square and only the central 66 circle (radius 33 from the center)
is guaranteed visible, so every corner of every rectangle is asserted inside that circle.

Why rectangles with this much mass: an icon is read at 48dp. The visible part of the 108 viewport
is its central 72, so one viewport unit is 48/72 = 0.667dp on a launcher grid. The tick that was
2 units tall in the icon this replaces measured 1.3dp there and vanished. Nothing below
MIN_STROKE units is drawn, which is 4dp at 48dp.

Run:  PYTHONIOENCODING=utf-8 python design/brand/marks.py     (prints the geometry table)
"""
import math

VIEWPORT = 108
CENTER = VIEWPORT / 2
SAFE_RADIUS = 33.0
# The smallest dimension any rectangle may have, in viewport units. 6 units is 4dp at 48dp.
MIN_STROKE = 6.0
# A 24 notification icon keeps 2 of padding on every side; the mark fills the 20 live box.
STAT_VIEWPORT = 24
STAT_LIVE = 20.0
STAT_MIN_STROKE = 2.0

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
    def __init__(self, key, title, idea, rects):
        self.key = key
        self.title = title
        self.idea = idea
        self.rects = rects
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

    def check(self):
        radius = self.radius()
        assert radius <= SAFE_RADIUS, "{} leaves the 66 safe zone: corner radius {:.2f}".format(self.key, radius)
        for r in self.rects:
            thin = min(r.width, r.height)
            assert thin >= MIN_STROKE, "{}/{} is {} units, under the {} floor".format(self.key, r.name, thin, MIN_STROKE)
        x0, y0, x1, y1 = self.bounds()
        assert abs((x0 + x1) / 2 - CENTER) < 0.01, "{} is not centered across".format(self.key)
        assert abs((y0 + y1) / 2 - CENTER) < 0.01, "{} is not centered down".format(self.key)
        assert sum(1 for r in self.rects if r.color == ACCENT) == 1, "{}: exactly one accent shape".format(self.key)

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

    def header(self, layer):
        x0, y0, x1, y1 = self.bounds()
        where = "  Block {} to {} across, {} to {} down, corner radius {:.2f} of the 33 safe zone.".format(
            number(x0), number(x1), number(y0), number(y1), self.radius()
        )
        if layer == "foreground":
            return (
                '  Brand mark, DESIGN.md section 9: "{}", {}\n'
                "  Ink shapes and one Accent shape over the Canvas background layer.\n".format(self.title, self.idea)
                + where
            )
        if layer == "monochrome":
            return (
                "  Monochrome layer of the adaptive icon (themed icons, Android 13 and later): the same\n"
                "  rectangles as the foreground in one color, the launcher supplies the color.\n" + where
            )
        return (
            "  Notification small icon: the mark in white on transparent, the system tints it.\n"
            "  The same rectangles fitted to the 20 live area of the 24 viewport."
        )


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
    "the tracking gauge at icon weight: the track, the reference at the center, the token off it.",
    [
        Rect("track", 26, 50, 82, 58, INK),
        Rect("reference", 50, 44, 58, 64, INK),
        Rect("token", 64, 32, 74, 76, ACCENT),
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

MARKS = {m.key: m for m in (GAUGE, OFFSET, DATUM)}
CHOSEN = "gauge"


def main():
    for mark in MARKS.values():
        x0, y0, x1, y1 = mark.bounds()
        print("{:8} bounds x {} to {}, y {} to {}, corner radius {:.2f}".format(
            mark.key, x0, x1, y0, y1, mark.radius()))
        for r in mark.rects:
            print("         {:10} x {} to {}, y {} to {}, {} x {} units, {:.2f} x {:.2f} dp at 48dp".format(
                r.name, r.x0, r.x1, r.y0, r.y1, r.width, r.height, r.width * 48 / 72, r.height * 48 / 72))
        for r in mark.stat_rects():
            print("    24dp {:10} x {:.2f} to {:.2f}, y {:.2f} to {:.2f}, {:.2f} x {:.2f}".format(
                r.name, r.x0, r.x1, r.y0, r.y1, r.width, r.height))


if __name__ == "__main__":
    main()

"""
Brand mark generator (DESIGN.md section 9).

Writes everything the launcher icon is made of, from the geometry in design/brand/marks.py:

  app/src/main/res/drawable/ic_launcher_foreground.xml   108 viewport, the mark over the ground
  app/src/main/res/drawable/ic_launcher_monochrome.xml   the same paths in one color, themed icons
  app/src/main/res/drawable/ic_brand_mark.xml            the same paths in Ink, for the splash
  app/src/main/res/drawable/ic_stat_plainticker.xml      24 viewport, white, notification small icon
  app/src/main/res/values/ic_launcher_background.xml     the adaptive icon's background layer

The mark is "Two corners", cell 4a of the founder's own selection gallery: two registration
corners on the 108 viewport, top-left and bottom-right, and an empty centre between them. It
replaces the tracking gauge, which replaced the JetBrains Mono P shipped in DT3.

2026-09-24: refit for Amber's tile (marks.py's own note on TWO_CORNERS, DESIGN.md section 9,
"Two corners, refit"). The arrangement is unchanged; the block pulled inside the plain 36-unit
circle a launcher actually cuts and the tile moved onto AMBER_ACTION, Amber's own accent.

The background layer is written here rather than kept beside the drawables, and that is the whole
point of this round. Four icon attempts were rejected and all four put a Canvas tile on a
near-black drawer wallpaper, where it measures 1.03 to 1 across its own edge and is not a tile at
all. The ground is part of the mark now: marks.py carries it, this file writes it out, and the
icon cannot be redrawn without the tile under it following.

marks.py holds the rectangles, the mask assertion, the stroke floor and the symmetry rule that
keeps the silhouette off a plus sign; this file only decides which mark ships (marks.CHOSEN) and
where the files go. design/brand/two-corners/measure.py puts the mark in the real drawer and
measures it; design/brand/render_icons.py draws every candidate at the sizes an icon is seen.

Only absolute path commands are emitted, so BrandAssetsTest can walk the coordinates.

Run from anywhere:  PYTHONIOENCODING=utf-8 python design/brand/glyph.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import marks  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DRAWABLE = ROOT / "app/src/main/res/drawable"
VALUES = ROOT / "app/src/main/res/values"


def main():
    mark = marks.MARKS[marks.CHOSEN]
    for folder, name, text in (
        (DRAWABLE, "ic_launcher_foreground.xml", mark.foreground()),
        (DRAWABLE, "ic_launcher_monochrome.xml", mark.monochrome()),
        (DRAWABLE, "ic_brand_mark.xml", mark.splash()),
        (DRAWABLE, "ic_stat_plainticker.xml", mark.stat()),
        (VALUES, "ic_launcher_background.xml", marks.background_resource(mark)),
    ):
        (folder / name).write_text(text, newline="\n")
        print("wrote", name)
    x0, y0, x1, y1 = mark.bounds()
    print('"{}" on a {} ground: x {} to {}, y {} to {}'.format(
        mark.title, mark.ground,
        marks.number(x0), marks.number(x1), marks.number(y0), marks.number(y1)))
    radius = mark.radius()
    print("  furthest corner {:.2f} units, {} the {} the circle guarantee once asked for and "
          "{:+.2f} inside the superellipse of exponent {} a launcher cuts".format(
              radius, "past" if radius > marks.SAFE_RADIUS else "inside",
              marks.number(marks.SAFE_RADIUS), mark.mask_clearance(), marks.number(marks.MASK_EXPONENT)))
    for rect in mark.rects:
        print("  {:26} {:g} x {:g} units, {:.2f} x {:.2f} dp at 48dp".format(
            rect.name, rect.width, rect.height, rect.width * 48 / 72, rect.height * 48 / 72))


if __name__ == "__main__":
    main()

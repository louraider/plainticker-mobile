"""
Brand mark generator (DESIGN.md section 9).

Writes the three vector drawables that carry the mark, from the geometry in design/brand/marks.py:

  app/src/main/res/drawable/ic_launcher_foreground.xml   108 viewport, Ink shapes, one Accent shape
  app/src/main/res/drawable/ic_launcher_monochrome.xml   the same paths in one color, themed icons
  app/src/main/res/drawable/ic_stat_plainticker.xml      24 viewport, white, notification small icon

The mark is the tracking gauge of DESIGN.md section 1, not a letter: a track, the reference tick
at the center where the NYSE close sits, and the token tick off it in Accent. It replaces the
JetBrains Mono P shipped in DT3, whose 2-unit accent tick measured 1.3dp at 48dp and was not
there. marks.py holds the rectangles, the safe-zone assertion and the stroke floor; this file only
decides which mark ships (marks.CHOSEN) and where the files go. design/brand/render_icons.py draws
every candidate at the sizes an icon is actually seen.

Only absolute path commands are emitted, so BrandAssetsTest can walk the coordinates.

Run from anywhere:  PYTHONIOENCODING=utf-8 python design/brand/glyph.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import marks  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
DRAWABLE = ROOT / "app/src/main/res/drawable"


def main():
    mark = marks.MARKS[marks.CHOSEN]
    for name, text in (
        ("ic_launcher_foreground.xml", mark.foreground()),
        ("ic_launcher_monochrome.xml", mark.monochrome()),
        ("ic_stat_plainticker.xml", mark.stat()),
    ):
        (DRAWABLE / name).write_text(text, newline="\n")
        print("wrote", name)
    x0, y0, x1, y1 = mark.bounds()
    print('"{}": x {} to {}, y {} to {}, corner radius {:.2f} of the 33 safe zone'.format(
        mark.title, marks.number(x0), marks.number(x1), marks.number(y0), marks.number(y1), mark.radius()))
    for rect in mark.rects:
        print("  {:10} {:g} x {:g} units, {:.2f} x {:.2f} dp at 48dp".format(
            rect.name, rect.width, rect.height, rect.width * 48 / 72, rect.height * 48 / 72))


if __name__ == "__main__":
    main()

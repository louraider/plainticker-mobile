# Brief: PlainTicker launcher icon, attempt five. Develop your own two, do not restart.

## Before anything else: do not run any commands

**Do not run any commands. Do not call RunCommand, do not call a shell, do not call a terminal, do
not try to execute python, git, ls, cat, find or grep. Do not try to write, create or edit any
file.** You are running headless, in plan mode, in a sandbox. A command permission request is
auto-denied there and the entire run returns empty output, which has already happened three times
on this project. Everything you need is written out below in full. Answer in prose, in your reply,
and nothing else.

## What this is

You wrote seven concepts for this icon on 2026-09-13. The founder looked at all seven in the real
drawer and kept two of yours: number 2, the live bar anchor, and number 6, the disconnected quote.
His instruction was to work with those two further. So this is not a new brief and you are not
being asked for new ideas. **Six variants of each of your two concepts have been drawn against
three named problems. You are being asked to judge the drawings and say what to change.**

Do not propose a new concept. Do not restart. If you think a family cannot be saved, say that
plainly and say which variant comes closest, but do not answer with a different idea.

## The measured facts about the target. Use them, do not re-derive them.

- The Seeker drawer tile is **182 device pixels, which is 60.7dp at 480dpi**, not the 48dp an icon
  brief assumes. One 108-viewport unit is **2.53 device pixels and 0.843dp**.
- The launcher mask was lifted off the Photos tile in a real drawer screenshot and fits a
  **superellipse of exponent 3.05**, not 4.
- The adaptive icon canvas is 108 units; a launcher shows only the central 72; only the central
  66 circle (radius 33 from centre) is guaranteed under every mask.
- Floors: no positive shape thinner than **6 units** (4dp at 48dp), no gap smaller than 6 units.
- The monochrome themed-icon layer is mandatory and has no colour of its own. Accent and Ink become
  the same colour there.

## The three problems this round exists to solve

**One, the ground, and it is the biggest.** Canvas is `#0B0F14` and this phone's drawer wallpaper is
within a few units of it. Six of your seven concepts asked for a Canvas ground and the brief had
already told you that a Canvas tile has no edge. This has now been measured rather than argued. For
every drawing below, the finished drawer image was sampled in a three-pixel ring just inside the
launcher's own measured mask and a three-pixel ring just outside it, and the WCAG contrast ratio
between the two means was computed. The readings:

```
  the drawer as it is, PlainTicker's slot today      1.03 to 1
  Photos, the brightest tile in the same row        18.06 to 1

  live-bar-as-proposed              ground           1.01 to 1
  live-bar-bleed-ground             ground           1.62 to 1
  live-bar-true-weight-ground       ground           1.39 to 1
  live-bar-bleed-inverted           inverted         6.61 to 1
  live-bar-true-weight-inverted     inverted         6.83 to 1
  live-bar-one-reading-inverted     inverted         6.61 to 1
  quote-as-proposed                 ground           1.03 to 1
  quote-square-pool-ground          ground           1.03 to 1
  quote-round-pool-ground           ground           1.03 to 1
  quote-square-pool-inverted        inverted         7.27 to 1
  quote-small-pool-inverted         inverted         7.27 to 1
  quote-heavy-bracket-inverted      inverted         7.27 to 1
```

A reading near 1 means the tile's boundary cannot be seen at all: what a person gets is a mark
floating on the wallpaper with no object under it. Both of your kept concepts are near 1 as
proposed. Every family below therefore carries inverted variants, where the tile is a solid Accent
field `#5AA9E6` and the mark is knocked out of it in Canvas.

**Two, your concept 2's misread and its scale.** You named the nearest system glyph yourself: a
hamburger menu. Separately, your 12-unit bar is 10.1dp on this phone's real tile, where the app's
own live bar is a 2dp rule. That is a scale lie about the app's own signature element.

**Three, your concept 6's dot.** Eight units is 6.7dp on the real tile, the smallest element in the
whole of attempt four. It is also a circle, which breaks the design system's radius 0 on
everything, and the system bans coloured dots as decoration. Your defence on record is that the dot
is not decoration, it is the $80. That defence is under test, not assumed.

## Family one, the live bar anchor. Six drawings, exact geometry.

Coordinates are in the 108 viewport, `x y width height`, origin top left, centre (54, 54).
"bleeds" means the shape runs past the viewport and the launcher's mask cuts it.

1. **live-bar-as-proposed**, ground, Canvas field. Your drawing from attempt four, unchanged.
   Accent bar `26 26 12 56`. Ink dashes `46 38 38 10` and `46 60 22 10`.

2. **live-bar-bleed-ground**, ground, Canvas field. Accent bar `32 0 12 108`, bleeds top and
   bottom. Ink dashes `52 44 32 10` and `52 66 18 10`.

3. **live-bar-bleed-inverted**, inverted, Accent field. The same geometry as 2, knocked out in
   Canvas: bar `32 0 12 108` bleeds, dashes `52 44 32 10` and `52 66 18 10`.

4. **live-bar-true-weight-ground**, ground, Canvas field. Accent bar `34 0 8 108`, bleeds. Ink
   dashes `50 44 34 8` and `50 66 20 8`.

5. **live-bar-true-weight-inverted**, inverted, Accent field, the same geometry as 4 knocked out.

6. **live-bar-one-reading-inverted**, inverted, Accent field. Bar `32 0 12 108` bleeds, one dash
   `52 58 32 12`. Two readings become one.

What the drawings were trying to do to your hamburger. A hamburger menu is inset from every edge of
its box and spreads equal rows evenly over the full height, and it has no vertical member. So the
bar was moved off centre-left and made to bleed past both edges so the mask cuts it, which no
system glyph ever does, and both readings were pushed below the middle so the upper third of the
tile is bar and nothing else. On the scale lie: 2dp of a 48dp tile is 4.5 units, which is under the
6-unit floor, so the absolute width of the app's live bar cannot be kept at any size. Drawings 4
and 5 keep its **proportion** instead, 8 units across the 72 a launcher shows, which is the 1 to 9
the bar has beside its own label in the app. Drawings 2, 3 and 6 keep your 12.

One thing about the inverted variants of this family that needs your judgement. When the bar is
knocked out it becomes a dark slot in a lit field, so the element that means "live" is now the
absence. There is a narrow band of Accent field to the left of it, about 14 units, which reads
either as the gutter beside a ruled margin or as an accident, and nobody here can tell which.

## Family two, the disconnected quote. Six drawings, exact geometry.

The bracket is a monospace right bracket: a spine at the right and two arms running left from it.

7. **quote-as-proposed**, ground, Canvas field. Your drawing from attempt four with one repair: the
   arms now overlap the spine instead of butting against it, because the original left a visible
   hairline seam at the join. Ink spine `70 30 12 48`, Ink arms `54 30 28 12` and `54 66 28 12`.
   Accent circle centre (28, 54) radius 4, so 8 units across.

8. **quote-square-pool-ground**, ground, Canvas field. The same bracket. The pool is now an Accent
   square `22 48 12 12`, 12 units, the width of the bracket's own stroke.

9. **quote-square-pool-inverted**, inverted, Accent field, drawing 8 knocked out in Canvas.

10. **quote-small-pool-inverted**, inverted, Accent field, the same bracket, pool held at your 8
    units and squared: `24 50 8 8`. Drawn so the size threshold can be looked at.

11. **quote-heavy-bracket-inverted**, inverted, Accent field. Spine `68 28 14 52`, arms
    `52 28 30 12` and `52 68 30 12`, pool `22 46 16 16`. The mass answer, drawn to be compared.

12. **quote-round-pool-ground**, ground, Canvas field. The same bracket, pool round again but at
    the square's size: centre (28, 54) radius 6. Drawn so circle against square is a comparison at
    equal size rather than an assertion.

The argument behind squaring the pool, which you should attack if it is wrong: a circle in this
design system is always decoration, because radius 0 is the shape lock on everything, so a round
dot reads as a bullet or a status light however it is captioned; a square the same width as the
bracket's own stroke reads as one unit of the thing on the other side of the tile, which is the
comparison the mark exists to make.

## What I want from you

Prose. No SVG, no coordinates, no code, no lists of new ideas.

1. **For each family, which one of the six would you ship, and what would you still change about
   it.** Name the variant by its key. If the change is geometric, describe it in words and in
   viewport units.

2. **Is the hamburger solved in family one?** The bleed and the low readings were a guess. Say
   whether they work, and if they do not, say whether the misread is soluble at all or whether the
   family should be dropped for that reason.

3. **Does your defence of the dot survive?** You wrote that the dot is not decoration, it is the
   $80. Test it rather than repeat it: at 60.7dp, on a phone, in half a second, beside Photos and
   the Play Store, does a person read a small isolated unit as a quantity, or as a speck? And say
   whether squaring it helps or whether it costs the family something you intended.

4. **The ground.** The numbers above say the inverted treatment is the only one that gives the tile
   an edge. Say whether you accept that, and if you do, say what inversion costs each family. For
   family one in particular: is a live bar drawn as a void still a live bar.

5. **Which family would you ship if you could only ship one**, and say what the ranking is on: what
   would stop a person's eye in a drawer for half a second, not what argues best on paper.

6. If any of the twelve drawings above has misread your original paragraph, say so and say how.

Write in the app's register: flat, specific, unexcited, sentence case, no adjectives doing a noun's
work. Banned words: seamless, powerful, unlock, empower, journey, insights, supercharge,
effortless, all-in-one, welcome to.

**Remember: do not run any commands. Reply in prose.**

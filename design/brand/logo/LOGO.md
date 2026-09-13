# PlainTicker, the logo

Three launcher icons were designed and all three were rejected, and on 2026-09-13 the founder named
the reason: there is no logo. An icon designed without one is a tile with a shape on it, judged
against nothing. This round designs the logo, and the launcher icon is cut out of it afterwards
rather than invented beside it.

Open `gallery.html` first. It is one self-contained file with every drawing, every measurement and
both critics quoted in full. This document is the reasoning.

## What ships

**The mark: the kept place.** Three registered places on one row. Two hold a figure. The third is
drawn, registered and left empty, and the accent is on the empty one.

**The wordmark: PlainTicker in Outfit SemiBold**, tracked -0.01em, outlined from
`app/src/main/res/font/outfit_semibold.ttf` so the wordmark on the web and the label in the app's
TopBar are the same letterforms rather than two near-misses.

**The lockup:** the mark's figure blocks stand at exactly the wordmark's cap height and its
registers hang below the wordmark's baseline, the way a descender does. That puts the mark 1.294
cap heights tall overall and it is a rule with a reason rather than a ratio chosen by eye. Clear
space is 0.42 of the Outfit em on every side, and nothing is ever set closer.

**The launcher icon:** the same drawing, on a cool off-white field, measuring **16.89 to 1** edge
contrast in the real Seeker drawer against the 1.03 the app scores there today.

Files: `svg/` holds the mark, the three wordmark settings and both lockups, each in full colour, one
colour and knocked out, plus a light colourway for a paper ground. `icon/` holds the 108-unit adaptive drawings and
their monochrome layers. `render/` holds everything rasterised where it is actually looked at.

## What the mark says, about this product and no other

The app reads tokenised US stocks on Solana and states what a token is before you swap into it. Its
one act that no neighbour on that phone performs is a refusal: below $10,000 of pool depth it will
not print a tracking figure at all, and says what the pool holds instead. Measured 2026-09-12: of
160 analysed tokens Jupiter reports a depth for only 53, UBERx quoted a 152.13 percent premium on a
pool holding $80, and a $100 order into UBERx costs 64.2 percent of itself in price impact.

The mark is that, drawn: a row where a place is kept and the figure is not printed. It is not a
missing value and it is not an error. The place is still registered, in the same row, on the same
pitch, and nothing has been closed up or filtered out, which is the difference between disclosure
and curation the product is built on. DESIGN.md section 1.1 states the rule; this is the picture of
it.

Codex, reviewing an earlier version, wrote the sentence that produced the final drawing: the mark
"does not visibly reserve a third place. There is no repeated container, divider or other structure
making that empty region a slot." The row was one continuous rule then. It is now three separate
registers on the same pitch as the places above them, and the third one is drawn and empty. That is
the whole mark.

## The nearest system glyph, and why it is not that glyph

A bar chart with a missing bar, and the battery. Naming the nearest system glyph is a required
field since attempt four, which discovered that attempt three's eight abstract marks had
accidentally drawn the split-screen button, a battery and the browser-extension jigsaw: a
composition of blank rectangles does not read as the meaning its author assigned it, it reads as
whichever glyph already owns that geometry.

Three things keep this one off those glyphs.

**Bars vary in height and these do not.** The two filled places are identical, 13 by 34 units each.
Identical heights say "places", not "values"; the moment one is taller than the other the drawing
becomes a chart and means something else.

**The third register is drawn.** A chart with a missing bar draws nothing where the bar is not. A
battery has one container, not three. Here the register is present, the same size as the other two,
and empty, which is a reserved slot and not an absence of data.

**The accent is on the empty place.** DESIGN.md section 2 lists what may carry `#5AA9E6` and a
blank is not on the list, so this is a declared departure rather than a smuggled one: a launcher
tile carries exactly one accent, the subject of the mark is the place that was not filled, so the
accent goes there. In the app the accent means interactive or live, and what is live about a
withheld figure is that the app is still reading and still refusing.

Two more things it is not. It does not mirror itself, and `marks3.reads_as_plus_or_tee` refuses any
construction that flattens into a plus or a T, which is how attempt two died in the monochrome
layer. And in one colour the third register is still drawn and still empty, so the meaning does not
depend on colour at all: `render/mark-kept-place-sizes-onecolour.png` is the proof.

## How it behaves at each size

Measured, not assumed. The Seeker drawer tile is 182 device pixels, 60.7dp at 480dpi, and its
launcher mask fits a superellipse of exponent 3.05. One viewport unit is 0.843dp there.

| size | what it is | what happens |
|---|---|---|
| 60.7dp | the real drawer tile | registers 25 device pixels tall, places 86. Nothing is near a floor. |
| 48dp | what an icon brief assumes | registers 20 device pixels. Reads as three registers, two filled. |
| 24dp | the notification icon | registers 10 device pixels, the 6-unit gaps 6. The rhythm survives. |
| 16dp | a favicon, and the smallest a logo is asked to be | registers 6.7 device pixels. The third place is still visibly a place. |
| headline | plainticker.com, a deck | the registers read as type: the mark sits in the line like a glyph. |
| inline | a row of running text | not used. The wordmark alone carries running text; the mark is for a lockup, an avatar, a favicon and the tile. |

The thinnest positive shape anywhere in the mark is 10 units, which is 8.4dp and 25 device pixels
on the real tile. The floor this repository has enforced since attempt one is 6 units, and it exists
because attempt one's 2-unit tick was 1.3dp and was simply not present on the phone.

The mark covers 27.1 percent of the visible 72 on a ground treatment. That number is in the file
because emptiness is the thing the founder responded to three times and it should be measured
rather than asserted.

## The ground, which is the only question a measurement has ever settled here

From attempt five, measured in the real drawer screenshot as the WCAG contrast ratio of a 3px ring
inside the launcher mask against 3px outside it:

| treatment | edge contrast |
|---|---|
| PlainTicker as it ships today | 1.03 to 1 |
| any Canvas `#0B0F14` ground | 1.01 to 1.03 |
| any inverted Accent `#5AA9E6` field | 7.25 to 7.27 |
| **a cool off-white `#F2F5F8` field** | **16.85 to 16.89** |
| Photos, the brightest tile in the row | 18.06 |

The fourth row is this round's own finding. Attempts three, four and five only ever asked one
ground question, Canvas or an Accent field, and every round took the Accent field and paid for it
by knocking the mark out. On a mark whose argument is that one part is Ink and one part is Accent,
knocking it out is not a treatment, it is an amputation: `render/kept-place-inverted-crop.png` shows
the accent disappearing into the field it is supposed to contrast with.

A paper field costs nothing the design system has not already conceded. DESIGN.md section 2 says a
light variant must keep the hierarchy rather than invert it and that the canvas becomes a cool
off-white, never cream, and that is exactly what this is. Codex's caveat is adopted rather than
argued with: on paper the mark does not keep Ink and Accent literally, the strokes become dark ink,
so what survives is the two-colour structure, and 16.89 buys a boundary, not distinctiveness.
Distinctiveness has to come from the drawing.

**This is not wired into the app yet.** `icon/kept-place-paper.svg` and its monochrome layer are the
drawings; shipping them means regenerating `res/drawable/ic_launcher_foreground.xml` through
`design/brand/glyph.py` and changing `@color/ic_launcher_background` from Canvas to `#F2F5F8`. That
is a separate change, after the founder has looked at the gallery, and it needs a build this machine
should not run.

## Does the "brand mark is the tracking gauge" claim survive?

No. `.agents/product-marketing.md`, Brand Voice, ends with: *"The brand mark is the tracking gauge
itself rather than a letter, because it is the one mark nobody else has and it says what the app
does."*

Both critics rejected it independently, having seen the gauge drawn inverted and given the edge the
measurement says it never had.

> Codex: "There is no reference, range or relationship that identifies tracking, much less tracking
> a token against its underlying stock. The blue field improves visibility without improving that
> explanation. 'Nobody else has' is an unsupported exclusivity claim, and 'says what the app does'
> assigns the drawing information it does not contain."

> Antigravity: "If the geometry is no longer recognizable as a gauge, the marketing copy is
> defending a ghost."

The deeper reason, which neither critic needed and which decides it anyway: **the gauge is drawn
only above the liquidity floor.** Below $10,000 of pool the app does not draw it at all. A brand
mark cannot be the one element the product withholds. It has to be the thing that is true of every
screen, and what is always true here is the withholding itself.

Both critics offered a replacement sentence. Antigravity's is the better one and is the one
proposed for `.agents/product-marketing.md`:

> The brand mark is the refusal itself: the deliberate empty space left behind when a pool is too
> thin to track.

That edit is not made in this branch. The file belongs to the marketing context and the sentence
should change when the mark ships, not before.

## The two critics

Run as attempt five ran them, with opposite jobs: Antigravity, which invented the earlier concepts,
was asked to develop and rank; Codex, which had argued against them, was asked to argue again and
was given the rendered sheets as images rather than geometry. Briefs as sent: `BRIEF-AGY.md` and
`BRIEF-CODEX.md`. Raw replies: `critique/antigravity.txt` and `critique/codex.txt`, both quoted in
full in `gallery.html`.

- Google Antigravity, gemini-3.1-pro-high, effort high, plan mode, sandbox, 2026-09-13. 48.1 s,
  23,401 tokens, one turn.
- Codex, codex-cli 0.154.0, non-interactive, read-only sandbox, three sheets attached as images.

Neither returned empty.

### Where they agree

Both killed the split wordmark. Both declared the tracking-gauge sentence dead. Neither disputed a
measurement.

### Where they disagree, and which I followed

**Which mark.** Antigravity ranked short-measure first and said it "genuinely answers Codex's
objection ... it explicitly proves that the foundation cannot justify the weight of a printed
figure." Codex, which wrote that objection, said it does not: "the product refuses to print an
unreliable figure, while this mark visibly prints a small figure. The empty area above it is
ordinary chart background, not an observable act of withholding."

I followed Codex, and not because it owns the sentence. Its reason is checkable against the product
rather than against a taste: below the floor the app prints nothing, not a small number, so a mark
that draws a short bar in the third place depicts a behaviour this app does not have. Short-measure
is out.

**The ground.** Antigravity said keep the Canvas near-black: "if the Canvas ground bleeds into a
dark drawer and loses the superellipse mask, that borderless darkness is a truer expression of the
product than a bright, safe paper wrapper." Codex said ship paper for visibility and stop treating a
contrast ratio as evidence of distinctiveness.

I followed Codex. Antigravity set an aesthetic preference against a measurement and its conclusion
is that the icon should have no boundary, which is the state three rounds of this have been trying
to escape: attempt three's compositor could not locate PlainTicker's own slot in the screenshot by
scanning, because there was nothing there to find.

**The wordmark.** Both killed it, so there is nothing to adjudicate, but the two reasons are not
equal. Antigravity called it "two fonts in a logo wearing a justification", which is a verdict.
Codex found the defect in the argument: "'Ticker' is a word, not a ticker symbol." The rule the
setting claimed to be applying is about ticker symbols and numerals. The second half of the name is
neither. The setting was a pun on the word, not an application of the law, and no amount of optical
matching fixes that.

### Both asked-for changes are drawn and neither is adopted as stated

Attempt five's rule: each critic gets exactly one concrete change, it is drawn exactly as stated,
and the drawing decides.

**Antigravity asked for the Accent stub at 4 units instead of 8.** Drawn as `short-measure-agy`,
rendered at every size and measured in the drawer. Four units is 3.4dp and 10 device pixels on the
Seeker tile, under the 6-unit floor this repository has enforced since attempt one, whose 2-unit
tick was 1.3dp and was not present on the phone. Halving the only element that carries the meaning
is attempt one with a different number on it, and it is applied to a drawing that lost on other
grounds anyway.

**Codex asked for the mark at exactly 1.00 of the wordmark's cap height in the horizontal lockup.**
Drawn as `render/lockup-horizontal-codex-dark.png`. It was aimed at a mark that no longer exists:
the "pause-and-tray" shape it was correcting is the continuous row that Codex's own paragraph three
got rid of. What the instruction encodes, that the mark had more authority than its meaning earns,
is taken, but by a rule rather than by a number: the mark's figure blocks stand at exactly the
wordmark's cap height and its registers hang below the baseline, which is 1.294 cap heights
overall. Codex can still say that is 29 percent too much, and both drawings are in the gallery so
the founder can look at the argument rather than read it.

## What the founder's three liked concepts got

The founder kept three of attempt four's seven: the withheld number, the live bar anchor and the
disconnected quote. What all three have in common is emptiness used deliberately, and that is what
this mark carries: 72.9 percent of the visible tile is nothing, and the part of it that matters is
one registered place with no figure in it. What none of the three had, and what this one has, is a
structure that makes the emptiness read as a kept place rather than as a gap.

## How to regenerate

No Gradle, nothing that builds the app. Four scripts, in order:

```
PYTHONIOENCODING=utf-8 python design/brand/logo/marks_logo.py     # geometry -> svg/ and icon/
PYTHONIOENCODING=utf-8 python design/brand/logo/wordmark.py       # type -> svg/ wordmarks, lockups
PYTHONIOENCODING=utf-8 python design/brand/logo/render_logo.py    # render/ at every size
PYTHONIOENCODING=utf-8 python design/brand/logo/icon_measure.py   # the drawer, and edge-contrast.json
PYTHONIOENCODING=utf-8 python design/brand/logo/gallery.py        # gallery.html
```

`icon_measure.py` imports `attempt-five/composite5.py` for `edge_contrast` and attempt three's
`composite.py` for the drawer grid, the measured mask and the rasterizer, rather than measuring the
phone again. `critic_sheets.py` builds the three images the second critic was shown. Needs pillow,
numpy and fonttools with brotli.

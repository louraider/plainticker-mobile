# Attempt six: the logo, and whether the round's own reasoning holds

Do not run any commands. Do not read or write any files. Do not edit anything. Everything you need
is in this message. Answer in prose, numbered to the six questions at the bottom. No SVG, no
coordinates, no code, no new concept unless a question asks for one.

You invented attempt four's seven concepts and attempt five's development of two of them. This is
the round after those, and the brief changed: the founder said there is no logo at all, so the logo
comes first and the launcher icon is cut out of it afterwards rather than invented beside it.

## The product, in one paragraph

PlainTicker Mobile is an Android app for the Solana Seeker. It reads tokenised US stocks (xStocks:
TSLAx, NVDAx) on Solana and states facts about a token before you swap into it: what it tracks,
what the issuer can still do to it (permanent delegate, pausable transfers), how far the quoted
price has drifted from the NYSE close, and how deep the pool behind that quote is. It never says
buy, sell, hold or avoid, and a unit test keeps those four words out of the codebase. Its one
distinctive behaviour is a refusal: below $10,000 of pool depth it will not print a tracking figure
at all and states the pool instead, "Pool holds $80, too thin to track". Measured 2026-09-12: of
160 analysed tokens Jupiter reports a depth for only 53; UBERx quoted a 152.13 percent premium on a
pool holding $80; a $100 order into UBERx costs 64.2 percent of itself in price impact.

Design system, "Instrument": Canvas `#0B0F14`, Ink `#E8ECF1`, exactly one accent `#5AA9E6` meaning
interactive or live, Caution `#D9A441` reserved for issuer-control risk only, radius 0 on
everything, no gradients, no shadows, no green or red for price direction. Outfit for words,
JetBrains Mono for every number, ticker, hash and timestamp: "numbers never appear in Outfit."

## What the three earlier rounds established, so you do not re-derive it

1. A composition of blank rectangles does not read as the meaning its author assigned it. It reads
   as whichever Android system glyph already owns that geometry. Attempt three's marks turned out
   to be the split-screen button, a battery and the browser-extension jigsaw. Naming the nearest
   system glyph is a required field now.
2. The ground is settled by measurement, not argument. In the real Seeker drawer screenshot, a
   three-pixel ring inside the launcher mask against three pixels outside it gives a WCAG contrast
   ratio of 1.03 to 1 for PlainTicker's tile as it ships, which is no boundary at all. Photos, the
   brightest tile in the same row, reads 18.06.
3. Your own conclusion in attempt five, which the round adopted: inverting a mark to get an edge
   costs the mark its identity. You said a knocked-out vertical bar "reads as a physical cut that
   separates the field into two distinct pieces"; Codex independently called the same drawing a
   sidebar. Both of you also dropped the defence of a small coloured dot as a quantity.
4. The objection attempt five could not answer, Codex's, is the brief for this round: *"These marks
   show two things sitting apart. They do not show that one cannot justify the other."*
5. The founder kept three of your seven (the withheld number, the live bar anchor, the disconnected
   quote) and the common property of all three is emptiness used deliberately.

## The measured facts. These are facts, not estimates.

- The Seeker drawer tile is 182 device pixels, 60.7dp at 480dpi, not the 48dp an icon brief assumes.
- Its launcher mask fits a superellipse of exponent 3.05, measured off the Photos tile.
- The adaptive canvas is 108 units; a launcher shows the central 72; only the central 66 circle is
  guaranteed by every mask. One unit is 0.843dp on the real tile.
- Edge contrast, measured this round on the same rig: a Canvas ground reads 1.01 to 1.03 to 1, an
  Accent `#5AA9E6` field reads 7.25 to 7.27, and a cool off-white field `#F2F5F8` reads 16.85 to
  16.89 against Photos's 18.06.

That last line is new and it is the round's own finding. No earlier attempt measured a light field.
It matters because a light field is the only treatment that lets a two-colour mark keep both its
colours and still have an edge: knocking a mark out of an Accent field flattens Ink and Accent into
one silhouette, which on a mark whose argument is that one part is Ink and one part is Accent is
not a treatment, it is an amputation.

## The wordmark, which is the part that has never existed

`Plain` set in Outfit SemiBold, `Ticker` set in JetBrains Mono Medium, on one baseline, cap heights
optically matched (Outfit caps are 703/1000 em, mono caps 730, so the mono half is set at 96.3
percent of the Outfit size). Outfit tracked -0.01em, mono -0.02em, with a -0.055em correction at
the `nT` join to close the mono side bearing.

The argument: the app's own typographic law is that words are Outfit and every ticker is mono. The
name is one word made of two kinds of object, so it is set as two kinds of object. The drawn ink is
7.66 cap heights wide.

Two other settings are drawn beside it: the whole name in Outfit (what the top bar ships today) and
the whole name in mono (the name treated as a ticker throughout).

## The six marks, as geometry, in the 108 viewport

Coordinates are x, y, width, height, colour. Ink is `#E8ECF1`, Accent is `#5AA9E6`. The visible
area is 18 to 90 on both axes.

**1. overhang.** Ink 22,38,64,12. Accent 30,50,12,22. A printed figure standing on a pool too
narrow to carry it: 44 units of the bar hang over nothing to the right, 8 to the left. Nearest
system glyph: a T, a map pin, the text-underline button, all of which centre their stem; this stem
is 15 units off centre on a 64-unit bar. Ink share of the visible 72: 20.9 percent.

**2. overhang-flush.** Ink 22,38,64,12. Accent 22,50,12,22. The same, with the footing at the near
end, so the whole 50 units hang one way. Nearest glyph: a reversed L, the align-left button.

**3. floor.** Ink 20,44,68,8. Accent 28,64,12,20. The $10,000 threshold drawn full width, and a
measure that stops 12 units short of it. Everything above the line is empty because nothing is
printed up there. Nearest glyph: the download glyph without its arrowhead, a vertical progress bar.
Ink share 16.2 percent.

**4. kept-place.** Ink 26,34,13,32. Ink 47,34,13,32. Ink 24,66,44,8. Accent 68,66,16,8. Three
places on one row, two of them holding a figure and the third kept and left blank; the row is not
shortened and the accent runs under the place that was not filled, beginning exactly where a third
figure would begin. Nearest glyph: a bar chart with a missing bar, the battery. Ink share 26.6
percent.

**5. short-measure.** Ink 26,34,13,32. Ink 47,34,13,32. Accent 68,58,13,8. Ink 24,66,60,8. The same
two figures, and in the third place a stub a quarter of the height a figure needs, sitting on the
same row. This is the one drawing that states the reason and the result at once: the stub is why
the figure is not printed, the empty space over it is the figure that is not printed. The ratio is
a lie of scale and it is declared: $80 against $10,000 is 1 to 125, which cannot be drawn, so the
stub is 1 to 4. Nearest glyph: a bar chart, the signal-strength meter. Ink share 28.7 percent.

**6. withheld-print.** An Accent field with one Canvas cut at 62,34,16,40 and nothing else: the one
figure that was not printed, cut out of everything that was read. Emptiness at the limit.

**Control, not a candidate: gauge-inverted.** The mark on the phone now, `#5AA9E6` field with the
track 26,54,56,8, the reference graduation 50,62,8,14 and the token tick 64,38,10,36 knocked out.
It is drawn so the claim in the product-marketing document, *"The brand mark is the tracking gauge
itself rather than a letter, because it is the one mark nobody else has and it says what the app
does"*, is tested against a drawing rather than argued about.

## The current pick, which you are being asked to attack

kept-place, on a paper field for the launcher, 16.89 to 1 edge contrast; the split wordmark; a
horizontal lockup with the mark at 1.32 cap heights centred on the wordmark's cap band.

## Answer these six, numbered, in prose

1. Does the split wordmark survive, or is it two fonts in a logo wearing a justification? Answer
   for a headline, a row of running text, and a browser tab, and say which of the three settings
   you would ship.

2. kept-place versus short-measure. kept-place draws the refusal and is cleaner at 16dp;
   short-measure draws the refusal *and its cause*, which is the objection attempt five could not
   answer, and pays for it with a smaller accent element. Which ships, and does short-measure
   actually answer Codex's sentence or only appear to?

3. The paper field. A cool off-white tile reads 16.89 to 1 against the drawer's 1.03, keeps both of
   the mark's colours, and puts PlainTicker in the same idiom as five of the eight tiles in its own
   drawer row. Is the edge worth abandoning the Canvas ground the whole design system is built on,
   or does a light tile make this app look like every other white tile on the phone?

4. The tracking-gauge claim. Having seen it inverted and given an edge, does the claim in the
   product-marketing document survive, or should that sentence be rewritten? Say which, and say
   what the replacement sentence is if it does not survive.

5. Rank the six. Then name the one thing about your top choice that would make a stranger misread
   it in the first half second, in the drawer, beside Photos and the Play Store.

6. One concrete change, stated as a number. Exactly one, to the drawing you would ship. It will be
   drawn exactly as you state it, whether or not it turns out to be right.

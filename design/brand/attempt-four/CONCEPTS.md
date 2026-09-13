# The app icon, attempt four: seven concepts from Antigravity

**These concepts are not mine and the words below are not mine.** They came back from Google Antigravity (Gemini 3.1 Pro), run headless against `BRIEF.md`: `Google Antigravity, gemini-3.1-pro-high, effort high, plan mode, 2026-09-13`. Every paragraph under **Antigravity** is quoted verbatim from that run, including the ranking and the pick. Nothing in them has been edited, tightened or reordered. Where a paragraph makes a claim about the product that is wrong, the claim is left standing and corrected underneath rather than quietly fixed, because the point of this attempt is that the ideas came from somewhere else.

What is mine is the drawing. Each concept has a **Drawn** note saying where the SVG departs from the paragraph and why, which is almost always the launcher mask taking a stated number away.

The order is Antigravity's ranking, best first. The founder's order may be different and the gallery does not assume otherwise.

## Why three attempts failed, which is the brief these answer

The brief put this diagnosis to Antigravity as a hypothesis to test rather than a
finding to accept, and it did not dispute it.

**Attempt one** was a JetBrains Mono capital P with a 2-unit Accent tick under it. The tick was
1.3dp at drawer size and was simply not present, so the mark was a letter in a box. It was judged
on the App info screen, where an icon is large, and it is not large anywhere it matters.

**Attempt two** was the tracking gauge promoted to a brand mark: an Ink track, a reference
graduation hanging under its centre, an Accent token tick crossing it. Correctly weighted this
time, nothing under 4dp at 48dp, with a real monochrome layer and a rule in the generator refusing
any construction that flattens into a plus sign. The founder's verdict on the phone was that it is
not much better.

**Attempt three** was eight tiles, each a different abstract claim, each drawn from axis-aligned
rectangles, one circle and knockouts: one to one, the wrapper and what is in it, someone else's
reach, the blank it will not fill, the split, before, the fit, and different shapes same measure.
Rejected outright.

The founder's hypothesis was that all three were geometric abstractions generated from primitives
with no specific idea behind them, so eight variants were really one non-idea eight times. That is
close, and the way it is not quite right is the useful part.

**One. The ideas existed but were never given a referent.** Attempt three had a specific written
idea per tile. Every one of those ideas was an abstract *relation*, containment, reach, absence,
division, precedence, fit, equivalence, and every drawing was that relation between blank
rectangles. A relation between blanks is the same picture whichever relation you meant. Nothing in
any of the eight is made of anything the product is made of: not a number, not a ticker, not a
price, not a mint, not a reading. So yes, one non-idea eight times, but by a mechanism more
specific than having no idea.

**Two, and this is what the hypothesis misses.** The abstractions did not land on nothing, they landed on
Android's existing icon vocabulary. In the real drawer beside real neighbours, attempt three's
number 1 is the split-screen button, number 2 is a stop button, number 4 is a battery, number 6 is
the universal duplicate-windows glyph, number 7 is a jigsaw piece, which is the browser-extension
icon, and number 8 is the shape tool. A blank geometric composition does not read as the meaning
its author assigned it. It reads as whichever system glyph already owns that geometry. At that
level of abstraction there is no free geometry left. This is why the brief made naming the nearest
system glyph a required part of every concept, and it is the one test none of the three attempts
applied.

**Three. All three attempts changed the argument and never changed the method.** The method every
time was: write the constraints as assertions, search for rectangles that satisfy them, ship
whatever passes, write the caption afterwards. That is a search over the space of shapes that pass
a lint, which is close to the space of shapes with no character. A constraint set can tell you a
mark is not broken. It cannot tell you a mark is anything. Attempt four changes the method rather
than the argument: the ideas come from outside this repository, as prose, before any geometry
exists, and the lint runs afterwards on a drawing that already means something.

**Four. Mass was the wrong fix, correctly derived.** Attempt two was too thin, so attempt three
made size the answer. Numbers 3, 4, 7 and 8 of that set are big and confident and say nothing, and
they are no better for being big. A correct diagnosis, too thin, was over-corrected into a size
rule, which let everyone skip the question of what the thing depicts for a third time.

**Five. The product's most distinctive asset had never been drawn.** It is not the gauge. It is the
withheld number: an app that will not print a figure when the pool holds $80, on a shelf where
every neighbour exists to print a figure. Attempt three's number 4 gestured at it and drew it as a
void in a field, which is a battery. Antigravity, given the same facts, put that idea first and
drew it as a baseline with nothing on it, which is the same idea with a referent.

## The seven

### 1. The withheld number

*a baseline and a cursor, and no figure on it*  
`svg/withheld-number.svg` and `svg/withheld-number-flat.svg`, treatment ground, furthest corner 36.7 of the 33-unit circle guarantee.

**Antigravity:** A 10-unit horizontal baseline with a 12-unit square cursor resting on its far right edge, leaving the center of the safe circle entirely empty, drawn on a Canvas ground. It carries the app's refusal to print a tracking figure; when the liquidity pool holds only $80, the blank space is the disclosure. It survives the monochrome layer by relying on the mass and placement of two elements rather than color dependency, and the 10-unit minimum stroke ensures both shapes render at 48dp. Crypto icons are centered objects, but this mark is an asymmetrical void. The closest system glyph is a text input field; the mark avoids misread because the cursor sits at the extreme boundary, weighting the composition to one side to diagram the deliberate absence of a number.

**Drawn:** The cursor is drawn touching the baseline rather than floating above it. Antigravity says resting on, and a 12-unit block held clear of the rule would need a 6-unit gap under it, which at 48dp is a seam wider than the rule is thick and reads as two loose parts. Antigravity named the mask as this concept's own weakness and it was right: the cursor's outer edge is at x 86, which is 36.7 units from centre against the 33-unit circle guarantee, so it survives the measured superellipse and would be clipped by a true circle mask. It is drawn where Antigravity put it, at the boundary, and the cost is stated.

**Breaks:** Nothing in DESIGN.md. It is the one concept that draws section 1.1, the liquidity floor, which is the rule the product is built around.

### 2. The live bar anchor

*the bar that means live, holding two readings off the mint*  
`svg/live-bar-anchor.svg` and `svg/live-bar-anchor-flat.svg`, treatment ground, furthest corner 39.6 of the 33-unit circle guarantee.

**Antigravity:** A vertical line 12 units wide running the height of the safe circle on the left margin, anchoring two horizontal dashes of unequal length to its right, drawn on a Canvas ground. It carries the live reading of the chain; the vertical line is the app's live bar that breathes during data reads, and the dashes are the raw extensions it pulls from the mint. It survives the monochrome layer because the structure separates its components by axis and weight, and the 12-unit thickness guarantees survival at 48dp. Crypto icons are floating shapes, but this anchors to a margin. The closest system glyph is a hamburger menu; the mark avoids misread because the vertical anchor is strictly thicker than the rows, establishing it as a measurement edge rather than a navigation list.

**Drawn:** The bar is 56 units tall, not the 66 of the safe circle. A 12-unit column standing on the left margin cannot also be 66 tall: at x 26 the measured superellipse allows 58 units of height and no more, and a bar that runs to 66 loses both its ends to the mask. The bar keeps the stated 12-unit width, which is what the paragraph says carries it at 48dp.

**Breaks:** Section 1's quiet character, and the live bar's own scale: it is a 2dp rule inside the app and 12 units here, which is 8dp at 48dp.

### 3. The bracketed fact

*one token clamped between two brackets and read*  
`svg/bracketed-fact.svg` and `svg/bracketed-fact-flat.svg`, treatment inverted, furthest corner 35.6 of the 33-unit circle guarantee.

**Antigravity:** A 16-unit square locked between two monospace brackets, separated by exactly 6 units, set inverted on an Accent ground and knocked out in Canvas. It carries the app as a reading instrument that isolates the token; it does not trade or recommend, but clamps the token in a viewport to extract its facts. It survives the monochrome layer natively as a knockout, and the 6-unit gaps ensure the brackets do not fuse with the central square when reduced to 48dp. Crypto icons use gradients and organic wrappers, but this is a mechanical fixture. The closest system glyph is a bounding box; the mark avoids misread because the brackets use thick tabular typography rather than thin hairlines, acting as a code block instead of a design tool.

**Drawn:** Drawn as specified. The 6-unit separation is the tightest positive measure in any of the seven and it is exactly at the floor: 4dp at 48dp, 15.2 device pixels on the Seeker. The brackets are 10 units in the stroke and 14 in the arm, which is the thick tabular weight the paragraph asks for rather than a hairline bracket.

**Breaks:** Section 2's one-accent rule is kept by inverting rather than by adding: the field is the accent and the mark is Canvas knocked out of it.

### 4. The extracted fact

*three lines of a mint, one of them the one that matters*  
`svg/extracted-fact.svg` and `svg/extracted-fact-flat.svg`, treatment ground, furthest corner 36.2 of the 33-unit circle guarantee.

**Antigravity:** Three horizontal dashes stacked vertically on a Canvas ground, where the top and bottom dashes are 6 units thick and the middle dash is an 18-unit block. It carries the isolation of a hidden risk; the app scans a mint and isolates a specific issuer control, like a permanent delegate, pulling the critical fact to the surface. It survives the monochrome layer because the middle dash reads as a heavy structural block against the thin lines without needing the Caution color, and the 8-unit gaps prevent fusion at 48dp. Crypto icons are mascots or coins, while this is a data scan. The closest system glyph is a text-alignment button; the mark avoids misread because the middle line breaks the rhythm of UI text, reading as a highlighted terminal line.

**Drawn:** The three lines are given ragged right edges, 52, 60 and 40 units, which the paragraph does not specify. Equal lengths would be three stacked bars, which is the text-alignment button the paragraph is trying to avoid; ragged ends are what make it lines of a document. The 6-unit thin dashes are exactly at the stroke floor, 4dp at 48dp, and are the thinnest shapes in the whole set.

**Breaks:** Section 2 reserves Caution for issuer control, and this is an issuer-control mark drawn in Accent instead, because a launcher icon cannot carry two colours and keep a monochrome layer.

### 5. The frozen path

*a transfer that runs into something it cannot move*  
`svg/frozen-path.svg` and `svg/frozen-path-flat.svg`, treatment ground, furthest corner 34.2 of the 33-unit circle guarantee.

**Antigravity:** A horizontal track 8 units thick running from the left margin, terminating abruptly into a 24-unit solid square block in the center, leaving the right half of the safe circle empty, drawn on a Canvas ground. It carries the reality of a frozen account; the asset cannot move freely, and the app reads this extension from the mint to stop the swap. It survives the monochrome layer because the track and the block form a single shape with distinct weights, and the 24-unit block anchors the mark at 48dp. Crypto icons imply motion or growth, but this is a dead stop. The closest system glyph is a progress bar; the mark avoids misread because the terminating block is three times thicker than the track, reading as an immovable barrier.

**Drawn:** The track starts at x 20, hard against the mask edge, so it reads as arriving from outside the tile rather than beginning inside it. The paragraph's claim that the app 'reads this extension from the mint to stop the swap' is not true of this product and is corrected in CONCEPTS.md: the app never stops a swap, it states what the mint says and leaves the swap where it is. The drawing is unaffected, the caption is.

**Breaks:** Section 8's ban on filled progress bars, and section 4's Track, which is 'never a filled bar'. This is a track that is stopped rather than filled, which is the distinction the icon rests on.

### 6. The disconnected quote

*the quote on one side, the pool it is quoted off on the other*  
`svg/disconnected-quote.svg` and `svg/disconnected-quote-flat.svg`, treatment ground, furthest corner 36.9 of the 33-unit circle guarantee.

**Antigravity:** A monospace right bracket 12 units thick positioned on the right boundary, and an 8-unit dot positioned on the far left, leaving the space between them completely empty, drawn on a Canvas ground. It carries the 152% premium on an $80 pool; the bracket is the quoted price, the dot is the actual liquidity pool behind it, and the app exposes the gulf between them. It survives the monochrome layer by relying strictly on size difference and spatial separation, and the 8-unit minimum weight ensures both elements survive at 48dp. Crypto icons are balanced, whereas this mark is strictly unbalanced. The closest system glyph is a terminal prompt; the mark avoids misread because the dot is stranded far from the bracket, creating a diagram of abandonment rather than a ready input line.

**Drawn:** The bracket is pulled in from the right boundary to x 82 so its corners clear the measured mask; at x 86 the upper and lower arms are cut. The dot is 8 units across as specified, which is 5.3dp at 48dp: above the 4dp floor but the smallest element in the set, and the one most likely to be judged absent on the phone.

**Breaks:** Section 4's radius 0 on everything, broken by the dot, and section 8's ban on coloured dots as decoration. The dot is not decoration here, it is the $80.

### 7. The broken peg

*the reference at the centre, the token not on it*  
`svg/broken-peg.svg` and `svg/broken-peg-flat.svg`, treatment ground, furthest corner 35.6 of the 33-unit circle guarantee.

**Antigravity:** A horizontal track with a 12-unit reference square fixed under its center, and a vertical tick 12 units wide crossing the track at the far right edge, drawn on a Canvas ground. It carries the premium quoted against a thin pool; the detached right tick is the token failing to track the reference. It survives the monochrome layer because the pieces are physically distant and do not collapse into a cross, while the 12-unit width survives reduction to 48dp. Crypto apps use upward charts, whereas this is a horizontal diagram of a peg failure. The closest system glyph is a UI slider; the mark avoids misread because the tick is a blade crossing the track, operating as a measuring gauge.

**Drawn:** Heavier than the mark that ships: the track is 10 units rather than 8, the reference is a 12-unit square rather than an 8 by 14 graduation, and the tick is 12 wide rather than 10. It is in the set because Antigravity ranked it, and it is ranked last by Antigravity itself. It is here so the founder can see the rejected direction beside six that are not it, in the same drawer, at the same size.

**Breaks:** Nothing new. This is attempt two's construction redrawn, which is why Antigravity ranked it last itself.

## Antigravity's ranking, verbatim

Here is the ranking, based strictly on what will stop an eye in a drawer of soft, saturated, friendly shapes:

1. **The Withheld Number**
2. **The Live Bar Anchor**
3. **The Bracketed Fact**
4. **The Extracted Fact**
5. **The Frozen Path**
6. **The Disconnected Quote**
7. **The Broken Peg** (Ranked last because it retreads the failed ground of Attempt Two; the gauge has already proven weak as a standalone mark).

## Antigravity's pick, verbatim

**The choice to ship: The Withheld Number.**

In a drawer overwhelmingly populated by centered, saturated, blobby shapes, an eye stops on an anomaly. This mark is an anomaly because it actively refuses to fill the tile, offering a vast, asymmetrical void instead of a mascot. It is the literal, structural depiction of the app's primary operational decision: the refusal to print a number when the pool is empty. It is a reading instrument outputting nothing.

Its weakness is the launcher's mask itself. By pushing the heavy cursor to the extreme right edge to maximize the void, it risks being clipped by the more aggressive Android superellipse masks. It must be measured and positioned strictly inside the 66dp safe circle, which reduces the absolute distance between the baseline start and the cursor, slightly weakening the impact of the empty space.

## Where Antigravity contradicted the product or the design system

Three, and they are worth reading before the drawings.

**The product does not stop a swap.** Concept 5, the frozen path, says the app "reads this
extension from the mint to stop the swap". It does not. The app reads the extension, states it, and
leaves the swap exactly where it was; a default-frozen mint is disclosed like any other fact and no
surface blocks, warns off or withholds the button. This is the disclosure-not-recommendation rule
in README and DESIGN.md section 7, and it is the one thing the whole product is built not to do.
The mark still works, a transfer meeting something immovable is a fair drawing of a frozen mint,
but the caption had to be rewritten and the paragraph is left standing above it.

**Six of seven sit on a near-black ground, which is the failure attempt three measured.** Every
concept except the bracketed fact asks for a Canvas ground. Canvas is `#0B0F14` and the Seeker's
drawer wallpaper is within a few units of it, which is why `composite.py` cannot even find
PlainTicker's tile by scanning: it has no edge at all and the mark floats on the wallpaper. That
finding is in the brief and Antigravity chose the dark ground anyway, six times. The drawer strips
in the gallery are where this gets settled, not here. The one inverted concept, an Accent field
with the mark knocked out of it, is the only one of the seven with an edge.

**Two departures from the design system, both declared rather than smuggled.** The disconnected
quote is drawn with a circle, which breaks section 4's radius 0 on everything, and its dot is the
kind of object section 8 bans as a coloured dot. Attempt three broke the same rule once, on the
grounds that a brand mark is not a UI container, and the same defence applies here: the dot is not
decoration, it is the $80. Separately, the extracted fact is an issuer-control mark and section 2
reserves Caution for exactly that, but a launcher icon carrying both Accent and Caution has no
monochrome layer worth the name, so it is drawn in Accent. Antigravity anticipated this and said so
in its own paragraph.

## The baseline in the sheet

`svg/as-shipped.svg` is attempt two's tracking gauge, the mark on the phone now. It is not a candidate and it is not ranked. It is drawn into the same drawer at the same size so the seven are judged against the thing they would replace rather than against nothing.


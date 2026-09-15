# Argue against these twelve icon drawings

You are the second critic on an Android launcher icon, and you are here to argue against it. A
different model (Gemini 3.1 Pro, via Google Antigravity) invented the two concepts below and is
being asked in parallel to develop them. Your job is the opposite one. Assume the drawings are
wrong and find out where. Do not be balanced, do not hedge, and do not end with encouragement. If
something is genuinely good, say so in one sentence and move on.

Answer in prose. Do not write SVG, coordinates, code, or a new concept. Do not run any commands or
edit any files; everything you need is in this message and in the three images attached.

## The product, in one paragraph

PlainTicker Mobile is an Android app for the Solana Seeker, a crypto phone with a hardware wallet.
It reads tokenised stocks (xStocks: TSLAx, NVDAx, and so on) and states facts about them before you
swap: what the token tracks, what the issuer can still do to it, how far the quoted price has
drifted from the NYSE close, and how deep the liquidity pool behind that quote actually is. It
gives no verdict and never says buy, sell, hold or avoid. Its one distinctive behaviour is a
refusal: below a $10,000 pool it will not print a tracking figure at all, and says "Pool holds $80,
too thin to track" instead. Measured live: UBERx quoted a 152% premium on a pool holding $80.

Its design system is called Instrument: cool near-black Canvas `#0B0F14`, Ink `#E8ECF1` for text,
exactly one accent `#5AA9E6` meaning "interactive or live", radius 0 on everything, no gradients,
no shadows, monospace for every number. Its named interface elements are a tracking gauge, a live
bar (a 2dp Accent vertical bar that breathes while data is live), a 64px monospace ticker, and a
1px blueprint grid.

Three icons have already been designed for this app and all three were rejected by the founder.

## The target, measured off the real phone. These numbers are facts, not estimates.

- The drawer tile is **182 device pixels, 60.7dp at 480dpi**. Not 48dp.
- The launcher mask was lifted off a neighbouring tile in a real drawer screenshot and fits a
  **superellipse of exponent 3.05**. Not a squircle of 4, not a circle.
- Art lives in a 108-unit square; the launcher shows the central 72; only the central 66 circle is
  guaranteed under every mask. One unit is **2.53 device pixels, 0.843dp**.
- Nothing thinner than 6 units (4dp at 48dp) and no gap under 6 units.
- A monochrome themed-icon layer is mandatory, one flat colour on one flat plate, both chosen by
  the launcher. Accent and Ink are the same colour there.
- Neighbours in the drawer: Google Photos, the Play Store, a green pill-shaped icon, Phantom,
  Jupiter, Backpack, OKX. Overwhelmingly saturated, blobby, friendly, many of them purple.

## The two families

**Family one, the live bar anchor.** A vertical member near the left with horizontal readings held
off it. The claim: the bar that means live is what the app is built on, and readings hang off it.
Its own author named the nearest system glyph as a hamburger menu. Its 12-unit bar is 10.1dp on the
real tile where the app's live bar is a 2dp rule.

**Family two, the disconnected quote.** A monospace right bracket at the right boundary, one small
unit stranded at the far left, the whole middle empty. The claim: the quote on one side, the $80
pool it was quoted off on the other, and the app is the thing that shows the distance. Its pool was
originally an 8-unit circle, which is 6.7dp on the real tile, breaks the radius 0 shape lock, and
falls under a design-system ban on coloured dots as decoration. The defence on record is "the dot
is not decoration, it is the $80".

## The twelve drawings, in the order they appear in the attached images

Image 1 (`colour60`) is every drawing at 60.7dp, 182 device pixels, 1:1, cut by the launcher's own
measured mask, sitting on the drawer's own wallpaper colour, seven per row. Image 2 (`drawerrows`)
is nine of them composited into the real drawer screenshot in PlainTicker's own slot, beside the
real Photos and Play Store tiles, one row each, labelled, at half device resolution. Image 3
(`mono`) is the mandatory monochrome layer for each, in launcher-supplied colours.

Row 1 of image 1, left to right: `live-bar-as-proposed` (Canvas ground, bar contained, two dashes
level with the middle), `live-bar-bleed-ground` (Canvas ground, 12-unit bar bleeding past top and
bottom, readings dropped below the middle), `live-bar-bleed-inverted` (the same, knocked out of a
solid Accent field), `live-bar-true-weight-ground` (Canvas ground, bar narrowed to 8 units, which
is the 1 to 9 aspect the live bar has in the app), `live-bar-true-weight-inverted`,
`live-bar-one-reading-inverted` (one reading instead of two), `quote-as-proposed` (Canvas ground,
8-unit round pool).

Row 2: `quote-square-pool-ground` (pool squared and grown to 12 units, the width of the bracket's
stroke), `quote-square-pool-inverted`, `quote-small-pool-inverted` (pool squared but held at the
doubted 8 units), `quote-heavy-bracket-inverted` (bracket to 14 units, pool to 16),
`quote-round-pool-ground` (round pool at the square's size, so the shape lock is a comparison),
`as-shipped` (the rejected mark on the phone today, not a candidate).

## The measurement that has already been made, so you do not repeat it

The tile boundary was sampled in the finished drawer image, three pixels inside the launcher's mask
against three pixels outside it, as a WCAG contrast ratio. PlainTicker's slot today reads **1.03 to
1**, which is no edge at all. Every Canvas-ground drawing reads between 1.01 and 1.62. Every
inverted drawing reads between 6.6 and 7.3. Photos reads 18.06. The ground question is settled
numerically; do not spend your answer on it. Spend it on what inversion costs, and on everything
below.

## What I want you to argue about

1. **Legibility at 60.7dp under a 3.05 superellipse.** Look at image 1 and image 2, not at the
   geometry. Which of these fall apart, and at what size do they fall apart. Name the specific
   shapes that fuse, disappear, or get shaved by the mask. The 3.05 exponent is a rounder mask than
   a squircle and it takes corners: say which drawings are paying for that and how much.

2. **The system-glyph misread, and be merciless.** For every drawing, name the Android or general
   UI glyph whose geometry it is closest to, and say whether a person flicking past it at 60.7dp
   would see the glyph or the mark. Family one was drawn specifically to escape a hamburger menu by
   bleeding the bar past the mask and dropping the readings low. Say whether that worked or whether
   it has only traded a hamburger for something else, and name the something else. Do the same for
   family two: a bracket alone in a tile, and a bracket with one small square beside it.

3. **Does either mark say anything that is true of THIS app and no other?** This is the test the
   first three attempts failed. Be specific and be hostile. If `live-bar-true-weight-inverted`
   would serve equally well as the icon for a log viewer, a note-taking app, a code editor or a
   podcast player, say so. If the disconnected quote's gulf reads as "empty" rather than "the pool
   is $80 and the quote is 152% off it", say so. A caption is not a defence: the mark is seen
   without its caption, always.

4. **The dot defence.** "The dot is not decoration, it is the $80." Attack it or concede it. Then
   say whether squaring the pool and growing it from 8 to 12 units rescued the idea or killed it,
   because a bigger pool is arguably a worse drawing of a pool that is too small to trust.

5. **The monochrome layer, image 3.** The inverted drawings become a light plate with a dark mark
   there, which is a figure-ground flip relative to the colour tile. Say whether that is a problem,
   and which drawings lose their idea entirely in one colour.

6. **One sentence at the end: if you had to ship one of these twelve tomorrow, which, and what is
   the single worst thing about it.**

Where you agree with the drawings, say it briefly. Where you think the whole round is a mistake and
neither family should ship, say that, and say what you would do with the reasoning instead.

Write flat and specific. Sentence case. No emoji, no exclamation marks, no headings made of
questions, and none of these words: seamless, powerful, unlock, empower, journey, insights,
supercharge, effortless, all-in-one.

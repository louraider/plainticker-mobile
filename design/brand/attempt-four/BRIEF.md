# Brief: the PlainTicker Mobile launcher icon, attempt four

## Before anything else: do not run any commands

**Do not run any commands. Do not call RunCommand, do not call a shell, do not call a terminal, do
not try to execute python, git, ls, cat, find, grep, or anything else. Do not try to write, create
or edit any file.** You are running headless, in plan mode, in a sandbox. A command permission
request is auto-denied there and the entire run returns empty output, which has already happened
twice on this brief. Everything you need is written out below in full. If you want to look at a
file, read it (read-only file access is fine); do not shell out to read it. Answer in prose, in
your reply, and nothing else.

## What you are being asked for

Ideas, not renders. Six to ten distinct **concepts** for an Android launcher icon, each one
paragraph of prose. Then a ranking of your own concepts and one you would ship. Do not produce SVG,
do not produce coordinates, do not produce code. Someone else draws them. Your job is the idea.

## The product

PlainTicker Mobile is an Android app that reads a tokenized US stock on Solana and tells you what
the token actually is before you swap into it.

Tokenized US stocks here are "xStocks", issued by a third party: a token on Solana that is supposed
to track a real NYSE-listed share. TSLAx is supposed to be Tesla. The app opens a token on the
chain rather than on a chart. On every open it reads, live, from the Token-2022 mint:

- whether the mint carries a **permanent delegate**, which is a third party who can move the token
  out of your wallet without your signature;
- whether transfers are **pausable** by the issuer;
- whether there is a **transfer hook** running someone else's code on every transfer;
- whether new accounts are **frozen by default**;
- the **proof of reserves** and the **split multiplier**;
- and the real **liquidity** behind the price that is being quoted to you.

An extension the mint does not carry renders as absent; a mint that could not be read renders as
unknown, never as "no risk". Beside every reading sits the chain slot it came from and how old it
is.

The measured fact that shaped the whole product: of **157 analyzed xStocks, only 19 hold more than
$10,000 of liquidity**. One of them, UBERx, was quoting a **152% premium on a pool holding $80**.
APPx quoted +89% on a pool of $34. Above $10,000 the pools track the NYSE close within 0.8%; below
it the quoted price stops describing anything at all. So the app has a floor: below $10,000 of pool
it **draws no tracking figure at all** and prints the pool instead, "Pool holds $34, too thin to
track". It does not hide the token, does not filter it, does not add a warning section. It just
declines to print a number it cannot stand behind.

The app's whole position is **disclosure, not recommendation**. It never says buy, sell, hold or
avoid; a unit test reads every string in the repository and fails the build if one of those four
words reaches a surface. It adds no verdict of its own. It shows you what the thing is and stops.

It is not a trading app. It has exactly one trading verb, "Swap", and the swap is the last thing on
the screen, under everything the token disclosed about itself.

## The audience and the shelf it sits on

Solana Seeker phone owners. The Seeker is a crypto-native Android phone with a hardware wallet.

The icon will sit in an Android app drawer, at roughly 48dp, for about half a second, next to
Phantom, Jupiter, Backpack, pump.fun, Jito, OKX and the rest. That neighbourhood is overwhelmingly
**purple, gradient-heavy, blobby and friendly**: a ghost, a planet, a capsule, soft organic shapes
in saturated candy colour, almost all of them on a light or vividly coloured ground. In the real
drawer screenshot we composite against, PlainTicker's near-black tile currently has no edge against
the wallpaper at all and reads as a hole in the grid.

A mark that beats this neighbourhood does not have to be louder than it. It has to be *a different
kind of object* from it. That is a real opening, because every neighbour is a mascot or a blob and
none of them is an instrument or a document.

## The design system, quoted

From `DESIGN.md`, the "Instrument" design system for this app. Quoted so you know what the product
looks like inside, and so you know which rules you are arguing with when you argue with one.

- Theme: "A reading instrument, not a trading terminal and not a website. Cold near-black canvas as
  the default (the Seeker is an OLED phone opened from a dark wallet), quiet secondary text, one
  blue accent that means 'interactive or live', and every number in a monospace face."
- Colour tokens, and these are the only ones:
  - **Canvas** `#0B0F14`, page background, cool near-black, never pure black.
  - **Elevated** `#121820`, the only second surface.
  - **Ink** `#E8ECF1`, primary text and numerals.
  - **Ink 2** `#B4BCC8`, secondary text.
  - **Muted** `#7F8A99`, metadata.
  - **Line** `rgba(232,236,241,0.10)` and **Line strong** `rgba(232,236,241,0.22)`, hairlines.
  - **Accent** `#5AA9E6`, one blue. "Every interactive text action, the active tab indicator, the
    primary button fill, the live bar, the token tick on the gauge. Nothing else."
  - **Caution** `#D9A441`, "only the value of an explicit issuer-control risk (permanent delegate
    present, transfers pausable). Never on prices, premiums, scores or list rows."
- "Rules: exactly one accent. No green or red for price direction anywhere; direction is a signed
  monospace number in Ink. **No gradients, no shadows, no glass.**"
- Typography: "**UI face:** Outfit (400, 500, 600). Words, labels, buttons, headings. **Numeral
  face:** JetBrains Mono (400, 500) with tabular numerals. Every number, every ticker symbol, every
  wallet or signature fragment, every timestamp. Numbers never appear in Outfit." No Roboto, no
  Inter, no Geist. "No uppercase transforms, no positive letter-spacing labels, no section numbers,
  no em or en dashes." Sentence case everywhere.
- "Shape lock: **radius 0 on everything.**"
- Anti-patterns, never: "Cream or paper backgrounds; Geist or Inter; numbered section labels;
  uppercase tracked eyebrows; em dashes; cards for lists; three equal tiles; filled progress bars;
  red and green price blocks; colored dots as decoration; gradients, shadows, glass, **purple**;
  spinners; snackbars; bottom navigation bars; sticky headers; hover-only states."
- Copy register, section 7: "Sentence case; buttons are verb plus object. Never BUY, SELL, HOLD or
  AVOID as words on any surface. Descriptive values stay in Ink. At most one middle dot per line.
  No exclamation marks, no emoji." Banned words: "seamless, powerful, unlock, empower, journey,
  insights, supercharge, effortless, all-in-one, welcome to." Write your concepts in this register.
  Flat, specific, unexcited, no adjectives doing work a noun could do.
- The named signature elements of the interface, so you know what the product's own visual language
  already contains: the **tracking gauge** (a hairline track with a Muted graduation at the NYSE
  close and an Accent tick for the token); the **live bar**, a 2dp Accent vertical bar that breathes
  for 2.4 s while data is live and goes static when it is not; the **64px monospace ticker**; the
  1px **blueprint grid** that holds the token's facts.

One licence you have, written down in the repository already: DESIGN.md section 9 currently says
"the mark is the tracking gauge, not a letter", and that sentence encodes a rejected answer as a
constraint. It is void for this attempt. Every other rule above is live, and if a concept needs to
break one, say which one and why the icon deserves an exception the interface does not.

## Technical constraints, hard

- **Android adaptive icon.** The art lives in a 108dp square viewport. The launcher shows only the
  central 72dp of it, and only the central **66dp circle** is guaranteed visible under every mask.
  Everything outside the 72 is thrown away before masking. So the idea has to be readable inside a
  circle, not inside a square.
- **The mask is not yours.** The launcher cuts a circle, a squircle or a rounded square out of your
  art, its choice, not yours. We measured the real one off the phone: a superellipse of exponent
  3.05. A concept that depends on a corner, on a square silhouette, or on the shape of its own
  outer edge is dead.
- **48dp in the drawer, 24dp in the status bar.** On the Seeker a drawer tile is 182 device pixels,
  so one 108-viewport unit is 2.53 px. A shape 6 viewport units thick is 4dp at 48dp, which is
  about the floor for anything that has to survive. A shape 2 units thick measures 1.3dp there and
  is simply not present; that is precisely how attempt one died.
- **A monochrome layer is mandatory.** Android themed icons redraw the mark as one flat colour on
  one flat plate, both supplied by the launcher, with no colour of your own. So the idea **cannot
  depend on colour to be legible**. Accent versus Ink is a distinction that disappears. If two
  elements of your mark are told apart only by being different colours, the mark has no monochrome
  layer and cannot ship. Elements have to be told apart by size, position, direction or by a real
  hole.
- **Negative space is a shape and it has a floor too.** A 4-unit gap closes up at tile size exactly
  the way a 1.3dp tick vanishes. Six units minimum for a gap, a seam, a ring opening or a void.
- The tile ground is ours to choose. Two treatments are both one line of XML: **ground**, where the
  tile stays Canvas near-black and the mark sits on it, or **inverted**, where the tile is a solid
  Accent field and the mark is knocked out of it in Canvas. Neither is privileged. Say which one
  your concept wants and why.

## The three attempts that were rejected, and my diagnosis

You are the fourth attempt. Three have been rejected outright by the founder, two of them within
seconds of seeing them on the actual phone. Here is what each was and what I think went wrong.
Argue with the diagnosis if you think it is wrong; it is a hypothesis, not a finding.

### Attempt one, rejected: the letter

A JetBrains Mono capital **P**, the product's initial, set in a tile, with a 2-unit Accent tick
under it. The tick measured 1.3dp at drawer size and was not there at all, so the mark was a letter
in a box. It was judged on the Android App info screen, where the icon is drawn large, and it
looked fine there and looked like nothing in the drawer.

### Attempt two, rejected: the gauge

The app's own tracking gauge, promoted to a brand mark. Three axis-aligned rectangles in the 108
viewport: an Ink **track** 56 wide and 8 high across the middle, an Ink **reference graduation** 8
by 14 hanging under its centre where the NYSE close sits, and an Accent **token tick** 10 by 36
standing over the track and crossing it. Weighted so nothing was thinner than 4dp at 48dp. The
first drawing of it put all three rectangles on the same centreline, which flattened into a **plus
sign** in the monochrome layer, so the ticks were made to point opposite ways and a rule was
written into the generator to refuse any mark symmetric about its own middle. The founder's verdict
on the phone was that it is "not much better".

### Attempt three, rejected: eight ideas from primitives

Eight tiles, each one a different abstract claim, each drawn only from axis-aligned rectangles, one
circle, two rectilinear paths, and knockouts. They were, in words:

1. **One to one.** An Accent field with two Canvas knockouts of equal size side by side, the left
   one solid, the right one an open frame with a small Accent block inside it. "The claim set beside
   the thing it claims, at the same measure."
2. **The wrapper and what is in it.** A large Ink square ring on Canvas, 54 units across, with a
   smaller Accent square inside its opening, undersized and pushed off centre. "A shell around a
   real asset, and the contents do not fill the claim."
3. **Someone else's reach.** A full Accent field with one large Canvas square bitten out of the top
   right corner, running off the edge. "A third party can move or freeze what you hold."
4. **The blank it will not fill.** A full Accent field with a single tall Canvas void, 24 by 56,
   standing in the middle of it. "Below the liquidity floor the app prints no number, and this is
   that refusal."
5. **The split.** Two unequal vertical blocks on Canvas, a larger Ink one and a smaller Accent one
   slid along the seam between them. "One holding cut into two unequal parts."
6. **Before.** An Ink square behind and an Accent square in front, offset diagonally, separated by a
   7-unit Canvas knockout. "What the token says, placed in front of what the company says."
7. **The fit.** An Accent square and an Ink square meeting along a seam, with a rectangular tenon on
   one interlocking into the other. "The token either fits the share exactly or it does not."
8. **Different shapes, same measure.** An Accent square with a Canvas disc inscribed in it, tangent
   on all four sides, so the four leftover corners are the whole mark. "A token and a share are not
   the same object, and the corners are the difference."

### My diagnosis

**One.** The founder's own hypothesis is that all three were geometric abstractions generated from
primitives with no specific idea behind them, so eight variants were really one non-idea eight
times. That is close but not quite it, and the difference matters. Attempt three did have a
specific idea per tile, written down. The failure is that every idea was an **abstract relation**
(containment, reach, absence, division, precedence, fit, equivalence) and every drawing was that
relation between **blank rectangles**. A relation between blanks is the same picture no matter which
relation you meant. Nothing in any of the eight is made of anything the product is made of: not a
number, not a ticker, not a price, not a mint, not a share, not a filing, not a reading. So yes, one
non-idea eight times, but by a specific mechanism: the ideas were never given a referent.

**Two, and this is the part the founder's hypothesis misses.** The abstractions did not land on
nothing. They landed on something worse, which is **Android's existing icon vocabulary**. Looked at
in the real drawer beside real neighbours, number 1 is the split-screen button, number 2 is a stop
button or a picture frame, number 4 is a battery, number 6 is the universal duplicate-windows
glyph, number 7 is a jigsaw piece, which is the browser-extension icon, and number 8 is the shape
tool. A blank geometric composition does not read as the meaning its author assigned to it. It
reads as whichever system glyph already owns that geometry. There is no free geometry left at this
level of abstraction.

**Three.** All three attempts changed the argument and never changed the method. The method, all
three times, was: write the constraints down as assertions (minimum stroke, safe circle, no mirror
symmetry, minimum gap), then search for rectangles that satisfy them, then ship whatever passes and
write a caption for it afterwards. That is a search over the space of shapes that pass a lint,
which is almost exactly the space of shapes with no character. A constraint set can tell you a mark
is not broken. It cannot tell you a mark is anything.

**Four.** Attempt two was too thin, so attempt three made mass the fix. But mass without a referent
is just a large blank. Numbers 3, 4, 7 and 8 are big and confident and say nothing, and they are no
better for being big. "Too thin" was diagnosed correctly and then over-corrected into a size rule,
which let everyone skip the question of what the thing depicts for a third time.

**Five.** The product's single most distinctive asset has never been drawn. It is not the gauge.
It is the **withheld number**: an app that will not print a figure when the pool holds $80, on a
shelf where every neighbour exists to print a figure. Attempt three's number 4 gestured at it and
drew it as a void in a field, which is a battery. And the product's second most distinctive quality
is that it is a **reading instrument, text-first, monospace, in a drawer full of mascots**. Neither
of those has had a serious drawing yet.

## What I want from you

Six to ten **concepts**. One paragraph each. For each one, say:

1. **What the mark is.** Describable in one or two sentences to someone who cannot see it. If it
   takes longer than that, it is not an icon.
2. **What idea it carries about this specific product.** Not about finance, not about crypto, not
   about clarity in general. About this app: the token read before the swap, the issuer's hand on
   your asset, the number that is not printed, the $80 pool behind the 152% quote, the reading
   instrument that adds no verdict.
3. **Why it survives the monochrome layer**, where Accent and Ink become the same colour, and **why
   it survives 48dp**, where a 2-unit detail does not exist.
4. **How it differs from the crypto-icon norm** it will sit beside, and from the Android system
   glyph whose geometry it is closest to. Name that system glyph explicitly, and say why your mark
   will not be mistaken for it. This is the test attempt three failed and nobody applied.

And then: **every concept must be defensible as belonging to THIS product and no other.** The test
is blunt. If the concept would work equally well for a portfolio tracker, a price alert app, a
wallet, a DEX, or a general "research" tool, it is out. If you cannot name the specific thing this
app does that the mark is about, it is out.

**Explicitly rejected, do not propose any of these:** generic charts of any kind; candlesticks;
upward arrows or any arrow implying direction of price; coins, tokens drawn as discs, or anything
that reads as currency; shields, locks, checkmarks or any trust-badge language; magnifying glasses,
over a chart or over anything; eyes; the letter P alone, or any single letter in a tile; a
wordmark; gradients; anything purple; anything with a soft or organic outline; a mascot.

Also: I do not want eight weights of one idea again. Two concepts that differ only in treatment or
proportion count as one. I would rather have six genuinely different claims about what this product
is than ten variations.

Finally, **rank your own concepts**, from the one you would ship to the one you would drop, and say
which single one you would ship and why. Say what the ranking is on: what would actually make a
person's eye stop on it in a drawer for half a second, not what argues best on paper. Attempt one
and attempt two both won every argument on paper and both looked weak on the phone, and the founder
was right both times. If your top pick has a weakness, name it.

Remember: **do not run any commands.** Reply in prose.

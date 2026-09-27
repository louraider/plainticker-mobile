# PlainTicker Mobile: deck

Ten slides. Written 2026-09-27 and checked against build 1.3.18, the next release, and the number set in
`docs/submission-answers.md` ("The one number set"). The Align coach reads the deck's text, so each
slide's text carries its point without the speaker notes, and nothing on a slide talks to the
reader about scoring or judging.

**How it looks.** The app's own Amber system, so the deck and the phone match: ground `#16130D`,
raised surface `#221E15`, text `#F5EEDD` and `#C6BCA4`, one amber `#FFC247` for figures and the
single highlight per slide, `#FF6B57` only for an issuer control. Bricolage Grotesque, with tabular
figures on numbers. Screenshots from `docs/img/`, full-bleed on the right, never in a phone mockup.
No purple, no gradients, no stock photos, no icons in coloured circles, no exclamation marks, no
buy or sell verbs.

---

## 1 · The problem, and who has it

**Slide text**

> **Your wallet can swap into a tokenized stock. It tells you a ticker and a price.**
>
> Seeker owners can hold Apple, Tesla or Nvidia as xStocks on Solana. Nothing on the phone says
> what the token is: whether the issuer can move it out of your wallet, whether it is backed,
> whether the price has any depth behind it, or where the company stands against its sector.
>
> PlainTicker reads all of that on the phone, before you hold the token.

*Screenshot: `docs/img/03-stock-page-aapl.png`.*

**Speaker notes.** Start from the gap, not the product. AAPLx's mint carries a permanent
delegate: its issuer can move the token without the holder's signature. That is
public on chain and no swap screen shows it. No swap screen shows the proof of reserves xStocks
reports either, or how thin some pools are. PlainTicker is the reading step before the swap.

---

## 2 · Who it is for

**Slide text**

> **Seeker owners who hold, or are about to hold, xStocks.**
>
> - They already have the phone, the Seed Vault and USDC.
> - They can already swap into 1,124 tokenized stocks on Solana.
> - They have no analysis for any of them on the device.
>
> A plain read, made for someone who is not a professional investor.

**Speaker notes.** The wedge is narrow on purpose: people who already hold the hardware, the wallet
and the asset, and are missing only the reading. That is also why this belongs on the Seeker and
not in a browser. Say "a classification by a fixed rule, not advice" once here, and keep it.

---

## 3 · A day with the app

**Slide text**

> 1. **Morning:** one digest notification on the stocks you watch.
> 2. **Today:** the NYSE state in your own time, your watched stocks, the reports due next week.
> 3. **A stock page:** issuer controls and supply read live from the mint, reserves as xStocks
>    reports them, the company against its sector, the token price against the NYSE close.
> 4. **A swap:** Jupiter routes it, the Seed Vault signs it, *Swap landed*, a receipt with the
>    executed fill plus estimated network costs. **Swap to USDC** for the way back.
> 5. **Vote:** staked SKR chooses the next stock to be analysed.
> 6. **You:** Pro, Google sign-in, one account with plainticker.com. The wallet connects to sign.

*Screenshots: `docs/img/01-today.png`, `docs/img/04-daily-digest.png`.*

**Speaker notes.** This is the video's order. The habit is Today and the digest; the swap is the
occasional action; the vote is the weekly one. The daily loop is small by design: one notification
a day and nothing else.

---

## 4 · Built for the Seeker

**Slide text**

> - **Mobile Wallet Adapter 2.2 and the Seed Vault** sign every swap, vote and Pro pass. The app
>   holds no wallet key.
> - **Verified as www.plainticker.com.** The release certificate is published in the site's
>   `assetlinks.json`, so the wallet names the app before anything is approved.
> - **Token-2022 read on the device.** Mint extensions and Scaled UI Amount balances, parsed in
>   Kotlin, with the slot and its age on screen.
> - **Native Kotlin and Jetpack Compose.** Five tabs, dark and light themes, about 1,850 unit tests.
>   Not a port, not a web wrapper.

**Speaker notes.** The analysis engine predates the hackathon and is the backend. Everything on
the phone is new: 405 commits since 10 September. The web product has no chain read, no swap and
no notification. The verified identity is the one line a Seeker owner reads before
approving: it says www.plainticker.com with a verification mark.

---

## 5 · Staked SKR decides what gets analysed next

**Slide text**

> **Coverage is the scarce thing.** 57 companies covered, 1,124 xStocks on Solana.
>
> Every week, Seeker owners vote for one of the 898 uncovered US xStocks. A vote is a transaction
> with a `PT-VOTE` memo, weighted by staked SKR read on chain by the server. The winner is
> analysed.
>
> **Round 1 closed the loop:** JEF won, the vote landed on mainnet, and Jefferies was analysed on
> 26 September.
>
> Every counted vote is public with its weight: `/api/v1/vote/rounds/{id}/ledger`.
>
> 7,500 SKR staked also opens Pro.

**Speaker notes.** Honest numbers: round 1 had one voter, the founder's demo wallet, with 878.98
SKR. The ledger publishes each vote's weight with the time and slot the server read the stake at
tally time; standard RPC cannot re-read a past balance, so the weight is published, not
re-derivable later. The mechanism is proven end to end on mainnet; participation is what launch has to build. The
tab says in plain words that a stake-weighted vote is decided by the largest stake, and the weight
is stored raw so a one-Seeker-one-voice rule can be applied in the tally alone.

---

## 6 · Security: every transaction is decoded before the wallet opens

**Slide text**

> - **TransactionGuard** reads the top level of every swap, vote and pass and compares it with
>   what the screen shows and with addresses pinned in the app. A transaction whose instructions
>   do more than the screen shows, or that the guard cannot parse, is refused.
> - Swaps: exactly one Jupiter instruction of a known layout; tokens must leave from and land in
>   the wallet's own accounts; token accounts opened only for the wallet; slippage at most 300
>   bps and Jupiter's platform fee at most 400 bps; no authority handed to anyone else. A pass
>   can never exceed 12 USDC.
> - **The app and server never hold a user's wallet signing keys.** No secrets in the APK. RPC
>   goes through a read-only five-method forwarder.
> - **What it cannot see:** Jupiter's routing inside its own program, and swap accounts loaded
>   from a lookup table. Stated in the README, with what stands in for each.

**Speaker notes.** The screen's figures come from JSON sent beside the bytes, so without the guard
a compromised server could show "12 USDC to the treasury" over a transaction that drains an
account. The guard closes that gap, and its limits are written down rather than implied. A
redaction guard hashes every base58 string in the tree and the whole history against a denylist.

---

## 7 · Traction and proof

**Slide text**

> **On mainnet, from the app on a Seeker:**
>
> | | |
> |---|---|
> | 13 Sep | Swap, 5 USDC into TSLAx |
> | 19 Sep | Vote for JEF, the round-1 winner |
> | 20 Sep | Pro pass, 12 USDC |
> | 24 Sep | Swap, 1 USDC into TSLAx |
> | 24 Sep | Swap to USDC, the whole position |
>
> 405 commits · 65 pull requests · about 1,850 unit tests · 57 companies covered
>
> **Usage so far is the founder's.** Round 3 runs 28 Sep to 5 Oct: [ROUND3_VOTERS] wallets have
> voted.
>
> *(Placeholder for the founder: replace `[ROUND3_VOTERS]` with the count of distinct voters in
> `GET /api/v1/vote/rounds/3/ledger` on the day the deck is exported, and say zero if it is zero.
> Delete this line before export.)*

*Screenshot: `docs/img/05-recent-swaps.png`.*

**Speaker notes.** Every signature is in the README and checkable on Solscan; the demo wallet is
public. Rounds 1 and 2 each have one vote, both from the demo wallet. There is no
user traction beyond that yet, and the slide says so. What exists is proof that each flow
works on mainnet, on the device, including the reverse swap and the paid pass.

---

## 8 · Business model

**Slide text**

> **Free:** every stock page, the chain facts, Today, the digest, the vote, and AAPL in full.
>
> **Pro** opens every figure on every covered stock:
>
> - **12 USDC for 30 days**, paid from the wallet, or
> - **7,500 SKR staked**, for as long as the stake stays.
>
> One account and one Pro plan with plainticker.com.

**Speaker notes.** The stake threshold was set against measured staker data: of 46,436 SKR stakers
the median stake is about 6,719 SKR and 36.6% stake more than 10,000, so 7,500 sits just above the
median. The pass is a plain transfer to a treasury address pinned in the app, and one has already
been paid on mainnet.

---

## 9 · Go-to-market

**Slide text**

> **Wedge:** Seeker owners holding xStocks.
>
> **Channels, planned:**
> 1. The Solana dApp Store: first submission targeted for 1 October.
> 2. SKR stakers as the vote community: 46,436 wallets stake SKR; each weekly round ends in a
>    public result and a public ledger. So far each round has had one voter, the founder.
> 3. plainticker.com: the same engine on the web, a shared account and Pro.
>
> **First 30 days:** listing live and a weekly round post · one-Seeker-one-voice weighting ·
> Ukrainian in the app · first usage figures published.

**Speaker notes.** The weekly round is the distribution loop: each Monday a round closes, the
winner is analysed, and the result is something stakers can point to. Usage figures will be
counted from the server's request log and on-chain memos, with no tracking SDK. The dApp Store
publisher account and KYC are already cleared.

---

## 10 · Roadmap and team

**Slide text**

> **Next:**
> - **One Seeker, one voice:** weight votes by the Seeker Genesis Token beside stake.
> - **A vote weight snapshot** taken when each round opens.
> - **Ukrainian localisation** in the app; the engine already writes Ukrainian.
> - **More coverage,** led by the vote.
>
> **Team:** [FOUNDER_NAME], solo founder. Built PlainTicker's analysis engine before the hackathon
> and this app from 10 September 2026. GitHub: louraider.

**Speaker notes.** Close on the loop rather than the feature list: the Seeker is where the wallet,
the stake and the reading meet, and staked SKR decides what gets read next. Keep the team line to
what is true and checkable. `[FOUNDER_NAME]` is a placeholder the founder fills in before
export; nothing else on the slide names a person.

# PlainTicker Mobile: dApp Store listing text

The text for the portal's "Add your dApp details" form, field by field. The research and the
publishing checklist behind it are in `docs/dapp-store-publishing.md`. The image files named below
are not committed: the icon and banner are rendered from the shipped launcher drawable with
`design/brand/render_icons.py` (`flat_tile`, `mark_block`, the bundled Bricolage Grotesque), and the
screenshots come from the release build on the Seeker.

For the Solana dApp Publisher Portal form "Add your dApp details". Release 1.3.27-amber, APK
https://github.com/louraider/plainticker-mobile/releases/latest/download/plainticker.apk.
Written 2026-10-02. Character counts include spaces. Every field below follows the copy rules:
no buy or sell verbs, no exclamation marks, no emoji, no dashes used as punctuation.

## App Name (limit about 30)

```
PlainTicker
```

11 characters. The same name as the launcher, the package and the Seed Vault Connect sheet.

## Package Name

```
com.plainticker.mobile
```

22 characters. Read from the APK by the portal; it is the app's permanent identity.

## Subtitle (max 50)

| # | Subtitle | Chars |
| --- | --- | --- |
| 1 | `Tokenized US stocks, read before you swap` | 41 |
| 2 | `Read any tokenized stock in plain words` | 39 |
| 3 | `Issuer controls and filings for every xStock` | 44 |

**Recommendation: option 1.** It says what the app covers (tokenized US stocks) to someone who has
never heard the word xStock, and it keeps the order the product insists on, read first and swap
second, which is the same promise as the banner line "Read the xStock, then swap". Option 2 is the
softer beginner line and matches the first screenshot caption; option 3 is precise but reads as a
feature list.

## Description

```
PlainTicker reads tokenized US stocks on Solana in plain words. A fixed rule classifies each company against its sector peers from its SEC filings: quality, valuation, momentum and the F-Score. The issuer's controls, such as the permanent delegate, paused transfers and the split multiplier, are read live from the Token-2022 mint every time a stock page opens. Swaps are routed by Jupiter, and the phone decodes every transaction and checks it against the screen before Seed Vault signs. Staked SKR votes on the next stock to analyse. Pro opens every figure: a 12 USDC pass for 30 days, 7,500 SKR staked, or a promo code. xStocks are issued by Backed Finance and are not available to US persons. Not investment advice.
```

719 characters. If the form also has a long description, the full text is in
`docs/dapp-store-publishing.md`, section "Long description".

## Images

| Field | File | Spec |
| --- | --- | --- |
| dApp Icon | `icon-512.png` | 512 x 512 PNG, mode RGB (no alpha), the shipped amber launcher tile flattened, square: the store applies its own mask |
| Banner | `banner-1200x600.png` | 1200 x 600 PNG, RGB, amber ground, mark plus wordmark and one line in the bundled Bricolage Grotesque; everything sits above y 330 so the lower band where the icon overlaps is empty amber |
| Screenshots | `screenshot-*.png` | Portrait 9:16, 1080 x 1920, each under 3 MB (see checklist.md for status) |

## Language

```
English
```

## Countries

Choose **all countries, with exclusions**. Recommended exclusion list, from the issuer's own page
(Backed Finance, "Restricted Countries", https://assets.backed.fi/legal-documentation/restricted-countries,
read 2026-10-02) plus its UK notice (https://assets.backed.fi/legal-documentation: "NOT available
for UK Clients"):

- **Prohibited by the issuer (sanctions):** Iran, North Korea, Syria.
- **Not offered to US persons or people in the US** (securities registration): United States. See
  the founder decision below before ticking it.
- **Not available for UK clients:** United Kingdom.
- **Not serviced by the issuer:** Afghanistan, Belarus, Central African Republic, Democratic
  Republic of the Congo, Cuba, Ethiopia, Haiti, Iraq, Lebanon, Libya, Mali, Mozambique, Myanmar,
  Nicaragua, Nigeria, Philippines, Russia, Somalia, South Sudan, Sudan, Venezuela, Yemen,
  Zimbabwe. "Occupied regions of Ukraine" is on the list too; the store picks countries, not
  regions, so it cannot be excluded on its own and Ukraine as a whole stays listed.

That is 27 countries with the US, 26 without. Distributors add more: Kraken's xStocks pages also
exclude Canada and Australia, and some third party summaries list the EU/EEA. Those are
distributor rules, not the issuer's, and are not on the recommended list.

**Founder decision on the United States.** The issuer forbids offering xStocks to US persons and
the app already asks for that self-certification on its first screen. Excluding the US is the
conservative reading and fits the dApp Store Terms of Use clause on promoting transactions in
securities (5.4.16). The cost: a Seeker set to the US will not show the listing, which includes
most Solana Mobile staff and probably the hackathon judges. Hackathon Terms 9.3 only require the
app to be "publicly available on the Solana dApp Store". Ask Solana Mobile (publishersupport) or
the hackathon organiser before choosing; if in doubt, exclude the US.

## App Website

```
https://www.plainticker.com
```

Returns 200 (checked 2026-10-02).

## Support Email and Contact Email

```
hi@plainticker.com
```

The only address on both web legal pages: the terms page names it 10 times and the privacy page 12
times, and no other address appears on either (checked 2026-10-02).

## Terms URL

```
https://www.plainticker.com/en/terms
```

Returns 200 (checked 2026-10-02).

## Privacy URL

```
https://www.plainticker.com/en/privacy
```

Returns 200 (checked 2026-10-02). Account deletion: https://www.plainticker.com/en/account#delete.

## Editor's Choice

Interest: **yes**. Pitch, 3 sentences, 439 characters:

```
PlainTicker is built around the Seeker: the phone decodes every Jupiter swap and refuses one that does more than the screen shows, and only then does Seed Vault sign. Staked SKR gets a job beyond holding, a weekly vote on which tokenized stock is analysed next, and 7,500 SKR staked opens Pro. It is the plain words layer tokenized stocks are missing, with each issuer's on chain controls read live from the mint instead of taken on trust.
```

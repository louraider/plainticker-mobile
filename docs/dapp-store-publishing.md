# Solana dApp Store publishing checklist (hackathon winner, 30-day clause)

Researched 2026-09-11 against docs.solanamobile.com, legal.solanamobile.com, the solana-mobile/dapp-publishing GitHub repo, and the Clock In hackathon Terms (clock-in-terms.pdf). Quotes are verbatim from the cited page. Where a number is not published, it says "not published" with the page that was checked.

## Summary

The publishing flow described in older guides (a local `dapp-store init` / `create publisher` / `create app` / `create release` / `publish submit` sequence that minted a Publisher NFT from your own keypair) is retired. The current README of solana-mobile/dapp-publishing states: "The legacy config-driven `init`, `create`, `validate`, and direct `publish submit|update|remove|support` flows are no longer part of the active CLI surface." Publishing now goes through the Solana dApp Publisher Portal at https://publish.solanamobile.com: you sign up, "Fill out your publisher profile and submit your KYC/KYB verification", connect a browser-extension wallet holding "sufficient SOL (~0.2 SOL) to cover transaction fees and ArDrive upload costs", create the app record (this mints the App NFT), and submit a release (this uploads assets to Arweave via ArDrive and mints the Release NFT). The `@solana-mobile/dapp-store-cli` package still exists but is now a portal-backed tool for pushing new versions with an API key. There is no separate publisher-NFT mint step for you to run; the publisher identity is the portal account plus the connected wallet, and the docs warn: "Your publisher wallet is required for all future submissions of this app. Do not lose access to it or you will not be able to make new submissions of this app." Review takes "3-5 business days", and the hackathon Terms require the app to be "published, listed, and publicly available on the Solana dApp Store no later than thirty (30) calendar days after the date on which the winners are first publicly announced", with the explicit statement that "Merely submitting an application for store review does not constitute publication." The correct Week 1 action is therefore: create the portal account, clear KYC/KYB, fund and back up the publisher wallet, and create the app record, so that only the release submission and the review wait remain after winners are announced.

## Progress (updated 2026-09-13)

| Step | State |
|---|---|
| 3. Portal account and KYC/KYB | **Done 2026-09-12**, six days ahead of the Sep 18 internal deadline. The unknown that worried this document most (provider and turnaround) is closed. |
| 1, 2. Publisher wallet chosen, funded ~0.2 SOL, backed up | Open. The wallet funded on 2026-09-12 is the **demo** wallet for the video and fixtures, not the publisher wallet (founder's decision the same day). |
| 4. Storage provider and ArDrive balance | Open, needs the APK size. |
| 5. App record and App NFT | **Still blocked, see below**, but on one thing now instead of two. Do not create it yet. |
| 6. Release signing key | Done in T6: env-driven signing, keystore off-machine, `docs/release-signing.md`. |

### Why step 5 is blocked

The portal reads the Android package name from the APK and that name is the app's permanent identity: changing it later means a new app record, a new App NFT and a lost listing. Two things had to land before the record is created. One has.

1. ~~The package is still `com.myapp`.~~ **Done 2026-09-13** on `chore/package-rename`. `applicationId` and `namespace` are `com.plainticker.mobile`; the Kotlin main, test and androidTest trees moved from `com/myapp` to `com/plainticker/mobile` with `git mv`, so history follows each of the 161 files; every reference that was a string rather than a symbol moved with them. The gate is unchanged: 605 unit tests green, the same count as `main`. The debug APK installs, launches and runs on the Seeker under the new name. What the portal will read out of the APK is now final.
2. **`assetlinks.json` still does not exist.** Plan task T3 is ticked and names it, but there is no `.well-known/assetlinks.json` in the server repo and `https://www.plainticker.com/.well-known/assetlinks.json` returns 404 (checked 2026-09-12). It also has no Android App Links intent filter to pair with in `AndroidManifest.xml`. The file must carry the package name, which now exists, plus the SHA-256 of the release signing certificate, which does not: `scripts/make-keystore.sh` has never been run and no signing environment variable is set, so a release build would produce `app-release-unsigned.apk` and there would be no certificate to fingerprint. The keystore is the founder's to create, because the password custody and the off-machine backup are theirs (`docs/release-signing.md`). Until it exists there is nothing honest to write into `sha256_cert_fingerprints`, and an `autoVerify` intent filter added ahead of the file would fail verification on every install, so the rename added neither.

Order, with the first step done: ~~rename the package~~, create the release keystore and back it up, cut a tag so the release workflow prints the SHA-256 of the certificate it actually signed with, publish `assetlinks.json` on plainticker.com, add the App Links intent filter, verify it resolves, then create the app record in the portal.

### Decisions taken 2026-09-12

- **Package name: `com.plainticker.mobile`.** Reverse domain of plainticker.com plus the client, matching the repository name and leaving `com.plainticker.*` free for anything later. It is permanent once the App NFT exists.
- **`assetlinks.json` ships with the rename**, not separately: rename, release APK, certificate fingerprint, the file on plainticker.com, then the App Links intent filter in the manifest. **Amended 2026-09-13:** it did not, and could not. The rename landed alone because the release keystore does not exist yet, so there is no certificate fingerprint to write and no signed release APK to read one from. `assetlinks.json` and the intent filter now hang off the keystore rather than off the rename.
- **No portal actions until the app is finished locally on the Seeker.** KYC is cleared and that is where the portal work stops for now; the app record waits until the device walkthrough is done.

### Decisions taken 2026-09-13

- **The identity a wallet shows a person is `PlainTicker` over `https://www.plainticker.com`.** Both were Android Studio template values (`Myapp`, `https://yourdapp.com`) in `MainViewModel` until today. The app now builds exactly one `ConnectionIdentity`, in `MwaWalletSession.defaultAdapter()`, and the debug wallet spike shares it, so the Seed Vault approval prompt and `assetlinks.json` name the same host and cannot drift apart.
- **What the rename deliberately did not touch**, because it is user-visible state rather than the package, and changing it would silently reset something under an already installed app: the `watchlist-digest` notification channel id, the `plainticker` SharedPreferences file, and the `watchlist-daily` / `watchlist-now` unique work names. `rootProject.name` and the npm package name stay `myapp` as well; neither reaches the APK or the portal.

## Numbered checklist

### Week 1 (do now; replaces "mint the publisher NFT")

1. Choose the publisher wallet and back it up.
   - The portal requires a browser-extension wallet: "Connect a Solana wallet via a browser extension wallet (e.g Phantom, Solflare, Backpack), which will be your publisher wallet." (https://docs.solanamobile.com/dapp-store/submit-new-app)
   - Use a dedicated wallet owned by the team, not a personal daily-use wallet. Store the seed phrase in at least two secure places. Warning from the same page: "Do not lose access to it or you will not be able to make new submissions of this app."
   - For CLI-driven updates later you also need "A signer keypair file (Solana CLI keypair)" (https://docs.solanamobile.com/dapp-store/publishing-cli). Generate or export it now and store it with the same care:
     ```bash
     solana-keygen new --outfile ./secrets/dapp-store-publisher.json --no-bip39-passphrase
     solana-keygen pubkey ./secrets/dapp-store-publisher.json
     ```
     Whether this must be the same key as the connected browser wallet is not stated on the CLI page (see Open questions). Safest assumption: import the same seed into the browser wallet so one key signs everything.

2. Fund the publisher wallet.
   - "Ensure the publisher wallet has sufficient SOL (~0.2 SOL) to cover transaction fees and ArDrive upload costs." (https://docs.solanamobile.com/dapp-store/submit-new-app)
   - Network: the docs do not name the cluster. The wallet pays real ArDrive storage and mints real Metaplex NFTs, which implies mainnet-beta SOL. Do not fund on devnet.
   - Per-NFT mint costs (App NFT, Release NFT) and priority-fee settings: not published (checked submit-new-app, publishing-cli, GitHub README). The portal signs and submits the transactions; there is no fee flag exposed in the current CLI docs.

3. Create the Publisher Portal account and submit KYC/KYB.
   - "Navigate to the Publisher Portal and sign up for an account." then "Fill out your publisher profile and submit your KYC/KYB verification." (https://docs.solanamobile.com/dapp-store/submit-new-app)
   - Gating: the Developer Agreement says Solana Mobile "may suspend or deny access to the dApp Publishing Platform pending completion of any required verification process" and that developers must "complete any identity or eligibility verification process required by Solana Mobile, including processes administered by third-party verification providers." (https://legal.solanamobile.com/developer-agreement-web, Last Updated Jun 30, 2026). Treat KYC/KYB as gating submission, not only listing.
   - Provider name and turnaround time: not published (checked submit-new-app, support, agreement, FAQ). This is the single largest unknown in the timeline, which is why it must start in Week 1.
   - Decide KYC (individual) vs KYB (entity) now. If the team has no legal entity, one named individual becomes the publisher of record.
   - Read and accept: Publisher Policy (https://legal.solanamobile.com/publisher-policy-web, Last Updated Jul 21, 2026) and Developer Agreement (above). Commission: "Solana Mobile charges a commission rate of 0.0% to Developer for any Developer Application."

4. Connect the wallet and set the storage provider.
   - "ArDrive is recommended for storage costs and ease of setup." "ArDrive uploads are paid from a storage balance, shown by the cost estimator next to the estimated cost." "AWS S3 uploads go to your own bucket and have no balance to top up." (https://docs.solanamobile.com/dapp-store/submit-new-app)
   - Top up the ArDrive balance once you know the APK size; the estimator gives the SOL figure. No fixed price is published.

5. Create the app record (this is the App NFT).
   - Portal: "Add a dApp" > "New dApp", then complete the submission form. The CLI page lists as a prerequisite an "app created in the Publisher Portal with App NFT minted", so the App NFT is created by the portal when you add the dApp, before any release exists. (https://docs.solanamobile.com/dapp-store/publishing-cli)
   - The Android package name is the identity key: "The portal reads the Android package name from the APK and matches it to the correct app under your publisher account." Freeze `applicationId` now; changing it later means a new app.
   - Publisher NFT: the on-chain spec still defines it ("Publisher NFTs" are the root Metaplex Certified Collection above App NFTs, https://github.com/solana-mobile/dapp-publishing/blob/main/publishing-spec/SPEC.md), but the CLI release v0.12.0 (2024-09-05) lists "Remove publisher nft" and the current portal docs never mention a publisher-NFT step. Whatever the portal creates on wallet connection is handled for you; there is nothing to run manually.

6. Prepare the release signing key (keep it forever).
   - "Store the keystore file and passwords securely - losing them means you cannot update your app. All future updates must be signed with the same key." (https://docs.solanamobile.com/dapp-store/build-and-sign-an-apk)
   - If you also ship on Google Play: "You cannot use the same signing key for both Google Play and the dApp Store." The dApp Store needs "a release APK file signed with a new, unique signing key (different from Google Play)". (https://docs.solanamobile.com/dapp-store/publishing-from-google-play)
   - Format: signed APK, not AAB. "Ensure you are submitting a release build of your app that is signed. Debug builds will not be accepted" (https://docs.solanamobile.com/dapp-publishing/prepare).
     ```bash
     keytool -genkeypair -v -keystore ./secrets/dappstore-release.keystore -alias dappstore -keyalg RSA -keysize 2048 -validity 10000
     ./gradlew assembleRelease
     ```
   - Back up the keystore and passwords with the wallet seed.

### Week 2 to submission (content and build)

7. Build the APK to the published rules.
   - `versionCode`: "must be incremented by one monotonically between each update" (https://docs.solanamobile.com/dapp-publishing/publishing-updates). "Every dApp Store update needs a higher `versionCode`." (build-and-sign-an-apk)
   - `versionName`: update it for each release; arbitrary but should be meaningful.
   - targetSdk / minSdk minimum, 64-bit ABI requirement, APK size cap: not published (checked build-and-sign-an-apk, prepare, publishing-cli, SPEC.md; the spec records `min_sdk` and `version_code` from the APK but sets no floor). Seeker ships with Android 15 per press coverage (secondary source, https://www.coindesk.com/tech/2025/08/06/solana-s-seeker-phone-fixes-saga-s-flaws-with-usability-upgrade), so targetSdk 35 and arm64-v8a included is the safe build.
   - Hackathon Terms 6.3: "The Project must produce a functional Android APK, integrate the Solana Mobile Stack and Mobile Wallet Adapter, and interact meaningfully with the Solana network."
   - Seed Vault: CLI v0.12.0 added "Show warnings for privileged seed vault permission"; do not request privileged Seed Vault permissions unless you need them.

8. Prepare listing assets to the Listing Page Guidelines (https://docs.solanamobile.com/dapp-store/listing-page-guidelines).
   - Icon: "Icon must be 512px by 512px dimensions."
   - Short description: "Short description cannot exceed 30 characters."
   - Long description: no published limit; "a well-written, concise overview".
   - Screenshots: "All images must be at least 1080px in width and height." "All images must have consistent orientation (landscape or portrait)" "All images must have equal aspect ratio." Minimum and maximum count: not published (the legacy example config.yaml shipped 4 screenshots; a third-party guide says "at least 4"; treat 4 or more as the working target).
   - Video (optional): "All videos must be `.mp4` video file format." "at least 720px in width and height. 1080p (1920px by 1080px) is strongly recommended."
   - Banner: 1024x500 was introduced in CLI v0.10.0 ("banner image functionality (1024x500 dimensions)", https://github.com/solana-mobile/dapp-publishing/releases); the portal form field size is not published. Prepare a 1024x500 PNG.
   - Category and age rating: not published in docs (checked listing-page-guidelines, submit-new-app, legacy example/config.yaml). Expect a dropdown in the portal form.
   - URLs the on-chain release metadata stores (SPEC.md): `license_url`, `copyright_url`, `privacy_policy_url`, publisher `name`, `website`, contact email, `support_email`. Have all of these live before submitting.

9. Meet the legal content requirements inside the app and listing.
   - Privacy policy: the Publisher Policy requires you to "Disclose the dApp's or its Developer's practices with respect to the collection, use, and disclosure of User Data in a privacy policy or other statement that complies with Applicable Law." The Developer Agreement requires you to "make available in an easily accessible location within each Developer Application a link to the applicable privacy policy and EULA and the text of such privacy policy and EULA."
   - Account deletion: "Provide a means or method for users to delete their account, and delete the User Data associated with such account (except to the extent you have lawful reasons, such as regulatory requirements or fraud prevention, to retain such data)." If the app has no accounts (wallet-only), state that in the privacy policy.
   - Financial services clause (Publisher Policy): "dApps that provide or purport to provide financial services subject to any regulation under Applicable Law must obtain and provide in connection with such Developer Assets all documentation required by Applicable Law." No exemption or carve-out language for wallets, DEX front-ends or trading apps is published. For an xStocks analysis plus Jupiter swap app: keep the listing copy descriptive (analysis, quotes, routing via Jupiter), avoid claiming to be a broker or to offer securities, and link your terms and risk disclosures.
   - dApp Store Terms of Use 5.4.16 and 5.4.17 (https://legal.solanamobile.com/en/dapp-store-tos, Last Updated Dec 3, 2025) prohibit using the store "to carry out any financial activities subject to registration or licensing, including but not limited to using the dApp Store to transact in or promote transactions in securities" and to "participate or promote participation in fundraising ... including ... assets that (i) are redeemable for financial instruments, (ii) give owner's rights to participate in an ICO or any securities offering, or (iii) entitle owners to financial rewards". These sit in the end-user Acceptable Use Policy ("You agree not to, directly or indirectly..."), but reviewers can apply the same logic to your listing; tokenized-stock exposure is the clause to watch.
   - US-persons or geo disclosure guidance: not published (checked Publisher Policy, Developer Agreement, ToU). The Developer Agreement only restricts use by persons "in, under the control of, or a national or resident of any jurisdiction subject to a U.S. trade embargo" or on sanctions lists. If xStocks contracts require a non-US disclosure, put it in the listing long description and in-app; the store provides no field for it.
   - Metadata honesty: content that "misleads users as to the true intent, nature, or purpose of any dApp" is prohibited; screenshots must show the real app.

### Submission

10. Submit the first release in the portal.
    - "Navigate to your app's Home menu and click the New Version button on the top-right", upload the APK, press "Submit". Then sign every prompt: "Ensure you approve each signing request and you do not skip any or else certain assets could be missing from your app submission." "These are required for uploading assets to Arweave and minting your release NFT." (https://docs.solanamobile.com/dapp-store/submit-new-app)
    - Provide "What's new" text (required for updates; write it for the first release too).

11. Wait for review and watch the inbox.
    - "Review results are sent to your developer email from `publishersupport@dappstore.solanamobile.com` within 3-5 business days." Apps are reviewed in submission order. "App goes live immediately upon approval under appropriate category."
    - If nothing after 5 business days: get the Developer role in `#developer` on https://discord.gg/solanamobile, then open an App Review Inquiry ticket in `#dev-answers`. (https://docs.solanamobile.com/dapp-store/support)
    - Common rejection reasons: not published as a list. Review criteria quoted by Blueshift's course (secondary): "The app functions as described", "No malware or malicious behavior", "Compliance with the Publisher Policy", "Screenshots accurately represent the app". Debug-signed APKs are rejected by rule.

12. Prove publication for the hackathon organizer.
    - Terms 9.3: "Merely submitting an application for store review does not constitute publication." Capture the live listing on a Seeker and the deep link `solanadappstore://details?id=<package_name>` (https://docs.solanamobile.com/dapp-store/link-to-dapp-listing-page). Send both to hackathon@radiant.nexus via the Team Representative.

### Updates (after launch)

13. Publish an update via the portal or the CLI.
    - Portal: same wallet, "Home" > "New Version"; APK must be "signed with the same Android signing key as the initial release" with `versionCode` incremented; fill "What's new". Detail edits "will only reflect in the dApp Store after a new release version is approved." (https://docs.solanamobile.com/dapp-store/submit-an-update)
    - CLI (Node 18 or newer, `engines: >=18` in packages/cli/package.json):
      ```bash
      npm install -g @solana-mobile/dapp-store-cli
      # create a key at https://publish.solanamobile.com/dashboard/settings/api-keys
      export DAPP_STORE_API_KEY=<your-api-key>
      dapp-store --apk-file ./app/build/outputs/apk/release/app-release.apk \
        --keypair ./secrets/dapp-store-publisher.json \
        --whats-new "Bug fixes and performance improvements"
      # or, from a hosted APK:
      dapp-store --apk-url https://example.com/app-release.apk --keypair ./secrets/dapp-store-publisher.json --whats-new "..."
      # CI: printf '%s' "$DAPP_STORE_API_KEY" | dapp-store --apk-file ... --keypair ... --whats-new "..."
      ```
      Documented flags are only `--apk-file`, `--apk-url`, `--keypair` (required), `--whats-new`. No RPC URL, build-tools path, storage or priority-fee flags exist in the current docs; "the backend handles Solana submissions".
    - Every update mints a new Release NFT; App NFT and publisher identity are reused. One publisher account can hold many apps ("Publishers can create & manage multiple app NFTs", SPEC.md).

### Legacy reference (only if you find old guides)

The old `config.yaml` (https://github.com/solana-mobile/dapp-publishing/blob/main/example/config.yaml) had `publisher` (name, website, email), `app` (name, android_package, license_url, copyright_url, privacy_policy_url, icon), `release` (media: icon, banner, featureGraphic, screenshot, video; files: apk; localized catalog: name, short_description, long_description, new_in_version, saga_features) and `solana_mobile_dapp_publisher_portal` (google_store_package, testing_instructions, alpha_testers with wallet addresses). Legacy commands `dapp-store init`, `create publisher`, `create app`, `create release`, `publish submit`, `publish update` with `-k`, `-u`, `-b`, `--requestor-is-authorized`, `--complies-with-solana-dapp-store-policies` are removed from the active CLI. Alpha testers and testing instructions in the portal: not published.

## Costs and timings

| Item | Value | Source |
| --- | --- | --- |
| Publisher wallet balance | "~0.2 SOL" for tx fees plus ArDrive uploads | https://docs.solanamobile.com/dapp-store/submit-new-app |
| Network for that SOL | Not named in docs; real ArDrive costs imply mainnet-beta | submit-new-app (checked) |
| App NFT / Release NFT mint cost | Not published; covered by the ~0.2 SOL | submit-new-app, publishing-cli (checked) |
| ArDrive storage cost | Size-based, shown by the portal cost estimator; no fixed price published | submit-new-app |
| Store commission | "commission rate of 0.0%"; "does not currently collect any fees on in-app purchases, app purchases, or subscriptions" | https://legal.solanamobile.com/developer-agreement-web ; https://docs.solanamobile.com/get-started/faq |
| KYC/KYB turnaround | Not published | submit-new-app, support, developer agreement (checked) |
| App review | "within 3-5 business days"; escalate after 5 business days | submit-new-app, submit-an-update, support |
| Update review | Same "3-5 business days" | submit-an-update |
| CLI Node version | `>=18` | packages/cli/package.json ; legacy README "Node version 18 or greater" |
| CLI install | `npm install -g @solana-mobile/dapp-store-cli` | https://docs.solanamobile.com/dapp-store/publishing-cli |
| Icon | 512 x 512 px | listing-page-guidelines |
| Short description | max 30 characters | listing-page-guidelines |
| Screenshots | at least 1080 px width and height, same orientation and aspect ratio; count not published | listing-page-guidelines |
| Video | .mp4, min 720 px, 1080p recommended | listing-page-guidelines |
| Banner | 1024 x 500 (CLI v0.10.0); portal field not published | GitHub releases |
| versionCode | +1 monotonically per release | https://docs.solanamobile.com/dapp-publishing/publishing-updates |
| Publication deadline | 30 calendar days after winners are first publicly announced | clock-in-terms.pdf section 9.3 |
| Hackathon finalist KYC | Sumsub (or designated provider), every roster member | clock-in-terms.pdf section 10.1 |

## Timeline reality check

- Earliest possible start: today. Portal signup, KYC/KYB submission, wallet funding and App NFT creation have no dependency on the hackathon result.
- Hackathon Terms 9.3 (verbatim): "each winning team must have its winning Android application published, listed, and publicly available on the Solana dApp Store no later than thirty (30) calendar days after the date on which the winners are first publicly announced by the Organizer. Merely submitting an application for store review does not constitute publication." Extensions: "The Organizer may grant a written extension in its sole discretion, including where appropriate for documented third-party store-review delays, but no extension is automatic."
- Terms 8.2 makes the prize conditional on 9.3 and on finalist KYC (section 10, Sumsub). Terms 9.2: teams with VC or angel funding are ineligible for USDC prizes.
- Official Clock In schedule: the site https://solanamobile.radiant.nexus/ is client-rendered and exposes no dates to a fetch; press coverage (2026-09-03, 2026-09-09) only says a "30-day window". Team notes record a submission deadline of 2026-10-08; verify on the Official Website. The winner announcement date is not published; the 2025 edition judged for about one month after close.
- Suggested internal deadlines:
  - 2026-09-18: portal account created, KYC/KYB submitted, wallet funded with 0.3 SOL (margin over the ~0.2 SOL guidance), seed and keystore backed up, App NFT created, privacy policy and account-deletion page live.
  - 2026-10-01: first release submitted for review with the hackathon build, so the listing can be live before the submission deadline. Publishing before winning is allowed and removes the 30-day risk entirely.
  - Fallback if you wait for the result: submit within 3 business days of the announcement. That leaves room for one rejection and one resubmission (2 x 5 business days, about 14 calendar days) inside the 30-day window.

## Open questions

1. KYC/KYB provider and turnaround for the Publisher Portal: not published anywhere checked. Ask in Discord `#dev-answers` after signup.
2. Whether the CLI `--keypair` must be the same key as the connected publisher wallet, or any wallet authorized on the account: not stated on publishing-cli. Plan for the same key.
3. Whether the portal still mints a Publisher NFT on wallet connection (SPEC.md defines it; CLI v0.12.0 removed it; portal docs are silent). Not needed for the checklist but relevant to wallet-ownership implications.
4. Screenshot minimum and maximum count, banner size in the portal form, category list, age-rating options, alpha-tester support: not published.
5. targetSdk / minSdk floor, 64-bit requirement, APK size cap: not published.
6. Exact per-transaction costs for App NFT and Release NFT mints and whether the portal sets priority fees: not published.
7. How Jupiter Mobile and Phantom satisfy the financial-services clause: no exemption language is published and there is no public web catalog to inspect; check their listings on a Seeker for disclosure wording.
8. Whether a non-US disclosure for xStocks needs to appear in the listing: no guidance published; decide with the xStocks terms.
9. Clock In winner announcement date and whether the "prize designated by the Organizer as subject to publication" covers all prize tiers: check the Official Website and confirm with hackathon@radiant.nexus.

## Sources

- https://docs.solanamobile.com/dapp-store/submit-new-app
- https://docs.solanamobile.com/dapp-store/submit-an-update
- https://docs.solanamobile.com/dapp-store/publishing-cli
- https://docs.solanamobile.com/dapp-store/build-and-sign-an-apk
- https://docs.solanamobile.com/dapp-store/publishing-from-google-play
- https://docs.solanamobile.com/dapp-store/listing-page-guidelines
- https://docs.solanamobile.com/dapp-store/link-to-dapp-listing-page
- https://docs.solanamobile.com/dapp-store/support
- https://docs.solanamobile.com/dapp-store/intro
- https://docs.solanamobile.com/dapp-publishing/prepare
- https://docs.solanamobile.com/dapp-publishing/publishing-updates
- https://docs.solanamobile.com/get-started/faq
- https://docs.solanamobile.com/llms.txt (docs index)
- https://legal.solanamobile.com/publisher-policy-web (Publisher Policy, Last Updated Jul 21, 2026)
- https://legal.solanamobile.com/developer-agreement-web (Developer Agreement, Last Updated Jun 30, 2026)
- https://legal.solanamobile.com/en/dapp-store-tos (dApp Store Terms of Use, Last Updated Dec 3, 2025)
- https://github.com/solana-mobile/dapp-publishing (README, current CLI)
- https://github.com/solana-mobile/dapp-publishing/blob/main/packages/cli/package.json
- https://github.com/solana-mobile/dapp-publishing/blob/main/publishing-spec/SPEC.md
- https://github.com/solana-mobile/dapp-publishing/blob/main/example/config.yaml (legacy)
- https://github.com/solana-mobile/dapp-publishing/releases (v0.10.0 banner 1024x500; v0.12.0 "Remove publisher nft")
- https://publish.solanamobile.com (Publisher Portal)
- https://solanamobile.radiant.nexus/legal/clock-in-terms.pdf (Terms sections 6, 8.2, 9, 10)
- https://cryptobriefing.com/solana-mobile-clock-in-hackathon-135000-prizes/ (2026-09-03, prizes)
- https://coinfomania.com/solanas-clock-in-hackathon-launches-promising-135k/ (2026-09-09, "30-day window")
- https://learn.blueshift.gg/en/courses/dapp-store-publishing/solana-dapp-store (secondary, review criteria)
- https://hackmd.io/@manbankat/HJGk-mT2-x (secondary, portal form fields)
- https://www.coindesk.com/tech/2025/08/06/solana-s-seeker-phone-fixes-saga-s-flaws-with-usability-upgrade (secondary, Seeker Android 15)

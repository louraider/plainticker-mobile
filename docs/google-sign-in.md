# Sign in with Google

You's Account section signs a person in with Google, links this device to the account they
already have on plainticker.com, and so makes that account's Pro count in the app. The server half
is the web repo's `POST /api/v1/auth/google` (PR #140, `server/auth/README.md`, section 1).

## The flow

1. The reader taps **Sign in with Google** in You, under Account.
2. `AccountViewModel` asks `GoogleAuthApi.fetchNonce()` for a server nonce before opening
   Google's sheet at all. Any failure — a 404 from a server that predates the route, a network
   error, or anything else, a blank or unparsable answer included — falls back silently to a
   fresh local nonce (`SignInNonce`, 256 random bits, base64url), the only kind this app ever
   sent before this endpoint existed.
3. `CredentialManagerGoogleSource` asks Android Credential Manager for a
   `GetSignInWithGoogleOption` with `serverClientId` set to the **web** OAuth client id
   (`BuildConfig.GOOGLE_SERVER_CLIENT_ID`) and that nonce (the server's, or the local fallback).
   Google shows its own sheet.
4. The ID token that comes back must carry the same `nonce` claim. If it does not, it is dropped
   and never sent.
5. The token goes to `POST https://www.plainticker.com/api/v1/auth/google` as
   `{ "idToken", "nonce" }`, with the device code in the `X-PT-Code` header, the same header and
   base URL every other `/api/v1` call in the app uses (`GoogleAuthApi`). `nonce` is the server's
   value from step 2; on a local fallback the field is left out entirely, so a server that has
   not deployed nonce checking yet sees exactly the one-field body it always has.
6. The server verifies the token, finds or creates the shared account, links this device to it
   and answers with the user, the linked wallets and the Pro status.
7. The app keeps only what it draws: the email, the name and the linked wallets
   (`DataStoreAccountStore`, its own `account.preferences_pb` file, apart from the preferences file
   that keeps the device code). The token is dropped.
8. You re-reads the entitlement through `PassViewModel.refreshEntitlement()`, the same refresh the
   Wallet block uses, so a Pro bought on the web shows in the Pro cell right away.

**Sign out** (2026-09-27) calls `POST /api/v1/account/signout` with the device code in
`X-PT-Code`, which removes this device's binding to the account server side, then forgets the
stored account on this phone whatever the answer. Only a 200 reads as a plain signed-out state.
Offline, or on any unclean answer, the account is still cleared here, You says the server has not
confirmed it yet, and one retry is queued (`AccountSignOut`: a minute later while the process
lives, and on the next launch); a new sign-in drops that retry first. A pass or promo code bound to
the device code itself stays with the device.

**Sign-in refused with `link_on_web`** (409): the Google account's email already belongs to a
PlainTicker account made another way. The app does not link it; You says so and offers
plainticker.com/en/account, where Google is linked to the existing account.

**Legacy device codes.** A device still carrying a 10-symbol code rekeys it to a 26-symbol one
through `POST /api/v1/device/rekey` on launch and before any account call (`DeviceRekeyer`; the
new code is written to disk before the call and becomes current only on the server's 200). An
account route that answers 401 `rekey_required` gets one rekey and one retry.

The ID token is never logged, never stored and never put in a URL. `AccountViewModelTest` walks
every path with a recording log and asserts the token appears in no line, no stored field and no
state; a source scan refuses any log call that names an ID token and any Ktor logging plugin.

## The nonce

`POST /api/v1/auth/google/nonce` hands out a nonce (`{ nonce, expiresAt }`) the app puts straight
into the Google request and echoes back to `POST /api/v1/auth/google` as `nonce`, so the server
can check the token's `nonce` claim against the one it issued: a captured, still-valid ID token
for the web client id can no longer be replayed once that nonce has expired. The app keeps its
own local check too (`SignInNonce.matches`, `AccountViewModel`), comparing the token's claim
against whichever nonce it actually asked Google for, server-issued or a local fallback.

**The fallback.** Fetching the nonce can fail — a 404 from a server that predates the route, a
network error, or anything else the fetch throws or fails to parse. Any of these falls back to a
fresh local random nonce, sent to Google exactly as before this endpoint existed, with no `nonce`
field at all in the `/api/v1/auth/google` body. This is why the app never needed a release when
the nonce endpoint was added: it degrades to its old behavior against an old server, and upgrades
itself the moment the new route answers.

## What each outcome says

Every outcome is one plain line under the button (`AccountModel.kt`, strings `account_msg_*`):
cancelled, no Google account on the phone, no Play services, a setup problem (Android or Google
itself could not run the request — see "Mapping a Credential Manager failure" below), an
interrupted request, Google returned nothing usable, a local nonce mismatch, a network failure,
and each server code in the README's table (`bad_request`, `bad_device_code`, `invalid_token`,
`expired_token`, `wrong_audience`, `nonce_invalid`, `nonce_expired`, `email_not_verified`,
`rate_limited`, `internal`, `auth_disabled`, `not_configured`, `jwks_unavailable`), plus a 404
(route not deployed) and anything unrecognized.

## Mapping a Credential Manager failure

`GoogleCredentialFailureClassifier` turns the exception Credential Manager throws into one of the
outcomes above:

| Exception | Outcome |
|---|---|
| `GetCredentialCancellationException` | cancelled, unless the heuristic below says otherwise |
| `NoCredentialException` | no Google account on the phone |
| `GetCredentialProviderConfigurationException` | setup problem |
| `GetCredentialUnsupportedException` | setup problem |
| `GetCredentialInterruptedException` | interrupted, try again |
| any other `GetCredentialException` | checked by the same heuristic, else credential failed |

**The heuristic, and why it exists.** A founder once picked an account on a real device and the
app said "Sign-in was cancelled. Nothing changed." What had actually happened: the Android OAuth
client did not exist yet in Google Cloud, so Google's servers answered "This android application
is not registered to use OAuth2.0, please confirm the package name and SHA-1 certificate
fingerprint match" (Play services' own log). The app only saw a generic framework error, because
Credential Manager has no dedicated "the caller is not registered" exception type — Play
services' provider folds that failure into the same shape it uses for a plain cancel, sometimes
even the literal `GetCredentialCancellationException` type.

Two signals catch this, either one enough on its own:

1. **Timing.** `CredentialManagerGoogleSource` times its own `getCredential` call. A cancellation
   that lands in under one second could not have followed a person reading the sheet and deciding
   against an account; it reads as a setup problem instead.
2. **Message.** When the framework does pass the provider's words through, they mention OAuth, a
   client id, a package name or a certificate; the classifier matches on those regardless of
   timing.

A slow cancellation whose message names none of this still reads as a genuine cancel. See
`GoogleCredentialFailureClassifier`'s own doc comment for the exact wording matched and the
tradeoff this makes.

## What the founder has to set up in Google Cloud

Sign in with Google on Android fails until an **Android** OAuth client exists for the app in the same Google
Cloud project as the web client. The app never uses this client's id. Google uses it only to
recognise the app's package and signing certificate. Only the project owner can create it.
Without it, Google answers with a developer-configuration error that reaches the app as a generic
Credential Manager failure; `GoogleCredentialFailureClassifier` ("Mapping a Credential Manager
failure" above) reads that as a setup problem and the reader sees "Google did not accept this
app's sign-in setup. Try again later." rather than a false "cancelled".

1. Open <https://console.cloud.google.com/> and select the project that owns the web client
   `170602485636-fo86ia1lc6r34ip5fj6id0v0ku8ffaib.apps.googleusercontent.com` (the project
   number is `170602485636`, project `analyst-495717`).
2. Go to **APIs & Services > OAuth consent screen** (Google Auth Platform > Branding) and confirm
   the consent screen is configured and **published** (In production). While it is in Testing,
   only listed test users can sign in.
3. Go to **APIs & Services > Credentials** (Google Auth Platform > Clients) and choose
   **Create credentials > OAuth client ID**.
4. Application type: **Android**.
5. Name: `PlainTicker Mobile (release)`.
6. Package name: `com.plainticker.mobile`
7. SHA-1 certificate fingerprint:
   `2D:7C:93:3A:B8:05:CC:E3:52:93:BC:CC:23:CF:1D:E4:F7:CE:DA:50`
   (the release keystore, `docs/release-signing.md`).
8. Click **Create**. Nothing from this client goes into the app or the server.
9. Leave the **web** client as it is. Its id is already in the app (`GOOGLE_SERVER_CLIENT_ID`
   in `app/build.gradle.kts`) and on the server (`AUTH_GOOGLE_ID` or `GOOGLE_CLIENT_ID`). The
   token's audience must stay the web client id; a token minted for the Android client is refused
   with `wrong_audience`.
10. Changes can take a few minutes, occasionally longer, to reach Google's servers.

**Debug builds** are signed with the local debug keystore, which has a different SHA-1. To try
sign-in on a debug build, create a second Android client with the same package name and that
SHA-1 (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass
android`). Release builds do not need it.

**If the app is ever distributed through Google Play with Play App Signing**, Google re-signs it
with a different key, and that key's SHA-1 (Play Console > App integrity) needs its own Android
client too. The Solana dApp Store ships the APK as CI signed it, so the SHA-1 above is the one
that matters today.

## Server side, for reference

- `AUTH_ENABLED` must not be `false` (else `auth_disabled`).
- `AUTH_GOOGLE_ID` or `GOOGLE_CLIENT_ID` must be the web client id above (else `not_configured`
  or `wrong_audience`).

## Dependencies

`androidx.credentials:credentials` and `credentials-play-services-auth` 1.6.0,
`com.google.android.libraries.identity.googleid:googleid` 1.2.1,
`androidx.datastore:datastore-preferences` 1.2.1. `proguard-rules.pro` keeps Credential Manager's
Play services provider, and `auditReleaseSerializers` checks the sign-in response models survive
R8.

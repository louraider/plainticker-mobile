# Sign in with Google

You's Account section signs a person in with Google, links this device to the account they
already have on plainticker.com, and so makes that account's Pro count in the app. The server half
is the web repo's `POST /api/v1/auth/google` (PR #140, `server/auth/README.md`, section 1).

## The flow

1. The reader taps **Sign in with Google** in You, under Account.
2. `AccountViewModel` makes a fresh nonce (`SignInNonce`, 256 random bits, base64url).
3. `CredentialManagerGoogleSource` asks Android Credential Manager for a
   `GetSignInWithGoogleOption` with `serverClientId` set to the **web** OAuth client id
   (`BuildConfig.GOOGLE_SERVER_CLIENT_ID`) and that nonce. Google shows its own sheet.
4. The ID token that comes back must carry the same `nonce` claim. If it does not, it is dropped
   and never sent.
5. The token goes to `POST https://www.plainticker.com/api/v1/auth/google` as `{ "idToken" }`,
   with the device code in the `X-PT-Code` header, the same header and base URL every other
   `/api/v1` call in the app uses (`GoogleAuthApi`).
6. The server verifies the token, finds or creates the shared account, links this device to it
   and answers with the user, the linked wallets and the Pro status.
7. The app keeps only what it draws: the email, the name and the linked wallets
   (`DataStoreAccountStore`, its own `account.preferences_pb` file, apart from the preferences file
   that keeps the device code). The token is dropped.
8. You re-reads the entitlement through `PassViewModel.refreshEntitlement()`, the same refresh the
   Wallet block uses, so a Pro bought on the web shows in the Pro cell right away.

**Sign out** forgets the stored account on this phone. The contract defines no sign-out call, so
there is none. The server keeps the device linked to the account, so the device keeps that
account's Pro after a local sign-out until the server offers a way to unlink it.

The ID token is never logged, never stored and never put in a URL. `AccountViewModelTest` walks
every path with a recording log and asserts the token appears in no line, no stored field and no
state; a source scan refuses any log call that names an ID token and any Ktor logging plugin.

## The nonce

The app sends a nonce on every request and refuses a token that does not carry it. **The server
does not check it yet**: `lib/auth/google-id-token.ts` in the web repo verifies the signature,
issuer, audience, expiry, `iat`, `sub` and `email_verified`, and nothing else. So a captured,
still-valid ID token for the web client id could be replayed against the endpoint until it
expires (about an hour). Closing that needs a server change: the route has to issue or accept a
nonce and compare it to the token's `nonce` claim. The app already sends one, so no app release
is needed when the server starts checking.

## What each outcome says

Every outcome is one plain line under the button (`AccountModel.kt`, strings `account_msg_*`):
cancelled, no Google account on the phone, no Play services, Google returned nothing usable, a
nonce mismatch, a network failure, and each server code in the README's table (`bad_request`,
`bad_device_code`, `invalid_token`, `expired_token`, `wrong_audience`, `email_not_verified`,
`rate_limited`, `internal`, `auth_disabled`, `not_configured`, `jwks_unavailable`), plus a 404
(route not deployed) and anything unrecognized.

## What the founder has to set up in Google Cloud

Sign in with Google on Android fails until an **Android** OAuth client exists for the app in the same Google
Cloud project as the web client. The app never uses this client's id. Google uses it only to
recognise the app's package and signing certificate. Only the project owner can create it.
Without it, Google answers with a developer-configuration error that reaches the app as a generic
Credential Manager failure, so the reader sees "Google did not return a sign-in" or "There is no
Google account on this phone" even though the phone has one.

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

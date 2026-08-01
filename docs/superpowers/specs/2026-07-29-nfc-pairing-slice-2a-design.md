# Slice 2a — NFC Pairing & Minimal Contact List

**Date:** 2026-07-29
**Status:** Design approved, pre-implementation
**Slice of:** Milestone 2 (contact pairing + discovery + plaintext messaging over Wi-Fi Aware)
**Depends on:** Milestone 1 (Wi-Fi Aware diagnostic app) — complete, verified on both S23s

## Purpose

Add NFC-based contact pairing to the Earshot app so two S23s held back-to-back can exchange long-term X25519 identity keys, verify the exchange with a safety-number confirmation, and persist the resulting contact locally. This slice establishes the identity layer that later slices (Aware discovery, messaging, encryption) will use.

## Scope

**In scope for slice 2a:**
- First-launch flow: user enters their display name, app generates an X25519 keypair
- Add Contact screen with two entry modes: Auto (timed-alternation NFC role negotiation) and Manual (explicit send/receive)
- Safety-number confirmation screen after two-way pubkey exchange
- Duplicate-pubkey confirmation dialog on save
- Minimal Home screen: display "You are: <name> · <pubkey-truncated>", scrollable list of paired contacts, floating "+ Add contact" button
- Persistence of local identity and contacts (DataStore Preferences)
- Milestone-1 Wi-Fi Aware diagnostic remains reachable via a Settings entry
- Package rename `com.earshot.diag` → `com.earshot.app`

**Explicitly out of scope (deferred to later slices):**
- Any Wi-Fi Aware discovery or messaging (slices 2b, 2c)
- Delete or rename contacts
- Contact avatars / colours / any decoration beyond name + pubkey
- Group pairing in a single motion
- At-rest encryption of the private key (deferred hardening; documented rationale below)
- Any use of Ed25519 or signing keys — X25519-only for identity

## User flow

### First launch (once, ever)

1. App opens, `IdentityRepository.identity()` emits `null`.
2. `NameSetupScreen` shows: text field "What should we call you?", Continue button (disabled while trimmed input is empty).
3. On Continue: generate X25519 keypair via Lazysodium, persist `displayName`, `publicKey`, `privateKey`. Navigate to Home.

### Steady state

- **Home:**
  - Top area: "You are: <displayName> · <pubkey-fingerprint>" — pubkey rendered as first-4-hex + "…" + last-4-hex (e.g. `a4c9…7f22`). Same format used everywhere pubkey is shown in the UI.
  - Settings gear icon (top-right of top bar) → `SettingsScreen`.
  - Scrollable list of `Contact` cards (name + same-format pubkey fingerprint per card), sorted by `pairedAt` desc.
  - Floating action button "+ Add contact" → `AddContactScreen`.
  - Empty state: "No contacts yet — tap + to pair with someone nearby."
- **Settings:**
  - One entry: "🔧 Wi-Fi Aware diagnostic" → `DiagnosticScreen` (the milestone-1 UI, unchanged).
  - App version footer.

### Add contact

- **AddContactScreen** has two segmented tabs at the top: **Auto** (default) and **Manual**.
- Both tabs run the same underlying state machine primitives, differing only in role-selection strategy (timed alternation vs user-initiated).
- On successful two-way exchange (regardless of tab), navigate to **SafetyNumberScreen**.
- On back or tab switch mid-flow: cancel and reset (see Error handling).

#### Auto tab
- Large "Hold phones together" prompt, animated glow indicator.
- Below: "Not working? Try Manual."
- Underlying state machine runs (see NFC state machine section).
- 30-second overall timeout → dialog Retry / Cancel / Switch to Manual.

#### Manual tab
- Two stacked buttons: **Send my contact** (HCE mode), **Receive contact** (reader mode).
- Copy under each: brief instruction ("Hold phones together while your friend taps Receive").
- After one direction succeeds, prompt: "Now swap: press the other button on your side."
- No overall timeout — user drives the flow.

### Safety number

- **SafetyNumberScreen** shows 6 emoji derived from hashing both pubkeys (see Crypto).
- Prompt: "Ask them to check this matches on their screen."
- Two buttons: **Confirm**, **Cancel**.
- **Confirm** → check for duplicate pubkey OR duplicate display name → either save-and-return-Home, or show duplicate-confirm dialog first.
- **Cancel** → discard, back to Home.

### Duplicate confirmation

On SafetyNumberScreen Confirm, three distinct paths depending on what already exists in storage:

**A. Incoming pubkey matches an existing contact** (regardless of name).
Dialog title: "Update existing contact?"
Body: "You've already paired with <existing.displayName>. Their name is now <incoming.displayName>."
Buttons: **Update** (upsert — replaces `displayName` and `pairedAt` on the existing record, `publicKey` unchanged) / **Cancel** (discard, back to Home).

**B. Incoming pubkey is new, but incoming displayName already used by a different pubkey.**
Dialog title: "Save as new contact?"
Body: "You already have a contact called '<name>' with a different key. Save this new one too?"
Buttons: **Save** (insert new contact; the two contacts share a display name but have distinct pubkeys) / **Cancel** (discard).

**C. Both pubkey and displayName are new.**
No dialog. Insert new contact, snackbar "Paired with <displayName>", pop to Home.

Compared to the earlier "silent overwrite" default (which the user rejected), this preserves the pairing-first ceremony: any collision surfaces to the user before any write happens.

### Navigation graph (Compose Navigation)

```
NameSetup ──► Home ◄──► AddContact ──► SafetyNumber ──► Home
                └──► Settings ──► Diagnostic
```

Backstack: Home is the root once identity exists. AddContact and SafetyNumber are pushed onto it. Confirm on SafetyNumber pops both back to Home. Back button anywhere in the pair flow triggers the cancellation path.

## NFC protocol

### AID (Application Identifier)

`F045415253484F5401` (9 bytes)
- `F0`: proprietary AID marker per ISO/IEC 7816-5
- `45 41 52 53 48 4F 54`: ASCII "EARSHOT"
- `01`: protocol version byte

Declared in `res/xml/apdu_service.xml` under a single `<aid-group android:category="other">` with `android:requireDeviceUnlock="false"`.

### On-the-tap wire format

**Reader → HCE (single SELECT AID APDU):**
```
00 A4 04 00 09 F0 45 41 52 53 48 4F 54 01 00
│  │  │  │  │  └──────────────────────────┴─── AID (9 bytes)
│  │  │  │  └── Lc = 9 (length of AID)
│  │  │  └── P2 = 00
│  │  └── P1 = 04 (select by name)
│  └── INS = A4 (SELECT)
└── CLA = 00
                                             └── Le = 00 (accept up to 256 bytes response)
```

**HCE → Reader (single response APDU):**
```
<version:1> <pubkey:32> <name_len:1> <name:name_len> <SW:2>
```
- `version`: `0x01` — protocol version. Any other value on the receiving side → treat as malformed, discard.
- `pubkey`: 32 raw bytes of X25519 public key.
- `name_len`: 0..64 — UTF-8 byte length of the display name.
- `name`: `name_len` bytes of UTF-8-encoded display name.
- `SW`: `90 00` (success) on the wire — followed by any non-`9000` value causes the reader to abort.

**Sizing check:** worst case response is `1 + 32 + 1 + 64 + 2 = 100 bytes`, well under the 256-byte classic-APDU response limit. No APDU chaining needed.

### Payload codec

A pure Kotlin object `PayloadCodec` with:
```kotlin
fun encode(name: String, pubkey: ByteArray): ByteArray  // throws on name > 64 UTF-8 bytes
fun decode(bytes: ByteArray): Result<Payload>            // returns Result.failure on any malformation
```
Both directions covered by round-trip unit tests. Malformed input never throws — it returns `Result.failure` so callers can handle "bad tap, retry" without try/catch pollution.

### State machines

Both auto and manual modes share two flags per phone tracked in `AddContactViewModel`:
- `readFromPeer: Payload?` — set on successful reader-mode read.
- `wasReadByPeer: Boolean` — set inside `EarshotHceService.processCommandApdu` when we serve our payload.

Both modes exit into **SafetyNumberScreen** when `readFromPeer != null && wasReadByPeer == true`.

#### Manual mode

- User taps **Send my contact** → enable HCE service, disable reader mode. On `processCommandApdu`: set `wasReadByPeer = true`, update UI to "Now tap Receive."
- User taps **Receive contact** → disable HCE, `NfcAdapter.enableReaderMode` with our AID. On successful read: parse payload, set `readFromPeer`, update UI to "Now tap Send."
- User can tap the same button twice with no effect (idempotent).

#### Auto mode — two phases

**Phase 1: alternate.**
On entering the Auto tab both phones begin cycling between reader mode and HCE, with a per-tick coroutine:
```
period = 300ms + uniformRandom(-50ms, +50ms)   // re-jittered each tick
role   = random(READER, HCE) at start
while (phase == PHASE_1) {
    if role == READER: enableReaderMode with AID; wait for read or period tick
    else:              enable HCE; wait for processCommandApdu or period tick
    if role-transition-event: exit phase 1 deterministically (see below)
    else on tick timeout: flip role, loop
}
```

**Exit conditions from phase 1 (deterministic):**
- If our reader succeeded (we got the peer's payload): `readFromPeer = payload`, transition to `PHASE_2` with role fixed as **HCE**.
- If our HCE was read (`processCommandApdu` fired): `wasReadByPeer = true`, transition to `PHASE_2` with role fixed as **READER**.

**Phase 2: fixed roles.**
No more alternation. Whichever role we're in, wait until the other flag flips true:
- READER waits for the peer's HCE response.
- HCE waits for the peer's SELECT AID.

When both flags are true → COMPLETE, navigate to SafetyNumberScreen.

**Why two phases (rather than continuing alternation):** once one direction has happened, the state of the world is asymmetric — one phone has "given," the other has "received." If both kept alternating we'd risk both phones flipping to the same role at the same tick and staring at each other. Forcing the swap deterministically at the moment of first exchange kills that class of failure.

**Overall auto-mode timeout:** 30 seconds from AddContactScreen entry (or Auto-tab-selection). On expiry → dialog Retry / Cancel / Switch to Manual.

#### Concurrency & lifecycle

- `AddContactViewModel` owns a `viewModelScope` coroutine that drives the state machine. On `onCleared` (screen destroyed / back button / tab switch), it cancels the scope, disables reader mode, and disables the HCE service component via `PackageManager.setComponentEnabledSetting(COMPONENT_ENABLED_STATE_DISABLED)`.
- `EarshotHceService` re-enables itself on ViewModel init.
- Flags exposed to Compose UI via `StateFlow<PairingUiState>`.
- Android's NFC stack suspends HCE while reader mode is active on the same device. Our state machine is built around this — it's the fundamental physics of the flip, not a bug.

## Data model

```kotlin
data class LocalIdentity(
    val displayName: String,   // 1..64 UTF-8 bytes; entered on first launch, immutable in slice 2a
    val publicKey: ByteArray,  // 32 bytes, X25519
    val privateKey: ByteArray  // 32 bytes, X25519
)

data class Contact(
    val publicKey: ByteArray,  // 32 bytes; primary identity key
    val displayName: String,   // as received during pairing
    val pairedAt: Long         // epoch millis
)
```

- `Contact.publicKey` is the identity. Names collide freely; pubkeys don't.
- `LocalIdentity` is created exactly once per install; if the app is uninstalled and reinstalled, a new identity is generated and old contacts on peers are no longer valid without re-pairing. This is expected.

## Storage

Single `DataStore<Preferences>` instance named `earshot_prefs`, backed by a file in the app's private storage:
- `my_display_name` : `String`
- `my_public_key`   : `String` (Base64, 44 chars for 32 bytes)
- `my_private_key`  : `String` (Base64)
- `contacts_json`   : `String` — kotlinx.serialization-encoded `List<SerializableContact>`

Contacts stored as a single JSON blob for atomic reads/writes and no key-set gymnastics. For 2–10 contacts (our target scale) the whole-blob rewrite cost is invisible; the design does not scale to hundreds of contacts, and this is explicitly acceptable.

### Private key at-rest storage

The private key is stored in plaintext within the DataStore file at `/data/data/com.earshot.app/files/datastore/earshot_prefs.preferences_pb`. Access is limited to:
- The Earshot app itself (Android app-sandbox UID isolation).
- Root or adb-with-USB-debugging users on the device.

**Rationale for accepting this:** the threat model of a hobby app used by 3 known people is "casual snooper with a briefly unlocked phone." Android's screen lock + app sandbox already covers that. Hardware-backed encryption of the key would require either (a) switching from X25519 to P-256 (loss of ecosystem interop with libsodium primitives we'll use in milestone 3) or (b) wrapping the X25519 private key with a Keystore-derived AES-256 key (roughly a 30-line addition — worth doing when we have an actual reason). The design leaves this migration open; it does not preclude it.

### Repositories

Thin coroutine/Flow-based wrappers over DataStore. Both are `object`s constructed in an `AppContainer` in `EarshotApp.onCreate` and injected manually into ViewModels (no Hilt/Dagger for this scope).

```kotlin
class IdentityRepository(private val ds: DataStore<Preferences>) {
    fun identity(): Flow<LocalIdentity?>
    suspend fun createIdentity(displayName: String): LocalIdentity
    suspend fun hasIdentity(): Boolean
}

class ContactsRepository(private val ds: DataStore<Preferences>) {
    fun contacts(): Flow<List<Contact>>              // sorted pairedAt desc
    suspend fun findByPubKey(pk: ByteArray): Contact?
    suspend fun findByDisplayName(name: String): Contact?
    suspend fun upsert(contact: Contact)
}
```

**Note on `ByteArray` equality:** Kotlin's default `==` on `ByteArray` is reference equality; `findByPubKey` must use `contentEquals`, and `Contact.equals`/`hashCode` must be overridden (data-class autogen relies on `==` and produces the wrong answer for `ByteArray` fields). This is a well-known JVM gotcha and easy to miss.

## Crypto

### Keypair generation

Lazysodium-android (JNI over libsodium):
```kotlin
val kp = lazySodium.cryptoBoxKeypair()   // X25519 under the hood
LocalIdentity(name, kp.publicKey.asBytes, kp.secretKey.asBytes)
```
`crypto_box_keypair` produces exactly the 32-byte X25519 keys we'll use in milestone 3 for `crypto_kx`/`crypto_box_easy`. No format conversion needed later.

### Safety-number derivation

Both phones must compute the same code regardless of which one is "us":

```kotlin
fun safetyEmoji(myPub: ByteArray, theirPub: ByteArray): List<String> {
    val (low, high) = listOf(myPub, theirPub).sortedWith(ByteArrayLexComparator)
    val hash = blake2b(low + high, digestSize = 32)      // via libsodium
    return (0 until 6).map { i ->
        val idx = hash[i].toInt() and 0x3F               // 6 bits per emoji
        SAFETY_EMOJI[idx]
    }
}
```

`SAFETY_EMOJI` is a hardcoded `List<String>` of 64 visually distinct emoji, chosen for at-a-glance discrimination (no lookalike pairs like 🐶/🐺 or 🌕/🌝). Actual list finalised at implementation time; not a spec-level decision.

Result: 6 emoji, ~36 bits of entropy — trivial to compare visually, impossibly expensive to forge on the millisecond timescale of a physical NFC tap.

## Manifest changes

Additions on top of milestone 1's manifest (Wi-Fi Aware entries preserved):

```xml
<uses-permission android:name="android.permission.NFC" />

<uses-feature android:name="android.hardware.nfc"     android:required="false" />
<uses-feature android:name="android.hardware.nfc.hce" android:required="false" />

<application android:label="Earshot" ...>
    <service
        android:name=".nfc.EarshotHceService"
        android:exported="true"
        android:permission="android.permission.BIND_NFC_SERVICE">
        <intent-filter>
            <action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE" />
        </intent-filter>
        <meta-data
            android:name="android.nfc.cardemulation.host_apdu_service"
            android:resource="@xml/apdu_service" />
    </service>
    <!-- MainActivity: unchanged intent-filter (LAUNCHER) -->
</application>
```

`res/xml/apdu_service.xml`:
```xml
<host-apdu-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/hce_service_description"
    android:requireDeviceUnlock="false">
    <aid-group
        android:description="@string/aid_group_description"
        android:category="other">
        <aid-filter android:name="F045415253484F5401" />
    </aid-group>
</host-apdu-service>
```

The two `@string/…` references above must be added to `res/values/strings.xml`. Suggested copy: `hce_service_description = "Earshot contact pairing"`, `aid_group_description = "Earshot pairing AID"`. These strings appear in Android's Tap-and-Pay settings if the user ever opens that screen, so they should be human-readable.

Also add `res/values/strings.xml` entries for the app label (`app_name = "Earshot"`) if we prefer that over the inline `android:label="Earshot"` currently in the manifest — either is fine.

Gradle:
- `namespace` and `applicationId` change to `com.earshot.app`
- `versionCode = 2`, `versionName = "0.2.0"`
- Add dependency: `com.goterl:lazysodium-android:5.1.0` (or latest stable at implementation time)
- Add dependency: `net.java.dev.jna:jna:5.14.0@aar` (Lazysodium requirement)
- Add dependency: `androidx.datastore:datastore-preferences:1.1.1`
- Add dependency: `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`
- Add plugin: `kotlin("plugin.serialization")`
- Add dependency: `androidx.navigation:navigation-compose:2.8.4`
- Add dependency: `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`

## Error handling

| Situation | Behaviour |
|---|---|
| Device has no NFC hardware | AddContactScreen shows static "not supported" state; no crash anywhere else in the app |
| NFC disabled in system settings | Inline banner on AddContactScreen with "Open NFC settings" button (fires `Settings.ACTION_NFC_SETTINGS` intent); banner is live via `BroadcastReceiver` on `NfcAdapter.ACTION_ADAPTER_STATE_CHANGED` |
| HCE feature absent (`FEATURE_NFC_HOST_CARD_EMULATION` false) | Same "not supported" state, distinct copy |
| Auto-mode 30 s timeout | Dialog: Retry / Cancel / Switch to Manual |
| APDU I/O error mid-read | Silent retry up to 3 attempts, then fail into timeout path |
| Payload has unknown version byte or malformed length | Treat as failed read, discard, keep trying (auto) or "That didn't work, try again" (manual) |
| User backs out mid-pairing | Reader disabled, HCE component disabled, ViewModel state cleared |
| App backgrounded mid-pairing | Same clean-up as back button |
| User accidentally paired with own pubkey | Detect on save; toast "You can't pair with your own phone"; discard |
| Same pubkey already exists | Duplicate-confirm dialog on SafetyNumberScreen Confirm |
| Same display name, different pubkey | Save as new contact (names aren't unique, pubkeys are); dialog says "This name is already used by a different pubkey — pair anyway?" |
| DataStore write failure | Snackbar "Couldn't save contact — try again"; state remains on SafetyNumberScreen |
| Empty display name on first-launch | Continue button disabled while trimmed name is empty |

## Testing plan

### JVM unit tests (must-have)

- `SafetyCodeTest`
  - `safetyEmoji(A, B) == safetyEmoji(B, A)` across 100 random keypair pairs
  - Distinct pubkey pairs produce distinct codes (birthday-bound sanity check across ~1000 pairs)
  - Deterministic output for fixed inputs
- `PayloadCodecTest`
  - Round-trip: `decode(encode(name, pk)).getOrThrow() == Payload(name, pk)` across random inputs
  - Malformed bytes return `Result.failure`, do not throw
  - Version-byte mismatch (`0x02` for now) → `Result.failure`
  - Name at 64 UTF-8 bytes encodes; at 65 throws
  - Empty name (name_len = 0) round-trips
- `ContactsRepositoryTest` (in-memory or tmpdir DataStore)
  - add / upsert / findByPubKey / findByDisplayName / list-sorted
  - Duplicate pubkey overwrites, retains sort order after upsert
- `IdentityRepositoryTest`
  - `createIdentity` produces 32-byte pubkey and 32-byte privkey
  - `hasIdentity` flips false → true after creation
  - `identity()` Flow emits the new identity

### Android instrumentation tests

**Skipped for slice 2a.** Setup cost for two-device NFC-aware instrumentation is disproportionate to the value for a hobby-scale codebase. Manual on-hardware pass covers the same ground more efficiently.

### Manual on-hardware pass (both S23s)

1. Fresh install on both phones → first-launch name prompt appears on each; name persists after `adb shell am force-stop com.earshot.app`.
2. Home screen empty state renders correctly.
3. Auto-mode: hold phones back-to-back at NFC-coil position; verify pairing completes within 5 seconds on ≥ 8 of 10 attempts.
4. Manual mode: two-button flow works end-to-end; prompts guide the swap correctly.
5. Safety code matches on both phones for a given pair.
6. Re-pair with same pubkey → duplicate-confirm dialog fires; Update path works.
7. Same name / different pubkey → new-contact dialog fires; save-anyway works.
8. Force-stop app mid-pair → no zombie HCE (verify via `adb shell dumpsys nfc | grep -A5 HostEmulationManager`).
9. Toggle NFC off during pairing → banner appears live; toggle back → banner clears live.
10. Wi-Fi Aware diagnostic screen still reachable from Settings → still reports live state on airplane-mode toggle.
11. Sideload upgrade path: install 0.2.0 over 0.1.0 with `adb install -r`; verify diagnostic still works, no crash on launch (there are no persisted-state migrations to worry about because 0.1.0 didn't persist anything).

## Build & install

Same commands as milestone 1:
```powershell
.\gradlew.bat :app:assembleDebug
adb -s <serial> install -r app\build\outputs\apk\debug\app-debug.apk
```
APK path unchanged: `C:\dev\earshot\app\build\outputs\apk\debug\app-debug.apk`.

## Milestone 1 preservation

Milestone 1's diagnostic screen is moved (not deleted) into `com.earshot.app.diag` and made reachable from `SettingsScreen`. The Wi-Fi Aware `BroadcastReceiver` behaviour is preserved unchanged. This slice adds no permissions that require runtime prompts.

## Known unknowns (things that will only be resolved on real hardware)

- Whether the Auto-mode timed-alternation actually feels magical, or whether users still perceive a ~1–2 s "trying…" phase. If it feels slow, we may want to shorten the alternation period; if it collides too often, lengthen it and add more jitter.
- Whether HCE component enable/disable via `PackageManager.setComponentEnabledSetting` incurs a visible latency on state changes. If yes, we may need to leave the service permanently enabled and gate the payload response inside `processCommandApdu` on the ViewModel state instead.
- Whether `blake2b` is exposed with a stable API from the currently-shipping Lazysodium version. If not, substitute `SHA-256` — 36 bits of prefix is 36 bits of prefix, and this decision is invisible to users.

## Not doing (deferred to explicit future slices)

- **Slice 2b:** Aware discovery of paired contacts — showing which contacts are currently in Wi-Fi Aware range.
- **Slice 2c:** Plaintext P2P messaging between paired-and-in-range peers.
- **Milestone 3:** End-to-end encryption of messages using long-term X25519 identity keys + ephemeral session keys. This is why we generate X25519 keys now: they slot straight into that milestone.
- **Delete/rename contact UI.** Escape hatch during development is `adb shell pm clear com.earshot.app`.
- **Group pairing** (three phones exchanging keys in one motion). Chain-pairing (pair A↔B, then A↔C, then B↔C) is what we'll do until this is proven annoying enough to warrant it.
- **Hardware-backed private key storage.** Left as a well-scoped future migration.

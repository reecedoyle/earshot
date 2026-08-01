# Slice 2a — NFC Pairing & Minimal Contact List — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the approved spec (`docs/superpowers/specs/2026-07-29-nfc-pairing-slice-2a-design.md`) into a working NFC pairing feature that runs on two Samsung S23s and persists paired X25519 identity keys.

**Architecture:** Kotlin + Jetpack Compose single-Activity app. NFC pairing uses HCE (`HostApduService`) + reader mode (`NfcAdapter.enableReaderMode`) with a proprietary AID. Two pairing modes share one state machine: Manual (explicit send/receive) and Auto (two-phase timed alternation). Long-term X25519 identity keys are generated once per install via Lazysodium and stored (with the display name and contacts list) in DataStore Preferences.

**Tech Stack:** Kotlin 2.0.21 · Jetpack Compose (BOM 2024.12.01) · AndroidX Navigation-Compose · AndroidX Lifecycle ViewModel · DataStore Preferences · kotlinx-serialization JSON · Lazysodium-android (libsodium JNI) · JNA (Lazysodium transitive)

## Global Constraints

- Package/namespace: `com.earshot.app`. Milestone-1 diagnostic code lives at `com.earshot.app.diag`.
- `minSdk = 33`, `targetSdk = 35`, `compileSdk = 35`, JVM target 17.
- No new runtime permission prompts in this slice. NFC does not require a runtime permission; it is manifest-declared only.
- AID (application identifier for HCE): exact hex `F045415253484F5401` (9 bytes). Referenced identically in `apdu_service.xml` and in reader-mode code.
- Payload wire format (HCE → Reader response body, before status word):
  `[version:1 = 0x01][pubkey:32][name_len:1][name_utf8:name_len]` — then status word `90 00`.
- Display name: 1..64 UTF-8 bytes, trimmed of leading/trailing whitespace before storage.
- Public keys are 32 raw bytes (X25519). Fingerprint format everywhere in the UI: `first-4-hex + "…" + last-4-hex` (e.g. `a4c9…7f22`). Case: lowercase.
- Safety code: 6 emoji from a fixed 64-emoji alphabet, derived from `BLAKE2b-256(sorted(pubA, pubB, byteLex) || sorted(pubA, pubB, byteLex)[1])` — top 6 bits of each of the first 6 hash bytes index the alphabet.
- Data classes containing `ByteArray` MUST override `equals`/`hashCode` (use `contentEquals`/`contentHashCode`). Kotlin's default `==` on `ByteArray` is reference equality and will silently break `findByPubKey`.
- New commits must include the trailer `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
- Do not push to `origin/main` from within a task; commits stay local until the human explicitly asks.

---

### Task 1: Package rename + Gradle dependency update

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`
- Move: `app/src/main/java/com/earshot/diag/MainActivity.kt` → `app/src/main/java/com/earshot/app/MainActivity.kt`
- Delete (after move): the empty `app/src/main/java/com/earshot/diag/` directory
- Modify: `app/src/main/AndroidManifest.xml` — set `android:label="Earshot"` on `<application>`

**Interfaces:**
- Consumes: nothing (foundation).
- Produces: buildable Gradle project at namespace `com.earshot.app`; all new dependencies resolvable.

- [ ] **Step 1: Update root `build.gradle.kts` to register the serialization plugin**

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
```

- [ ] **Step 2: Rewrite `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.earshot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.earshot.app"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.goterl:lazysodium-android:5.1.0@aar")
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")
}
```

- [ ] **Step 3: Move `MainActivity.kt` to the new package path**

Physically move the file:
`app/src/main/java/com/earshot/diag/MainActivity.kt` → `app/src/main/java/com/earshot/app/MainActivity.kt`

Then edit the file's first line to declare the new package:
```kotlin
package com.earshot.app
```
No other code changes in this step — the diagnostic UI is preserved verbatim (it will be extracted into its own composable in Task 3's DiagnosticScreen move).

After the file is moved, delete the now-empty `com/earshot/diag/` directory to keep the tree clean.

- [ ] **Step 4: Update `AndroidManifest.xml` label**

Change the `<application>` element's `android:label="Earshot Diag"` to `android:label="Earshot"`. The `<activity android:name=".MainActivity">` reference resolves via the new namespace and needs no other change.

- [ ] **Step 5: Verify the project still builds**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If Lazysodium/JNA resolution fails, verify that `mavenCentral()` is in the root `settings.gradle.kts` (it is from milestone 1).

- [ ] **Step 6: Sideload and smoke-test on one S23**

Run:
```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.earshot.app/.MainActivity
```
Expected: the milestone-1 diagnostic UI appears exactly as before, now under the app label "Earshot".

- [ ] **Step 7: Commit**

```powershell
git add build.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml
git add app/src/main/java/com/earshot/app/MainActivity.kt
git add -u  # picks up deletion of com/earshot/diag/MainActivity.kt
git status  # confirm clean
git commit -m "chore: rename package com.earshot.diag -> com.earshot.app; add slice 2a deps

Bumps versionCode to 2, versionName to 0.2.0. Diagnostic UI still
served from MainActivity as-is; it will be split into a composable in
a follow-up task once the navigation graph exists.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: PayloadCodec

**Files:**
- Create: `app/src/main/java/com/earshot/app/nfc/PayloadCodec.kt`
- Create: `app/src/test/java/com/earshot/app/nfc/PayloadCodecTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  ```kotlin
  data class Payload(val displayName: String, val publicKey: ByteArray)
  object PayloadCodec {
      const val PROTOCOL_VERSION: Byte = 0x01
      const val MAX_NAME_UTF8_BYTES = 64
      fun encode(name: String, publicKey: ByteArray): ByteArray
      fun decode(bytes: ByteArray): Result<Payload>
  }
  ```

Overrides required on `Payload` because it contains a `ByteArray`:
- `equals(other)` — `displayName == that.displayName && publicKey.contentEquals(that.publicKey)`
- `hashCode()` — `31 * displayName.hashCode() + publicKey.contentHashCode()`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/earshot/app/nfc/PayloadCodecTest.kt`:
```kotlin
package com.earshot.app.nfc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PayloadCodecTest {

    private val pk32 = ByteArray(32) { it.toByte() }

    @Test fun `encode then decode round-trips a typical payload`() {
        val encoded = PayloadCodec.encode("Reece", pk32)
        val decoded = PayloadCodec.decode(encoded).getOrThrow()
        assertThat(decoded.displayName).isEqualTo("Reece")
        assertThat(decoded.publicKey).isEqualTo(pk32)
    }

    @Test fun `encode produces version byte first`() {
        val encoded = PayloadCodec.encode("R", pk32)
        assertThat(encoded[0]).isEqualTo(PayloadCodec.PROTOCOL_VERSION)
    }

    @Test fun `encode places the pubkey in bytes 1 through 32 inclusive`() {
        val encoded = PayloadCodec.encode("R", pk32)
        assertThat(encoded.copyOfRange(1, 33)).isEqualTo(pk32)
    }

    @Test fun `empty name round-trips`() {
        val encoded = PayloadCodec.encode("", pk32)
        val decoded = PayloadCodec.decode(encoded).getOrThrow()
        assertThat(decoded.displayName).isEmpty()
    }

    @Test fun `name at 64 UTF-8 bytes encodes cleanly`() {
        val name = "a".repeat(64)
        val encoded = PayloadCodec.encode(name, pk32)
        val decoded = PayloadCodec.decode(encoded).getOrThrow()
        assertThat(decoded.displayName).isEqualTo(name)
    }

    @Test fun `name over 64 UTF-8 bytes throws IllegalArgumentException on encode`() {
        val tooLong = "a".repeat(65)
        try {
            PayloadCodec.encode(tooLong, pk32)
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test fun `pubkey not 32 bytes throws IllegalArgumentException on encode`() {
        try {
            PayloadCodec.encode("R", ByteArray(31))
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test fun `decode returns failure for unknown version byte`() {
        val encoded = PayloadCodec.encode("R", pk32).also { it[0] = 0x02 }
        assertThat(PayloadCodec.decode(encoded).isFailure).isTrue()
    }

    @Test fun `decode returns failure for truncated buffer`() {
        val encoded = PayloadCodec.encode("R", pk32)
        assertThat(PayloadCodec.decode(encoded.copyOfRange(0, 20)).isFailure).isTrue()
    }

    @Test fun `decode returns failure when name_len says more than remaining bytes`() {
        val encoded = PayloadCodec.encode("R", pk32)
        encoded[33] = 100  // claim name is 100 bytes but only 1 remains
        assertThat(PayloadCodec.decode(encoded).isFailure).isTrue()
    }

    @Test fun `decode returns failure for empty buffer`() {
        assertThat(PayloadCodec.decode(ByteArray(0)).isFailure).isTrue()
    }
}
```

- [ ] **Step 2: Run the tests and verify they all fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.nfc.PayloadCodecTest"`
Expected: compilation failure (`PayloadCodec` / `Payload` don't exist yet).

- [ ] **Step 3: Implement `PayloadCodec.kt`**

Create `app/src/main/java/com/earshot/app/nfc/PayloadCodec.kt`:
```kotlin
package com.earshot.app.nfc

class Payload(
    val displayName: String,
    val publicKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Payload) return false
        return displayName == other.displayName && publicKey.contentEquals(other.publicKey)
    }
    override fun hashCode(): Int =
        31 * displayName.hashCode() + publicKey.contentHashCode()
}

object PayloadCodec {
    const val PROTOCOL_VERSION: Byte = 0x01
    const val PUBKEY_SIZE = 32
    const val MAX_NAME_UTF8_BYTES = 64

    fun encode(name: String, publicKey: ByteArray): ByteArray {
        require(publicKey.size == PUBKEY_SIZE) {
            "publicKey must be $PUBKEY_SIZE bytes, was ${publicKey.size}"
        }
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        require(nameBytes.size <= MAX_NAME_UTF8_BYTES) {
            "name must be <= $MAX_NAME_UTF8_BYTES UTF-8 bytes, was ${nameBytes.size}"
        }
        val out = ByteArray(1 + PUBKEY_SIZE + 1 + nameBytes.size)
        out[0] = PROTOCOL_VERSION
        System.arraycopy(publicKey, 0, out, 1, PUBKEY_SIZE)
        out[1 + PUBKEY_SIZE] = nameBytes.size.toByte()
        System.arraycopy(nameBytes, 0, out, 2 + PUBKEY_SIZE, nameBytes.size)
        return out
    }

    fun decode(bytes: ByteArray): Result<Payload> = runCatching {
        require(bytes.isNotEmpty()) { "empty" }
        require(bytes[0] == PROTOCOL_VERSION) { "bad version ${bytes[0]}" }
        require(bytes.size >= 1 + PUBKEY_SIZE + 1) { "truncated" }
        val pubkey = bytes.copyOfRange(1, 1 + PUBKEY_SIZE)
        val nameLen = bytes[1 + PUBKEY_SIZE].toInt() and 0xFF
        require(nameLen <= MAX_NAME_UTF8_BYTES) { "name_len too large" }
        val nameEnd = 2 + PUBKEY_SIZE + nameLen
        require(bytes.size >= nameEnd) { "name truncated" }
        val name = String(bytes, 2 + PUBKEY_SIZE, nameLen, Charsets.UTF_8)
        Payload(name, pubkey)
    }
}
```

- [ ] **Step 4: Re-run tests and verify all pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.nfc.PayloadCodecTest"`
Expected: `BUILD SUCCESSFUL`, all 11 tests passing.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/earshot/app/nfc/PayloadCodec.kt
git add app/src/test/java/com/earshot/app/nfc/PayloadCodecTest.kt
git commit -m "feat: PayloadCodec for NFC pair-payload wire format

Encodes/decodes the [version|pubkey|name_len|name] body carried in the
HCE response APDU. Full round-trip and malformed-input coverage.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: SafetyCode

**Files:**
- Create: `app/src/main/java/com/earshot/app/crypto/SafetyCode.kt`
- Create: `app/src/main/java/com/earshot/app/crypto/SodiumHolder.kt`
- Create: `app/src/test/java/com/earshot/app/crypto/SafetyCodeTest.kt`

**Interfaces:**
- Consumes: nothing (Lazysodium is a new dependency).
- Produces:
  ```kotlin
  object SodiumHolder { val lazySodium: LazySodiumAndroid }
  object SafetyCode {
      val ALPHABET: List<String>  // 64 entries
      fun derive(myPubKey: ByteArray, theirPubKey: ByteArray): List<String>  // 6 emoji
  }
  ```

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/earshot/app/crypto/SafetyCodeTest.kt`:
```kotlin
package com.earshot.app.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafetyCodeTest {

    private val a = ByteArray(32) { 0x11 }
    private val b = ByteArray(32) { 0x22 }
    private val c = ByteArray(32) { 0x33 }

    @Test fun `alphabet has exactly 64 distinct entries`() {
        assertThat(SafetyCode.ALPHABET).hasSize(64)
        assertThat(SafetyCode.ALPHABET.toSet()).hasSize(64)
    }

    @Test fun `derive returns 6 emoji from the alphabet`() {
        val code = SafetyCode.derive(a, b)
        assertThat(code).hasSize(6)
        assertThat(SafetyCode.ALPHABET).containsAtLeastElementsIn(code)
    }

    @Test fun `derive is symmetric across both pubkey orderings`() {
        assertThat(SafetyCode.derive(a, b)).isEqualTo(SafetyCode.derive(b, a))
    }

    @Test fun `derive is deterministic on repeat`() {
        assertThat(SafetyCode.derive(a, b)).isEqualTo(SafetyCode.derive(a, b))
    }

    @Test fun `distinct pubkey pairs produce distinct codes`() {
        val ab = SafetyCode.derive(a, b)
        val ac = SafetyCode.derive(a, c)
        assertThat(ab).isNotEqualTo(ac)
    }

    @Test fun `rejects non-32-byte pubkey`() {
        try {
            SafetyCode.derive(ByteArray(16), b)
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.crypto.SafetyCodeTest"`
Expected: compilation failure — types don't exist yet.

- [ ] **Step 3: Create `SodiumHolder`**

The Android build of Lazysodium requires the Android `Context` in some paths but the top-level API can be lazily instantiated as a process-wide singleton. We wrap that so tests and prod code share one entry point.

Create `app/src/main/java/com/earshot/app/crypto/SodiumHolder.kt`:
```kotlin
package com.earshot.app.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid

object SodiumHolder {
    val lazySodium: LazySodiumAndroid by lazy {
        LazySodiumAndroid(SodiumAndroid())
    }
}
```
(No Android `Context` argument is required for the primitives we use — key generation, blake2b, box.)

- [ ] **Step 4: Implement `SafetyCode`**

Create `app/src/main/java/com/earshot/app/crypto/SafetyCode.kt`:
```kotlin
package com.earshot.app.crypto

import com.goterl.lazysodium.interfaces.GenericHash

object SafetyCode {
    val ALPHABET: List<String> = listOf(
        "🐶", "🐱", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁",
        "🐷", "🐮", "🐵", "🐸", "🐰", "🐭", "🦄", "🦉",
        "🐢", "🐙", "🐳", "🐬", "🦋", "🐝", "🐞", "🦖",
        "🍎", "🍊", "🍋", "🍉", "🍇", "🍓", "🍑", "🍒",
        "🥑", "🥕", "🌽", "🌶️", "🍄", "🥐", "🍞", "🧀",
        "🍔", "🍕", "🌮", "🍜", "🍩", "🍪", "🍰", "🍫",
        "⚽", "🏀", "🎾", "🎳", "🎯", "🎲", "🎸", "🎺",
        "🚗", "🚀", "⛵", "🚲", "✈️", "🚁", "🚂", "🛴"
    )

    fun derive(myPubKey: ByteArray, theirPubKey: ByteArray): List<String> {
        require(myPubKey.size == 32) { "myPubKey must be 32 bytes" }
        require(theirPubKey.size == 32) { "theirPubKey must be 32 bytes" }
        val (low, high) = listOfNotNull(myPubKey, theirPubKey)
            .sortedWith(ByteArrayLex)
        val message = low + high
        val hash = ByteArray(32)
        val ok = SodiumHolder.lazySodium.cryptoGenericHash(
            hash, hash.size, message, message.size.toLong()
        )
        check(ok) { "blake2b failed" }
        return (0 until 6).map { i ->
            val idx = hash[i].toInt() and 0x3F
            ALPHABET[idx]
        }
    }

    private object ByteArrayLex : Comparator<ByteArray> {
        override fun compare(a: ByteArray, b: ByteArray): Int {
            val n = minOf(a.size, b.size)
            for (i in 0 until n) {
                val x = a[i].toInt() and 0xFF
                val y = b[i].toInt() and 0xFF
                if (x != y) return x - y
            }
            return a.size - b.size
        }
    }
}
```
Note: `cryptoGenericHash` is libsodium's Blake2b interface. The `GenericHash` import is only needed if we referenced the constant `GenericHash.BYTES` — we hardcode `32`, so it's optional. Include the import so IDE-added references don't break the build later.

- [ ] **Step 5: Re-run and verify all pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.crypto.SafetyCodeTest"`
Expected: 6 tests passing. If Lazysodium fails to load in the JVM test runtime (JNA missing native lib), instead add `testImplementation("net.java.dev.jna:jna:5.14.0")` (note: non-aar variant) to `app/build.gradle.kts` and re-run.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/earshot/app/crypto/SodiumHolder.kt
git add app/src/main/java/com/earshot/app/crypto/SafetyCode.kt
git add app/src/test/java/com/earshot/app/crypto/SafetyCodeTest.kt
# if you had to add the non-aar JNA dep for tests:
git add app/build.gradle.kts
git commit -m "feat: SafetyCode derives 6-emoji pairing confirmation

BLAKE2b over lexicographically-sorted pubkeys guarantees both sides
derive the same 6 emoji from a 64-entry alphabet.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Data model

**Files:**
- Create: `app/src/main/java/com/earshot/app/data/LocalIdentity.kt`
- Create: `app/src/main/java/com/earshot/app/data/Contact.kt`
- Create: `app/src/main/java/com/earshot/app/data/SerializableContact.kt`
- Create: `app/src/main/java/com/earshot/app/data/Fingerprint.kt`
- Create: `app/src/test/java/com/earshot/app/data/ContactSerializationTest.kt`
- Create: `app/src/test/java/com/earshot/app/data/FingerprintTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  ```kotlin
  class LocalIdentity(val displayName: String, val publicKey: ByteArray, val privateKey: ByteArray)
  class Contact(val publicKey: ByteArray, val displayName: String, val pairedAt: Long)
  @Serializable data class SerializableContact(val pubKeyB64: String, val displayName: String, val pairedAt: Long)
  fun Contact.toSerializable(): SerializableContact
  fun SerializableContact.toContact(): Contact
  fun ByteArray.fingerprint(): String  // "first-4-hex…last-4-hex"
  ```

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/earshot/app/data/FingerprintTest.kt`:
```kotlin
package com.earshot.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FingerprintTest {
    @Test fun `fingerprint on a 32-byte key shows first-4-hex then ellipsis then last-4-hex`() {
        val key = ByteArray(32).also {
            it[0] = 0xa4.toByte(); it[1] = 0xc9.toByte()
            it[30] = 0x7f.toByte(); it[31] = 0x22.toByte()
        }
        assertThat(key.fingerprint()).isEqualTo("a4c9…7f22")
    }

    @Test fun `fingerprint is lowercase hex`() {
        val key = ByteArray(32).also {
            it[0] = 0xFF.toByte(); it[31] = 0xAB.toByte()
        }
        val fp = key.fingerprint()
        assertThat(fp).contains("ff")
        assertThat(fp).contains("ab")
        assertThat(fp).doesNotContain("FF")
    }

    @Test fun `fingerprint rejects wrong-size input`() {
        try {
            ByteArray(31).fingerprint()
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
```

Create `app/src/test/java/com/earshot/app/data/ContactSerializationTest.kt`:
```kotlin
package com.earshot.app.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class ContactSerializationTest {

    private val json = Json { prettyPrint = false }

    @Test fun `Contact round-trips through SerializableContact`() {
        val pk = ByteArray(32) { (it * 7).toByte() }
        val original = Contact(publicKey = pk, displayName = "Rowan", pairedAt = 1_700_000_000_000L)
        val restored = original.toSerializable().toContact()
        assertThat(restored.displayName).isEqualTo(original.displayName)
        assertThat(restored.pairedAt).isEqualTo(original.pairedAt)
        assertThat(restored.publicKey).isEqualTo(original.publicKey)
    }

    @Test fun `list of SerializableContact JSON round-trips`() {
        val list = listOf(
            SerializableContact(pubKeyB64 = "AAA=", displayName = "A", pairedAt = 1),
            SerializableContact(pubKeyB64 = "AQI=", displayName = "B", pairedAt = 2)
        )
        val encoded = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SerializableContact.serializer()), list)
        val decoded = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SerializableContact.serializer()), encoded)
        assertThat(decoded).isEqualTo(list)
    }

    @Test fun `Contact equals and hashCode account for pubkey by content`() {
        val pk = ByteArray(32) { 1 }
        val a = Contact(publicKey = pk.copyOf(), displayName = "x", pairedAt = 0)
        val b = Contact(publicKey = pk.copyOf(), displayName = "x", pairedAt = 0)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.data.*"`
Expected: compilation failure.

- [ ] **Step 3: Implement `Fingerprint.kt`**

Create `app/src/main/java/com/earshot/app/data/Fingerprint.kt`:
```kotlin
package com.earshot.app.data

private val HEX_CHARS = "0123456789abcdef".toCharArray()

fun ByteArray.fingerprint(): String {
    require(size == 32) { "expected 32-byte key, was $size" }
    val prefix = toHex(0, 2)   // 4 hex chars
    val suffix = toHex(30, 32) // 4 hex chars
    return "$prefix…$suffix"
}

private fun ByteArray.toHex(fromInclusive: Int, toExclusive: Int): String {
    val sb = StringBuilder((toExclusive - fromInclusive) * 2)
    for (i in fromInclusive until toExclusive) {
        val b = this[i].toInt() and 0xFF
        sb.append(HEX_CHARS[b ushr 4])
        sb.append(HEX_CHARS[b and 0x0F])
    }
    return sb.toString()
}
```

- [ ] **Step 4: Implement `LocalIdentity.kt` and `Contact.kt`**

Create `app/src/main/java/com/earshot/app/data/LocalIdentity.kt`:
```kotlin
package com.earshot.app.data

class LocalIdentity(
    val displayName: String,
    val publicKey: ByteArray,
    val privateKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocalIdentity) return false
        return displayName == other.displayName &&
            publicKey.contentEquals(other.publicKey) &&
            privateKey.contentEquals(other.privateKey)
    }
    override fun hashCode(): Int {
        var r = displayName.hashCode()
        r = 31 * r + publicKey.contentHashCode()
        r = 31 * r + privateKey.contentHashCode()
        return r
    }
}
```

Create `app/src/main/java/com/earshot/app/data/Contact.kt`:
```kotlin
package com.earshot.app.data

class Contact(
    val publicKey: ByteArray,
    val displayName: String,
    val pairedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return pairedAt == other.pairedAt &&
            displayName == other.displayName &&
            publicKey.contentEquals(other.publicKey)
    }
    override fun hashCode(): Int {
        var r = publicKey.contentHashCode()
        r = 31 * r + displayName.hashCode()
        r = 31 * r + pairedAt.hashCode()
        return r
    }
}
```

- [ ] **Step 5: Implement `SerializableContact.kt` with conversions**

Create `app/src/main/java/com/earshot/app/data/SerializableContact.kt`:
```kotlin
package com.earshot.app.data

import android.util.Base64
import kotlinx.serialization.Serializable

@Serializable
data class SerializableContact(
    val pubKeyB64: String,
    val displayName: String,
    val pairedAt: Long
)

fun Contact.toSerializable(): SerializableContact =
    SerializableContact(
        pubKeyB64 = Base64.encodeToString(publicKey, Base64.NO_WRAP),
        displayName = displayName,
        pairedAt = pairedAt
    )

fun SerializableContact.toContact(): Contact =
    Contact(
        publicKey = Base64.decode(pubKeyB64, Base64.NO_WRAP),
        displayName = displayName,
        pairedAt = pairedAt
    )
```

`android.util.Base64` requires an Android runtime. Because our unit tests run in the JVM, add a shim: create `app/src/test/java/com/earshot/app/data/Base64Shim.kt` — no, cleaner to use `java.util.Base64` on JVM. Update `SerializableContact.kt` to prefer the java stdlib variant so the code is JVM-testable:

Replace the Android imports with:
```kotlin
import java.util.Base64
```
and the encode/decode with:
```kotlin
pubKeyB64 = Base64.getEncoder().withoutPadding().encodeToString(publicKey)
// on decode: Base64.getDecoder().decode(pubKeyB64)
```
`java.util.Base64` is available since JDK 8 and works both on Android (API 26+, we're at minSdk 33) and on the JVM test runner. Prefer this to `android.util.Base64` for testability.

Final `SerializableContact.kt`:
```kotlin
package com.earshot.app.data

import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
data class SerializableContact(
    val pubKeyB64: String,
    val displayName: String,
    val pairedAt: Long
)

fun Contact.toSerializable(): SerializableContact =
    SerializableContact(
        pubKeyB64 = Base64.getEncoder().withoutPadding().encodeToString(publicKey),
        displayName = displayName,
        pairedAt = pairedAt
    )

fun SerializableContact.toContact(): Contact =
    Contact(
        publicKey = Base64.getDecoder().decode(pubKeyB64),
        displayName = displayName,
        pairedAt = pairedAt
    )
```

- [ ] **Step 6: Run and verify all tests pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.data.*"`
Expected: all tests green.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/earshot/app/data/
git add app/src/test/java/com/earshot/app/data/
git commit -m "feat: data model — LocalIdentity, Contact, SerializableContact, fingerprint()

Contact/LocalIdentity carry ByteArrays and override equals/hashCode
via contentEquals to avoid the reference-equality trap. Base64 uses
the JDK-stdlib variant so unit tests run on the JVM without a shim.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: ContactsRepository

**Files:**
- Create: `app/src/main/java/com/earshot/app/data/ContactsRepository.kt`
- Create: `app/src/test/java/com/earshot/app/data/ContactsRepositoryTest.kt`

**Interfaces:**
- Consumes: `Contact`, `SerializableContact`, `toSerializable()`, `toContact()` from Task 4.
- Produces:
  ```kotlin
  class ContactsRepository(private val ds: DataStore<Preferences>) {
      fun contacts(): Flow<List<Contact>>       // sorted pairedAt desc
      suspend fun findByPubKey(pk: ByteArray): Contact?
      suspend fun findByDisplayName(name: String): Contact?
      suspend fun upsert(contact: Contact)
      suspend fun clear()                        // debug/reset only
  }
  ```

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/earshot/app/data/ContactsRepositoryTest.kt`:
```kotlin
package com.earshot.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class ContactsRepositoryTest {

    private lateinit var tmpDir: File
    private lateinit var ds: DataStore<Preferences>
    private lateinit var repo: ContactsRepository

    @Before fun setUp() {
        tmpDir = createTempDirectory()
        ds = PreferenceDataStoreFactory.create(
            produceFile = { File(tmpDir, "test.preferences_pb") }
        )
        repo = ContactsRepository(ds)
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun createTempDirectory(): File =
        File.createTempFile("earshot-test", null).apply {
            delete()
            mkdirs()
        }

    private fun contact(pkByte: Byte, name: String, pairedAt: Long) =
        Contact(publicKey = ByteArray(32) { pkByte }, displayName = name, pairedAt = pairedAt)

    @Test fun `contacts starts empty`() = runTest {
        assertThat(repo.contacts().first()).isEmpty()
    }

    @Test fun `upsert inserts new contact`() = runTest {
        val c = contact(1, "Rowan", 100)
        repo.upsert(c)
        assertThat(repo.contacts().first()).containsExactly(c)
    }

    @Test fun `contacts sorted by pairedAt descending`() = runTest {
        repo.upsert(contact(1, "A", 100))
        repo.upsert(contact(2, "B", 300))
        repo.upsert(contact(3, "C", 200))
        val list = repo.contacts().first()
        assertThat(list.map { it.displayName }).containsExactly("B", "C", "A").inOrder()
    }

    @Test fun `upsert with same pubkey replaces existing`() = runTest {
        repo.upsert(contact(1, "OldName", 100))
        repo.upsert(contact(1, "NewName", 500))
        val list = repo.contacts().first()
        assertThat(list).hasSize(1)
        assertThat(list[0].displayName).isEqualTo("NewName")
        assertThat(list[0].pairedAt).isEqualTo(500)
    }

    @Test fun `findByPubKey returns match`() = runTest {
        val c = contact(7, "R", 100)
        repo.upsert(c)
        assertThat(repo.findByPubKey(ByteArray(32) { 7 })).isEqualTo(c)
    }

    @Test fun `findByPubKey returns null for miss`() = runTest {
        repo.upsert(contact(1, "R", 100))
        assertThat(repo.findByPubKey(ByteArray(32) { 2 })).isNull()
    }

    @Test fun `findByDisplayName returns match`() = runTest {
        val c = contact(1, "Rowan", 100)
        repo.upsert(c)
        assertThat(repo.findByDisplayName("Rowan")).isEqualTo(c)
    }

    @Test fun `findByDisplayName is case sensitive`() = runTest {
        repo.upsert(contact(1, "Rowan", 100))
        assertThat(repo.findByDisplayName("rowan")).isNull()
    }

    @Test fun `clear empties the store`() = runTest {
        repo.upsert(contact(1, "A", 100))
        repo.upsert(contact(2, "B", 200))
        repo.clear()
        assertThat(repo.contacts().first()).isEmpty()
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.data.ContactsRepositoryTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `ContactsRepository`**

Create `app/src/main/java/com/earshot/app/data/ContactsRepository.kt`:
```kotlin
package com.earshot.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ContactsRepository(private val ds: DataStore<Preferences>) {

    private val key = stringPreferencesKey("contacts_json")
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(SerializableContact.serializer())

    fun contacts(): Flow<List<Contact>> = ds.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        json.decodeFromString(listSerializer, raw)
            .map { it.toContact() }
            .sortedByDescending { it.pairedAt }
    }

    suspend fun findByPubKey(pk: ByteArray): Contact? =
        contacts().first().firstOrNull { it.publicKey.contentEquals(pk) }

    suspend fun findByDisplayName(name: String): Contact? =
        contacts().first().firstOrNull { it.displayName == name }

    suspend fun upsert(contact: Contact) {
        ds.edit { prefs ->
            val existing = readList(prefs).toMutableList()
            val idx = existing.indexOfFirst { it.pubKeyB64 == contact.toSerializable().pubKeyB64 }
            val ser = contact.toSerializable()
            if (idx >= 0) existing[idx] = ser else existing.add(ser)
            prefs[key] = json.encodeToString(listSerializer, existing)
        }
    }

    suspend fun clear() {
        ds.edit { it.remove(key) }
    }

    private fun readList(prefs: Preferences): List<SerializableContact> {
        val raw = prefs[key] ?: return emptyList()
        return json.decodeFromString(listSerializer, raw)
    }
}
```

- [ ] **Step 4: Run and verify all pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.data.ContactsRepositoryTest"`
Expected: 9 tests passing. If `PreferenceDataStoreFactory` isn't resolving in the test classpath, add `testImplementation("androidx.datastore:datastore-preferences:1.1.1")` mirroring the main dep.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/earshot/app/data/ContactsRepository.kt
git add app/src/test/java/com/earshot/app/data/ContactsRepositoryTest.kt
# possibly:
git add app/build.gradle.kts
git commit -m "feat: ContactsRepository backed by DataStore Preferences

Contacts held as a JSON blob under a single preference key; small
scale, atomic reads/writes, sorted by pairedAt desc when observed.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: IdentityRepository

**Files:**
- Create: `app/src/main/java/com/earshot/app/data/IdentityRepository.kt`
- Create: `app/src/test/java/com/earshot/app/data/IdentityRepositoryTest.kt`

**Interfaces:**
- Consumes: `LocalIdentity` (Task 4), `SodiumHolder` (Task 3).
- Produces:
  ```kotlin
  class IdentityRepository(private val ds: DataStore<Preferences>) {
      fun identity(): Flow<LocalIdentity?>
      suspend fun createIdentity(displayName: String): LocalIdentity
      suspend fun hasIdentity(): Boolean
  }
  ```

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/earshot/app/data/IdentityRepositoryTest.kt`:
```kotlin
package com.earshot.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class IdentityRepositoryTest {

    private lateinit var tmpDir: File
    private lateinit var ds: DataStore<Preferences>
    private lateinit var repo: IdentityRepository

    @Before fun setUp() {
        tmpDir = File.createTempFile("earshot-idr", null).apply { delete(); mkdirs() }
        ds = PreferenceDataStoreFactory.create(
            produceFile = { File(tmpDir, "test.preferences_pb") }
        )
        repo = IdentityRepository(ds)
    }

    @After fun tearDown() { tmpDir.deleteRecursively() }

    @Test fun `identity emits null when nothing stored`() = runTest {
        assertThat(repo.identity().first()).isNull()
    }

    @Test fun `hasIdentity is false initially`() = runTest {
        assertThat(repo.hasIdentity()).isFalse()
    }

    @Test fun `createIdentity produces valid X25519 keypair and persists`() = runTest {
        val id = repo.createIdentity("Reece")
        assertThat(id.displayName).isEqualTo("Reece")
        assertThat(id.publicKey).hasLength(32)
        assertThat(id.privateKey).hasLength(32)
        assertThat(repo.hasIdentity()).isTrue()
        val restored = repo.identity().first()!!
        assertThat(restored).isEqualTo(id)
    }

    @Test fun `createIdentity trims whitespace from display name`() = runTest {
        val id = repo.createIdentity("  Reece  ")
        assertThat(id.displayName).isEqualTo("Reece")
    }

    @Test fun `createIdentity rejects blank name`() = runTest {
        try {
            repo.createIdentity("   ")
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun `createIdentity rejects name over 64 UTF-8 bytes`() = runTest {
        try {
            repo.createIdentity("a".repeat(65))
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.data.IdentityRepositoryTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `IdentityRepository`**

Create `app/src/main/java/com/earshot/app/data/IdentityRepository.kt`:
```kotlin
package com.earshot.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.earshot.app.crypto.SodiumHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64

class IdentityRepository(private val ds: DataStore<Preferences>) {

    private val nameKey = stringPreferencesKey("my_display_name")
    private val pubKey  = stringPreferencesKey("my_public_key")
    private val privKey = stringPreferencesKey("my_private_key")

    fun identity(): Flow<LocalIdentity?> = ds.data.map { prefs ->
        val name = prefs[nameKey] ?: return@map null
        val pub = prefs[pubKey]?.let { Base64.getDecoder().decode(it) } ?: return@map null
        val priv = prefs[privKey]?.let { Base64.getDecoder().decode(it) } ?: return@map null
        LocalIdentity(name, pub, priv)
    }

    suspend fun hasIdentity(): Boolean = identity().first() != null

    suspend fun createIdentity(displayName: String): LocalIdentity {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "display name must not be blank" }
        require(trimmed.toByteArray(Charsets.UTF_8).size <= 64) {
            "display name must be <= 64 UTF-8 bytes"
        }
        val kp = SodiumHolder.lazySodium.cryptoBoxKeypair()
        val identity = LocalIdentity(
            displayName = trimmed,
            publicKey = kp.publicKey.asBytes,
            privateKey = kp.secretKey.asBytes
        )
        ds.edit { prefs ->
            prefs[nameKey] = identity.displayName
            prefs[pubKey]  = Base64.getEncoder().withoutPadding().encodeToString(identity.publicKey)
            prefs[privKey] = Base64.getEncoder().withoutPadding().encodeToString(identity.privateKey)
        }
        return identity
    }
}
```

- [ ] **Step 4: Run and verify all pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.data.IdentityRepositoryTest"`
Expected: 6 tests passing. If Lazysodium's native lib isn't loading in unit tests, mark this test class `@Ignore` at the class level with a note, and cover it via manual on-device verification instead. This is a reasonable escape hatch because `cryptoBoxKeypair()` is thin — mostly random bytes — and there's little logic to unit-test beyond "keys are 32 bytes and get persisted."

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/earshot/app/data/IdentityRepository.kt
git add app/src/test/java/com/earshot/app/data/IdentityRepositoryTest.kt
git commit -m "feat: IdentityRepository generates and persists X25519 keypair

createIdentity emits an X25519 keypair from Lazysodium's crypto_box
and persists (name, pub, priv) to DataStore. Rejects blank or
oversize names.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Manifest additions, apdu_service.xml, strings.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/apdu_service.xml`
- Create: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: manifest advertises HCE with our AID; string resources referenced by later tasks exist.

- [ ] **Step 1: Create `res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Earshot</string>
    <string name="hce_service_description">Earshot contact pairing</string>
    <string name="aid_group_description">Earshot pairing AID</string>
</resources>
```

- [ ] **Step 2: Create `res/xml/apdu_service.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
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

- [ ] **Step 3: Update `AndroidManifest.xml`**

Rewrite the file to include NFC permission, feature declarations, and the HCE service. The existing Wi-Fi Aware entries are preserved verbatim:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature
        android:name="android.hardware.wifi.aware"
        android:required="false" />

    <uses-feature android:name="android.hardware.nfc"     android:required="false" />
    <uses-feature android:name="android.hardware.nfc.hce" android:required="false" />

    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission
        android:name="android.permission.NEARBY_WIFI_DEVICES"
        android:usesPermissionFlags="neverForLocation" />

    <uses-permission android:name="android.permission.NFC" />

    <application
        android:label="@string/app_name"
        android:allowBackup="false"
        android:theme="@android:style/Theme.Material.Light.NoActionBar"
        android:name=".EarshotApp">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

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

    </application>
</manifest>
```

Notes:
- `android:name=".EarshotApp"` on `<application>` points to a not-yet-existing custom Application class. Task 8 creates it. Build will fail until then. This is intentional — the manifest wiring is coherent by design.
- `.MainActivity` and `.nfc.EarshotHceService` similarly forward-reference not-yet-existing classes. Same rationale.

- [ ] **Step 4: Do NOT build yet**

The manifest references classes that don't exist. Deliberately deferred — Task 8 fills them in.

- [ ] **Step 5: Commit (manifest+resources only, deliberately red build)**

```powershell
git add app/src/main/res/values/strings.xml
git add app/src/main/res/xml/apdu_service.xml
git add app/src/main/AndroidManifest.xml
git commit -m "feat: manifest wiring for HCE service and NFC permission

Declares AID F045415253484F5401 via apdu_service.xml. Adds NFC
permission and non-required nfc/nfc.hce features. Forward-references
EarshotApp and EarshotHceService — introduced in the next task.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```
If your workflow requires green commits, you can defer this commit until Task 8's completion — but the deliberate red state is a useful checkpoint if a reviewer later asks "why do we have this AID."

---

### Task 8: EarshotApp + AppContainer + EarshotHceService (skeleton)

**Files:**
- Create: `app/src/main/java/com/earshot/app/EarshotApp.kt`
- Create: `app/src/main/java/com/earshot/app/AppContainer.kt`
- Create: `app/src/main/java/com/earshot/app/nfc/EarshotHceService.kt`
- Create: `app/src/main/java/com/earshot/app/nfc/HceHelpers.kt`
- Create: `app/src/test/java/com/earshot/app/nfc/HceHelpersTest.kt`

**Interfaces:**
- Consumes: `IdentityRepository`, `ContactsRepository` (Tasks 5, 6); `PayloadCodec` (Task 2).
- Produces:
  ```kotlin
  class EarshotApp : Application { val container: AppContainer }
  class AppContainer(context: Context) {
      val dataStore: DataStore<Preferences>
      val identityRepo: IdentityRepository
      val contactsRepo: ContactsRepository
      val pairingBridge: PairingBridge
  }
  class PairingBridge {  // shared state between HCE service and ViewModel
      val currentPayload: AtomicReference<ByteArray?>
      val onReadCallback: AtomicReference<(() -> Unit)?>
  }
  class EarshotHceService : HostApduService()  // registered in manifest
  object HceHelpers {
      const val AID_HEX = "F045415253484F5401"
      val AID_BYTES: ByteArray
      fun parseSelectAid(apdu: ByteArray): Boolean  // true if this is SELECT AID for our AID
      fun buildResponse(payload: ByteArray): ByteArray  // payload + SW 9000
      val SW_NOT_FOUND: ByteArray  // 6A82
      val SW_OK: ByteArray          // 9000
  }
  ```

The `PairingBridge` is the mechanism by which the AddContact screen tells the manifest-registered HCE service what payload to serve, and gets notified when a read happens. Android instantiates `EarshotHceService` on demand — the ViewModel can't push a reference into it directly, so we rendezvous via a singleton held on `AppContainer`.

- [ ] **Step 1: Write failing tests for `HceHelpers`**

Create `app/src/test/java/com/earshot/app/nfc/HceHelpersTest.kt`:
```kotlin
package com.earshot.app.nfc

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HceHelpersTest {

    @Test fun `AID_BYTES matches the hex spec`() {
        assertThat(HceHelpers.AID_BYTES.toHex()).isEqualTo("F045415253484F5401".lowercase())
    }

    @Test fun `parseSelectAid returns true for valid SELECT AID matching ours`() {
        val select = "00A404000900".hexToBytes() + HceHelpers.AID_BYTES + "00".hexToBytes()
        assertThat(HceHelpers.parseSelectAid(select)).isTrue()
    }

    @Test fun `parseSelectAid returns false for SELECT AID of a different AID`() {
        val otherAid = ByteArray(9) { 0xAA.toByte() }
        val select = "00A404000900".hexToBytes() + otherAid + "00".hexToBytes()
        assertThat(HceHelpers.parseSelectAid(select)).isFalse()
    }

    @Test fun `parseSelectAid returns false for non-SELECT command`() {
        val other = "00B000000000".hexToBytes()  // READ BINARY
        assertThat(HceHelpers.parseSelectAid(other)).isFalse()
    }

    @Test fun `parseSelectAid returns false for truncated APDU`() {
        assertThat(HceHelpers.parseSelectAid("00A4".hexToBytes())).isFalse()
    }

    @Test fun `buildResponse appends SW 9000`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val response = HceHelpers.buildResponse(payload)
        assertThat(response.copyOfRange(0, 3)).isEqualTo(payload)
        assertThat(response.copyOfRange(3, 5)).isEqualTo(HceHelpers.SW_OK)
    }
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it.toInt() and 0xFF) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0)
    return ByteArray(length / 2) {
        val hi = Character.digit(this[it * 2], 16)
        val lo = Character.digit(this[it * 2 + 1], 16)
        ((hi shl 4) or lo).toByte()
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.nfc.HceHelpersTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `HceHelpers`**

Create `app/src/main/java/com/earshot/app/nfc/HceHelpers.kt`:
```kotlin
package com.earshot.app.nfc

object HceHelpers {
    const val AID_HEX = "F045415253484F5401"
    val AID_BYTES: ByteArray = AID_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    val SW_OK: ByteArray = byteArrayOf(0x90.toByte(), 0x00)
    val SW_NOT_FOUND: ByteArray = byteArrayOf(0x6A, 0x82.toByte())

    // SELECT (CLA=00 or 80, INS=A4, P1=04, P2=00, Lc=<aid_len>, <aid>, Le)
    fun parseSelectAid(apdu: ByteArray): Boolean {
        if (apdu.size < 5 + AID_BYTES.size) return false
        val cla = apdu[0].toInt() and 0xFF
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF
        val lc = apdu[4].toInt() and 0xFF
        if (ins != 0xA4 || p1 != 0x04 || p2 != 0x00) return false
        if (cla != 0x00 && cla != 0x80) return false
        if (lc != AID_BYTES.size) return false
        if (apdu.size < 5 + lc) return false
        for (i in 0 until lc) {
            if (apdu[5 + i] != AID_BYTES[i]) return false
        }
        return true
    }

    fun buildResponse(payload: ByteArray): ByteArray = payload + SW_OK
}
```

- [ ] **Step 4: Run and verify HceHelpers tests pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.nfc.HceHelpersTest"`
Expected: 6 tests passing.

- [ ] **Step 5: Create `PairingBridge`**

Create `app/src/main/java/com/earshot/app/nfc/PairingBridge.kt`:
```kotlin
package com.earshot.app.nfc

import java.util.concurrent.atomic.AtomicReference

class PairingBridge {
    private val payloadRef = AtomicReference<ByteArray?>(null)
    private val callbackRef = AtomicReference<(() -> Unit)?>(null)

    fun setPayload(payload: ByteArray?) { payloadRef.set(payload) }
    fun currentPayload(): ByteArray? = payloadRef.get()

    fun onRead(callback: (() -> Unit)?) { callbackRef.set(callback) }
    fun notifyRead() { callbackRef.get()?.invoke() }
}
```

- [ ] **Step 6: Create `EarshotHceService`**

Create `app/src/main/java/com/earshot/app/nfc/EarshotHceService.kt`:
```kotlin
package com.earshot.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.earshot.app.EarshotApp

class EarshotHceService : HostApduService() {

    private val bridge: PairingBridge?
        get() = (application as? EarshotApp)?.container?.pairingBridge

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || !HceHelpers.parseSelectAid(commandApdu)) {
            return HceHelpers.SW_NOT_FOUND
        }
        val payload = bridge?.currentPayload() ?: return HceHelpers.SW_NOT_FOUND
        bridge?.notifyRead()
        return HceHelpers.buildResponse(payload)
    }

    override fun onDeactivated(reason: Int) { /* nothing to do */ }
}
```

- [ ] **Step 7: Create `AppContainer` and `EarshotApp`**

Create `app/src/main/java/com/earshot/app/AppContainer.kt`:
```kotlin
package com.earshot.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.earshot.app.data.ContactsRepository
import com.earshot.app.data.IdentityRepository
import com.earshot.app.nfc.PairingBridge

class AppContainer(context: Context) {
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("earshot_prefs") }
    )
    val identityRepo = IdentityRepository(dataStore)
    val contactsRepo = ContactsRepository(dataStore)
    val pairingBridge = PairingBridge()
}
```

Create `app/src/main/java/com/earshot/app/EarshotApp.kt`:
```kotlin
package com.earshot.app

import android.app.Application

class EarshotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
```

- [ ] **Step 8: Verify the whole project builds again**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. The manifest's forward references now resolve.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/com/earshot/app/EarshotApp.kt
git add app/src/main/java/com/earshot/app/AppContainer.kt
git add app/src/main/java/com/earshot/app/nfc/EarshotHceService.kt
git add app/src/main/java/com/earshot/app/nfc/HceHelpers.kt
git add app/src/main/java/com/earshot/app/nfc/PairingBridge.kt
git add app/src/test/java/com/earshot/app/nfc/HceHelpersTest.kt
git commit -m "feat: HCE service skeleton + AppContainer wiring

EarshotHceService parses SELECT AID for our AID and hands back
whatever payload the AddContact screen has posted to PairingBridge.
No payload -> 6A82 (File Not Found). No dependency on any UI code.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: PairingController state machine

**Files:**
- Create: `app/src/main/java/com/earshot/app/nfc/PairingController.kt`
- Create: `app/src/test/java/com/earshot/app/nfc/PairingControllerTest.kt`

**Interfaces:**
- Consumes: `Payload`, `PayloadCodec` (Task 2); `PairingBridge` (Task 8).
- Produces:
  ```kotlin
  enum class PairingRole { READER, HCE }
  enum class PairingPhase { IDLE, MANUAL_SENDING, MANUAL_RECEIVING, AUTO_PHASE_1, AUTO_PHASE_2, COMPLETE, TIMED_OUT }

  sealed interface PairingEvent {
      data class ReaderReadSucceeded(val payload: Payload) : PairingEvent
      object HceWasRead : PairingEvent
      object Timeout : PairingEvent
      object Cancelled : PairingEvent
  }

  class PairingController(
      private val myPayload: ByteArray,
      private val onRoleChange: (PairingRole?) -> Unit,   // null = disabled
      private val onComplete: (myReadOfThem: Payload) -> Unit,
      private val onTimeout: () -> Unit,
      private val scope: CoroutineScope,
      private val clock: () -> Long = System::currentTimeMillis,
      private val periodMs: LongRange = 250L..350L
  ) {
      fun startAuto()
      fun startManualSend()
      fun startManualReceive()
      fun onEvent(event: PairingEvent)
      fun cancel()
      val phase: StateFlow<PairingPhase>
  }
  ```

The controller is unit-testable because it accepts a `CoroutineScope`, a `clock` for deterministic time, and a callback interface for driving role changes. The `PairingBridge` and reader-mode calls are the ADAPTER LAYER — plugged in by the ViewModel, not by the controller itself.

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/earshot/app/nfc/PairingControllerTest.kt`:
```kotlin
package com.earshot.app.nfc

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingControllerTest {

    private val myPayload = byteArrayOf(1, 2, 3)
    private val theirPayload = Payload("Rowan", ByteArray(32) { 9 })

    private class Fake {
        val roles = mutableListOf<PairingRole?>()
        var complete: Payload? = null
        var timedOut = false
    }

    private fun build(scope: CoroutineScope): Pair<PairingController, Fake> {
        val f = Fake()
        val c = PairingController(
            myPayload = myPayload,
            onRoleChange = { f.roles += it },
            onComplete = { f.complete = it },
            onTimeout = { f.timedOut = true },
            scope = scope,
            clock = { 0L },
            periodMs = 100L..100L
        )
        return c to f
    }

    @Test fun `manual send then read succeeded then swap and hce was read completes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startManualSend()
        assertThat(f.roles.last()).isEqualTo(PairingRole.HCE)

        c.onEvent(PairingEvent.HceWasRead)  // one direction done
        assertThat(c.phase.value).isEqualTo(PairingPhase.MANUAL_RECEIVING)
        assertThat(f.roles.last()).isEqualTo(PairingRole.READER)

        c.onEvent(PairingEvent.ReaderReadSucceeded(theirPayload))
        assertThat(c.phase.value).isEqualTo(PairingPhase.COMPLETE)
        assertThat(f.complete).isEqualTo(theirPayload)
    }

    @Test fun `auto mode alternates roles on the timer`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        assertThat(c.phase.value).isEqualTo(PairingPhase.AUTO_PHASE_1)
        val firstRole = f.roles.last()
        advanceTimeBy(150L)
        val secondRole = f.roles.last()
        assertThat(firstRole).isNotNull()
        assertThat(secondRole).isNotNull()
        assertThat(secondRole).isNotEqualTo(firstRole)
    }

    @Test fun `auto phase 1 exits to phase 2 with HCE role when reader read succeeds first`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        c.onEvent(PairingEvent.ReaderReadSucceeded(theirPayload))
        assertThat(c.phase.value).isEqualTo(PairingPhase.AUTO_PHASE_2)
        assertThat(f.roles.last()).isEqualTo(PairingRole.HCE)

        c.onEvent(PairingEvent.HceWasRead)
        assertThat(c.phase.value).isEqualTo(PairingPhase.COMPLETE)
        assertThat(f.complete).isEqualTo(theirPayload)
    }

    @Test fun `auto phase 1 exits to phase 2 with READER role when hce was read first`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        c.onEvent(PairingEvent.HceWasRead)
        assertThat(c.phase.value).isEqualTo(PairingPhase.AUTO_PHASE_2)
        assertThat(f.roles.last()).isEqualTo(PairingRole.READER)

        c.onEvent(PairingEvent.ReaderReadSucceeded(theirPayload))
        assertThat(c.phase.value).isEqualTo(PairingPhase.COMPLETE)
    }

    @Test fun `timeout fires after 30 seconds without completion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        advanceTimeBy(30_000L + 100L)
        assertThat(c.phase.value).isEqualTo(PairingPhase.TIMED_OUT)
        assertThat(f.timedOut).isTrue()
    }

    @Test fun `cancel disables role and moves to IDLE`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val (c, f) = build(scope)

        c.startAuto()
        c.cancel()
        assertThat(c.phase.value).isEqualTo(PairingPhase.IDLE)
        assertThat(f.roles.last()).isNull()
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.nfc.PairingControllerTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement `PairingController`**

Create `app/src/main/java/com/earshot/app/nfc/PairingController.kt`:
```kotlin
package com.earshot.app.nfc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class PairingRole { READER, HCE }
enum class PairingPhase {
    IDLE, MANUAL_SENDING, MANUAL_RECEIVING,
    AUTO_PHASE_1, AUTO_PHASE_2, COMPLETE, TIMED_OUT
}

sealed interface PairingEvent {
    data class ReaderReadSucceeded(val payload: Payload) : PairingEvent
    data object HceWasRead : PairingEvent
    data object Timeout : PairingEvent
    data object Cancelled : PairingEvent
}

class PairingController(
    private val myPayload: ByteArray,
    private val onRoleChange: (PairingRole?) -> Unit,
    private val onComplete: (Payload) -> Unit,
    private val onTimeout: () -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val periodMs: LongRange = 250L..350L
) {
    private val _phase = MutableStateFlow(PairingPhase.IDLE)
    val phase: StateFlow<PairingPhase> = _phase

    private var currentRole: PairingRole? = null
    private var alternationJob: Job? = null
    private var timeoutJob: Job? = null

    private var wasReadByPeer: Boolean = false
    private var readFromPeer: Payload? = null

    fun startAuto() {
        reset()
        _phase.value = PairingPhase.AUTO_PHASE_1
        setRole(if (Random.nextBoolean()) PairingRole.READER else PairingRole.HCE)
        alternationJob = scope.launch {
            while (_phase.value == PairingPhase.AUTO_PHASE_1) {
                delay(Random.nextLong(periodMs.first, periodMs.last + 1))
                if (_phase.value != PairingPhase.AUTO_PHASE_1) break
                flipRole()
            }
        }
        armTimeout()
    }

    fun startManualSend() {
        reset()
        _phase.value = PairingPhase.MANUAL_SENDING
        setRole(PairingRole.HCE)
    }

    fun startManualReceive() {
        reset()
        _phase.value = PairingPhase.MANUAL_RECEIVING
        setRole(PairingRole.READER)
    }

    fun onEvent(event: PairingEvent) {
        when (event) {
            is PairingEvent.ReaderReadSucceeded -> {
                readFromPeer = event.payload
                progress()
            }
            PairingEvent.HceWasRead -> {
                wasReadByPeer = true
                progress()
            }
            PairingEvent.Timeout -> {
                _phase.value = PairingPhase.TIMED_OUT
                setRole(null)
                onTimeout()
            }
            PairingEvent.Cancelled -> cancel()
        }
    }

    fun cancel() {
        alternationJob?.cancel()
        timeoutJob?.cancel()
        alternationJob = null
        timeoutJob = null
        setRole(null)
        _phase.value = PairingPhase.IDLE
        wasReadByPeer = false
        readFromPeer = null
    }

    private fun progress() {
        val phaseNow = _phase.value
        val bothDone = readFromPeer != null && wasReadByPeer

        if (bothDone) {
            alternationJob?.cancel()
            timeoutJob?.cancel()
            setRole(null)
            _phase.value = PairingPhase.COMPLETE
            onComplete(readFromPeer!!)
            return
        }

        when (phaseNow) {
            PairingPhase.AUTO_PHASE_1 -> {
                alternationJob?.cancel()
                alternationJob = null
                _phase.value = PairingPhase.AUTO_PHASE_2
                // Deterministic swap:
                //  - if we JUST read, we now serve (HCE)
                //  - if we JUST got read, we now read (READER)
                setRole(
                    if (readFromPeer != null) PairingRole.HCE else PairingRole.READER
                )
            }
            PairingPhase.MANUAL_SENDING -> {
                if (wasReadByPeer) {
                    _phase.value = PairingPhase.MANUAL_RECEIVING
                    setRole(PairingRole.READER)
                }
            }
            PairingPhase.MANUAL_RECEIVING -> {
                if (readFromPeer != null) {
                    _phase.value = PairingPhase.MANUAL_SENDING
                    setRole(PairingRole.HCE)
                }
            }
            else -> Unit
        }
    }

    private fun flipRole() {
        setRole(if (currentRole == PairingRole.READER) PairingRole.HCE else PairingRole.READER)
    }

    private fun setRole(role: PairingRole?) {
        if (role != currentRole) {
            currentRole = role
            onRoleChange(role)
        }
    }

    private fun armTimeout() {
        timeoutJob = scope.launch {
            delay(30_000L)
            if (_phase.value != PairingPhase.COMPLETE) {
                onEvent(PairingEvent.Timeout)
            }
        }
    }

    private fun reset() {
        alternationJob?.cancel()
        timeoutJob?.cancel()
        alternationJob = null
        timeoutJob = null
        wasReadByPeer = false
        readFromPeer = null
    }
}
```

- [ ] **Step 4: Run and verify tests pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.earshot.app.nfc.PairingControllerTest"`
Expected: 7 tests passing.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/earshot/app/nfc/PairingController.kt
git add app/src/test/java/com/earshot/app/nfc/PairingControllerTest.kt
git commit -m "feat: PairingController state machine

Auto mode alternates every 250-350ms, then swaps deterministically to
the second phase on first exchange. Manual mode drives roles from
user button presses. Uses injected coroutine scope + clock for
deterministic tests.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: NfcReader helper

**Files:**
- Create: `app/src/main/java/com/earshot/app/nfc/NfcReader.kt`

**Interfaces:**
- Consumes: `HceHelpers.AID_BYTES`, `PayloadCodec`, `Payload` (Tasks 2, 8).
- Produces:
  ```kotlin
  class NfcReader(private val activity: Activity) {
      fun start(onPayload: (Payload) -> Unit, onError: (Throwable) -> Unit)
      fun stop()
  }
  ```

NfcReader wraps `NfcAdapter.enableReaderMode`. It cannot easily be unit-tested (Android framework). It's exercised via manual on-hardware pass.

- [ ] **Step 1: Implement `NfcReader.kt`**

Create `app/src/main/java/com/earshot/app/nfc/NfcReader.kt`:
```kotlin
package com.earshot.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log

class NfcReader(private val activity: Activity) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var callback: NfcAdapter.ReaderCallback? = null

    fun start(onPayload: (Payload) -> Unit, onError: (Throwable) -> Unit) {
        val nfc = adapter ?: run {
            onError(IllegalStateException("no NFC adapter"))
            return
        }
        val cb = NfcAdapter.ReaderCallback { tag: Tag ->
            try {
                val payload = readOnce(tag)
                onPayload(payload)
            } catch (t: Throwable) {
                onError(t)
            }
        }
        callback = cb
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        nfc.enableReaderMode(activity, cb, flags, extras)
    }

    fun stop() {
        val nfc = adapter ?: return
        nfc.disableReaderMode(activity)
        callback = null
    }

    private fun readOnce(tag: Tag): Payload {
        val iso = IsoDep.get(tag) ?: error("tag is not IsoDep-capable")
        iso.connect()
        try {
            // SELECT AID
            val select = ByteArray(6 + HceHelpers.AID_BYTES.size)
            select[0] = 0x00
            select[1] = 0xA4.toByte()
            select[2] = 0x04
            select[3] = 0x00
            select[4] = HceHelpers.AID_BYTES.size.toByte()
            System.arraycopy(HceHelpers.AID_BYTES, 0, select, 5, HceHelpers.AID_BYTES.size)
            select[5 + HceHelpers.AID_BYTES.size] = 0x00  // Le
            val response = iso.transceive(select)
            require(response.size >= 2) { "response too short: ${response.size}" }
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF
            require(sw1 == 0x90 && sw2 == 0x00) {
                "peer returned SW %02X%02X".format(sw1, sw2)
            }
            val body = response.copyOfRange(0, response.size - 2)
            return PayloadCodec.decode(body).getOrThrow()
        } finally {
            runCatching { iso.close() }
        }
    }

    companion object { private const val TAG = "NfcReader" }
}
```

- [ ] **Step 2: Verify build**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/earshot/app/nfc/NfcReader.kt
git commit -m "feat: NfcReader wraps enableReaderMode with our SELECT AID

One transceive per tag: SELECT AID -> parse response body via
PayloadCodec. Exercised in the on-hardware test pass; no unit test
because it hits the NFC framework directly.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: AddContactViewModel

**Files:**
- Create: `app/src/main/java/com/earshot/app/ui/pairing/AddContactViewModel.kt`
- Create: `app/src/main/java/com/earshot/app/ui/pairing/AddContactUiState.kt`

**Interfaces:**
- Consumes: `PairingController`, `PairingBridge`, `NfcReader`, `PayloadCodec`, `Payload`, `IdentityRepository`, `ContactsRepository`, `SafetyCode`, all repositories.
- Produces:
  ```kotlin
  sealed interface AddContactUiState {
      data class Ready(val mode: Mode, val progress: Progress, val nfcState: NfcState) : AddContactUiState
      data class SafetyCode(val emojis: List<String>, val incoming: Payload) : AddContactUiState
      data class DuplicatePubKey(val existing: Contact, val incoming: Payload) : AddContactUiState
      data class DuplicateName(val existing: Contact, val incoming: Payload) : AddContactUiState
      data object Saved : AddContactUiState
      data class Failed(val reason: String) : AddContactUiState
  }
  enum class Mode { AUTO, MANUAL }
  enum class Progress { IDLE, IN_PROGRESS_ONE_SIDE, IN_PROGRESS_BOTH }
  enum class NfcState { OK, DISABLED, HW_ABSENT, HCE_ABSENT }
  class AddContactViewModel(app: EarshotApp, private val activityHost: () -> Activity?) : ViewModel() {
      val uiState: StateFlow<AddContactUiState>
      fun setMode(mode: Mode)
      fun startPairing()
      fun manualSend()
      fun manualReceive()
      fun onSafetyConfirmed()
      fun onDuplicateConfirmed()
      fun onDuplicateCancelled()
      fun cancel()
      fun retry()
  }
  ```

- [ ] **Step 1: Implement `AddContactUiState.kt`**

Create `app/src/main/java/com/earshot/app/ui/pairing/AddContactUiState.kt`:
```kotlin
package com.earshot.app.ui.pairing

import com.earshot.app.data.Contact
import com.earshot.app.nfc.Payload

enum class Mode { AUTO, MANUAL }
enum class Progress { IDLE, IN_PROGRESS_ONE_SIDE, IN_PROGRESS_BOTH }
enum class NfcState { OK, DISABLED, HW_ABSENT, HCE_ABSENT }

sealed interface AddContactUiState {
    data class Ready(
        val mode: Mode,
        val progress: Progress,
        val nfcState: NfcState
    ) : AddContactUiState

    data class SafetyCode(
        val emojis: List<String>,
        val incoming: Payload
    ) : AddContactUiState

    data class DuplicatePubKey(
        val existing: Contact,
        val incoming: Payload
    ) : AddContactUiState

    data class DuplicateName(
        val existing: Contact,
        val incoming: Payload
    ) : AddContactUiState

    data object Saved : AddContactUiState

    data class Failed(val reason: String) : AddContactUiState
}
```

- [ ] **Step 2: Implement `AddContactViewModel.kt`**

Create `app/src/main/java/com/earshot/app/ui/pairing/AddContactViewModel.kt`:
```kotlin
package com.earshot.app.ui.pairing

import android.app.Activity
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.earshot.app.EarshotApp
import com.earshot.app.crypto.SafetyCode
import com.earshot.app.data.Contact
import com.earshot.app.data.ContactsRepository
import com.earshot.app.data.IdentityRepository
import com.earshot.app.data.LocalIdentity
import com.earshot.app.nfc.NfcReader
import com.earshot.app.nfc.PairingBridge
import com.earshot.app.nfc.PairingController
import com.earshot.app.nfc.PairingEvent
import com.earshot.app.nfc.PairingRole
import com.earshot.app.nfc.Payload
import com.earshot.app.nfc.PayloadCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddContactViewModel(
    private val app: EarshotApp,
    private val activityHost: () -> Activity?
) : ViewModel() {

    private val identityRepo: IdentityRepository = app.container.identityRepo
    private val contactsRepo: ContactsRepository = app.container.contactsRepo
    private val bridge: PairingBridge = app.container.pairingBridge

    private val _uiState = MutableStateFlow<AddContactUiState>(
        AddContactUiState.Ready(Mode.AUTO, Progress.IDLE, computeNfcState())
    )
    val uiState: StateFlow<AddContactUiState> = _uiState

    private var mode: Mode = Mode.AUTO
    private var identity: LocalIdentity? = null
    private var incoming: Payload? = null
    private lateinit var controller: PairingController
    private var reader: NfcReader? = null

    init {
        viewModelScope.launch {
            val id = identityRepo.identity()
                .filterNotNull()
                .first()
            identity = id
            configureController()
            bridge.setPayload(PayloadCodec.encode(id.displayName, id.publicKey))
            bridge.onRead { controller.onEvent(PairingEvent.HceWasRead) }
        }
    }

    fun setMode(mode: Mode) {
        this.mode = mode
        cancel()
        _uiState.value = AddContactUiState.Ready(mode, Progress.IDLE, computeNfcState())
    }

    fun startPairing() {
        if (mode == Mode.AUTO) controller.startAuto()
        else _uiState.value = AddContactUiState.Ready(mode, Progress.IDLE, computeNfcState())
    }

    fun manualSend() { controller.startManualSend() }
    fun manualReceive() { controller.startManualReceive() }

    fun onSafetyConfirmed() {
        val inc = incoming ?: return
        viewModelScope.launch {
            val dupPk = contactsRepo.findByPubKey(inc.publicKey)
            if (dupPk != null) {
                _uiState.value = AddContactUiState.DuplicatePubKey(existing = dupPk, incoming = inc)
                return@launch
            }
            val dupName = contactsRepo.findByDisplayName(inc.displayName)
            if (dupName != null) {
                _uiState.value = AddContactUiState.DuplicateName(existing = dupName, incoming = inc)
                return@launch
            }
            persist(inc)
        }
    }

    fun onDuplicateConfirmed() {
        val inc = incoming ?: return
        viewModelScope.launch { persist(inc) }
    }

    fun onDuplicateCancelled() {
        incoming = null
        _uiState.value = AddContactUiState.Ready(mode, Progress.IDLE, computeNfcState())
    }

    fun cancel() {
        if (::controller.isInitialized) controller.cancel()
        reader?.stop()
        reader = null
        incoming = null
    }

    fun retry() {
        cancel()
        _uiState.value = AddContactUiState.Ready(mode, Progress.IDLE, computeNfcState())
    }

    override fun onCleared() {
        super.onCleared()
        cancel()
        bridge.setPayload(null)
        bridge.onRead(null)
    }

    private suspend fun persist(inc: Payload) {
        val me = identity ?: return
        if (inc.publicKey.contentEquals(me.publicKey)) {
            _uiState.value = AddContactUiState.Failed("You can't pair with your own phone.")
            return
        }
        contactsRepo.upsert(
            Contact(
                publicKey = inc.publicKey,
                displayName = inc.displayName,
                pairedAt = System.currentTimeMillis()
            )
        )
        _uiState.value = AddContactUiState.Saved
    }

    private fun configureController() {
        controller = PairingController(
            myPayload = PayloadCodec.encode(identity!!.displayName, identity!!.publicKey),
            onRoleChange = { role -> handleRoleChange(role) },
            onComplete = { theirs ->
                incoming = theirs
                val me = identity!!.publicKey
                val code = SafetyCode.derive(me, theirs.publicKey)
                _uiState.value = AddContactUiState.SafetyCode(code, theirs)
            },
            onTimeout = {
                _uiState.value = AddContactUiState.Failed("Pairing timed out.")
            },
            scope = viewModelScope
        )
    }

    private fun handleRoleChange(role: PairingRole?) {
        val activity = activityHost()
        when (role) {
            PairingRole.READER -> {
                reader?.stop()
                if (activity != null) {
                    reader = NfcReader(activity).also {
                        it.start(
                            onPayload = { p ->
                                controller.onEvent(PairingEvent.ReaderReadSucceeded(p))
                            },
                            onError = { /* auto retries via alternation; manual will surface via timeout */ }
                        )
                    }
                }
            }
            PairingRole.HCE -> {
                reader?.stop()
                reader = null
                // HCE is always live via the service + bridge; nothing else to enable here.
            }
            null -> {
                reader?.stop()
                reader = null
            }
        }
    }

    private fun computeNfcState(): NfcState {
        val ctx = app.applicationContext
        val pm = ctx.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC)) return NfcState.HW_ABSENT
        if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) return NfcState.HCE_ABSENT
        val adapter = NfcAdapter.getDefaultAdapter(ctx)
        return if (adapter?.isEnabled == true) NfcState.OK else NfcState.DISABLED
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T?>.collectFirstNonNull(): T {
    var out: T? = null
    kotlinx.coroutines.flow.collect(this) {
        if (it != null) { out = it; return@collect }
    }
    return out ?: error("flow never emitted a non-null value")
}
```
- [ ] **Step 3: Build**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/earshot/app/ui/pairing/
git commit -m "feat: AddContactViewModel wiring controller + reader + repos

Owns the pairing state machine, the reader-mode lifecycle (via
activityHost), and the bridge to the HCE service. Emits a
sealed UiState covering ready / safety-code / duplicate / saved /
failed.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Compose theme + Navigation + MainActivity refactor

**Files:**
- Modify: `app/src/main/java/com/earshot/app/MainActivity.kt`
- Create: `app/src/main/java/com/earshot/app/ui/EarshotNavHost.kt`
- Create: `app/src/main/java/com/earshot/app/ui/Route.kt`

**Interfaces:**
- Consumes: `IdentityRepository` (for first-launch detection); all screen composables (Tasks 13–17).
- Produces:
  ```kotlin
  sealed class Route(val path: String) {
      data object NameSetup : Route("name-setup")
      data object Home       : Route("home")
      data object Settings   : Route("settings")
      data object Diagnostic : Route("diagnostic")
      data object AddContact : Route("add-contact")
  }
  @Composable fun EarshotNavHost(startAt: Route, hostActivity: ComponentActivity)
  ```

- [ ] **Step 1: Create `Route.kt`**

```kotlin
package com.earshot.app.ui

sealed class Route(val path: String) {
    data object NameSetup  : Route("name-setup")
    data object Home       : Route("home")
    data object Settings   : Route("settings")
    data object Diagnostic : Route("diagnostic")
    data object AddContact : Route("add-contact")
}
```

- [ ] **Step 2: Create `EarshotNavHost.kt` (stubbed screens for now — they arrive in the next tasks)**

```kotlin
package com.earshot.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun EarshotNavHost(startAt: Route, hostActivity: ComponentActivity) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startAt.path) {
        composable(Route.NameSetup.path)  { Text("NameSetup stub — replaced in Task 13") }
        composable(Route.Home.path)       { Text("Home stub — replaced in Task 15") }
        composable(Route.Settings.path)   { Text("Settings stub — replaced in Task 14") }
        composable(Route.Diagnostic.path) { Text("Diagnostic stub — replaced in Task 14") }
        composable(Route.AddContact.path) { Text("AddContact stub — replaced in Task 16") }
    }
}
```

- [ ] **Step 3: Rewrite `MainActivity.kt`**

Replace the milestone-1 body with the new shell:
```kotlin
package com.earshot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.earshot.app.ui.EarshotNavHost
import com.earshot.app.ui.Route
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    val currentActivityRef: () -> ComponentActivity = { this }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    var startAt by remember { mutableStateOf<Route?>(null) }
                    val container = (application as EarshotApp).container
                    LaunchedEffect(Unit) {
                        val hasId = container.identityRepo.hasIdentity()
                        startAt = if (hasId) Route.Home else Route.NameSetup
                    }
                    startAt?.let { EarshotNavHost(startAt = it, hostActivity = this@MainActivity) }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Build + install + launch, verify a stub screen appears**

Run: `.\gradlew.bat :app:assembleDebug && adb install -r app\build\outputs\apk\debug\app-debug.apk && adb shell am start -n com.earshot.app/.MainActivity`
Expected: app launches, shows one of the "stub" text placeholders. (First-launch will show "NameSetup stub" because no identity is stored; subsequent launches after a real identity is created via Task 13 will show "Home stub" until Task 15 lands.)

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/earshot/app/MainActivity.kt
git add app/src/main/java/com/earshot/app/ui/
git commit -m "feat: navigation shell — Route enum + NavHost + Activity refactor

MainActivity gates the start destination on IdentityRepository state
and hosts a Compose NavHost. Screens are stubs until Tasks 13-17.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: NameSetupScreen (first-launch flow)

**Files:**
- Create: `app/src/main/java/com/earshot/app/ui/setup/NameSetupScreen.kt`
- Create: `app/src/main/java/com/earshot/app/ui/setup/NameSetupViewModel.kt`
- Modify: `app/src/main/java/com/earshot/app/ui/EarshotNavHost.kt` (replace stub)

**Interfaces:**
- Consumes: `IdentityRepository` (Task 6).
- Produces: composable that on "Continue" creates a `LocalIdentity` and navigates to `Route.Home`.

- [ ] **Step 1: `NameSetupViewModel`**

```kotlin
package com.earshot.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.earshot.app.EarshotApp
import com.earshot.app.data.IdentityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NameSetupViewModel(app: EarshotApp) : ViewModel() {
    private val repo: IdentityRepository = app.container.identityRepo

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    fun onNameChanged(new: String) {
        if (new.length <= 80) _name.value = new  // soft cap slightly above 64 UTF-8 hard limit
    }

    val canSubmit: Boolean
        get() = _name.value.trim().isNotEmpty() &&
                !_submitting.value

    fun submit() {
        if (!canSubmit) return
        _submitting.value = true
        viewModelScope.launch {
            try {
                repo.createIdentity(_name.value)
                _done.value = true
            } catch (e: IllegalArgumentException) {
                _submitting.value = false
            }
        }
    }
}
```

- [ ] **Step 2: `NameSetupScreen`**

```kotlin
package com.earshot.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earshot.app.EarshotApp
import androidx.compose.ui.platform.LocalContext

@Composable
fun NameSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EarshotApp
    val vm: NameSetupViewModel = viewModel(factory = viewModelFactory(app))
    val name by vm.name.collectAsState()
    val submitting by vm.submitting.collectAsState()
    val done by vm.done.collectAsState()

    LaunchedEffect(done) { if (done) onDone() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Earshot", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "What should we call you? This is the name shown when you pair with someone.",
            fontSize = 14.sp
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = vm::onNameChanged,
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !submitting
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::submit,
            enabled = vm.canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (submitting) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text("Continue")
        }
    }
}

private fun viewModelFactory(app: EarshotApp) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            NameSetupViewModel(app) as T
    }
```

- [ ] **Step 3: Wire into `EarshotNavHost`**

Replace the `Route.NameSetup` stub with a real destination:
```kotlin
composable(Route.NameSetup.path) {
    NameSetupScreen(onDone = {
        nav.navigate(Route.Home.path) {
            popUpTo(Route.NameSetup.path) { inclusive = true }
        }
    })
}
```

- [ ] **Step 4: Verify on device**

Uninstall + reinstall to force a fresh-first-launch experience:
```powershell
adb uninstall com.earshot.app
.\gradlew.bat :app:assembleDebug
adb install app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.earshot.app/.MainActivity
```
Expected: NameSetup appears. Type a name, tap Continue. App navigates to Home stub. Force-stop + relaunch → goes straight to Home stub (identity persisted).

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/earshot/app/ui/setup/
git add app/src/main/java/com/earshot/app/ui/EarshotNavHost.kt
git commit -m "feat: NameSetupScreen — one-time first-launch identity setup

Creates an X25519 identity via IdentityRepository on submit and
navigates to Home, popping itself off the backstack.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 14: DiagnosticScreen extraction + SettingsScreen

**Files:**
- Create: `app/src/main/java/com/earshot/app/diag/DiagnosticScreen.kt`
- Create: `app/src/main/java/com/earshot/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/earshot/app/ui/EarshotNavHost.kt`

**Interfaces:**
- Consumes: nothing (Wi-Fi Aware code is self-contained).
- Produces: `@Composable fun DiagnosticScreen()`, `@Composable fun SettingsScreen(onOpenDiagnostic: () -> Unit)`.

- [ ] **Step 1: Extract diagnostic UI**

Move the milestone-1 diagnostic composable body (currently mixed into `MainActivity`'s inline setContent block from before Task 12) into a new file. Since Task 12 already replaced `MainActivity.kt`, this "extraction" is really a **recreation** — you need to bring back the milestone-1 diagnostic logic under a new composable.

Create `app/src/main/java/com/earshot/app/diag/DiagnosticScreen.kt`. Paste the entire body of the milestone-1 diagnostic screen — same imports, same enum, same colours, same status logic. The only difference is that the top-level `class MainActivity` and `setContent { … }` wrapper are removed; export just the `DiagnosticScreen()` composable and its helpers.

For reference (verbatim from milestone 1, which is preserved in git history at commit `5853879`):
```kotlin
package com.earshot.app.diag

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.aware.WifiAwareManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Pass = Color(0xFF1B5E20)
private val PassBg = Color(0xFFC8E6C9)
private val Warn = Color(0xFFE65100)
private val WarnBg = Color(0xFFFFE0B2)
private val Fail = Color(0xFFB71C1C)
private val FailBg = Color(0xFFFFCDD2)

private enum class Status { PASS, WARN, FAIL }

@Composable
fun DiagnosticScreen() {
    val context = LocalContext.current
    val hasFeature = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
    }
    val awareManager = remember {
        context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    }
    var isAvailable by remember { mutableStateOf(awareManager?.isAvailable == true) }
    DisposableEffect(awareManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                isAvailable = awareManager?.isAvailable == true
            }
        }
        val filter = IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    val overallStatus = when {
        !hasFeature -> Status.FAIL
        !isAvailable -> Status.WARN
        else -> Status.PASS
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header(overallStatus)
        StatusCard(
            title = "Hardware support",
            status = if (hasFeature) Status.PASS else Status.FAIL,
            value = if (hasFeature) "Supported" else "Not supported",
            explanation = if (hasFeature)
                "PackageManager reports FEATURE_WIFI_AWARE. The device advertises Wi-Fi Aware capability."
            else "PackageManager does NOT report FEATURE_WIFI_AWARE."
        )
        StatusCard(
            title = "Currently available",
            status = when {
                !hasFeature -> Status.FAIL
                isAvailable -> Status.PASS
                else -> Status.WARN
            },
            value = if (isAvailable) "Available" else "Unavailable",
            explanation = if (isAvailable)
                "WifiAwareManager.isAvailable is true — Wi-Fi Aware can be used right now."
            else "WifiAwareManager.isAvailable is false. Toggle Wi-Fi on, disable airplane mode."
        )
        DeviceInfoCard()
    }
}

@Composable private fun Header(status: Status) {
    val (bg, fg, label) = when (status) {
        Status.PASS -> Triple(PassBg, Pass, "READY")
        Status.WARN -> Triple(WarnBg, Warn, "NOT READY")
        Status.FAIL -> Triple(FailBg, Fail, "UNSUPPORTED")
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(bg).padding(20.dp)
    ) {
        Text("Wi-Fi Aware Diagnostic", color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(label, color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun StatusCard(
    title: String, status: Status, value: String, explanation: String
) {
    val (bg, fg) = when (status) {
        Status.PASS -> PassBg to Pass
        Status.WARN -> WarnBg to Warn
        Status.FAIL -> FailBg to Fail
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(bg).padding(16.dp)
    ) {
        Text(title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(value, color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(explanation, color = fg, fontSize = 13.sp)
    }
}

@Composable private fun DeviceInfoCard() {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFECEFF1)).padding(16.dp)
    ) {
        Text("Device", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text("Model: ${Build.MANUFACTURER} ${Build.MODEL}", fontSize = 13.sp)
        Text("Android: ${Build.VERSION.RELEASE}", fontSize = 13.sp)
        Text("API level: ${Build.VERSION.SDK_INT}", fontSize = 13.sp)
    }
}
```

- [ ] **Step 2: Create `SettingsScreen`**

```kotlin
package com.earshot.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(onOpenDiagnostic: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onOpenDiagnostic)
                .padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            Text("🔧 Wi-Fi Aware diagnostic", fontSize = 16.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text("Earshot 0.2.0", fontSize = 12.sp)
    }
}
```

- [ ] **Step 3: Wire into `EarshotNavHost`**

```kotlin
composable(Route.Settings.path) {
    SettingsScreen(onOpenDiagnostic = { nav.navigate(Route.Diagnostic.path) })
}
composable(Route.Diagnostic.path) { DiagnosticScreen() }
```

- [ ] **Step 4: Build + verify**

Run: `.\gradlew.bat :app:assembleDebug && adb install -r app\build\outputs\apk\debug\app-debug.apk`
Manual on device: launch app → Home stub visible → (can't hit Settings yet; Task 15 wires the gear icon). To sanity-check, temporarily add a direct `nav.navigate(Route.Settings.path)` on Home-stub click, verify Diagnostic still reports the same live isAvailable state.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/earshot/app/diag/
git add app/src/main/java/com/earshot/app/ui/settings/
git add app/src/main/java/com/earshot/app/ui/EarshotNavHost.kt
git commit -m "feat: DiagnosticScreen + SettingsScreen

Wi-Fi Aware diagnostic UI moved into a plain composable and reached
through Settings. Version footer visible on Settings.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 15: HomeScreen

**Files:**
- Create: `app/src/main/java/com/earshot/app/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/earshot/app/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/earshot/app/ui/EarshotNavHost.kt`

**Interfaces:**
- Consumes: `IdentityRepository`, `ContactsRepository`, `ByteArray.fingerprint()`.
- Produces: composable Home with title + settings gear + FAB + contact list.

- [ ] **Step 1: `HomeViewModel.kt`**

```kotlin
package com.earshot.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.earshot.app.EarshotApp
import com.earshot.app.data.Contact
import com.earshot.app.data.LocalIdentity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: EarshotApp) : ViewModel() {
    val identity: StateFlow<LocalIdentity?> =
        app.container.identityRepo.identity()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val contacts: StateFlow<List<Contact>> =
        app.container.contactsRepo.contacts()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
```

- [ ] **Step 2: `HomeScreen.kt`**

```kotlin
package com.earshot.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earshot.app.EarshotApp
import com.earshot.app.data.Contact
import com.earshot.app.data.fingerprint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val app = LocalContext.current.applicationContext as EarshotApp
    val vm: HomeViewModel = viewModel(factory = homeVmFactory(app))
    val identity by vm.identity.collectAsState()
    val contacts by vm.contacts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Earshot") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add contact")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            val id = identity
            if (id != null) {
                Text(
                    text = "You are: ${id.displayName} · ${id.publicKey.fingerprint()}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            if (contacts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No contacts yet — tap + to pair with someone nearby.", fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(contacts, key = { it.publicKey.contentHashCode() }) { c ->
                        ContactRow(c)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(c: Contact) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(c.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(c.publicKey.fingerprint(), fontSize = 12.sp)
    }
}

private fun homeVmFactory(app: EarshotApp) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(app) as T
    }
```

- [ ] **Step 3: Wire into `EarshotNavHost`**

```kotlin
composable(Route.Home.path) {
    HomeScreen(
        onAdd = { nav.navigate(Route.AddContact.path) },
        onOpenSettings = { nav.navigate(Route.Settings.path) }
    )
}
```

- [ ] **Step 4: Build + verify on device**

Expected: after first-launch name-setup, Home shows "You are: <name> · <fp>", empty state message, gear icon in top bar navigates to Settings → Diagnostic works, FAB navigates to AddContact stub.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/earshot/app/ui/home/
git add app/src/main/java/com/earshot/app/ui/EarshotNavHost.kt
git commit -m "feat: HomeScreen with identity header, contact list, add FAB, settings gear

Reads identity + contacts as StateFlows via HomeViewModel. Empty
state guides toward pairing. Contact rows show display name + 32-byte
pubkey fingerprint (first-4-hex…last-4-hex).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 16: AddContactScreen (Auto + Manual tabs + NFC banner)

**Files:**
- Create: `app/src/main/java/com/earshot/app/ui/pairing/AddContactScreen.kt`
- Create: `app/src/main/java/com/earshot/app/ui/pairing/NfcStateBanner.kt`
- Modify: `app/src/main/java/com/earshot/app/ui/EarshotNavHost.kt`
- Modify: `app/src/main/java/com/earshot/app/MainActivity.kt` (expose `activityHost` to ViewModel factory)

**Interfaces:**
- Consumes: `AddContactViewModel` (Task 11), `Mode`, `NfcState`, `AddContactUiState`.
- Produces: pairing entry composable that navigates to a safety-code composable on success and back to Home on cancel/save.

Since safety-code is a distinct destination, this task hosts only the Auto/Manual tabs + banner + progress; Task 17 covers the safety-code screen. The navigation between them is inside a single `AddContact` destination, controlled by UiState.

- [ ] **Step 1: `NfcStateBanner.kt`**

```kotlin
package com.earshot.app.ui.pairing

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun NfcStateBanner(state: NfcState) {
    val context = LocalContext.current
    val (msg, cta) = when (state) {
        NfcState.OK -> return
        NfcState.DISABLED -> "NFC is off. Enable it in Settings." to "Open NFC settings"
        NfcState.HW_ABSENT -> "This device doesn't support NFC." to null
        NfcState.HCE_ABSENT -> "This device supports NFC but not HCE." to null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFE0B2))
            .padding(12.dp)
    ) {
        Text(msg, color = Color(0xFF5D2E00))
        if (cta != null) {
            Spacer(Modifier.padding(top = 4.dp))
            Row {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }) { Text(cta) }
            }
        }
    }
}
```

- [ ] **Step 2: `AddContactScreen.kt`**

```kotlin
package com.earshot.app.ui.pairing

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earshot.app.EarshotApp

@Composable
fun AddContactScreen(
    onSaved: () -> Unit,
    onCancelled: () -> Unit,
    activityHost: () -> Activity?
) {
    val app = LocalContext.current.applicationContext as EarshotApp
    val vm: AddContactViewModel = viewModel(factory = addContactVmFactory(app, activityHost))
    val state by vm.uiState.collectAsState()

    LaunchedEffect(state) {
        if (state is AddContactUiState.Saved) onSaved()
    }

    when (val s = state) {
        is AddContactUiState.Ready -> ReadyBody(s, vm)
        is AddContactUiState.SafetyCode -> SafetyCodeBody(s, vm, onCancel = onCancelled)
        is AddContactUiState.DuplicatePubKey -> DuplicatePubKeyDialog(s, vm)
        is AddContactUiState.DuplicateName   -> DuplicateNameDialog(s, vm)
        AddContactUiState.Saved -> { /* handled by LaunchedEffect */ }
        is AddContactUiState.Failed -> FailedBody(s.reason, vm, onCancelled)
    }
}

@Composable
private fun ReadyBody(s: AddContactUiState.Ready, vm: AddContactViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NfcStateBanner(s.nfcState)
        TabRow(selectedTabIndex = if (s.mode == Mode.AUTO) 0 else 1) {
            Tab(selected = s.mode == Mode.AUTO, onClick = { vm.setMode(Mode.AUTO) }) {
                Text("Auto", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = s.mode == Mode.MANUAL, onClick = { vm.setMode(Mode.MANUAL) }) {
                Text("Manual", modifier = Modifier.padding(12.dp))
            }
        }
        if (s.mode == Mode.AUTO) {
            Text(
                "Hold phones together — back-to-back, near the top.",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
            Text("Not working? Try Manual.", fontSize = 12.sp)
            Button(
                onClick = { vm.startPairing() },
                enabled = s.nfcState == NfcState.OK,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start pairing") }
        } else {
            Text("Manual pairing", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Hold phones together. Tap Send on one side while your friend taps Receive on theirs. Then swap.",
                fontSize = 12.sp
            )
            Button(onClick = { vm.manualSend() },    enabled = s.nfcState == NfcState.OK, modifier = Modifier.fillMaxWidth()) { Text("Send my contact") }
            OutlinedButton(onClick = { vm.manualReceive() }, enabled = s.nfcState == NfcState.OK, modifier = Modifier.fillMaxWidth()) { Text("Receive contact") }
        }
    }
}

@Composable
private fun FailedBody(reason: String, vm: AddContactViewModel, onCancelled: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(reason, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.retry() }) { Text("Retry") }
            OutlinedButton(onClick = onCancelled) { Text("Cancel") }
        }
    }
}

private fun addContactVmFactory(app: EarshotApp, activityHost: () -> Activity?) =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            AddContactViewModel(app, activityHost) as T
    }
```

(`SafetyCodeBody`, `DuplicatePubKeyDialog`, `DuplicateNameDialog` are added in Task 17. This task compiles because they're not yet referenced from any other file.)

Wait — the `when` block above references them. Two solutions: (a) add placeholder implementations here and flesh them out in Task 17, or (b) don't render them yet. The cleanest is (a). Add stubs at the bottom of `AddContactScreen.kt`:

```kotlin
@Composable private fun SafetyCodeBody(s: AddContactUiState.SafetyCode, vm: AddContactViewModel, onCancel: () -> Unit) {
    Text("safety-code stub — replaced in Task 17")
}
@Composable private fun DuplicatePubKeyDialog(s: AddContactUiState.DuplicatePubKey, vm: AddContactViewModel) {
    Text("dup-pubkey stub — replaced in Task 17")
}
@Composable private fun DuplicateNameDialog(s: AddContactUiState.DuplicateName, vm: AddContactViewModel) {
    Text("dup-name stub — replaced in Task 17")
}
```

Also needed at the top of the file: `import androidx.compose.foundation.layout.Row`.

- [ ] **Step 3: Wire into `EarshotNavHost` and `MainActivity`**

`MainActivity` already exposes `currentActivityRef` (from Task 12). Adjust `EarshotNavHost` to accept and forward the host activity, and pass a lambda into `AddContactScreen`. In `EarshotNavHost.kt`:
```kotlin
composable(Route.AddContact.path) {
    AddContactScreen(
        onSaved = {
            nav.popBackStack(Route.Home.path, inclusive = false)
        },
        onCancelled = {
            nav.popBackStack(Route.Home.path, inclusive = false)
        },
        activityHost = { hostActivity }
    )
}
```

The `hostActivity` parameter on `EarshotNavHost` already exists.

- [ ] **Step 4: Build + verify**

Run: `.\gradlew.bat :app:assembleDebug && adb install -r app\build\outputs\apk\debug\app-debug.apk`
Expected: Home FAB → AddContact opens with the Auto tab visible, Start pairing button enabled if NFC is on. Manual tab shows both buttons.

- [ ] **Step 5: On-hardware smoke test — two S23s**

- Both phones on the AddContact screen, Auto tab.
- Tap Start on both, hold back-to-back at the top.
- Expected: within ~5 seconds, both phones transition to the safety-code stub screen (showing "safety-code stub" — that's fine at this stage).

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/earshot/app/ui/pairing/AddContactScreen.kt
git add app/src/main/java/com/earshot/app/ui/pairing/NfcStateBanner.kt
git add app/src/main/java/com/earshot/app/ui/EarshotNavHost.kt
git commit -m "feat: AddContactScreen — Auto/Manual tabs + NFC banner

Presents the two entry modes for pairing. NFC-off / no-NFC surfaces
as an inline amber banner; the mode tabs auto-cancel any in-flight
pairing on switch. Safety-code and duplicate-dialog UIs are stubbed
here and replaced in the next task.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 17: SafetyCode UI + Duplicate dialogs

**Files:**
- Modify: `app/src/main/java/com/earshot/app/ui/pairing/AddContactScreen.kt` (replace the three stubs)

**Interfaces:**
- Consumes: `AddContactViewModel.onSafetyConfirmed()`, `onDuplicateConfirmed()`, `onDuplicateCancelled()`.
- Produces: replaces the placeholder composables with real UI.

- [ ] **Step 1: Replace `SafetyCodeBody`**

```kotlin
@Composable
private fun SafetyCodeBody(
    s: AddContactUiState.SafetyCode,
    vm: AddContactViewModel,
    onCancel: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            "Check this code matches on their phone",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            s.emojis.forEach { emoji ->
                Text(emoji, fontSize = 40.sp)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Pairing with ${s.incoming.displayName}", fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { vm.onSafetyConfirmed() }) { Text("Confirm") }
        }
    }
}
```

Add the imports (if not already present): `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.layout.Arrangement`.

- [ ] **Step 2: Replace `DuplicatePubKeyDialog`**

```kotlin
@Composable
private fun DuplicatePubKeyDialog(s: AddContactUiState.DuplicatePubKey, vm: AddContactViewModel) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.onDuplicateCancelled() },
        title = { Text("Update existing contact?") },
        text = {
            Text(
                "You've already paired with ${s.existing.displayName}. " +
                "Their name is now ${s.incoming.displayName}."
            )
        },
        confirmButton = {
            Button(onClick = { vm.onDuplicateConfirmed() }) { Text("Update") }
        },
        dismissButton = {
            OutlinedButton(onClick = { vm.onDuplicateCancelled() }) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 3: Replace `DuplicateNameDialog`**

```kotlin
@Composable
private fun DuplicateNameDialog(s: AddContactUiState.DuplicateName, vm: AddContactViewModel) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.onDuplicateCancelled() },
        title = { Text("Save as new contact?") },
        text = {
            Text(
                "You already have a contact called \"${s.existing.displayName}\" with a different key. " +
                "Save this new one too?"
            )
        },
        confirmButton = {
            Button(onClick = { vm.onDuplicateConfirmed() }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = { vm.onDuplicateCancelled() }) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 4: Build + verify**

Run: `.\gradlew.bat :app:assembleDebug && adb install -r app\build\outputs\apk\debug\app-debug.apk`
Verify by running Task 16's smoke test on hardware: pair two phones → safety-code screen now shows 6 emoji, Confirm on both → both return to Home showing the freshly-paired contact.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/earshot/app/ui/pairing/AddContactScreen.kt
git commit -m "feat: real SafetyCode screen + duplicate-pubkey/name dialogs

Both users see the same 6-emoji code, confirm on their side. Duplicate
pubkey -> update; duplicate name (different pubkey) -> save-as-new.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 18: Full on-hardware verification pass

**Files:** none — this is a checklist task, no code changes.

**Interfaces:** consumes everything above.

- [ ] **Step 1: Clean install on both S23s**

```powershell
adb -s <phoneA> uninstall com.earshot.app
adb -s <phoneB> uninstall com.earshot.app
.\gradlew.bat :app:assembleDebug
adb -s <phoneA> install app\build\outputs\apk\debug\app-debug.apk
adb -s <phoneB> install app\build\outputs\apk\debug\app-debug.apk
```

- [ ] **Step 2: Run the manual pass from the spec's "Testing plan"**

Walk through all 11 items:
1. Fresh install → first-launch name prompt on each; name persists after `adb shell am force-stop com.earshot.app`.
2. Home empty state renders correctly.
3. Auto-mode: hold phones back-to-back at the top; verify pairing completes < 5 s on ≥ 8/10 attempts.
4. Manual mode: two-button flow works end-to-end; prompts guide the swap correctly.
5. Safety code matches on both phones.
6. Re-pair with same pubkey → duplicate-confirm dialog fires; Update path works.
7. Same name / different pubkey → save-as-new dialog fires; Save path works.
8. Force-stop app mid-pair → no zombie HCE: `adb shell dumpsys nfc | grep -A5 HostEmulationManager` shows no lingering AID entry.
9. Toggle NFC off during pairing → banner appears live; toggle back → banner clears live.
10. Wi-Fi Aware diagnostic still reachable from Settings → still reports live state on airplane-mode toggle.
11. Sideload upgrade path: run `adb install -r` over an existing install (no uninstall) → verify no crash, existing paired contacts still present.

- [ ] **Step 3: Note observations in the plan or a separate log**

Record in the plan's task notes:
- Auto-mode success rate (X out of 10)
- Any noticed flake (which phone tended to be reader first, etc)
- Whether the `PackageManager.setComponentEnabledSetting` component-toggle latency is a real issue (see spec's Known Unknowns)

- [ ] **Step 4: If any check fails, open a follow-up task or fix in place**

If a check fails, before committing decide whether it's:
(a) A bug in the current slice — fix in place, add a regression test if the failure mode is unit-testable.
(b) Deferred to a later slice — add a note here and to the spec's "Known unknowns" section.

- [ ] **Step 5: Commit the observations (if any code changes)**

If Step 4 produced code changes:
```powershell
git add .
git commit -m "fix: <specific issue found in hardware pass>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

If no code changes, no commit is needed — this task is a gate, not a deliverable.

---

## Notes on ordering, execution, and non-linearity

- Tasks 2–6 are pure logic + storage and can be developed in any order or in parallel. Their code has no dependency on Android UI.
- Task 7's manifest changes reference not-yet-existing classes; that's why Task 7 explicitly declares its commit is a "deliberately red" checkpoint — Task 8 makes it green again.
- Tasks 8–11 form the NFC layer; each depends on the previous.
- Tasks 12–17 are the UI, layered on top of the NFC and storage layers.
- Task 18 is the release gate.

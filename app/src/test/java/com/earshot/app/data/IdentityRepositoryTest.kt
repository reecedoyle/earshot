package com.earshot.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
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

    @Ignore("requires libsodium native lib; verified on-device in Task 18")
    @Test fun `createIdentity produces valid X25519 keypair and persists`() = runTest {
        val id = repo.createIdentity("Reece")
        assertThat(id.displayName).isEqualTo("Reece")
        assertThat(id.publicKey).hasLength(32)
        assertThat(id.privateKey).hasLength(32)
        assertThat(repo.hasIdentity()).isTrue()
        val restored = repo.identity().first()!!
        assertThat(restored).isEqualTo(id)
    }

    @Ignore("requires libsodium native lib; verified on-device in Task 18")
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

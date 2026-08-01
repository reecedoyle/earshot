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

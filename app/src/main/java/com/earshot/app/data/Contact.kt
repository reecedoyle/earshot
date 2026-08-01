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

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

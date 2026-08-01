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

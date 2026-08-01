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

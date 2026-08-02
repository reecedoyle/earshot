package com.earshot.app.crypto


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

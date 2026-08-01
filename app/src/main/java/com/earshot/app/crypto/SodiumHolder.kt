package com.earshot.app.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid

object SodiumHolder {
    val lazySodium: LazySodiumAndroid by lazy {
        LazySodiumAndroid(SodiumAndroid())
    }
}

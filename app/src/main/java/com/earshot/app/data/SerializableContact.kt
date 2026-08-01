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

package mindustry.client.crypto

import com.beust.klaxon.*

/** finishme remove */
data class KeyHolder(val keys: PublicKeyPair, val name: String, val official: Boolean = false, @Json(ignored = true) val messageCrypto: MessageCrypto) {
    @Json(ignored = true)
    val crypto = CryptoClient(messageCrypto.keyQuad).apply { generate(keys) }
}

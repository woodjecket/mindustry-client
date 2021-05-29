package mindustry.client.crypto

import java.io.File
import java.security.KeyStore

class KeyStorage(val dataDir: File) {
    val trustStore: KeyStore = KeyStore.getInstance("BKS", "BC")
    val store: KeyStore = KeyStore.getInstance("BKS", "BC")

    init {
        val password = "abc123"  // maybe fix?
        if (dataDir.resolve("trusted").exists()) {
            trustStore.load(dataDir.resolve("trusted").inputStream(), null)
        } else {
            trustStore.load(null, null)
        }

        if (dataDir.resolve("key").exists()) {
            store.load(dataDir.resolve("key").inputStream(), password.toCharArray())
        } else {
            store.load(null, password.toCharArray())
        }
    }

    /** Generates a key and certificate (expires in two years), then puts them in [store].  This will take a bit. */
    fun genKey(name: String) {
        val pair = rsaKeyPair()

        val cert = generateCert(name, pair)

        store.setCertificateEntry("cert", cert)
        store.setKeyEntry("key", pair.private, "abc123".toCharArray(), arrayOf(cert))
    }

    fun save() {
    }
}

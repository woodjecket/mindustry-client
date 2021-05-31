package mindustry.client.crypto

import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

class KeyStorage(val dataDir: File) {
    val trustStore: KeyStore = KeyStore.getInstance("BKS", "BC")
    val store: KeyStore = KeyStore.getInstance("BKS", "BC")
    private val password = "abc123"  // maybe fix?

    init {
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
        save()
    }

    fun addTrusted(certificate: X509Certificate) {
        trustStore.setCertificateEntry(certificate.subjectDN.name, certificate)
    }

    fun save() {
        store.store(dataDir.resolve("key").outputStream(), password.toCharArray())
        trustStore.store(dataDir.resolve("trusted").outputStream(), null)
    }
}

package mindustry.client.crypto

import mindustry.client.crypto.TLS.ecKeyPair
import mindustry.client.crypto.TLS.generateCert
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.cert.X509Certificate

class KeyStorage(private val dataDir: File, name: String) {
    val trustStore: KeyStore = KeyStore.getInstance("PKCS12", "BC")
    val store: KeyStore = KeyStore.getInstance("PKCS12", "BC")
    private val password = "abc123"  // maybe fix?

    companion object {
        const val TRUST_STORE_FILENAME = "trusted"
        const val KEY_STORE_FILENAME = "key"
    }

    init {
        if (dataDir.resolve(TRUST_STORE_FILENAME).exists()) {
            trustStore.load(dataDir.resolve(TRUST_STORE_FILENAME).inputStream(), null)
        } else {
            trustStore.load(null, null)
            //finishme automatically enroll dev signing certificates
        }

        if (dataDir.resolve(KEY_STORE_FILENAME).exists()) {
            store.load(dataDir.resolve(KEY_STORE_FILENAME).inputStream(), password.toCharArray())
        } else {
            store.load(null, password.toCharArray())
            genKey(name)
        }

        save()
    }

    /** Generates a key and certificate (expires in five years), then puts them in [store]. */
    fun genKey(name: String) {
        val pair = ecKeyPair()

        val cert = generateCert(name, pair)  // Self signed

        store.setCertificateEntry("cert", cert)
        store.setKeyEntry("key", pair.private, password.toCharArray(), arrayOf(cert))
        save()
    }

    /** Trusts a given certificate. */
    fun trust(certificate: X509Certificate) {
        if (trustStore.containsAlias("cert${certificate.serialNumber}")) return
        trustStore.setCertificateEntry("cert${certificate.serialNumber}", certificate)
        save()
    }

    fun untrust(certificate: X509Certificate) {
        try {
            trustStore.deleteEntry("cert${certificate.serialNumber}")
            save()
        } catch (e: KeyStoreException) {}
    }

    fun key(): PrivateKey? = store.getKey("key", password.toCharArray()) as? PrivateKey

    fun cert(): X509Certificate? = store.getCertificate("cert") as? X509Certificate

    fun certChain(): Array<X509Certificate>? = store.getCertificateChain("key").mapNotNull { it as? X509Certificate }.toTypedArray().run { if (this.isEmpty()) null else this }

    fun save() {
        store.store(dataDir.resolve(KEY_STORE_FILENAME).outputStream(), password.toCharArray())
        trustStore.store(dataDir.resolve(TRUST_STORE_FILENAME).outputStream(), null)
    }
}

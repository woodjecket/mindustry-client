package mindustry.client.crypto

import mindustry.client.*
import mindustry.client.crypto.TLS.ecKeyPair
import mindustry.client.crypto.TLS.generateCert
import mindustry.client.utils.*
import java.io.*
import java.math.*
import java.security.*
import java.security.cert.*

class KeyStorage(private val dataDir: File, name: String) {
    val trustStore: KeyStore = KeyStore.getInstance("PKCS12", "BC")
    val store: KeyStore = KeyStore.getInstance("PKCS12","BC")
    private val password = "abc123"  // maybe fix?
    private val aliases = mutableListOf<CertAlias>()

    companion object {
        const val TRUST_STORE_FILENAME = "trusted"
        const val KEY_STORE_FILENAME = "key"
        const val CERT_ALIAS_FILENAME = "certAliases"
    }

    init {
        if (dataDir.resolve(TRUST_STORE_FILENAME).exists()) {
            trustStore.load(dataDir.resolve(TRUST_STORE_FILENAME).inputStream(), null)
        } else {
            trustStore.load(null, null)
            // foo
            trust(deserializeCert("MIIBITCB1KADAgECAggOcV+MTpj+jDAFBgMrZXAwDjEMMAoGA1UEAwwDZm9vMB4XDTIxMDcwOTE4MTY0MFoXDTI2MDcwOTE4MTY0MFowDjEMMAoGA1UEAwwDZm9vMCowBQYDK2VwAyEAe6bLBo2109wwgYfyt2bT7v8bMJEVffJXPeGy9f/ELEejUDBOMA8GA1UdEQQIMAaHBH8AAAEwHQYDVR0OBBYEFHnm9gtZlKDTfIYb6cg5rTdv7SQVMA4GA1UdDwEB/wQEAwIBjjAMBgNVHRMEBTADAQH/MAUGAytlcANBAHknCuv74G4Yh90Wi78Evl+NJzuBQW5tw8M2Rcn+pWJr9cuK6DhYfVuHz6vb/hVMZV0NLod7KGKK/UqB7Ho+hw8=".base64()!!)!!, false)
            // buthed
            trust(deserializeCert("MIIBRTCB+KADAgECAghLbYs0jVEwITAFBgMrZXAwIDEeMBwGA1UEAwwVWyMwMGUzMzldYnV0aGVkMDEwMjAzMB4XDTIxMDcwOTE4MzA0MloXDTI2MDcwOTE4MzA0MlowIDEeMBwGA1UEAwwVWyMwMGUzMzldYnV0aGVkMDEwMjAzMCowBQYDK2VwAyEAcGK1U/6uaC/49qaV9Osuv+kOzL6h/Q1S6z4gXN7HR4qjUDBOMA8GA1UdEQQIMAaHBH8AAAEwHQYDVR0OBBYEFPHcKz1gKXBrOOH8C77aa6W7zjbBMA4GA1UdDwEB/wQEAwIBjjAMBgNVHRMEBTADAQH/MAUGAytlcANBABBgb2Rsiz+ZgQlCm6vpJQln4nGfhraPDyr1n4NQoiCU3wPIQW8n2GRGGKcYWE9ytFKBgi0bFgcS1WjBFtqoxAk=".base64()!!)!!, false)
        }

        if (dataDir.resolve(KEY_STORE_FILENAME).exists()) {
            store.load(dataDir.resolve(KEY_STORE_FILENAME).inputStream(), password.toCharArray())
        } else {
            store.load(null, password.toCharArray())
            genKey(name)
        }

        save()
    }

    fun aliases() = try { Main.klaxon.parseArray<CertAlias>(dataDir.resolve(CERT_ALIAS_FILENAME)) ?: listOf() } catch (e: Exception) { dataDir.resolve(CERT_ALIAS_FILENAME).writeText("[]"); listOf() }

    fun alias(certificate: X509Certificate, name: String?) {
        aliases.removeAll { it.sn == certificate.serialNumber.toString() }
        if (name != null) aliases.add(CertAlias(certificate.serialNumber.toString(), name))
        save()
    }

    fun trusted() = trustStore.aliases().toList().mapNotNull { trustStore.getCertificate(it) as? X509Certificate }

    fun alias(certificate: X509Certificate) = aliases.find { it.sn == certificate.serialNumber.toString() }

    fun cert(alias: String) = trusted().find { it.readableName == alias || aliases.any { a -> a.sn == it.serialNumber.toString() && a.alias == alias } }

    /** Generates a key and certificate (expires in five years), then puts them in [store]. */
    private fun genKey(name: String) {
        val pair = ecKeyPair()

        val cert = generateCert(name, pair, isCa = false)  // Self signed but not CA

        store.setCertificateEntry("cert", cert)
        store.setKeyEntry("key", pair.private, password.toCharArray(), arrayOf(cert))
        save()
    }

    /** Trusts a given certificate. */
    fun trust(certificate: X509Certificate, save: Boolean = true) {
        if (trustStore.containsAlias("cert${certificate.serialNumber}")) return
        trustStore.setCertificateEntry("cert${certificate.serialNumber}", certificate)
        if (save) save()
    }

    fun untrust(certificate: X509Certificate) {
        try {
            trustStore.deleteEntry("cert${certificate.serialNumber}")
            save()
        } catch (e: KeyStoreException) {
            e.printStackTrace()
        }
    }

    fun key(): PrivateKey? = store.getKey("key", password.toCharArray()) as? PrivateKey

    fun cert(): X509Certificate? = store.getCertificate("cert") as? X509Certificate

    fun certChain(): Array<X509Certificate>? = store.getCertificateChain("key").mapNotNull { it as? X509Certificate }.toTypedArray().run { if (this.isEmpty()) null else this }

    fun save() {
        store.store(dataDir.resolve(KEY_STORE_FILENAME).outputStream(), password.toCharArray())
        trustStore.store(dataDir.resolve(TRUST_STORE_FILENAME).outputStream(), null)
        val f = dataDir.resolve(CERT_ALIAS_FILENAME)
        f.writeText(Main.klaxon.toJsonString(aliases.toTypedArray()))
    }

    fun cert(serialNum: BigInteger): X509Certificate? = store.getCertificate("cert$serialNum") as X509Certificate?

    fun deserializeCert(bytes: ByteArray): X509Certificate? {
        val factory = CertificateFactory.getInstance("X509")
        return factory.generateCertificate(bytes.inputStream()) as? X509Certificate
    }

    data class CertAlias(val sn: String, val alias: String)
}

package mindustry.client.crypto

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.tls.*
import org.bouncycastle.tls.crypto.TlsCryptoParameters
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCertificate
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import org.bouncycastle.tls.crypto.impl.bc.BcTlsRSASigner
import java.io.*
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.*
import kotlin.math.absoluteValue
import kotlin.random.Random

object TLS {
    private val crypto = BcTlsCrypto(Crypto.random)
    private val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())

    fun rsaKeyPair(): FullRSAPair {
        val keyGen = RSAKeyPairGenerator()
        keyGen.init(RSAKeyGenerationParameters(BigInteger("65537"), Crypto.random, 4096, 80))
        val parameters = keyGen.generateKeyPair()

        val publicRaw  = parameters.public as RSAKeyParameters
        val privateRaw = parameters.private as RSAPrivateCrtKeyParameters

        val public  = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(publicRaw.modulus, publicRaw.exponent))
        val private = KeyFactory.getInstance("RSA").generatePrivate(RSAPrivateCrtKeySpec(publicRaw.modulus, publicRaw.exponent, privateRaw.exponent, privateRaw.p, privateRaw.q, privateRaw.dp, privateRaw.dq, privateRaw.qInv))
        return FullRSAPair(publicRaw, privateRaw, public, private)
    }

    fun selfSigned(name: String, keyPair: FullRSAPair): Certificate {
        val domain = X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.NAME, name).build()
        val time = System.currentTimeMillis()
        val serialNum = BigInteger(Random.nextLong().absoluteValue.toString())  // todo: secure???

        val calendar = Calendar.getInstance()
        calendar.time = Date(time)
        val start = calendar.time
        calendar.add(Calendar.YEAR, 2)  // Expires in 2 years
        val end = calendar.time

        val algorithm = "SHA512withRSA"
        val signer = JcaContentSignerBuilder(algorithm).build(keyPair.private)

        val certBuilder = JcaX509v3CertificateBuilder(domain, serialNum, start, end, domain, keyPair.public)
        return Certificate(arrayOf(BcTlsCertificate(crypto, certBuilder.build(signer).toASN1Structure())))
    }

    data class FullRSAPair(
        val publicParams: RSAKeyParameters,
        val privateParams: RSAPrivateCrtKeyParameters,
        val public: PublicKey,
        val private: PrivateKey
    )

    class TLSServer(val pair: FullRSAPair, val cert: Certificate, val input: InputStream, val output: OutputStream) {
        private val tlsServerProtocol = TlsServerProtocol(input, output)

        fun accept() {
            tlsServerProtocol.accept(object : DefaultTlsServer(BcTlsCrypto(Crypto.random)) {
                @Throws(IOException::class)
                override fun getRSASignerCredentials(): TlsCredentialedSigner {
                    return DefaultTlsCredentialedSigner(
                        TlsCryptoParameters(context),
                        BcTlsRSASigner(
                            crypto as BcTlsCrypto,
                            pair.private as RSAKeyParameters,
                            pair.public as RSAKeyParameters
                        ),
                        cert,
                        SignatureAndHashAlgorithm.rsa_pss_pss_sha512
                    )
                }
            })
        }
    }

    class TLSClient(val input: InputStream, val output: OutputStream) {
        private val tlsClientProtocol = TlsClientProtocol(input, output)

        fun connect() {
            tlsClientProtocol.connect(object : DefaultTlsClient(BcTlsCrypto(Crypto.random)) {
                override fun getAuthentication(): TlsAuthentication {
                    return object : ServerOnlyTlsAuthentication() {
                        override fun notifyServerCertificate(serverCertificate: TlsServerCertificate?) {
                            validate(serverCertificate?.certificate ?: throw IOException("Certificate cannot be null!"))
                        }
                    }
                }
            })
        }
    }

    fun validate(cert: Certificate) {
        val encoded = cert.certificateList.firstOrNull()?.encoded ?: throw IllegalArgumentException("Empty certificate list!")
        val jsCert = CertificateFactory.getInstance("X.509").generateCertificate(encoded.inputStream())
        keyStore.getCertificateAlias(jsCert) ?: throw IOException("Unknown certificate '$jsCert'!")
    }
}

private class InputOutputStream {
    private val buffer = ByteBuffer.allocate(4096)
    val input = object : InputStream() {
        override fun read(): Int {
            return buffer.get().toInt() + 128
        }
    }

    val output = object : OutputStream() {
        override fun write(b: Int) {
            buffer.put((b - 120).toByte())
        }
    }
}

fun main() {
    val oneKey = TLS.rsaKeyPair()
    val twoKey = TLS.rsaKeyPair()

    val oneCert = TLS.selfSigned("one", oneKey)
    val twoCert = TLS.selfSigned("two", twoKey)

    val oneToTwo = InputOutputStream()
    val twoToOne = InputOutputStream()

    val one = TLS.TLSServer(oneKey, oneCert, twoToOne.input, oneToTwo.output)
    val two = TLS.TLSClient(oneToTwo.input, twoToOne.output)

    one.accept()
    two.connect()
}

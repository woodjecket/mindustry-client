package mindustry.client.crypto

import kotlinx.coroutines.*
import mindustry.client.crypto.TLS.certToKeyStore
import mindustry.client.crypto.TLS.ecKeyPair
import mindustry.client.crypto.TLS.generateCert
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.x500.RDN
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.bc.BcX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaAttributeCertificateIssuer
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.est.jcajce.JcaJceUtils
import org.bouncycastle.jcajce.provider.asymmetric.edec.KeyPairGeneratorSpi
import org.bouncycastle.jce.X509KeyUsage
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.tls.TlsProtocol
import java.io.*
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.*
import java.security.cert.*
import java.util.*
import javax.net.ServerSocketFactory
import javax.net.SocketFactory
import javax.net.ssl.*
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * An object containing utilities for using TLSv1.3.
 */
object TLS {
    val random: SecureRandom = SecureRandom.getInstanceStrong()

    fun certToKeyStore(certificate: X509Certificate, key: PrivateKey? = null, certChain: Array<X509Certificate>, password: String = "abc123"): KeyStore {
        val keyStore = KeyStore.getInstance("pkcs12", "BC")
        keyStore.load(null, password.toCharArray())
        if (key != null) keyStore.setKeyEntry("key", key, password.toCharArray(), certChain)
        keyStore.setCertificateEntry("cert", certificate)
        return keyStore
    }

    /** Generates an Ed25519 keypair for signing. */
    fun ecKeyPair(): KeyPair {
        return KeyPairGeneratorSpi.Ed25519().generateKeyPair()
    }

    /**
     * Base class for a TLS peer.  Provides an [input] and [output] stream and a secure [socket] (null before
     * connected).
     */
    abstract class TLSPeer(key: PrivateKey, certificate: X509Certificate, certChain: Array<X509Certificate>, trusted: KeyStore) : Closeable {
        protected val context: SSLContext

        abstract val ready: Boolean

        /** The inner, secured socket. */
        var socket: Socket? = null
            protected set

        /** The input stream over which TLS will be tunneled. */
        abstract val input: InputStream

        /** The input stream over which TLS will be tunneled. */
        abstract val output: OutputStream

        protected companion object {
            var lastPort = 20_000  // note: this will eventually run out
        }

        init {
            val password = "abc123".toCharArray()  // It stays in memory so this is ok

            val tmf = TrustManagerFactory.getInstance("X509", "BCJSSE")
            tmf.init(trusted)

            val keyStore = certToKeyStore(certificate, key, certChain)

            val kmf = KeyManagerFactory.getInstance("X509", "BCJSSE")
            kmf.init(keyStore, password)
            context = SSLContext.getInstance("TLSv1.3", "BCJSSE")
            context.init(
                kmf.keyManagers, tmf.trustManagers,
                random
            )
        }
    }

    private inline fun <reified T> Any?.reflect(field: String): T? {
        this ?: return null
        return try {
            val f = this::class.java.getDeclaredField(field)
            f.isAccessible = true
            f.get(this) as? T
        } catch (e: Exception) {
            null
        }
    }

    /**
     *  A TLS server.
     *
     *  @see TLSPeer
     */
    class TLSServer(key: PrivateKey, certificate: X509Certificate, certChain: Array<X509Certificate>, trusted: KeyStore) : TLSPeer(key, certificate, certChain, trusted) {
        private val serverSocket: SSLServerSocket

        override val ready: Boolean
            get() = socket != null && socket?.isConnected == true && socket?.isClosed == false && socket.reflect<TlsProtocol>("protocol")?.isHandshaking == false

        override lateinit var input: InputStream
            private set
        override lateinit var output: OutputStream
            private set

        init {
            val sock = SocketFactory.getDefault().createSocket()
            serverSocket = context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
            serverSocket.needClientAuth = true
            serverSocket.enabledProtocols = arrayOf("TLSv1.3")

            runBlocking {
                launch(Dispatchers.IO) { socket = serverSocket.accept() }
                sock.connect(InetSocketAddress("127.0.0.1", serverSocket.localPort))
                input = sock.getInputStream()
                output = sock.getOutputStream()
            }
        }

        override fun close() {
            socket?.close()
            serverSocket.close()
            socket = null
        }
    }

    /**
     *  A TLS client.
     *
     *  @see TLSPeer
     */
    class TLSClient(key: PrivateKey, certificate: X509Certificate, certChain: Array<X509Certificate>, trusted: KeyStore) : TLSPeer(key, certificate, certChain, trusted) {
        override lateinit var input: InputStream
            private set
        override lateinit var output: OutputStream
            private set
        override var ready: Boolean = false
            private set

        init {
            val factory: SocketFactory = context.socketFactory

            // Create internal socket server for the tls client to connect to.  This is needed to get the input and output streams.
            val server = ServerSocketFactory.getDefault().createServerSocket(0)

            runBlocking {
                launch(Dispatchers.IO) {
                    val sock = server.accept()
                    input = sock.getInputStream()
                    output = sock.getOutputStream()
                }
                // Connect to the internal server with a TLS socket
                val connection = factory.createSocket("127.0.0.1", server.localPort) as SSLSocket
                connection.addHandshakeCompletedListener { ready = true }
                connection.enabledProtocols = arrayOf("TLSv1.3")
                val sslParams = SSLParameters()
                sslParams.endpointIdentificationAlgorithm = "HTTPS"
                connection.sslParameters = sslParams
                socket = connection
            }
        }

        override fun close() {
            socket?.close()
            socket = null
        }
    }

    /**
     * Generates an X509 certificate with the specified domain name and keypair.  The certificate expires in five years by
     * default and uses the ed25519 signature algorithm.  It is valid for 127.0.0.1.
     */
    fun generateCert(
        name: String,
        keyPair: KeyPair,
        ca: Pair<X509Certificate, KeyPair>? = null,
        expiryYears: Int = 5,
        isCa: Boolean = ca == null,
    ): X509Certificate {

        infix fun ASN1ObjectIdentifier.rdn(str: String) = RDN(this, DERUTF8String(str))

        val domain = X500Name(
            arrayOf(
                BCStyle.C rdn "AQ",  // prepare to be forcibly relocated to antarctica
                BCStyle.POSTAL_CODE rdn "96598-0001",
                BCStyle.POSTAL_ADDRESS rdn "PSC 768 Box 400",
                BCStyle.CN rdn name
            )
        )
        val serialNum = BigInteger(Random.nextLong().absoluteValue.toString())

        val calendar = Calendar.getInstance()
        calendar.time = Date()
        val start = calendar.time
        calendar.add(Calendar.YEAR, expiryYears)
        val end = calendar.time

        val v3Bldr = JcaX509v3CertificateBuilder(
            ca?.first?.subjectX500Principal?.name?.run { X500Name(this) } ?: domain,
            serialNum,
            start, end,
            domain,
            keyPair.public
        )

        v3Bldr.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(GeneralName(GeneralName.iPAddress, "127.0.0.1"))
        )

        v3Bldr.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            JcaX509ExtensionUtils().createSubjectKeyIdentifier(keyPair.public)
        )

        if (ca != null) {
            v3Bldr.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                JcaX509ExtensionUtils().createAuthorityKeyIdentifier(ca.second.public)
            )

            v3Bldr.addExtension(
                Extension.certificateIssuer,
                false,
                GeneralNames(GeneralName(X500Name(ca.first.subjectX500Principal.name)))
            )
        }

        if (isCa) {
            v3Bldr.addExtension(
                Extension.keyUsage,
                true,
                X509KeyUsage(X509KeyUsage.cRLSign or X509KeyUsage.keyCertSign or X509KeyUsage.digitalSignature or X509KeyUsage.keyAgreement)
            )

            v3Bldr.addExtension(Extension.basicConstraints, false, BasicConstraints(true))
        }


        val certHldr: X509CertificateHolder =
            v3Bldr.build(JcaContentSignerBuilder("Ed25519").build(ca?.second?.private ?: keyPair.private))

        return JcaX509CertificateConverter().setProvider("BC").getCertificate(certHldr)
    }
}

fun main() {
    val bouncy = BouncyCastleProvider()
    Security.addProvider(bouncy)
    Security.addProvider(BouncyCastleJsseProvider(bouncy))

    val caKey = ecKeyPair()
    val caCert = generateCert("ca", caKey)

    val intKey = ecKeyPair()
    val intCert = generateCert("int", intKey, Pair(caCert, caKey), isCa = true)

    val clientKey = ecKeyPair()
    val clientCert = generateCert("client", clientKey)

    val serverKey = ecKeyPair()
    val serverCert = generateCert("server", serverKey, Pair(intCert, intKey))

    val client = TLS.TLSClient(clientKey.private, clientCert, arrayOf(clientCert), certToKeyStore(caCert, certChain = arrayOf(caCert)))
    val server = TLS.TLSServer(serverKey.private, serverCert, arrayOf(serverCert, intCert, caCert), certToKeyStore(clientCert, certChain = arrayOf(clientCert)))

    runBlocking {
        launch(Dispatchers.IO) {
            while (true) {
                val read1 = client.input.readNBytes(client.input.available())
                val read2 = server.input.readNBytes(server.input.available())
                if (read1.size + read2.size > 0) println("Up: ${read1.size}B   Down: ${read2.size}B")
                delay(500)
                server.output.write(read1)
                client.output.write(read2)
            }
        }

        launch(Dispatchers.IO) {
            val out = PrintWriter(server.socket!!.getOutputStream(), true)
            while (true) {
                out.println("difjiofjiowejf")
                delay(1000)
            }
        }

        launch(Dispatchers.IO) {
            val input = BufferedReader(InputStreamReader(client.socket!!.inputStream))
            while (true) {
                val item = input.readLine() ?: continue
                println("Got '$item'")
            }
        }
    }
}

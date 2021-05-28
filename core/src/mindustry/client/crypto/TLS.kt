package mindustry.client.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.*
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.*
import javax.net.ServerSocketFactory
import javax.net.SocketFactory
import javax.net.ssl.*
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.random.Random

fun certToKeyStore(certificate: X509Certificate, key: PrivateKey? = null, password: String = "abc123"): KeyStore {
    val keyStore = KeyStore.getInstance("PKCS12", "BC")
    keyStore.load(null, password.toCharArray())
    val certChain = arrayOf(certificate)
    if (key != null) keyStore.setKeyEntry("key", key, password.toCharArray(), certChain)
    keyStore.setCertificateEntry("cert", certificate)
    return keyStore
}

fun rsaKeyPair(): KeyPair {
    val keyGen = RSAKeyPairGenerator()
    keyGen.init(RSAKeyGenerationParameters(BigInteger("65537"), Crypto.random, 4096, 80))
    val parameters = keyGen.generateKeyPair()

    val publicRaw  = parameters.public as RSAKeyParameters
    val privateRaw = parameters.private as RSAPrivateCrtKeyParameters

    val public  = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(publicRaw.modulus, publicRaw.exponent))
    val private = KeyFactory.getInstance("RSA").generatePrivate(RSAPrivateCrtKeySpec(publicRaw.modulus, publicRaw.exponent, privateRaw.exponent, privateRaw.p, privateRaw.q, privateRaw.dp, privateRaw.dq, privateRaw.qInv))
    return KeyPair(public, private)
}

abstract class TLSPeer(protected val key: PrivateKey, protected val certificate: X509Certificate, protected val trusted: KeyStore) : Closeable {
    protected val context: SSLContext
    var socket: Socket? = null
        protected set
    abstract val input: InputStream
    abstract val output: OutputStream

    protected companion object {
        var lastPort = 20_000
    }

    init {
        val password = "abc123".toCharArray()  // It stays in memory so this is ok

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trusted)

        val keyStore = certToKeyStore(certificate, key)

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password)
        context = SSLContext.getInstance("TLS")
        context.init(
            kmf.keyManagers, tmf.trustManagers,
            SecureRandom.getInstanceStrong()
        )
    }
}

class TLSServer(key: PrivateKey, certificate: X509Certificate, trusted: KeyStore) : TLSPeer(key, certificate, trusted) {
    private val serverSocket: SSLServerSocket

    override lateinit var input: InputStream
    override lateinit var output: OutputStream

    init {
        val sock = SocketFactory.getDefault().createSocket()
        val port = lastPort++
        serverSocket = context.serverSocketFactory.createServerSocket(port) as SSLServerSocket
        serverSocket.needClientAuth = true
        serverSocket.enabledProtocols = arrayOf("TLSv1.3")

        runBlocking {
            launch(Dispatchers.IO) { socket = serverSocket.accept() }
            sock.connect(InetSocketAddress("127.0.0.1", port))
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

class TLSClient(key: PrivateKey, certificate: X509Certificate, trusted: KeyStore) : TLSPeer(key, certificate, trusted) {
    override lateinit var input: InputStream
        private set
    override lateinit var output: OutputStream
        private set

    init {
        val factory: SocketFactory = context.socketFactory
        val port = lastPort++

        val server = ServerSocketFactory.getDefault().createServerSocket(port)

        runBlocking {
            launch(Dispatchers.IO) {
                val sock = server.accept()
                input = sock.getInputStream()
                output = sock.getOutputStream()
            }
            val connection = factory.createSocket("127.0.0.1", port) as SSLSocket
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

fun generateCert(name: String, keyPair: KeyPair): X509Certificate {
    val domain = X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.NAME, name).build()
    val time = System.currentTimeMillis()
    val serialNum = BigInteger(Random.nextLong().absoluteValue.toString())

    val calendar = Calendar.getInstance()
    calendar.time = Date(time)
    val start = calendar.time
    calendar.add(Calendar.YEAR, 2)  // Expires in 2 years
    val end = calendar.time

    val algorithm = "SHA512withRSA"
    val signer = JcaContentSignerBuilder(algorithm).build(keyPair.private)

    val certBuilder = JcaX509v3CertificateBuilder(domain, serialNum, start, end, domain, keyPair.public)

    certBuilder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(GeneralName(GeneralName.iPAddress, "127.0.0.1")))

    val factory = CertificateFactory.getInstance("X.509", "BC")
    return factory.generateCertificate(certBuilder.build(signer).encoded.inputStream()) as X509Certificate
}

fun main() {
    Security.addProvider(BouncyCastleProvider())
    val clientKey = rsaKeyPair()
    val clientCert = generateCert("client", clientKey)
    val serverKey = rsaKeyPair()
    val serverCert = generateCert("server", serverKey)

    val client = TLSClient(clientKey.private, clientCert, certToKeyStore(serverCert))
    val server = TLSServer(serverKey.private, serverCert, certToKeyStore(clientCert))

    thread {
        while (true) {
            val read1 = client.input.readNBytes(client.input.available())
            val read2 = server.input.readNBytes(server.input.available())
            if (read1.size + read2.size > 0) println("Up: ${read1.size}B   Down: ${read2.size}B")
            Thread.sleep(500)
            server.output.write(read1)
            client.output.write(read2)
        }
    }

    thread {
        val out = PrintWriter(server.socket!!.getOutputStream(), true)
        while (true) {
            out.println("Hello World!")
            Thread.sleep(1000)
        }
    }

    val input = BufferedReader(InputStreamReader(client.socket!!.inputStream))
    while (true) {
        val item = input.readLine() ?: continue
        println("Got '$item'")
    }
}

package mindustry.client

import arc.*
import arc.graphics.Color
import arc.math.geom.*
import arc.struct.*
import arc.util.Interval
import kotlinx.coroutines.*
import mindustry.*
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.crypto.*
import mindustry.client.navigation.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.entities.units.*
import mindustry.game.*
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.input.*
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.jcajce.JcaX500NameUtil
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import java.security.Security
import java.security.cert.X509Certificate
import java.time.DateTimeException
import java.time.Instant

object Main : ApplicationListener {
    lateinit var communicationSystem: SwitchableCommunicationSystem
    lateinit var communicationClient: Packets.CommunicationClient
    private var dispatchedBuildPlans = mutableListOf<BuildPlan>()
    private val buildPlanInterval = Interval()
    val tlsSessions = mutableListOf<TLSSession>()
    val mainScope = CoroutineScope(Dispatchers.Default)
    var keyStorage: KeyStorage? = null
    private val waitingForCertResponse = mutableListOf<(TLSCertFinder, Int) -> Unit>()

    data class TLSSession(val player: Int, val peer: TLS.TLSPeer) {
        val stale get() = Groups.player?.getByID(player) == null || peer.dead
        val commsClient = Packets.CommunicationClient(peer)
    }

    /** Run on client load. */
    override fun init() {
        val bouncy = BouncyCastleProvider()
        Security.addProvider(bouncy)
        Security.addProvider(BouncyCastleJsseProvider(bouncy))

        if (Core.app.isDesktop) {
            communicationSystem = SwitchableCommunicationSystem(MessageBlockCommunicationSystem)
            communicationSystem.init()

            TileRecords.initialize()

            Core.app.post {
                val setting = Core.settings.getString("name", null)
                if (setting != null) {
                    keyStorage = KeyStorage(Core.settings.dataDirectory.file(), setting)
                }
            }

            Events.on(EventType.SendChatMessageEvent::class.java) { event ->
                val cert = keyStorage?.cert() ?: return@on
                val key = keyStorage?.key() ?: return@on
                val time = Instant.now().toEpochMilli()
                communicationClient.send(SignatureTransmission(TLS.sign(SignatureTransmission.format(event.message.encodeToByteArray(), time, cert.serialNumber), key), time, cert.serialNumber))
            }
        } else {
            communicationSystem = SwitchableCommunicationSystem(DummyCommunicationSystem(mutableListOf()))
            communicationSystem.init()
        }
        communicationClient = Packets.CommunicationClient(communicationSystem)

        Navigation.navigator = AStarNavigator

        Events.on(EventType.WorldLoadEvent::class.java) {
            dispatchedBuildPlans.clear()
        }
        Events.on(EventType.ServerJoinEvent::class.java) {
            communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem
        }

        communicationClient.addListener { transmission, senderId ->
            when (transmission) {
                is BuildQueueTransmission -> {
                    if (senderId == communicationSystem.id) return@addListener
                    val path = Navigation.currentlyFollowing as? BuildPath ?: return@addListener
                    if (path.queues.contains(path.networkAssist)) {
                        val positions = IntSet()
                        for (plan in path.networkAssist) positions.add(Point2.pack(plan.x, plan.y))

                        for (plan in transmission.plans.sortedByDescending { it.dst(Vars.player) }) {
                            if (path.networkAssist.size > 1000) return@addListener  // too many plans, not accepting new ones
                            if (positions.contains(Point2.pack(plan.x, plan.y))) continue
                            path.networkAssist.add(plan)
                        }
                    }
                }
                is TLSRequest -> {
                    if (transmission.destination != communicationSystem.id) return@addListener
                    println("Got tls request from $senderId")
                    val store = keyStorage ?: return@addListener

                    val key = keyStorage?.key() ?: return@addListener
                    val cert = keyStorage?.cert() ?: return@addListener
                    val chain = keyStorage?.certChain() ?: return@addListener

                    println("Creating client...")
                    val peer = TLS.TLSClient(key, cert, chain, store.trustStore, communicationSystem.id, senderId)
                    println("Done")

                    mainScope.launch {
                        val start = Instant.now()
                        println("Creating session...")
                        val session = TLSSession(senderId, peer)
                        println("Done")
                        tlsListeners(session)
                        tlsSessions.add(session)
                        println("Waiting for ready...")
                        while (!peer.ready && start.age() in 0..30) {
                            delay(100)
                        }
                        if (start.age() >= 30) {
                            println("Timeout")
                            tlsSessions.remove(session)
                            return@launch
                        }
                        println("Ready!")
                    }
                }
                is TLSTransmission -> {
                    println("${transmission.content.size} byte TLS transmission")
                    if (transmission.destination != communicationSystem.id) return@addListener
                    println("(for me)")
                    val session = tlsSessions.find { it.player == senderId } ?: return@addListener
                    session.peer.output.write(transmission.content)
                }
                is TLSCertFinder -> {
                    if (senderId == communicationSystem.id) return@addListener
                    waitingForCertResponse.forEach { it(transmission, senderId) }
                    if (transmission.response) return@addListener
                    val cert = keyStorage?.cert() ?: return@addListener
                    if (cert.serialNumber == transmission.serialNum) {
                        communicationClient.send(TLSCertFinder(cert.serialNumber, true))
                    }
                }
                is SignatureTransmission -> {
                    if (!processSignatureTransmission(transmission)) {  // do it again if it doesn't work the first time
                        mainScope.launch {
                            delay(250)
                            processSignatureTransmission(transmission)
                        }
                    }
                }
            }
        }

        // Periodically clear out tlsSessions
        mainScope.launch {
            while (true) {
                tlsSessions.filter { it.stale }.forEach { println("Clearing TLS session"); it.peer.close(); tlsSessions.remove(it) }
                delay(1000)
            }
        }

        // Flush
        mainScope.launch(Dispatchers.IO) {
            while (true) {
                for (session in tlsSessions) {
                    session.commsClient.update()
                    val bytes = ByteArray(session.peer.input.available())
                    session.peer.input.read(bytes)
                    if (bytes.isEmpty()) continue
                    communicationClient.send(TLSTransmission(session.player, bytes))
                }
                delay(500)
            }
        }
    }

    /**
     * @return if it's done and does not need to be called again
     */
    private fun processSignatureTransmission(transmission: SignatureTransmission): Boolean {
        val cert = keyStorage?.cert(transmission.certSN) ?: return true
        val recentMessages = Vars.ui?.chatfrag?.messages?.toList()?.takeLast(5) ?: return false
        try { if (Instant.ofEpochMilli(transmission.timeSentMillis).age() > 60) return true } catch (e: DateTimeException) { return true }
        for (msg in recentMessages) {
            val formatted = SignatureTransmission.format(msg.message.encodeToByteArray(), transmission.timeSentMillis, transmission.certSN)
            if (TLS.verify(transmission.signature, formatted, cert)) {
                msg.sender = cert.readableName + " " + Iconc.ok
                msg.backgroundColor = Color.green.mul(0.4f)
                msg.format()
                return true
            }
        }
        return false
    }

    /** Run once per frame. */
    override fun update() {
        communicationClient.update()

        if (Core.scene.keyboardFocus == null && Core.input?.keyTap(Binding.send_build_queue) == true) {
            ClientVars.dispatchingBuildPlans = !ClientVars.dispatchingBuildPlans
        }

        if (ClientVars.dispatchingBuildPlans && !communicationClient.inUse && buildPlanInterval.get(5 * 60f)) {
            sendBuildPlans()
        }
    }

    fun setPluginNetworking(enable: Boolean) {
        when {
            enable -> {
                communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem //FINISHME: Re-implement packet plugin
            }
            Core.app?.isDesktop == true -> {
                communicationSystem.activeCommunicationSystem = MessageBlockCommunicationSystem
            }
            else -> {
                communicationSystem.activeCommunicationSystem = DummyCommunicationSystem(mutableListOf())
            }
        }
    }

    fun floatEmbed(): Vec2 {
        return when {
            Navigation.currentlyFollowing is AssistPath && Core.settings.getBool("displayasuser") ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.ASSISTING)
                )
            Navigation.currentlyFollowing is AssistPath ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.ASSISTING),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.ASSISTING)
                )
            Core.settings.getBool("displayasuser") ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, ClientVars.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, ClientVars.FOO_USER)
                )
            else -> Vec2(Vars.player.unit().aimX, Vars.player.unit().aimY)
        }
    }

    private fun sendBuildPlans(num: Int = 500) {
        val toSend = Vars.player.unit().plans.toList().takeLast(num).toTypedArray()
        if (toSend.isEmpty()) return
        communicationClient.send(BuildQueueTransmission(toSend), { Toast(3f).add(Core.bundle.format("client.sentplans", toSend.size)) }, { Toast(3f).add("@client.nomessageblock")})
        dispatchedBuildPlans.addAll(toSend)
    }

    private suspend fun playerIDFromCert(certificate: X509Certificate): Int? {
        communicationClient.send(TLSCertFinder(certificate.serialNumber, false))
        var num: Int? = null
        val job = mainScope.launch { delay(11_000) }
        val listener: (TLSCertFinder, Int) -> Unit = { transmission, id ->
            if (transmission.serialNum == certificate.serialNumber && transmission.response) {
                num = id
                job.cancel()
            }
        }
        waitingForCertResponse.add(listener)
        job.join()
        waitingForCertResponse.remove(listener)
        return num
    }

    suspend fun connectTLS(certificate: X509Certificate) {
        println("Getting player id...")
        val id = playerIDFromCert(certificate) ?: return
        println("Got id")
        connectTLS(id, certificate)
    }

    private suspend fun connectTLS(player: Int, expectedCertificate: X509Certificate) {
        val store = keyStorage ?: return

        val key = keyStorage?.key() ?: return
        val cert = keyStorage?.cert() ?: return
        val chain = keyStorage?.certChain() ?: return

        println("Creating server...")
        val peer = TLS.TLSServer(key, cert, chain, store.trustStore, communicationSystem.id, player)
        println("Created")

        val start = Instant.now()
        println("Creating session...")
        val session = TLSSession(player, peer)
        println("Created")
        tlsListeners(session)
        tlsSessions.add(session)
        println("Sending TLS request...")
        communicationClient.send(TLSRequest(player))
        while (!peer.ready && start.age() in 0..30) {
            delay(100)
        }
        println("Timeout/got connection")
        if (start.age() >= 30) {
            tlsSessions.remove(session)
            return
        }
        println("Checking peer cert...")
        if (peer.peerCert() != expectedCertificate) {
            println("Invalid!  Terminating connection")
            session.peer.close()
            tlsSessions.remove(session)
        }
        println("Valid connection established!")
    }

    private fun tlsListeners(session: TLSSession) {
        session.commsClient.addListener { transmission, _ ->
            when (transmission) {
                is MessageTransmission -> {
                    println("Got a transmission over TLS!")
                    val name = JcaX500NameUtil.getX500Name(session.peer.peerPrincipal()).getRDNs(BCStyle.CN).firstOrNull()?.first?.value
                    Vars.ui.chatfrag.addMessage(transmission.content, "$name [coral]to [white]${Vars.player.name}", Color.green.cpy().mul(0.35f))  //todo bundle?
                }
            }
        }
    }

    /** Run when the object is disposed. */
    override fun dispose() {}
}

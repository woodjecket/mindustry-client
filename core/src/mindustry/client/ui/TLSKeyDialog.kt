package mindustry.client.ui

import arc.Core
import arc.input.KeyCode
import arc.scene.ui.Dialog
import arc.scene.ui.TextField
import arc.scene.ui.layout.Table
import mindustry.Vars
import mindustry.client.Main
import mindustry.client.crypto.KeyStorage
import mindustry.client.utils.*
import mindustry.gen.Icon
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import org.bouncycastle.asn1.x509.BasicConstraints
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class TLSKeyDialog : BaseDialog("@client.keyshare") {

    private val keys = Table()
    private lateinit var importDialog: Dialog

    init {
        build()
    }

    private fun regenerate() {
        keys.clear()
        keys.defaults().pad(5f).left()
        val store = Main.keyStorage ?: return
        for (key in store.trustStore.aliases()) {
            val table = Table()
            val cert = store.trustStore.getCertificate(key) as X509Certificate
            table.button(Icon.cancel, Styles.settingtogglei, 16f) {
                store.untrust(cert)
                regenerate()
            }.padRight(7f)
            table.label(cert.readableName ?: "unknown")
            table.label(store.alias(cert)?.alias ?: "@client.noalias").right()
            table.button(Icon.edit, Styles.settingtogglei, 16f) {
                Vars.ui.showTextInput("@client.alias", "@client.alias", 32, "", false) { inp ->
                    if (inp.isBlank()) {
                        store.alias(cert, null)
                        regenerate()
                        return@showTextInput
                    }
                    if ((store.trusted().any { it.readableName?.equals(inp, true) == true }) || store.aliases().any { it.alias.equals(inp, true) }) {
                        Vars.ui.showInfoFade("@client.aliastaken")
                        return@showTextInput
                    }
                    store.alias(cert, inp)
                    regenerate()
                }
            }
            keys.row(table)
        }
    }

    private fun build() {
        val store = Main.keyStorage.run {
            if (this != null) return@run this
            Core.app.post {
                Vars.ui.showTextInput(
                    "@client.certname.title",
                    "@client.certname.text",
                    Core.settings.getString("name", "")
                ) { text ->
                    if (text.length < 2) {
                        hide()
                        return@showTextInput
                    }
                    if (text.contains("\\s")) {
                        hide()
                        Vars.ui.showInfoFade("@client.keyprincipalspaces")
                        return@showTextInput
                    }
                    Main.keyStorage = KeyStorage(Core.settings.dataDirectory.file(), text)
                    build()
                }
            }
            return@run null
        } ?: return

        regenerate()

        cont.table{ t ->
            t.defaults().pad(5f)

            t.pane { pane ->
                pane.add(keys)
            }.growX()

            t.row()

            t.button("@client.importkey") {
                importDialog = dialog("@client.importkey") {
                    val keyInput = TextField("")
                    keyInput.messageText = "@client.key"
                    cont.row(keyInput).width(400f)

                    cont.row().table{ ta ->
                        ta.defaults().width(194f).pad(3f)
                        ta.button("@client.importkey") button2@{
                            val factory = CertificateFactory.getInstance("X509")
                            val cert = factory.generateCertificate(keyInput.text?.base64()?.inputStream() ?: return@button2) as? X509Certificate ?: return@button2
                            if (cert.subjectX500Principal.readableName?.contains("\\s") != false) {
                                Vars.ui.showInfoFade("@client.keyprincipalspaces")  // spaces break the commands
                                return@button2
                            }
                            if (cert.basicConstraints != -1) {  // don't allow it to be a CA cert
                                Vars.ui.showInfoFade("@client.evilcert")
                                return@button2
                            }
                            Vars.ui.showConfirm("@client.importKey", cert.subjectX500Principal.name) {
                                store.trust(cert)
                                regenerate()
                            }
                            hide()
                        }

                        ta.button("@close") {
                            hide()
                        }
                    }
                    addCloseListener()
                }.show()
            }.growX().get().label.setWrap(false)

            t.row()

            t.button("@client.exportkey") {
                Core.app.clipboardText = store.cert()?.encoded?.base64() ?: return@button
                Toast(3f).add("@copied")
            }.growX().get().label.setWrap(false)

            t.row()

            t.button("@close") {
                hide()
            }.growX().get().label.setWrap(false)
        }
        keyDown {
            if(it == KeyCode.escape || it == KeyCode.back){
                if (this::importDialog.isInitialized && importDialog.isShown) Core.app.post(importDialog::hide) // This game hates being not dumb so this is needed
                else Core.app.post(this::hide)
            }
        }
    }
}

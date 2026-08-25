package com.lucasserafin94.iptvburo.desktop.license

import kotlin.test.Test

/**
 * Asks the real server whether a connection is waiting for this machine, and prints what happens.
 *
 * Opt-in, because it talks to production:
 *
 *     ./gradlew :apps:desktop:test --tests "*LiveProvisioningProbe*" -DburoProvisioningProbe=1
 *
 * ## Why this exists
 *
 * The client is deliberately silent: every failure returns null, because almost every launch of
 * every machine finds nothing and a message about it would only alarm someone who asked for
 * nothing. That is right for shipping and useless when the seller presses Apply and the customer
 * says the list did not appear — unreachable server, refused proof, nothing waiting, and a payload
 * the app rejected are indistinguishable from outside.
 *
 * This runs the same code with the same identity and says which one it was. It prints no
 * credentials: the device code, whether something was waiting, and the host if so.
 */
class LiveProvisioningProbe {
    @Test
    fun `report what the live server has for this machine`() {
        if (System.getProperty("buroProvisioningProbe").isNullOrBlank()) return

        val identity = DeviceFingerprint.getOrCreate()
        println("")
        println("=== SONDA DE PROVISIONAMENTO ===")
        println("dispositivo : ${identity.deviceId}")
        println("endereco    : ${LicenseEndpoints.PROVISIONING_CLAIM}")
        println("")
        println("Para testar: abra o painel, procure este codigo, preencha")
        println("endereco/usuario/senha e clique em enviar. Depois rode isto de novo.")
        println("")

        val source = ProvisioningClient(DeviceFingerprint).claim()
        if (source == null) {
            println("resultado   : NADA A ESPERA")
            println("              (ou o servidor recusou, ou nada foi aplicado no painel)")
        } else {
            // The host only. The full address can carry a token, and the credentials never print.
            val host =
                runCatching { java.net.URI(String(source.server)).host }.getOrNull() ?: "<ilegivel>"
            println("resultado   : CONEXAO RECEBIDA")
            println("host        : $host")
            println("usuario     : ${source.username.size} caracteres")
            println("senha       : ${source.password.size} caracteres")
            println("chave TMDb  : ${source.metadataKey?.let { "${it.size} caracteres" } ?: "nao enviada"}")
            println("chave OMDb  : ${source.criticsKey?.let { "${it.size} caracteres" } ?: "nao enviada"}")
            println("")
            println("NAO confirmada: esta sonda nao aplica nem apaga o envio, para que")
            println("o teste possa ser repetido e para nao consumir o que o painel enviou.")
            source.clear()
        }
        println("=== FIM ===")
        println("")
    }
}

package com.lucasserafin94.iptvburo.desktop.license

import kotlin.test.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to the real licence server with this machine's real identity, and prints what happens.
 *
 * Opt-in, because it makes a network request to production:
 *
 *     ./gradlew :apps:desktop:test --tests "*LiveLicenceProbe*" -DburoLicenceProbe=1
 *
 * ## Why this exists
 *
 * The client is deliberately quiet. Every failure path returns a decision instead of throwing, and
 * logs only an exception type — because a licence answer carries URLs and headers that must never
 * reach a customer's log file.
 *
 * That is right for shipping and useless for diagnosis. When the app blocks with "could not verify
 * your licence", the four possible causes — unreachable server, refused identity, failed signature,
 * genuinely expired — are indistinguishable from outside. This runs the same code with the same
 * identity and says which one it was.
 *
 * It prints no secrets: a device code, an HTTP status, and whether a signature verified.
 */
class LiveLicenceProbe {

    @Test
    fun `report what the live server says about this machine`() {
        if (System.getProperty("buroLicenceProbe").isNullOrBlank()) return

        val identity = DeviceFingerprint.getOrCreate()
        val server = LicenseServerConfiguration.production()

        println("")
        println("=== SONDA DE LICENCA ===")
        println("dispositivo : ${identity.deviceId}")
        println("servidor    : ${server.validateUrl}")
        println("chave no app: ${server.publicKeyBase64.take(28)}...")
        println("configurado : ${server.isConfigured}")
        println("")

        // The raw exchange, so a refusal shows its status and code rather than being folded into a
        // single "unreachable".
        val nonce = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) })

        val body = com.google.gson.JsonObject().apply {
            addProperty("deviceId", identity.deviceId)
            addProperty("installationId", identity.installationId)
            addProperty("publicKey", identity.publicKeyDerBase64)
            addProperty("nonce", nonce)
            addProperty("proof", identity.proof(DeviceProofAction.VALIDATE, nonce))
        }

        val http = okhttp3.OkHttpClient.Builder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = okhttp3.Request.Builder()
            .url(server.validateUrl)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                println("validate -> HTTP ${response.code}")
                // Truncated: enough to see an error code, never enough to hold a whole licence.
                println("resposta -> ${text.take(180)}")
                println("")

                if (response.isSuccessful) {
                    val json = com.google.gson.JsonParser.parseString(text).asJsonObject
                    val signed = SignedLicense(
                        payload = json.get("payload").asString,
                        signatureBase64 = json.get("signature").asString,
                    )
                    val verified = signed.verified(
                        publicKeyBase64 = server.publicKeyBase64,
                        expectedDeviceId = identity.deviceId,
                        expectedNonce = nonce,
                    )
                    if (verified == null) {
                        println("A ASSINATURA NAO VERIFICA.")
                        println("A chave publica no app nao corresponde a privada do Worker.")
                        println("Gere um par novo e ponha as DUAS metades da MESMA execucao.")
                    } else {
                        println("assinatura OK — estado: ${verified.state}")
                        println("teste termina: ${verified.trialEndsAt}")
                        println("expira       : ${verified.expiresAt}")
                    }
                }
            }
        }.onFailure { error ->
            println("A REDE FALHOU: ${error.javaClass.simpleName}")
        }

        println("")
        println("=== decisao do cliente real ===")
        val status = LicenseClient().check()
        println("permite usar : ${status.allowsUse}")
        println("motivo       : ${status.blockReason}")
        println("offline      : ${status.offline}")
        println("relogio susp.: ${status.clockSuspect}")
        println("")
    }
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

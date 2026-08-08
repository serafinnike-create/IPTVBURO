package com.lucasserafin94.iptvburo.desktop.license

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import java.util.Base64
import java.util.UUID

/** The three operations whose requests must prove possession of the installation key. */
enum class DeviceProofAction(val wireValue: String) {
    REGISTER("register"),
    VALIDATE("validate"),
    REDEEM("redeem"),
}

/**
 * A random installation identity, with no MAC address, hostname or account name in it.
 *
 * [installationId] and the public key are safe to send during first registration. The private key
 * is deliberately not exposed: callers can ask this value to sign one bounded protocol message and
 * nothing else. Its persisted PKCS#8 bytes are protected by Windows DPAPI before they reach disk.
 */
class DeviceInstallationIdentity internal constructor(
    val installationId: String,
    val deviceId: String,
    val publicKeyDerBase64: String,
    private val privateKey: PrivateKey,
) {
    fun proof(action: DeviceProofAction, nonce: String): String {
        val canonical = canonicalDeviceProof(action, deviceId, nonce)
        val signer = Signature.getInstance(P1363_SIGNATURE)
        signer.initSign(privateKey)
        signer.update(canonical.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())
    }

    internal fun encodedPrivateKey(): ByteArray =
        privateKey.encoded?.clone() ?: error("The EC private key is not encodable.")

    override fun toString(): String =
        "DeviceInstallationIdentity(deviceId=$deviceId, installationId=<redacted>, publicKey=<redacted>)"
}

/** Supplies the one identity created for this Windows installation. */
fun interface DeviceIdentityProvider {
    fun getOrCreate(): DeviceInstallationIdentity
}

/**
 * Persists the random UUID and EC P-256 key pair as one DPAPI-protected blob.
 *
 * An existing but unreadable blob fails closed. Silently replacing it would change the public
 * device code, strand a paid entitlement and make local corruption look like a fresh installation.
 */
class WindowsDeviceIdentityStore internal constructor(
    private val file: Path,
    private val protector: DeviceIdentityProtector,
) : DeviceIdentityProvider {
    constructor() : this(defaultFile(), WindowsDpapiIdentityProtector)

    @Synchronized
    override fun getOrCreate(): DeviceInstallationIdentity {
        check(protector.isAvailable) { "Windows device identity protection is unavailable." }
        if (Files.exists(file)) {
            check(Files.isRegularFile(file)) { "The device identity path is not a file." }
            return load() ?: error("The protected device identity is unreadable.")
        }

        val created = generateIdentity()
        save(created)
        return load() ?: error("The protected device identity could not be verified after writing.")
    }

    private fun load(): DeviceInstallationIdentity? {
        val protected = runCatching { Files.readAllBytes(file) }.getOrNull() ?: return null
        if (protected.isEmpty() || protected.size > MAX_PROTECTED_BYTES) {
            Arrays.fill(protected, ZERO_BYTE)
            return null
        }

        var plaintext = ByteArray(0)
        return try {
            plaintext = protector.unprotect(protected)
            decodeIdentity(plaintext)
        } catch (_: Exception) {
            null
        } finally {
            Arrays.fill(protected, ZERO_BYTE)
            Arrays.fill(plaintext, ZERO_BYTE)
        }
    }

    private fun save(identity: DeviceInstallationIdentity) {
        val plaintext = encodeIdentity(identity)
        var protected = ByteArray(0)
        try {
            protected = protector.protect(plaintext)
            require(protected.isNotEmpty() && protected.size <= MAX_PROTECTED_BYTES)
            Files.createDirectories(file.parent)
            val temporary = file.resolveSibling("${file.fileName}.tmp")
            Files.write(
                temporary,
                protected,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Arrays.fill(plaintext, ZERO_BYTE)
            Arrays.fill(protected, ZERO_BYTE)
        }
    }

    private fun encodeIdentity(identity: DeviceInstallationIdentity): ByteArray {
        val publicBytes = Base64.getDecoder().decode(identity.publicKeyDerBase64)
        val privateBytes = identity.encodedPrivateKey()
        return try {
            ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(FORMAT_VERSION)
                    output.writeUTF(identity.installationId)
                    output.writeInt(publicBytes.size)
                    output.write(publicBytes)
                    output.writeInt(privateBytes.size)
                    output.write(privateBytes)
                }
                bytes.toByteArray()
            }
        } finally {
            Arrays.fill(publicBytes, ZERO_BYTE)
            Arrays.fill(privateBytes, ZERO_BYTE)
        }
    }

    private fun decodeIdentity(plaintext: ByteArray): DeviceInstallationIdentity =
        DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
            require(input.readInt() == FORMAT_VERSION)
            val installationId = canonicalUuid(input.readUTF())
            val publicBytes = input.readBoundedBytes(MAX_PUBLIC_KEY_BYTES)
            val privateBytes = input.readBoundedBytes(MAX_PRIVATE_KEY_BYTES)
            require(input.available() == 0)

            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes)) as ECPublicKey
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes)) as ECPrivateKey
                require(isP256(publicKey) && isP256(privateKey))

                val publicKeyBase64 = Base64.getEncoder().encodeToString(publicBytes)
                val deviceId = deriveDeviceId(installationId, publicBytes)
                val identity =
                    DeviceInstallationIdentity(
                        installationId = installationId,
                        deviceId = deviceId,
                        publicKeyDerBase64 = publicKeyBase64,
                        privateKey = privateKey,
                    )
                require(verifyPair(identity, publicKey)) { "The stored EC key pair does not match." }
                identity
            } finally {
                Arrays.fill(publicBytes, ZERO_BYTE)
                Arrays.fill(privateBytes, ZERO_BYTE)
            }
        }

    private fun DataInputStream.readBoundedBytes(maximum: Int): ByteArray {
        val length = readInt()
        require(length in 1..maximum)
        return ByteArray(length).also(::readFully)
    }

    private fun verifyPair(identity: DeviceInstallationIdentity, publicKey: ECPublicKey): Boolean {
        val nonce = "AAAAAAAAAAAAAAAAAAAAAA"
        val signature = Base64.getUrlDecoder().decode(identity.proof(DeviceProofAction.VALIDATE, nonce))
        return try {
            val verifier = Signature.getInstance(P1363_SIGNATURE)
            verifier.initVerify(publicKey)
            verifier.update(
                canonicalDeviceProof(DeviceProofAction.VALIDATE, identity.deviceId, nonce)
                    .toByteArray(StandardCharsets.UTF_8),
            )
            verifier.verify(signature)
        } finally {
            Arrays.fill(signature, ZERO_BYTE)
        }
    }

    private fun generateIdentity(): DeviceInstallationIdentity {
        val installationId = UUID.randomUUID().toString()
        val keyPair =
            KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec(P256_CURVE))
            }.generateKeyPair()
        val publicBytes = keyPair.public.encoded
        return DeviceInstallationIdentity(
            installationId = installationId,
            deviceId = deriveDeviceId(installationId, publicBytes),
            publicKeyDerBase64 = Base64.getEncoder().encodeToString(publicBytes),
            privateKey = keyPair.private,
        )
    }

    private fun isP256(key: ECPublicKey): Boolean =
        key.params.curve.field.fieldSize == 256 && key.params.order.bitLength() == 256

    private fun isP256(key: ECPrivateKey): Boolean =
        key.params.curve.field.fieldSize == 256 && key.params.order.bitLength() == 256

    private companion object {
        const val FORMAT_VERSION = 1
        const val MAX_PUBLIC_KEY_BYTES = 512
        const val MAX_PRIVATE_KEY_BYTES = 512
        const val MAX_PROTECTED_BYTES = 8 * 1024
        const val ZERO_BYTE: Byte = 0

        fun defaultFile(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
            val root =
                localAppData?.let(Path::of)
                    ?: Path.of(System.getProperty("user.home"), "AppData", "Local")
            return root.resolve("lucasserafin94").resolve("IPTVBURO").resolve("device-identity.dpapi")
        }
    }
}

/** Backwards-compatible call site name; it no longer fingerprints any machine property. */
object DeviceFingerprint : DeviceIdentityProvider {
    private val store by lazy(::WindowsDeviceIdentityStore)

    override fun getOrCreate(): DeviceInstallationIdentity = store.getOrCreate()

    fun deviceId(): String = getOrCreate().deviceId
}

internal interface DeviceIdentityProtector {
    val isAvailable: Boolean
    fun protect(plaintext: ByteArray): ByteArray
    fun unprotect(protected: ByteArray): ByteArray
}

internal object WindowsDpapiIdentityProtector : DeviceIdentityProtector {
    override val isAvailable: Boolean get() = Platform.isWindows()

    override fun protect(plaintext: ByteArray): ByteArray {
        check(isAvailable)
        return Crypt32Util.cryptProtectData(plaintext)
    }

    override fun unprotect(protected: ByteArray): ByteArray {
        check(isAvailable)
        return Crypt32Util.cryptUnprotectData(protected)
    }
}

internal fun canonicalDeviceProof(
    action: DeviceProofAction,
    deviceId: String,
    nonce: String,
): String {
    require(DEVICE_ID.matches(deviceId))
    require(PROOF_NONCE.matches(nonce))
    return "$PROOF_DOMAIN\n${action.wireValue}\n$deviceId\n$nonce"
}

internal fun deriveDeviceId(installationId: String, publicKeyDer: ByteArray): String {
    val uuid = canonicalUuid(installationId)
    val digest =
        MessageDigest.getInstance("SHA-256").digest(
            publicKeyDer + uuid.toByteArray(StandardCharsets.UTF_8),
        )

    var bitBuffer = 0
    var bitCount = 0
    val output = StringBuilder(DEVICE_ID_CHARACTERS)
    for (byte in digest) {
        bitBuffer = (bitBuffer shl 8) or (byte.toInt() and 0xFF)
        bitCount += 8
        while (bitCount >= 5 && output.length < DEVICE_ID_CHARACTERS) {
            bitCount -= 5
            output.append(DEVICE_ID_ALPHABET[(bitBuffer shr bitCount) and 31])
        }
        bitBuffer = if (bitCount == 0) 0 else bitBuffer and ((1 shl bitCount) - 1)
        if (output.length == DEVICE_ID_CHARACTERS) break
    }
    return output.toString().chunked(4).joinToString("-")
}

internal fun canonicalUuid(value: String): String {
    require(value.length == UUID_TEXT_LENGTH)
    val uuid = UUID.fromString(value)
    require(uuid.version() == 4 && uuid.variant() == 2) { "Installation UUID is not random RFC-4122 v4." }
    val canonical = uuid.toString()
    require(canonical == value) { "Installation UUID is not canonical." }
    return canonical
}

private const val P1363_SIGNATURE = "SHA256withECDSAinP1363Format"
private const val P256_CURVE = "secp256r1"
private const val PROOF_DOMAIN = "iptvburo-device-proof-v1"
private const val UUID_TEXT_LENGTH = 36
private const val DEVICE_ID_CHARACTERS = 12
private const val DEVICE_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private val DEVICE_ID = Regex("[A-Z2-9]{4}(?:-[A-Z2-9]{4}){2}")
private val PROOF_NONCE = Regex("[A-Za-z0-9_-]{22}")

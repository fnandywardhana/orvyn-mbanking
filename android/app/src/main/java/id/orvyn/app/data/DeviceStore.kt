package id.orvyn.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps what this phone needs between launches, in app-private storage (never backed up, see
 * data_extraction_rules). The device token is encrypted with a key that never leaves the Android Keystore.
 */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("orvyn", Context.MODE_PRIVATE)

    var deviceToken: String?
        get() = prefs.getString("device_token", null)?.let(::decrypt)
        set(v) = prefs.edit().apply { if (v == null) remove("device_token") else putString("device_token", encrypt(v)) }.apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(v) = prefs.edit().putBoolean("biometric", v).apply()

    var balanceHidden: Boolean
        get() = prefs.getBoolean("balance_hidden", false)
        set(v) = prefs.edit().putBoolean("balance_hidden", v).apply()

    /** Last home screen, shown with its time when the phone is offline (story S1.7). Example data only, so plain JSON is fine. */
    var lastHome: Pair<Long, String>?
        get() = prefs.getString("last_home", null)?.let { prefs.getLong("last_home_at", 0) to it }
        set(v) = prefs.edit().apply {
            if (v == null) remove("last_home").remove("last_home_at") else putLong("last_home_at", v.first).putString("last_home", v.second)
        }.apply()

    fun forgetDevice() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12)) }
        String(c.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()

    private companion object { const val ALIAS = "orvyn_device_token" }
}

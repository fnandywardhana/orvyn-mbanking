package id.orvyn.app.data

import id.orvyn.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/** Something the customer should read: server messages are already written for them. */
class ApiException(val code: String, override val message: String, val status: Int, val body: JSONObject? = null) : Exception(message)

data class Account(val id: String, val kind: String, val name: String, val number: String?, val balance: Long)

data class Transaction(
    val id: String,
    val at: Instant,
    val amount: Long,
    val title: String,
    val subtitle: String?,
    val category: String?,
    val note: String?,
    val fee: Long,
    val kind: String,
    val reversed: Boolean,
    val reference: String? = null,
    val accountNumber: String? = null,
)

data class Home(
    val firstName: String,
    val fullName: String,
    val tier: String,
    val totalBalance: Long,
    val accounts: List<Account>,
    val recent: List<Transaction>,
)

data class Page(val items: List<Transaction>, val next: String?)

/**
 * Tiny JSON-over-HTTPS client on the platform's HttpURLConnection and org.json (no extra libraries).
 * [session] is held in memory only; it dies with the process, which also enforces sign-out.
 */
object Api {
    @Volatile var session: String? = null
    /** Called when the server says the session is gone (idle timeout, signed in elsewhere, locked). */
    var onSignedOut: () -> Unit = {}

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val conn = (URL(BuildConfig.API_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            session?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray()) }
            }
        }
        try {
            val status = conn.responseCode
            val text = (if (status < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status >= 400) {
                if (json.optString("error") == "signed_out") { session = null; onSignedOut() }
                throw ApiException(json.optString("error", "http_$status"), json.optString("message", "Something went wrong on our side. Try again in a moment."), status, json)
            }
            json
        } catch (e: IOException) {
            throw ApiException("offline", "You're offline. Check your connection and try again.", 0)
        } finally {
            conn.disconnect()
        }
    }

    data class SignIn(val deviceToken: String?, val sessionToken: String, val needsPin: Boolean)

    suspend fun signInWithPassword(phone: String, password: String): SignIn {
        val r = call("POST", "/auth/password", JSONObject().put("phone", phone).put("password", password))
        return SignIn(r.getString("deviceToken"), r.getString("sessionToken"), r.getBoolean("needsPin")).also { session = it.sessionToken }
    }

    suspend fun signInWithPin(deviceToken: String, pin: String) {
        session = call("POST", "/auth/pin", JSONObject().put("deviceToken", deviceToken).put("pin", pin)).getString("sessionToken")
    }

    suspend fun signInWithDevice(deviceToken: String) {
        session = call("POST", "/auth/device", JSONObject().put("deviceToken", deviceToken)).getString("sessionToken")
    }

    suspend fun setPin(pin: String) { call("POST", "/pin", JSONObject().put("pin", pin)) }

    suspend fun signOut() {
        runCatching { call("POST", "/auth/signout") }
        session = null
    }

    suspend fun homeJson(): JSONObject = call("GET", "/home")

    suspend fun transactions(accountId: String, before: String? = null, direction: String? = null, query: String? = null): Page {
        val params = listOfNotNull(
            "limit=30",
            before?.let { "before=" + enc(it) },
            direction?.let { "direction=$it" },
            query?.takeIf { it.isNotBlank() }?.let { "q=" + enc(it) },
        ).joinToString("&")
        val r = call("GET", "/accounts/$accountId/transactions?$params")
        return Page(r.getJSONArray("items").map(::parseTransaction), r.optString("next").takeIf { it.isNotEmpty() && it != "null" })
    }

    suspend fun transaction(id: String): Transaction = parseTransaction(call("GET", "/transactions/$id"))

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

fun parseHome(j: JSONObject): Home {
    val c = j.getJSONObject("customer")
    return Home(
        firstName = c.getString("firstName"),
        fullName = c.getString("name"),
        tier = c.getString("tier"),
        totalBalance = j.getLong("totalBalance"),
        accounts = j.getJSONArray("accounts").map {
            Account(it.getString("id"), it.getString("kind"), it.getString("name"), it.optString("number").takeIf { n -> n.isNotEmpty() && n != "null" }, it.getLong("balance"))
        },
        recent = j.getJSONArray("recent").map(::parseTransaction),
    )
}

fun parseTransaction(t: JSONObject) = Transaction(
    id = t.getString("id"),
    at = Instant.parse(t.getString("at")),
    amount = t.getLong("amount"),
    title = t.getString("title"),
    subtitle = t.optStringOrNull("subtitle"),
    category = t.optStringOrNull("category"),
    note = t.optStringOrNull("note"),
    fee = t.optLong("fee"),
    kind = t.getString("kind"),
    reversed = t.optString("status") == "reversed",
    reference = t.optStringOrNull("reference"),
    accountNumber = t.optStringOrNull("accountNumber"),
)

private fun JSONObject.optStringOrNull(key: String) = if (isNull(key) || !has(key)) null else getString(key)
private inline fun <T> JSONArray.map(f: (JSONObject) -> T) = List(length()) { f(getJSONObject(it)) }

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    val unread: Int,
    val firstName: String,
    val fullName: String,
    val tier: String,
    val totalBalance: Long,
    val accounts: List<Account>,
    val recent: List<Transaction>,
)

data class Page(val items: List<Transaction>, val next: String?)

data class Bank(val code: String, val name: String)

/** Someone to send money to. [id] is set for Saved Recipients. */
data class Recipient(
    val bank: String,
    val bankName: String,
    val accountNumber: String,
    val name: String,
    val nickname: String? = null,
    val id: String? = null,
) { val internal get() = bank == "ORVYN" }

data class Method(val method: String, val label: String, val fee: Long, val note: String)
data class TransferOptions(val methods: List<Method>, val remainingToday: Long, val balance: Long)
data class Recipients(val saved: List<Recipient>, val recent: List<Recipient>)

data class Merchant(val id: String, val name: String, val city: String, val category: String)
sealed interface Scan {
    data class Pay(val merchant: Merchant, val amount: Long?) : Scan
    data class Send(val recipient: Recipient, val amount: Long?) : Scan
}
data class MyQr(val payload: String, val name: String, val accountNumber: String)

data class SavedNumber(val number: String, val name: String)
data class Biller(val code: String, val name: String, val kind: String, val fee: Long, val saved: List<SavedNumber>)
data class Product(val id: String, val label: String, val price: Long)
data class Inquiry(
    val biller: String, val billerName: String, val kind: String, val number: String, val name: String?, val details: String?,
    val fee: Long, val billPeriod: String?, val billAmount: Long?, val products: List<Product>, val minAmount: Long?,
)
data class Credit(val limit: Long, val owed: Long, val available: Long, val minimum: Long, val dueDate: String)
data class Card(
    val id: String, val kind: String, val product: String, val name: String, val last4: String,
    val frozen: Boolean, val online: Boolean, val overseas: Boolean, val contactless: Boolean, val dailyLimit: Long, val credit: Credit?,
)
data class CardSecret(val number: String, val cvv: String, val expiry: String)

data class InboxItem(val id: String, val title: String, val body: String, val link: String?, val read: Boolean, val at: Instant)
data class Announcement(val id: String, val title: String, val body: String)

data class Banner(val id: String, val title: String, val body: String, val detail: String, val style: String)
data class Faq(val id: String, val category: String, val question: String, val answer: String)
data class Content(val banners: List<Banner>, val faqs: List<Faq>)

data class BillPaid(val reference: String, val token: String?, val amount: Long, val fee: Long, val label: String)

/**
 * Tiny JSON-over-HTTPS client on the platform's HttpURLConnection and org.json (no extra libraries).
 * [session] is held in memory only; it dies with the process, which also enforces sign-out.
 */
object Api {
    @Volatile var session: String? = null
    /** Called when the server says the session is gone (idle timeout, signed in elsewhere, locked). */
    var onSignedOut: () -> Unit = {}

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val hadSession = session != null
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
                // Only someone who was signed in can be signed out; sign-up and sign-in screens just show the message.
                if (json.optString("error") == "signed_out" && hadSession) { session = null; onSignedOut() }
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

    suspend fun banks(): List<Bank> = call("GET", "/banks").getJSONArray("banks").map { Bank(it.getString("code"), it.getString("name")) }

    suspend fun lookup(bank: String, accountNumber: String): Recipient =
        parseRecipient(call("POST", "/transfers/lookup", JSONObject().put("bank", bank).put("accountNumber", accountNumber)))

    suspend fun transferOptions(bank: String, amount: Long): TransferOptions {
        val r = call("GET", "/transfers/options?bank=${enc(bank)}&amount=$amount")
        return TransferOptions(
            r.getJSONArray("methods").map { Method(it.getString("method"), it.getString("label"), it.getLong("fee"), it.getString("note")) },
            r.getLong("remainingToday"), r.getLong("balance"),
        )
    }

    /** Returns the reference shown on the success screen. */
    suspend fun sendTransfer(to: Recipient, amount: Long, method: String, note: String, pin: String, idempotencyKey: String): String =
        call("POST", "/transfers", JSONObject().put("bank", to.bank).put("accountNumber", to.accountNumber).put("amount", amount)
            .put("method", method).put("note", note).put("pin", pin).put("idempotencyKey", idempotencyKey)).getString("reference")

    suspend fun recipients(): Recipients {
        val r = call("GET", "/recipients")
        return Recipients(r.getJSONArray("saved").map(::parseRecipient), r.getJSONArray("recent").map(::parseRecipient))
    }

    suspend fun saveRecipient(to: Recipient, nickname: String?) {
        call("POST", "/recipients", JSONObject().put("bank", to.bank).put("accountNumber", to.accountNumber).put("nickname", nickname ?: ""))
    }

    suspend fun deleteRecipient(id: String) { call("DELETE", "/recipients/$id") }

    suspend fun renameRecipient(id: String, nickname: String) { call("POST", "/recipients/$id/nickname", JSONObject().put("nickname", nickname)) }

    suspend fun qrisScan(payload: String): Scan {
        val r = call("POST", "/qris/scan", JSONObject().put("payload", payload))
        val amount = if (r.isNull("amount")) null else r.getLong("amount")
        return if (r.getString("type") == "p2p") Scan.Send(parseRecipient(r.getJSONObject("recipient")), amount)
        else r.getJSONObject("merchant").let { Scan.Pay(Merchant(it.getString("id"), it.getString("name"), it.getString("city"), it.getString("category")), amount) }
    }

    suspend fun qrisPay(merchantId: String, amount: Long, pin: String, idempotencyKey: String): String =
        call("POST", "/qris/pay", JSONObject().put("merchantId", merchantId).put("amount", amount).put("pin", pin).put("idempotencyKey", idempotencyKey)).getString("reference")

    suspend fun myQr(amount: Long?): MyQr {
        val r = call("GET", "/qris/mine" + (amount?.let { "?amount=$it" } ?: ""))
        return MyQr(r.getString("payload"), r.getString("name"), r.getString("accountNumber"))
    }

    suspend fun billers(): List<Biller> = call("GET", "/billers").getJSONArray("billers").map { b ->
        Biller(b.getString("code"), b.getString("name"), b.getString("kind"), b.getLong("fee"),
            b.getJSONArray("saved").map { SavedNumber(it.getString("number"), it.getString("name")) })
    }

    suspend fun inquire(biller: String, number: String): Inquiry {
        val r = call("POST", "/bills/inquiry", JSONObject().put("biller", biller).put("number", number))
        val bill = r.optJSONObject("bill")
        return Inquiry(
            r.getString("biller"), r.getString("billerName"), r.getString("kind"), r.getString("number"),
            if (r.isNull("name")) null else r.getString("name"), if (r.isNull("details")) null else r.getString("details"),
            r.getLong("fee"), bill?.getString("period"), bill?.getLong("amount"),
            r.getJSONArray("products").map { Product(it.getString("id"), it.getString("label"), it.getLong("price")) },
            if (r.isNull("minAmount")) null else r.getLong("minAmount"),
        )
    }

    suspend fun payBill(biller: String, number: String, productId: String?, amount: Long?, pin: String, idempotencyKey: String): BillPaid {
        val r = call("POST", "/bills/pay", JSONObject().put("biller", biller).put("number", number).put("productId", productId ?: JSONObject.NULL)
            .put("amount", amount ?: JSONObject.NULL).put("pin", pin).put("idempotencyKey", idempotencyKey))
        return BillPaid(r.getString("reference"), if (r.isNull("token")) null else r.getString("token"), r.getLong("amount"), r.getLong("fee"), r.getString("label"))
    }

    private fun parseCards(r: JSONObject) = r.getJSONArray("cards").map { c ->
        Card(c.getString("id"), c.getString("kind"), c.getString("product"), c.getString("name"), c.getString("last4"),
            c.getBoolean("frozen"), c.getBoolean("online"), c.getBoolean("overseas"), c.getBoolean("contactless"), c.getLong("dailyLimit"),
            c.optJSONObject("credit")?.let { Credit(it.getLong("limit"), it.getLong("owed"), it.getLong("available"), it.getLong("minimum"), it.getString("dueDate")) })
    }

    suspend fun cards(): List<Card> = parseCards(call("GET", "/cards"))

    /** Change switches or the daily limit; returns the updated list. */
    suspend fun updateCard(id: String, patch: JSONObject): List<Card> = parseCards(call("POST", "/cards/$id/settings", patch))

    suspend fun cardDetails(id: String, pin: String): CardSecret =
        call("POST", "/cards/$id/details", JSONObject().put("pin", pin)).let { CardSecret(it.getString("number"), it.getString("cvv"), it.getString("expiry")) }

    suspend fun cardTransactions(id: String): List<Transaction> = call("GET", "/cards/$id/transactions").getJSONArray("items").map(::parseTransaction)

    suspend fun payCard(id: String, amount: Long, pin: String, idempotencyKey: String): String =
        call("POST", "/cards/$id/pay", JSONObject().put("amount", amount).put("pin", pin).put("idempotencyKey", idempotencyKey)).getString("reference")

    suspend fun inbox(): List<InboxItem> = call("GET", "/inbox").getJSONArray("items").map {
        InboxItem(it.getString("id"), it.getString("title"), it.getString("body"), if (it.isNull("link")) null else it.getString("link"),
            it.getBoolean("read"), Instant.parse(it.getString("at")))
    }

    suspend fun markInboxRead() { call("POST", "/inbox/read") }

    suspend fun announcement(): Announcement? = call("GET", "/announcement").optJSONObject("announcement")
        ?.let { Announcement(it.getString("id"), it.getString("title"), it.getString("body")) }

    suspend fun announcementSeen(id: String) { call("POST", "/announcement/$id/seen") }

    suspend fun content(): Content {
        val r = call("GET", "/content")
        return Content(
            r.getJSONArray("banners").map { Banner(it.getString("id"), it.getString("title"), it.getString("body"), it.getString("detail"), it.getString("style")) },
            r.getJSONArray("faqs").map { Faq(it.getString("id"), it.getString("category"), it.getString("question"), it.getString("answer")) },
        )
    }

    suspend fun changePin(oldPin: String, newPin: String) { call("POST", "/me/pin", JSONObject().put("oldPin", oldPin).put("newPin", newPin)) }

    suspend fun changePassword(old: String, new: String) { call("POST", "/me/password", JSONObject().put("oldPassword", old).put("newPassword", new)) }

    /** Returns the demo OTP; a real bank would only send it by SMS. */
    suspend fun registerOtp(phone: String): Pair<String, String> =
        call("POST", "/register/otp", JSONObject().put("phone", phone)).let { it.getString("phone") to it.getString("demoCode") }

    suspend fun registerVerify(phone: String, code: String): String =
        call("POST", "/register/verify", JSONObject().put("phone", phone).put("code", code)).getString("token")

    suspend fun registerSubmit(token: String, details: JSONObject, password: String, pin: String) {
        call("POST", "/register/submit", JSONObject().put("token", token).put("details", details).put("password", password).put("pin", pin))
    }

    /** (status, reason, phone) */
    suspend fun registerStatus(token: String): Triple<String, String?, String> =
        call("GET", "/register/status?token=${enc(token)}").let { Triple(it.getString("status"), if (it.isNull("reason")) null else it.getString("reason"), it.getString("phone")) }

    suspend fun loadConfig() {
        val r = call("GET", "/config")
        val f = r.getJSONObject("features")
        AppConfig.features = f.keys().asSequence().associateWith { f.getBoolean(it) }
        val m = r.getJSONObject("maintenance")
        AppConfig.maintenance = if (m.getBoolean("on")) m.getString("message") else null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

fun parseHome(j: JSONObject): Home {
    val c = j.getJSONObject("customer")
    return Home(
        unread = j.optInt("unread"),
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

fun parseRecipient(r: JSONObject) = Recipient(
    bank = r.getString("bank"), bankName = r.getString("bankName"), accountNumber = r.getString("accountNumber"),
    name = r.getString("name"), nickname = r.optStringOrNull("nickname"), id = r.optStringOrNull("id"),
)

/** Switches from the admin (story S1.37). Defaults keep everything on if the server can't be reached. */
object AppConfig {
    var features by androidx.compose.runtime.mutableStateOf(mapOf<String, Boolean>())
    var maintenance by androidx.compose.runtime.mutableStateOf<String?>(null)
    fun on(feature: String) = features[feature] != false
}

/** Bumped after money moves so screens that show balances reload. */
object DataVersion { var count by androidx.compose.runtime.mutableIntStateOf(0) }

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

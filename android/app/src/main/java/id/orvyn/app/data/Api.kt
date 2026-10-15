package id.orvyn.app.data

import id.orvyn.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
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
    val spendCategory: String? = null,
)

/** Story S2.1. [rule] is the server's auto-save rule as-is, e.g. {"type":"weekly","day":1,"amount":100000}. */
data class Pocket(val id: String, val name: String, val balance: Long, val image: String, val target: Long?, val targetDate: String?, val rule: JSONObject?, val reached: Boolean) {
    val progress get() = target?.let { (balance.toFloat() / it).coerceIn(0f, 1f) }
}

/** Story S2.6. [status]: open, paid, declined, cancelled or expired. [mine]: I asked; otherwise I'm asked to pay. */
data class MoneyRequest(val id: String, val amount: Long, val note: String?, val status: String, val mine: Boolean, val from: String, val to: String, val splitId: String?, val at: Instant)
data class SplitPerson(val requestId: String, val name: String, val amount: Long, val status: String)
data class Split(val id: String, val total: Long, val note: String?, val people: List<SplitPerson>)

/** Stories S2.8–S2.11. [change] is percent against last month, null when last month had nothing. */
data class CategorySpend(val key: String, val label: String, val total: Long, val previous: Long, val change: Int?)
data class Budget(val category: String, val label: String, val limit: Long, val spent: Long)
data class Insights(val month: String, val months: List<Pair<String, Long>>, val categories: List<CategorySpend>, val budgets: List<Budget>)

data class StatementLine(val at: Instant, val description: String, val channel: String?, val amount: Long, val balance: Long)
data class Statement(val name: String, val accountName: String, val accountNumber: String, val month: String,
    val opening: Long, val closing: Long, val moneyIn: Long, val moneyOut: Long, val lines: List<StatementLine>)

data class Scheduled(val id: String, val name: String, val bank: String, val accountNumber: String, val amount: Long,
    val repeat: String, val nextRun: String?, val paused: Boolean, val lastOk: Boolean?, val lastMessage: String?)

data class Home(
    val unread: Int,
    val firstName: String,
    val fullName: String,
    val tier: String,
    val totalBalance: Long,
    val accounts: List<Account>,
    val recent: List<Transaction>,
    val pockets: List<Pocket> = emptyList(),
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

data class Banner(val id: String, val title: String, val body: String, val detail: String, val style: String, val image: String?, val action: String?)
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

    /** [forget]: signing out from Profile, so the server also forgets this phone and stops pushing to it. */
    suspend fun signOut(forget: Boolean = false) {
        runCatching { call("POST", "/auth/signout", JSONObject().put("forget", forget)) }
        session = null
    }

    suspend fun registerPush(token: String) { call("POST", "/me/push", JSONObject().put("token", token)) }

    suspend fun homeJson(): JSONObject = call("GET", "/home")

    suspend fun transactions(accountId: String, before: String? = null, direction: String? = null, query: String? = null, category: String? = null, from: Instant? = null): Page {
        val params = listOfNotNull(
            "limit=30",
            category?.let { "category=$it" },
            from?.let { "from=" + enc(it.toString()) },
            before?.let { "before=" + enc(it) },
            direction?.let { "direction=$it" },
            query?.takeIf { it.isNotBlank() }?.let { "q=" + enc(it) },
        ).joinToString("&")
        val r = call("GET", "/accounts/$accountId/transactions?$params")
        return Page(r.getJSONArray("items").map(::parseTransaction), r.optString("next").takeIf { it.isNotEmpty() && it != "null" })
    }

    suspend fun transaction(id: String, account: String? = null): Transaction =
        parseTransaction(call("GET", "/transactions/$id" + (account?.let { "?account=$it" } ?: "")))

    // Pockets (stories S2.1–S2.3)
    suspend fun pockets(): List<Pocket> = call("GET", "/pockets").getJSONArray("pockets").map(::parsePocket)
    private fun pocketBody(name: String, image: String, target: Long?, targetDate: String?) =
        JSONObject().put("name", name).put("image", image).put("target", target ?: JSONObject.NULL).put("targetDate", targetDate ?: JSONObject.NULL)
    suspend fun createPocket(name: String, image: String, target: Long?, targetDate: String?): String =
        call("POST", "/pockets", pocketBody(name, image, target, targetDate)).getString("id")
    suspend fun updatePocket(p: Pocket, name: String = p.name, image: String = p.image, target: Long? = p.target, targetDate: String? = p.targetDate, rule: JSONObject? = p.rule) {
        call("POST", "/pockets/${p.id}", pocketBody(name, image, target, targetDate).put("rule", rule ?: JSONObject.NULL))
    }
    /** Returns true when this move reached the pocket's target. */
    suspend fun movePocket(id: String, intoPocket: Boolean, amount: Long, key: String): Boolean =
        call("POST", "/pockets/$id/move", JSONObject().put("direction", if (intoPocket) "in" else "out").put("amount", amount).put("idempotencyKey", key)).optBoolean("reached")
    suspend fun closePocket(id: String, key: String) { call("POST", "/pockets/$id/close", JSONObject().put("idempotencyKey", key)) }

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

    // Scheduled Transfers (stories S2.4–S2.5). [repeat] is once, weekly or monthly; [firstDate] YYYY-MM-DD.
    suspend fun createSchedule(to: Recipient, amount: Long, method: String, note: String, repeat: String, firstDate: String, pin: String) {
        call("POST", "/scheduled", JSONObject().put("bank", to.bank).put("accountNumber", to.accountNumber).put("amount", amount)
            .put("method", method).put("note", note).put("repeat", repeat).put("firstDate", firstDate).put("pin", pin))
    }
    suspend fun scheduled(): List<Scheduled> = call("GET", "/scheduled").getJSONArray("scheduled").map {
        val last = it.optJSONObject("lastResult")
        Scheduled(it.getString("id"), it.getString("name"), it.getString("bank"), it.getString("accountNumber"), it.getLong("amount"),
            it.getString("repeat"), it.optStringOrNull("nextRun"), it.getBoolean("paused"), last?.optBoolean("ok"), last?.optString("message"))
    }
    suspend fun updateSchedule(id: String, paused: Boolean? = null, delete: Boolean = false) {
        call("POST", "/scheduled/$id", JSONObject().apply { paused?.let { put("paused", it) }; if (delete) put("delete", true) })
    }

    // Vyna (stories S2.12–S2.16)
    /** Questions allowed today and already asked; null when Vyna isn't available on the server. */
    suspend fun vyna(): Pair<Int, Int>? = call("GET", "/vyna").let { if (it.optBoolean("available")) it.getInt("limit") to it.getInt("used") else null }

    /**
     * Vyna's answer as it is written, from server-sent events: ("text", {text}), ("action", {...}), ("error", {message}), ("done", {}).
     * [history] is (role, text) pairs, the newest question last.
     */
    fun vynaChat(history: List<Pair<String, String>>): kotlinx.coroutines.flow.Flow<Pair<String, JSONObject>> = kotlinx.coroutines.flow.flow {
        val conn = (URL(BuildConfig.API_URL + "/vyna/chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 60_000
            setRequestProperty("Accept", "text/event-stream"); setRequestProperty("Content-Type", "application/json")
            session?.let { setRequestProperty("Authorization", "Bearer $it") }
            doOutput = true
        }
        try {
            conn.outputStream.use { out ->
                out.write(JSONObject().put("messages", JSONArray(history.map { (r, c) -> JSONObject().put("role", r).put("content", c) })).toString().toByteArray())
            }
            if (conn.responseCode >= 400) {
                val json = runCatching { JSONObject(conn.errorStream.bufferedReader().use { it.readText() }) }.getOrElse { JSONObject() }
                if (json.optString("error") == "signed_out") { session = null; onSignedOut() }
                emit("error" to JSONObject().put("message", json.optString("message", "Vyna couldn't answer just now. Try again in a moment.")))
                return@flow
            }
            var event = "message"
            conn.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) when {
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> emit(event to runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrElse { JSONObject() })
                }
            }
        } catch (e: IOException) {
            emit("error" to JSONObject().put("message", "You're offline, or the connection dropped. Check it and try again."))
        } finally { conn.disconnect() }
    }.flowOn(Dispatchers.IO)

    // E-statements (story S2.17)
    suspend fun statementMonths(): List<String> = call("GET", "/statements").getJSONArray("months").let { a -> List(a.length()) { a.getString(it) } }
    suspend fun statement(month: String): Statement {
        val r = call("GET", "/statements/$month")
        return Statement(r.getString("name"), r.getJSONObject("account").getString("name"), r.getJSONObject("account").getString("number"), month,
            r.getLong("opening"), r.getLong("closing"), r.getLong("moneyIn"), r.getLong("moneyOut"),
            r.getJSONArray("items").map { StatementLine(Instant.parse(it.getString("at")), it.getString("description"), it.optStringOrNull("channel"), it.getLong("amount"), it.getLong("balance")) })
    }

    // Insights and budgets (stories S2.8–S2.11)
    suspend fun insights(): Insights {
        val r = call("GET", "/insights")
        return Insights(r.getString("month"),
            r.getJSONArray("months").map { it.getString("month") to it.getLong("total") },
            r.getJSONArray("categories").map { CategorySpend(it.getString("key"), it.getString("label"), it.getLong("total"), it.getLong("previous"), if (it.isNull("change")) null else it.getInt("change")) },
            r.getJSONArray("budgets").map { Budget(it.getString("category"), it.getString("label"), it.getLong("limit"), it.getLong("spent")) })
    }
    /** One sentence for the home screen, or null when there isn't enough history yet. */
    suspend fun insightCard(): String? = call("GET", "/insights/card").optJSONObject("card")?.getString("text")
    suspend fun setBudget(category: String, limit: Long?) { call("POST", "/budgets", JSONObject().put("category", category).put("limit", limit ?: JSONObject.NULL)) }
    suspend fun setCategory(transactionId: String, category: String) { call("POST", "/transactions/$transactionId/category", JSONObject().put("category", category)) }

    // Money Requests and Split Bills (stories S2.6–S2.7)
    private fun parseRequest(r: JSONObject) = MoneyRequest(r.getString("id"), r.getLong("amount"), r.optStringOrNull("note"), r.getString("status"),
        r.getBoolean("mine"), r.getString("from"), r.getString("to"), r.optStringOrNull("splitId"), Instant.parse(r.getString("createdAt")))
    suspend fun requests(): Pair<List<MoneyRequest>, List<Split>> {
        val r = call("GET", "/requests")
        return r.getJSONArray("requests").map(::parseRequest) to r.getJSONArray("splits").map { s ->
            Split(s.getString("id"), s.getLong("total"), s.optStringOrNull("note"), s.getJSONArray("people").map {
                SplitPerson(it.getString("requestId"), it.getString("name"), it.getLong("amount"), it.getString("status"))
            })
        }
    }
    suspend fun request(id: String): MoneyRequest = parseRequest(call("GET", "/requests/$id"))
    suspend fun askForMoney(accountNumber: String, amount: Long, note: String) {
        call("POST", "/requests", JSONObject().put("accountNumber", accountNumber).put("amount", amount).put("note", note))
    }
    suspend fun splitBill(total: Long, note: String, shares: List<Pair<String, Long>>) {
        call("POST", "/splits", JSONObject().put("total", total).put("note", note).put("shares",
            org.json.JSONArray(shares.map { (number, amount) -> JSONObject().put("accountNumber", number).put("amount", amount) })))
    }
    suspend fun payRequest(id: String, pin: String): String = call("POST", "/requests/$id/pay", JSONObject().put("pin", pin)).getString("reference")
    suspend fun declineRequest(id: String) { call("POST", "/requests/$id/decline") }
    suspend fun cancelRequest(id: String) { call("POST", "/requests/$id/cancel") }

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
            r.getJSONArray("banners").map { Banner(it.getString("id"), it.getString("title"), it.getString("body"), it.getString("detail"), it.getString("style"),
                it.optString("image").takeIf { s -> s.isNotEmpty() && s != "null" }, it.optString("action").takeIf { s -> s.isNotEmpty() && s != "null" }) },
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
        pockets = j.optJSONArray("pockets")?.map(::parsePocket) ?: emptyList(),
    )
}

fun parsePocket(p: JSONObject) = Pocket(
    p.getString("id"), p.getString("name"), p.getLong("balance"), p.getString("image"),
    p.optLong("target").takeIf { !p.isNull("target") }, p.optStringOrNull("targetDate"), p.optJSONObject("rule"), p.optBoolean("reached"),
)

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
    spendCategory = t.optStringOrNull("spendCategory"),
)

private fun JSONObject.optStringOrNull(key: String) = if (isNull(key) || !has(key)) null else getString(key)
private inline fun <T> JSONArray.map(f: (JSONObject) -> T) = List(length()) { f(getJSONObject(it)) }

package id.orvyn.app.ui.vyna

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import id.orvyn.app.R
import id.orvyn.app.data.Api
import id.orvyn.app.data.DeviceStore
import id.orvyn.app.ui.Money
import id.orvyn.app.ui.components.OrvynButton
import id.orvyn.app.ui.theme.Brand
import id.orvyn.app.ui.theme.OrvynTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

// Stories S2.12–S2.15: chat with Vyna. The conversation lives in memory for this sign-in only (never on the server).

class VynaMessage(val fromVyna: Boolean, text: String, val actions: List<JSONObject> = emptyList(), val failed: Boolean = false) {
    var text by mutableStateOf(text)
    var done by mutableStateOf(!fromVyna || failed)
    val liveActions = mutableStateListOf<JSONObject>().apply { addAll(actions) }
}

/** Kept until sign-out: a new session starts a new conversation (S2.12 AC4). */
object VynaConversation {
    private var owner: String? = null
    val messages = mutableStateListOf<VynaMessage>()
    fun forSession(session: String?): List<VynaMessage> {
        if (session != owner) { messages.clear(); owner = session }
        return messages
    }
}

private val SUGGESTIONS = listOf("What's my balance?", "How much did I spend on food last month?", "Berapa total transfer saya minggu ini?", "When is my next scheduled transfer?")

@Composable
private fun VynaAvatar(size: Int = 36) = Box(
    Modifier.size(size.dp).clip(CircleShape).background(Brand.Hero), contentAlignment = Alignment.Center,
) { Icon(painterResource(R.drawable.ph_sparkle), null, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp)) }

/** Opens the screen an action card points at; the customer still reviews and confirms there. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VynaScreen(store: DeviceStore, onBack: () -> Unit, onAction: (JSONObject) -> Unit) {
    val context = LocalContext.current
    val messages = remember { VynaConversation.forSession(Api.session) as MutableList<VynaMessage> }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var speaks by remember { mutableStateOf(store.vynaSpeaks) }
    var allowance by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var available by remember { mutableStateOf(true) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val voice = rememberVoice(context)
    var announce by remember { mutableStateOf("") } // Q-E6: the whole answer, said once when it's finished
    LaunchedEffect(Unit) { runCatching { Api.vyna() }.onSuccess { allowance = it; available = it != null } }
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || busy) return
        input = ""
        messages.add(VynaMessage(false, q))
        val history = messages.filter { !it.failed }.map { (if (it.fromVyna) "assistant" else "user") to it.text }
        val reply = VynaMessage(true, "")
        messages.add(reply)
        busy = true
        scope.launch {
            Api.vynaChat(history).collect { (event, data) ->
                when (event) {
                    "text" -> reply.text += data.optString("text")
                    "action" -> reply.liveActions.add(data)
                    "error" -> { messages[messages.indexOf(reply)] = VynaMessage(true, data.optString("message"), failed = true) }
                }
            }
            busy = false
            reply.done = true
            announce = reply.text
            allowance = allowance?.let { it.first to it.second + 1 }
            if (speaks && reply.text.isNotBlank()) voice.speak(reply.text)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { voice.stop(); onBack() }) { Icon(painterResource(R.drawable.ph_arrow_left), contentDescription = "Back") }
            VynaAvatar()
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Vyna", style = MaterialTheme.typography.titleMedium)
                Text(allowance?.let { "${maxOf(0, it.first - it.second)} questions left today" } ?: "Your ORVYN assistant",
                    style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
            }
            // A switch: "Read replies aloud, switch, on" (design.md 9.6).
            Box(Modifier.size(48.dp).clip(CircleShape).toggleable(speaks, role = Role.Switch) { speaks = it; store.vynaSpeaks = it; if (!it) voice.stop() }
                .semantics { contentDescription = "Read replies aloud" }, contentAlignment = Alignment.Center) {
                Icon(painterResource(if (speaks) R.drawable.ph_speaker_high else R.drawable.ph_speaker_slash), contentDescription = null,
                    tint = if (speaks) MaterialTheme.colorScheme.primary else OrvynTheme.colors.textSecondary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (messages.isEmpty()) Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.weight(1f))
            VynaAvatar(56)
            Text("Hi, I'm Vyna.", style = MaterialTheme.typography.headlineSmall)
            Text(if (available) "Ask me about your balance, your spending or anything in ORVYN, in English or Bahasa Indonesia. I can also get a transfer ready for you to check."
                 else "I'm not switched on yet. Try again later.",
                style = MaterialTheme.typography.bodyLarge, color = OrvynTheme.colors.textSecondary)
            if (available) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in SUGGESTIONS) id.orvyn.app.ui.components.Chip(s, selected = false, role = Role.Button) { send(s) }
            }
        } else LazyColumn(Modifier.weight(1f), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(messages) { i, m ->
                if (!m.fromVyna) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(m.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 10.dp))
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VynaAvatar(28)
                    Column(Modifier.widthIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (m.text.isEmpty() && busy && i == messages.lastIndex && m.liveActions.isEmpty()) Thinking()
                        // No live region while it streams in, so TalkBack doesn't read every piece (Q-E6 AC1).
                        if (m.text.isNotEmpty()) VynaText(m.text, if (m.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            Modifier.clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp).testTag("vyna-bubble"))
                        for (a in m.liveActions) ActionCard(a) { voice.stop(); onAction(a) }
                        if (m.failed && i == messages.lastIndex) id.orvyn.app.ui.components.OrvynTextButton("Try again", onClick = {
                            val lastQuestion = messages.lastOrNull { !it.fromVyna }?.text ?: return@OrvynTextButton
                            messages.removeAt(messages.lastIndex); messages.removeAt(messages.lastIndex); send(lastQuestion)
                        })
                    }
                }
            }
        }

        Box(Modifier.size(1.dp).testTag("vyna-announce").semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announce })
        Text("Vyna can get things wrong. Check the details before you send money.", style = MaterialTheme.typography.labelMedium,
            color = OrvynTheme.colors.textTertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it.take(500) }, enabled = available,
                placeholder = { Text(if (voice.listening) "Listening…" else "Ask Vyna") }, maxLines = 4,
                shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
            )
            if (voice.canListen && input.isBlank()) MicButton(voice, enabled = available && !busy) { heard -> input = heard }
            else IconButton(onClick = { send(input) }, enabled = available && !busy && input.isNotBlank()) {
                Icon(painterResource(R.drawable.ph_paper_plane_right), contentDescription = "Send",
                    tint = if (input.isNotBlank() && !busy) MaterialTheme.colorScheme.primary else OrvynTheme.colors.textTertiary)
            }
        }
    }
}

/** Announced once when it appears; still with animations off (Q-E6 AC5). */
@Composable
private fun Thinking() {
    val reduced = id.orvyn.app.ui.components.rememberReducedMotion()
    val a = if (reduced) 1f else {
        val t = rememberInfiniteTransition(label = "thinking")
        t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dots").value
    }
    Row(Modifier.padding(vertical = 8.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { Box(Modifier.size(6.dp).alpha(a).clip(CircleShape).background(OrvynTheme.colors.textSecondary)) }
        Text("Vyna is thinking…", style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
    }
}

/**
 * The small markdown Vyna uses (design.md 9.6): lines starting "- ", "* " or "1. " become a list with a hanging 16dp indent,
 * and **text** is bold. Anything else shows as written.
 */
@Composable
fun VynaText(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in text.split('\n')) {
            val bullet = Regex("^\\s*([-*]|\\d+\\.)\\s+(.*)").find(line)
            if (bullet != null) Row {
                Text(if (bullet.groupValues[1].first().isDigit()) bullet.groupValues[1] else "•", style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.width(16.dp))
                Text(bold(bullet.groupValues[2]), style = MaterialTheme.typography.bodyLarge, color = color)
            } else if (line.isNotBlank()) Text(bold(line), style = MaterialTheme.typography.bodyLarge, color = color)
        }
    }
}

/** "**Rp1,000**" → bold 700; unmatched asterisks stay as they are. */
fun bold(s: String) = androidx.compose.ui.text.buildAnnotatedString {
    var rest = s
    while (true) {
        val m = Regex("\\*\\*(.+?)\\*\\*").find(rest) ?: break
        append(rest.substring(0, m.range.first))
        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        rest = rest.substring(m.range.last + 1)
    }
    append(rest)
}

/** S2.14: what Vyna prepared. The button only opens the screen; nothing is sent from here. */
@Composable
private fun ActionCard(a: JSONObject, onReview: () -> Unit) {
    val (title, lines, button) = when (a.optString("type")) {
        "transfer" -> Triple("Transfer to ${a.optString("name")}", listOf(
            "${a.optString("bankName")} · ${id.orvyn.app.ui.groupAccountNumber(a.optString("accountNumber"))}",
            Money.format(a.optLong("amount")) + if (a.optLong("fee") > 0) " + ${Money.format(a.optLong("fee"))} fee (${a.optString("method")})" else " · free"), "Review")
        "pocket" -> Triple(if (a.optString("direction") == "out") "Take out of ${a.optString("pocket")}" else "Add to ${a.optString("pocket")}",
            listOf(Money.format(a.optLong("amount")) + " · no fee"), "Review")
        else -> Triple("Pay a bill", listOf(a.optString("what").ifBlank { "PLN, BPJS, phone credit or e-wallet" }), "Open Pay")
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        for (l in lines) Text(l, style = MaterialTheme.typography.bodyMedium, color = OrvynTheme.colors.textSecondary)
        OrvynButton(button, onClick = onReview, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/** On-device speech in, phone's own voice out (S2.15). Nothing here touches the network. */
class Voice(private val context: Context) {
    val canListen = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    var listening by mutableStateOf(false)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun listen(onText: (String) -> Unit) {
        if (!canListen || Build.VERSION.SDK_INT < 31) return
        val r = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                listening = false
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
            }
            override fun onPartialResults(partial: Bundle) { partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText) }
            override fun onError(error: Int) { listening = false }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listening = true
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
    }

    fun stopListening() { recognizer?.stopListening(); listening = false }

    /** Indonesian replies get an Indonesian voice; a female, offline voice is picked when the phone has one. */
    fun speak(text: String) {
        val say = {
            val t = tts!!
            val indonesian = Regex("\\b(saya|anda|yang|untuk|dengan|bulan|tidak|sudah)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val locale = if (indonesian) Locale.forLanguageTag("id-ID") else Locale.US
            t.language = locale
            t.voices?.filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
                ?.sortedByDescending { it.name.contains("female", true) || it.features.any { f -> f.contains("female", true) } }
                ?.firstOrNull()?.let { t.voice = it }
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vyna")
        }
        if (ttsReady) say() else tts = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) { ttsReady = true; say() } }
    }

    fun stop() { tts?.stop() }
    fun release() { recognizer?.destroy(); tts?.shutdown() }
}

@Composable
private fun rememberVoice(context: Context): Voice {
    val v = remember { Voice(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { v.release() } }
    return v
}

/** The microphone permission is asked for the first time the button is pressed (S2.15 AC2). */
@Composable
private fun MicButton(voice: Voice, enabled: Boolean, onText: (String) -> Unit) {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) voice.listen(onText) }
    IconButton(enabled = enabled, onClick = {
        when {
            voice.listening -> voice.stopListening()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> voice.listen(onText)
            else -> ask.launch(Manifest.permission.RECORD_AUDIO)
        }
    }) {
        Icon(painterResource(R.drawable.ph_microphone), contentDescription = if (voice.listening) "Stop listening" else "Speak to Vyna",
            tint = if (voice.listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

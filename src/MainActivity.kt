package org.voicemail.hindi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.View
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity(), RecognitionListener {
    private var model: Model? = null
    private var speech: SpeechService? = null
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var inbox: LinearLayout
    private var currentText = ""
    private var recording = false
    private var demo = false
    private var player: MediaPlayer? = null
    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val messages by lazy { File(filesDir, "inbox.json") }
    private val background = Color.rgb(11, 20, 42)
    private val ink = Color.rgb(239, 246, 255)
    private val muted = Color.rgb(178, 196, 217)
    private val teal = Color.rgb(45, 212, 191)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun panel(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun space(view: android.view.View, top: Int = 8) {
        view.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = 0
        val scroll = ScrollView(this); body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(32)); setBackgroundColor(this@MainActivity.background)
        }; scroll.addView(body); setContentView(scroll)
        text("Hindi Voicemail", 30f)
        text("ENGLISH UI  •  HINDI VOICE  •  OFFLINE", 15f)
        text("Your private Hindi message space. Import audio or speak into the mic. This app does not answer live SIM calls.", 16f)
        status = text("Loading offline Hindi model...", 15f)
        button("Import audio") {
            if (!ready()) return@button
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 10)
        }
        button("Dictate in Hindi / Stop") { startOrStop(false) }
        button("Play Hindi greeting / Stop") { startOrStop(true) }
        text("Demo: hear the Hindi greeting, then say your name and message in Hindi. Scripted demo, not an AI chat or phone call.", 14f)
        transcript = text("Your Hindi transcript appears here", 19f)
        button("Save transcript") { saveMessage(currentText, "Mic message"); currentText = "" }
        text("Call screening", 23f)
        text("Optional rules for exact numbers only. Include the country code. Screening starts off and does not speak to callers.", 14f)
        val numbers = EditText(this).apply {
            hint = "Example: +911234567890, +919876543210"
            setText(settings.getString("numbers", "")); setTextColor(ink); setHintTextColor(muted)
            textSize = 16f; setPadding(dp(16), dp(16), dp(16), dp(16))
            background = panel(Color.rgb(24, 43, 67)); inputType = android.text.InputType.TYPE_CLASS_PHONE
            minHeight = dp(56)
        }; space(numbers); body.addView(numbers)
        val enabled = Switch(this).apply {
            text = "Enable rules for these numbers"; isChecked = settings.getBoolean("enabled", false)
            setTextColor(ink); textSize = 16f; minHeight = dp(56)
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(teal, muted))
        }; space(enabled); body.addView(enabled)
        val block = Switch(this).apply {
            text = "Block calls (off = silence only)"; isChecked = settings.getBoolean("block", false)
            setTextColor(ink); textSize = 16f; minHeight = dp(56)
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(teal, muted))
        }; space(block); body.addView(block)
        button("Save screening rules") {
            settings.edit().putString("numbers", numbers.text.toString()).putBoolean("enabled", enabled.isChecked)
                .putBoolean("block", block.isChecked).apply()
            status.text = "Rules saved. Android screening permission is also required."
        }
        button("Allow screening in Android") {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (!roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 11)
                else status.text = "Screening permission already granted"
            } else status.text = "Screening is not available on this phone"
        }
        text("Exact number matching. Android may skip some contact calls. Blocking a call does not create carrier voicemail.", 14f)
        text("Message inbox", 23f)
        inbox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(inbox)
        button("Delete all messages") {
            AlertDialog.Builder(this).setMessage("Delete all messages stored on this phone?").setPositiveButton("Delete") { _, _ ->
                messages.delete(); refreshInbox()
            }.setNegativeButton("Cancel", null).show()
        }
        text("Privacy: no internet permission or cloud backup. Original audio is unchanged. Text is shared only when you tap Share. Transcripts may contain errors.", 14f)
        refreshInbox()
        StorageService.unpack(this, "vosk-model-small-hi-0.22", "model", { m ->
            model = m; status.text = "Ready • Hindi offline model"
        }, { error -> status.text = "Could not load model: ${error.localizedMessage}" })
    }
    private fun text(value: String, size: Float): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(if (size < 18f) muted else ink)
        setPadding(0, dp(10), 0, dp(10))
        if (size >= 23f) { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(teal) }
        if (value == "Your Hindi transcript appears here") {
            background = panel(Color.rgb(24, 43, 67)); setPadding(dp(18), dp(18), dp(18), dp(18)); minHeight = dp(112)
        }
        body.addView(this)
    }
    private fun button(value: String, action: () -> Unit) {
        body.addView(Button(this).apply {
            text = value; isAllCaps = false; textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            val danger = value.startsWith("Delete")
            setTextColor(if (danger) Color.rgb(254, 202, 202) else this@MainActivity.background)
            background = if (danger) panel(Color.rgb(83, 29, 49), 14) else GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(teal, Color.rgb(56, 189, 248))
            ).apply { cornerRadius = dp(14).toFloat() }
            minHeight = dp(56); setPadding(dp(12), dp(10), dp(12), dp(10))
            space(this); setOnClickListener { action() }
        })
    }
    private fun ready(): Boolean {
        if (model == null) { status.text = "Please wait for the Hindi model"; return false }; return true
    }
    private fun startOrStop(isDemo: Boolean) {
        if (recording) { stopListening(); return }
        if (!ready()) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 12); return
        }
        demo = isDemo; currentText = ""; transcript.text = ""
        if (isDemo) {
            player?.release()
            val fd = assets.openFd("greeting.wav")
            player = MediaPlayer().apply {
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length); fd.close(); prepare()
                setOnCompletionListener { it.release(); player = null; listen() }; start()
            }
            status.text = "Playing Hindi greeting..."
        } else listen()
    }
    private fun listen() {
        if (isFinishing || isDestroyed) return
        try {
            speech = SpeechService(Recognizer(model!!, 16000f), 16000f)
            recording = true; speech!!.startListening(this); status.text = "Speak in Hindi • Tap the same button to stop"
        } catch (e: Exception) { recording = false; status.text = "Could not open mic: ${e.localizedMessage}" }
    }
    private fun stopListening() {
        speech?.stop(); speech?.shutdown(); speech = null; recording = false
        status.text = if (demo) "Message captured. Tap Save transcript. This demo is not a phone call." else "Recording stopped. Tap Save transcript."
    }
    private fun append(json: String) {
        val value = JSONObject(json).optString("text")
        if (value.isNotBlank()) currentText = listOf(currentText, value).filter { it.isNotBlank() }.joinToString(" ")
        transcript.text = currentText.ifBlank { "No clear speech detected" }
    }
    override fun onResult(hypothesis: String) = append(hypothesis)
    override fun onFinalResult(hypothesis: String) = append(hypothesis)
    override fun onPartialResult(hypothesis: String) {
        transcript.text = "$currentText ${JSONObject(hypothesis).optString("partial")}".trim()
    }
    override fun onError(exception: Exception) { stopListening(); status.text = "Speech recognition error: ${exception.localizedMessage}" }
    override fun onTimeout() = stopListening()
    private fun load(): JSONArray = runCatching { JSONArray(messages.readText()) }.getOrElse { JSONArray() }
    private fun saveMessage(value: String, label: String) {
        if (value.isBlank()) { status.text = "No message to save"; return }
        val list = load(); list.put(JSONObject().put("text", value).put("label", label).put("time", System.currentTimeMillis()))
        messages.writeText(list.toString()); refreshInbox(); status.text = "Message saved on this phone"
    }
    private fun refreshInbox() {
        inbox.removeAllViews(); val list = load()
        if (list.length() == 0) inbox.addView(TextView(this).apply { text = "No messages yet. Your saved Hindi transcripts will appear here."; setTextColor(muted); setPadding(0, dp(12), 0, dp(12)) })
        for (i in list.length() - 1 downTo 0) {
            val item = list.getJSONObject(i)
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)); background = panel(Color.rgb(24, 43, 67)); space(this, 12) }
            card.addView(TextView(this).apply { text = item.getString("label") + " • " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.getLong("time"))); setTextColor(ink) })
            card.addView(TextView(this).apply { text = item.getString("text"); textSize = 18f; setTextColor(ink); setPadding(0, 12, 0, 12) })
            card.addView(Button(this).apply { text = "Share text"; isAllCaps = false; setTextColor(this@MainActivity.background); backgroundTintList = ColorStateList.valueOf(teal); setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, item.getString("text")) }, "Share"))
            } })
            card.addView(Button(this).apply { text = "Delete this message"; isAllCaps = false; setTextColor(ink); backgroundTintList = ColorStateList.valueOf(Color.rgb(83, 29, 49)); setOnClickListener {
                val keep = JSONArray(); for (j in 0 until list.length()) if (j != i) keep.put(list.getJSONObject(j))
                messages.writeText(keep.toString()); refreshInbox()
            } }); inbox.addView(card)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 10 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (recording) stopListening()
        status.text = "Transcribing Hindi audio..."
        worker.execute {
            try {
                val r = Recognizer(model!!, 16000f)
                val result = try { AudioDecoder.transcribe(this, uri, r) } finally { r.close() }
                runOnUiThread { if (!isDestroyed) { currentText = result; transcript.text = result; saveMessage(result, "Imported audio") } }
            } catch (e: Exception) { runOnUiThread { status.text = "Could not read file: ${e.localizedMessage}" } }
        }
    }
    override fun onStop() { super.onStop(); player?.release(); player = null; if (recording) stopListening() }
    override fun onDestroy() { speech?.shutdown(); worker.shutdownNow(); /* Model stays alive until running decode ends. */ super.onDestroy() }
}

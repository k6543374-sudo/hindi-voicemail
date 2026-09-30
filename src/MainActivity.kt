package org.voicemail.hindi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
    private val background = Color.rgb(246, 248, 252)
    private val ink = Color.rgb(23, 37, 58)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this); body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 32); setBackgroundColor(this@MainActivity.background)
        }; scroll.addView(body); setContentView(scroll)
        text("हिंदी वॉइसमेल", 30f)
        text("निजी • ऑफलाइन • कोई API शुल्क नहीं", 15f)
        text("यह ऐप SIM कॉल पर AI से बात नहीं करता। सहेजी गई ऑडियो फ़ाइलें और आपके माइक के संदेश यहाँ पढ़ें।", 16f)
        status = text("हिंदी मॉडल तैयार हो रहा है…", 15f)
        button("ऑडियो फ़ाइल आयात करें") {
            if (!ready()) return@button
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, 10)
        }
        button("माइक से संदेश लिखें / रोकें") { startOrStop(false) }
        button("वॉइस एजेंट डेमो / रोकें") { startOrStop(true) }
        text("डेमो: हिंदी स्वागत सुनें, फिर अपना नाम और संदेश बोलें। यह नियम-आधारित है, LLM नहीं।", 14f)
        transcript = text("आपका संदेश यहाँ दिखाई देगा", 19f)
        button("टेक्स्ट इनबॉक्स में रखें") { saveMessage(currentText, "माइक संदेश"); currentText = "" }
        text("कॉल स्क्रीनिंग", 23f)
        text("केवल नीचे दिए पूरे नंबरों पर नियम लागू होते हैं। देश कोड सहित नंबर दें। कोई नियम अपने आप चालू नहीं होता।", 14f)
        val numbers = EditText(this).apply {
            hint = "उदाहरण: +911234567890, +919876543210"
            setText(settings.getString("numbers", "")); setTextColor(ink)
        }; body.addView(numbers)
        val enabled = Switch(this).apply {
            text = "चुने नंबरों के नियम चालू करें"; isChecked = settings.getBoolean("enabled", false)
        }; body.addView(enabled)
        val block = Switch(this).apply {
            text = "ब्लॉक करें (बंद होने पर केवल साइलेंट)"; isChecked = settings.getBoolean("block", false)
        }; body.addView(block)
        button("नियम सहेजें") {
            settings.edit().putString("numbers", numbers.text.toString()).putBoolean("enabled", enabled.isChecked)
                .putBoolean("block", block.isChecked).apply()
            status.text = "नियम सहेजे। सिस्टम की स्क्रीनिंग अनुमति भी चाहिए।"
        }
        button("Android में स्क्रीनिंग अनुमति चुनें") {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (!roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 11)
                else status.text = "स्क्रीनिंग अनुमति पहले से मिली है"
            } else status.text = "इस फ़ोन पर स्क्रीनिंग भूमिका उपलब्ध नहीं है"
        }
        text("नंबर मिलान ठीक-ठीक होता है। कुछ कॉन्टैक्ट कॉल Android इस सेवा को नहीं देता। ब्लॉक करना कैरियर वॉइसमेल बनाना नहीं है।", 14f)
        text("संदेश इनबॉक्स", 23f)
        inbox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(inbox)
        button("सभी संदेश मिटाएँ") {
            AlertDialog.Builder(this).setMessage("सभी स्थानीय संदेश मिटाएँ?").setPositiveButton("मिटाएँ") { _, _ ->
                messages.delete(); refreshInbox()
            }.setNegativeButton("रद्द", null).show()
        }
        text("गोपनीयता: इंटरनेट अनुमति नहीं है। क्लाउड बैकअप बंद है। आयात की गई मूल फ़ाइल नहीं बदलती। टेक्स्ट शेयर केवल आपके टैप पर। प्रतिलिपि गलत हो सकती है।", 14f)
        refreshInbox()
        StorageService.unpack(this, "vosk-model-small-hi-0.22", "model", { m ->
            model = m; status.text = "तैयार • हिंदी ऑफलाइन मॉडल"
        }, { error -> status.text = "मॉडल नहीं खुला: ${error.localizedMessage}" })
    }
    private fun text(value: String, size: Float): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(ink); setPadding(0, 12, 0, 12); body.addView(this)
    }
    private fun button(value: String, action: () -> Unit) {
        body.addView(Button(this).apply { text = value; isAllCaps = false; setOnClickListener { action() } })
    }
    private fun ready(): Boolean {
        if (model == null) { status.text = "मॉडल तैयार होने दें"; return false }; return true
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
            status.text = "स्वागत सुनें…"
        } else listen()
    }
    private fun listen() {
        if (isFinishing || isDestroyed) return
        try {
            speech = SpeechService(Recognizer(model!!, 16000f), 16000f)
            recording = true; speech!!.startListening(this); status.text = "बोलें • रोकने के लिए वही बटन दबाएँ"
        } catch (e: Exception) { recording = false; status.text = "माइक नहीं खुला: ${e.localizedMessage}" }
    }
    private fun stopListening() {
        speech?.stop(); speech?.shutdown(); speech = null; recording = false
        status.text = if (demo) "संदेश लिया। नीचे सहेजें। यह डेमो फ़ोन कॉल नहीं है।" else "रिकॉर्डिंग रुकी। नीचे सहेजें।"
    }
    private fun append(json: String) {
        val value = JSONObject(json).optString("text")
        if (value.isNotBlank()) currentText = listOf(currentText, value).filter { it.isNotBlank() }.joinToString(" ")
        transcript.text = currentText.ifBlank { "कोई स्पष्ट आवाज़ नहीं मिली" }
    }
    override fun onResult(hypothesis: String) = append(hypothesis)
    override fun onFinalResult(hypothesis: String) = append(hypothesis)
    override fun onPartialResult(hypothesis: String) {
        transcript.text = "$currentText ${JSONObject(hypothesis).optString("partial")}".trim()
    }
    override fun onError(exception: Exception) { stopListening(); status.text = "आवाज़ पढ़ने में त्रुटि: ${exception.localizedMessage}" }
    override fun onTimeout() = stopListening()
    private fun load(): JSONArray = runCatching { JSONArray(messages.readText()) }.getOrElse { JSONArray() }
    private fun saveMessage(value: String, label: String) {
        if (value.isBlank()) { status.text = "सहेजने के लिए संदेश नहीं है"; return }
        val list = load(); list.put(JSONObject().put("text", value).put("label", label).put("time", System.currentTimeMillis()))
        messages.writeText(list.toString()); refreshInbox(); status.text = "संदेश फ़ोन पर सहेजा"
    }
    private fun refreshInbox() {
        inbox.removeAllViews(); val list = load()
        if (list.length() == 0) inbox.addView(TextView(this).apply { text = "अभी कोई संदेश नहीं"; setPadding(0, 12, 0, 12) })
        for (i in list.length() - 1 downTo 0) {
            val item = list.getJSONObject(i)
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 12, 16, 12); setBackgroundColor(Color.WHITE) }
            card.addView(TextView(this).apply { text = item.getString("label") + " • " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.getLong("time"))); setTextColor(ink) })
            card.addView(TextView(this).apply { text = item.getString("text"); textSize = 18f; setTextColor(ink); setPadding(0, 12, 0, 12) })
            card.addView(Button(this).apply { text = "टेक्स्ट शेयर करें"; setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, item.getString("text")) }, "शेयर"))
            } })
            card.addView(Button(this).apply { text = "यह संदेश मिटाएँ"; setOnClickListener {
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
        status.text = "ऑडियो पढ़ रहे हैं…"
        worker.execute {
            try {
                val r = Recognizer(model!!, 16000f)
                val result = try { AudioDecoder.transcribe(this, uri, r) } finally { r.close() }
                runOnUiThread { if (!isDestroyed) { currentText = result; transcript.text = result; saveMessage(result, "आयात किया ऑडियो") } }
            } catch (e: Exception) { runOnUiThread { status.text = "फ़ाइल नहीं पढ़ी: ${e.localizedMessage}" } }
        }
    }
    override fun onStop() { super.onStop(); player?.release(); player = null; if (recording) stopListening() }
    override fun onDestroy() { speech?.shutdown(); worker.shutdownNow(); /* Model stays alive until running decode ends. */ super.onDestroy() }
}

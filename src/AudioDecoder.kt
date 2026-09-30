package org.voicemail.hindi

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.vosk.Recognizer
import org.json.JSONObject
import java.nio.ByteOrder

/** Decodes Android-supported audio to mono 16 kHz PCM. No networking. */
object AudioDecoder {
    fun transcribe(context: Context, uri: Uri, recognizer: Recognizer): String {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val words = mutableListOf<String>()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("इस फ़ाइल में ऑडियो नहीं मिला")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var samplePosition = 0L
            var outputPosition = 0L
            var inputEnded = false
            var outputEnded = false
            val info = MediaCodec.BufferInfo()
            while (!outputEnded) {
                if (Thread.currentThread().isInterrupted) error("रद्द किया गया")
                if (!inputEnded) {
                    val index = decoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0); extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = decoder.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) != 2)
                        error("केवल 16-bit PCM समर्थित है")
                } else if (index >= 0) {
                    val out = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                    out.position(info.offset); out.limit(info.offset + info.size)
                    val pcm = ArrayList<Short>()
                    while (out.remaining() >= channels * 2) {
                        var sum = 0
                        repeat(channels) { sum += out.short.toInt() }
                        val mono = (sum / channels).toShort()
                        while (outputPosition * rate / 16000L <= samplePosition) {
                            pcm.add(mono); outputPosition++
                        }
                        samplePosition++
                    }
                    if (pcm.isNotEmpty() && recognizer.acceptWaveForm(pcm.toShortArray(), pcm.size))
                        JSONObject(recognizer.result).optString("text").takeIf { it.isNotBlank() }?.let(words::add)
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(index, false)
                }
            }
            JSONObject(recognizer.finalResult).optString("text").takeIf { it.isNotBlank() }?.let(words::add)
            return words.joinToString(" ")
        } finally { codec?.let { runCatching { it.stop() }; it.release() }; extractor.release() }
    }
}

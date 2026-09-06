package com.rebass.app

import android.app.*
import android.os.*
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Color
import android.media.*
import android.view.*
import android.widget.*
import java.io.*

class MainActivity : Activity() {
    private var uri: Uri? = null
    private var target = 25f
    private var amount = 70
    private lateinit var fileLabel: TextView
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        buildUi()
    }

    private fun tv(text: String, size: Float, color: Int = Color.LTGRAY): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(Color.rgb(9, 9, 9))
        }

        root.addView(tv("REBASS", 36f, Color.WHITE), LinearLayout.LayoutParams(-1, 70))
        root.addView(
            tv("LOW-FREQUENCY SUBHARMONIC GENERATOR", 11f, Color.GRAY),
            LinearLayout.LayoutParams(-1, 35)
        )

        val pick = Button(this).apply {
            text = "SELECT AUDIO"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "audio/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }, 100)
            }
        }
        root.addView(pick, LinearLayout.LayoutParams(-1, 58).apply { setMargins(0, 25, 0, 10) })
        fileLabel = tv("No song selected", 14f)
        root.addView(fileLabel, LinearLayout.LayoutParams(-1, 45))

        root.addView(
            tv("TARGET LOW FREQUENCY", 12f, Color.GRAY),
            LinearLayout.LayoutParams(-1, 40).apply { setMargins(0, 15, 0, 0) }
        )
        val hzSpinner = Spinner(this)
        val labels = (20..40).filter { it % 5 == 0 }.map { "${it} Hz" } + listOf("AUTO ÷2")
        hzSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        hzSpinner.setSelection(1)
        hzSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                target = if (pos == labels.lastIndex) 0f else labels[pos].substringBefore(" ").toFloat()
            }
        }
        root.addView(hzSpinner, LinearLayout.LayoutParams(-1, 52))

        val amountLabel = tv("REBASS AMOUNT  $amount%", 12f, Color.GRAY)
        root.addView(amountLabel, LinearLayout.LayoutParams(-1, 40).apply { setMargins(0, 18, 0, 0) })
        val seek = SeekBar(this).apply {
            max = 100
            progress = amount
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                amount = p
                amountLabel.text = "REBASS AMOUNT  $amount%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(seek, LinearLayout.LayoutParams(-1, 52))

        val go = Button(this).apply {
            text = "REBASS"
            textSize = 18f
            setOnClickListener { process() }
        }
        root.addView(go, LinearLayout.LayoutParams(-1, 65).apply { setMargins(0, 25, 0, 8) })

        status = tv("Detect bass → generate lower subharmonics → limit → export", 12f, Color.GRAY)
        root.addView(status, LinearLayout.LayoutParams(-1, 65))
        setContentView(root)
    }

    private fun displayName(u: Uri): String {
        var c: Cursor? = null
        return try {
            c = contentResolver.query(u, null, null, null, null)
            if (c != null && c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            else "Selected audio"
        } catch (_: Exception) {
            "Selected audio"
        } finally {
            c?.close()
        }
    }

    override fun onActivityResult(r: Int, result: Int, data: Intent?) {
        super.onActivityResult(r, result, data)
        if (r == 100 && result == RESULT_OK && data?.data != null) {
            uri = data.data
            fileLabel.text = displayName(uri!!)
            try {
                contentResolver.takePersistableUriPermission(
                    uri!!,
                    data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            status.text = "Ready. Press REBASS."
        }
    }

    private fun process() {
        val u = uri ?: run {
            Toast.makeText(this, "Select a song first", Toast.LENGTH_SHORT).show()
            return
        }
        status.text = "Decoding + detecting bass…"
        Thread {
            try {
                val result = processAnyAudio(u, target, amount / 100f)
                runOnUiThread {
                    status.text = "Done: ${result.name}"
                    Toast.makeText(this, "Rebass WAV saved in app storage", Toast.LENGTH_LONG).show()
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/wav"
                        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(result))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Error: ${e.message ?: "processing failed"}" }
            }
        }.start()
    }

    private fun processAnyAudio(u: Uri, target: Float, amount: Float): File {
        val pfd = contentResolver.openFileDescriptor(u, "r")
        val magic = ByteArray(12)
        pfd?.use { FileInputStream(it.fileDescriptor).use { input -> input.read(magic) } }
        val isWav = magic.size >= 12 && String(magic, 0, 4) == "RIFF" && String(magic, 8, 4) == "WAVE"
        if (isWav) return processWavLike(u, target, amount)

        val decoded = decodeToPcm(u)
        if (decoded.samples.isEmpty()) error("No audio track found")
        val processed = FloatArray(decoded.samples.size)
        val block = (decoded.sampleRate * 2).coerceAtLeast(4096)
        var off = 0
        while (off < decoded.samples.size) {
            val end = minOf(off + block, decoded.samples.size)
            val part = decoded.samples.copyOfRange(off, end)
            val p = RebassProcessor.processBlock(part, decoded.sampleRate, target, amount)
            p.copyInto(processed, off)
            off = end
        }
        return writeWav(processed, decoded.sampleRate, decoded.channels)
    }

    data class PcmResult(val samples: FloatArray, val sampleRate: Int, val channels: Int)

    private fun decodeToPcm(u: Uri): PcmResult {
        val extractor = MediaExtractor()
        extractor.setDataSource(this, u, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                track = i
                format = f
                break
            }
        }
        if (track < 0 || format == null) {
            extractor.release()
            error("No supported audio track")
        }
        extractor.selectTrack(track)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcm = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10000)
                when {
                    outIndex >= 0 -> {
                        val out = codec.getOutputBuffer(outIndex)
                        if (out != null && info.size > 0) {
                            val old = out.position()
                            out.position(info.offset)
                            val bytes = ByteArray(info.size)
                            out.get(bytes)
                            out.position(old)
                            pcm.write(bytes)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        val b = pcm.toByteArray()
        val samples = FloatArray(b.size / 2)
        for (i in samples.indices) {
            val lo = b[i * 2].toInt() and 255
            val hi = b[i * 2 + 1].toInt()
            val v = (lo or (hi shl 8)).toShort().toInt()
            samples[i] = v / 32768f
        }
        return PcmResult(samples, sampleRate, channels)
    }

    private fun writeWav(samples: FloatArray, rate: Int, channels: Int): File {
        val data = samples.size * 2
        val h = ByteArray(44)
        fun put4(p: Int, v: Int) {
            h[p] = (v and 255).toByte()
            h[p + 1] = ((v shr 8) and 255).toByte()
            h[p + 2] = ((v shr 16) and 255).toByte()
            h[p + 3] = ((v shr 24) and 255).toByte()
        }
        fun put2(p: Int, v: Int) {
            h[p] = (v and 255).toByte()
            h[p + 1] = ((v shr 8) and 255).toByte()
        }
        System.arraycopy("RIFF".toByteArray(), 0, h, 0, 4)
        put4(4, 36 + data)
        System.arraycopy("WAVE".toByteArray(), 0, h, 8, 4)
        System.arraycopy("fmt ".toByteArray(), 0, h, 12, 4)
        put4(16, 16)
        put2(20, 1)
        put2(22, channels)
        put4(24, rate)
        put4(28, rate * channels * 2)
        put2(32, channels * 2)
        put2(34, 16)
        System.arraycopy("data".toByteArray(), 0, h, 36, 4)
        put4(40, data)
        val f = File(cacheDir, "ReBass_${System.currentTimeMillis()}.wav")
        FileOutputStream(f).use { out ->
            out.write(h)
            for (s in samples) {
                val v = (s * 32767f).toInt().coerceIn(-32768, 32767)
                out.write(v and 255)
                out.write((v shr 8) and 255)
            }
        }
        return f
    }

    private fun processWavLike(u: Uri, target: Float, amount: Float): File {
        val input = contentResolver.openInputStream(u) ?: error("Cannot open file")
        val bytes = input.readBytes()
        input.close()
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE")
            error("Invalid WAV")
        val channels = le16(bytes, 22)
        val rate = le32(bytes, 24)
        val bits = le16(bytes, 34)
        if (bits != 16) error("Only 16-bit PCM WAV is supported.")
        var pos = 12
        var dataPos = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = le32(bytes, pos + 4)
            pos += 8
            if (id == "data") {
                dataPos = pos
                dataLen = minOf(len, bytes.size - pos)
                break
            }
            pos += len + (len and 1)
        }
        if (dataPos < 0) error("WAV data chunk not found")
        val samples = FloatArray(dataLen / 2)
        for (i in samples.indices) samples[i] = le16s(bytes, dataPos + i * 2) / 32768f
        val processed = RebassProcessor.processBlock(samples, rate, target, amount)
        return writeWav(processed, rate, channels)
    }

    private fun le16(b: ByteArray, p: Int) =
        (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)

    private fun le16s(b: ByteArray, p: Int) = le16(b, p).let { if (it > 32767) it - 65536 else it }

    private fun le32(b: ByteArray, p: Int) =
        (b[p].toInt() and 255) or
            ((b[p + 1].toInt() and 255) shl 8) or
            ((b[p + 2].toInt() and 255) shl 16) or
            ((b[p + 3].toInt() and 255) shl 24)
}

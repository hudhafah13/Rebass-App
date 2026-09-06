package com.rebass.app

import android.app.*
import android.content.*
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import androidx.core.content.FileProvider
import java.io.*

class MainActivity : Activity() {

    private var uri: Uri? = null
    private var target = 25f
    private var amount = 100
    private lateinit var fileLabel: TextView
    private lateinit var status: TextView
    private lateinit var goBtn: Button
    private lateinit var shareBtn: Button
    private lateinit var viewport: BassViewport
    private var lastOutput: File? = null
    private var lastDownloadsUri: Uri? = null
    private var isProcessing = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor("#0A0A0A")
        window.navigationBarColor = Color.parseColor("#0A0A0A")
        buildUi()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, radius: Float = 16f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(16), 0, dp(6))
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(36))
        }

        val title = TextView(this).apply {
            text = "REBASS"
            textSize = 32f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Strip the old bass  \u2022  Add deep subharmonics"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(18))
        }
        root.addView(subtitle)

        viewport = BassViewport(this)
        root.addView(viewport, LinearLayout.LayoutParams(-1, dp(210)).apply {
            bottomMargin = dp(16)
        })

        val pickCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.parseColor("#161616"), 16f)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val pickBtn = Button(this).apply {
            text = "SELECT AUDIO FILE"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = roundedBg(Color.parseColor("#FF6A00"), 12f)
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                if (isProcessing) return@setOnClickListener
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "audio/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }, 100)
            }
        }
        pickCard.addView(pickBtn, LinearLayout.LayoutParams(-1, -2))

        fileLabel = TextView(this).apply {
            text = "No file selected"
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        pickCard.addView(fileLabel)
        root.addView(pickCard, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        root.addView(sectionTitle("TARGET LOW FREQUENCY"))

        val hzOptions = listOf(
            15, 18, 20, 22, 25, 27, 30, 32, 35, 38, 40, 45,
            50, 55, 60, 65, 70, 75
        )
        val labels = hzOptions.map { "$it Hz" } + listOf("AUTO \u00f72")

        val hzSpinner = Spinner(this).apply {
            background = roundedBg(Color.parseColor("#1E1E1E"), 12f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(4)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p: AdapterView<*>?) {}
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    target = if (pos == labels.lastIndex) 0f else hzOptions[pos].toFloat()
                    if (!isProcessing) {
                        viewport.update(emptyList(), if (target > 0) listOf(target) else emptyList(), target)
                    }
                }
            }
        }
        root.addView(hzSpinner, LinearLayout.LayoutParams(-1, dp(48)))

        root.addView(sectionTitle("REBASS AMOUNT"))

        val amountLabel = TextView(this).apply {
            text = "$amount%"
            textSize = 20f
            setTextColor(Color.parseColor("#FF6A00"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        root.addView(amountLabel)

        val seek = SeekBar(this).apply {
            max = 100
            progress = amount
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                amount = p
                amountLabel.text = "$amount%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(seek)

        goBtn = Button(this).apply {
            text = "REBASS IT"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = roundedBg(Color.parseColor("#FF6A00"), 14f)
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { process() }
        }
        root.addView(goBtn, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(20)
            bottomMargin = dp(10)
        })

        // Share button - always present, starts disabled/hidden style until ready
        shareBtn = Button(this).apply {
            text = "SHARE / SAVE WAV"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = roundedBg(Color.parseColor("#2A2A2A"), 12f)
            setPadding(0, dp(14), 0, dp(14))
            isEnabled = false
            alpha = 0.4f
            setOnClickListener { shareResult() }
        }
        root.addView(shareBtn, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })

        status = TextView(this).apply {
            text = "Select a track  \u2022  Choose Hz  \u2022  Hit REBASS IT\nWhen done, the file is also saved to Downloads."
            textSize = 12f
            setTextColor(Color.parseColor("#777777"))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.25f)
        }
        root.addView(status)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun displayName(u: Uri): String {
        var c: Cursor? = null
        return try {
            c = contentResolver.query(u, null, null, null, null)
            if (c != null && c.moveToFirst()) {
                c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else "Selected audio"
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
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            status.text = "Ready. Press REBASS IT."
            status.setTextColor(Color.parseColor("#AAAAAA"))
            shareBtn.isEnabled = false
            shareBtn.alpha = 0.4f
            viewport.clear()
        }
    }

    private fun process() {
        if (isProcessing) return
        val u = uri ?: run {
            Toast.makeText(this, "Select a song first", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        goBtn.isEnabled = false
        goBtn.text = "PROCESSING\u2026"
        shareBtn.isEnabled = false
        shareBtn.alpha = 0.4f
        status.text = "Decoding + detecting bass + removing old lows\u2026\n(large files can take a minute)"
        status.setTextColor(Color.parseColor("#FF6A00"))

        val t = Thread {
            try {
                val result = processAnyAudio(u, target, amount / 100f)
                lastOutput = result.file

                // Also copy to Downloads so user can find it easily
                val downloadsUri = saveToDownloads(result.file)
                lastDownloadsUri = downloadsUri

                runOnUiThread {
                    isProcessing = false
                    goBtn.isEnabled = true
                    goBtn.text = "REBASS IT"
                    shareBtn.isEnabled = true
                    shareBtn.alpha = 1f
                    shareBtn.background = roundedBg(Color.parseColor("#4CAF50"), 12f)

                    val msg = if (downloadsUri != null) {
                        "Done!\nSaved to Downloads as ${result.file.name}\nTap SHARE / SAVE WAV below"
                    } else {
                        "Done!\n${result.file.name}\nTap SHARE / SAVE WAV below"
                    }
                    status.text = msg
                    status.setTextColor(Color.parseColor("#4CAF50"))
                    viewport.update(result.peaks, result.newHz, target)
                    Toast.makeText(this, "Rebass complete – check Downloads", Toast.LENGTH_LONG).show()
                }
            } catch (oom: OutOfMemoryError) {
                runOnUiThread {
                    isProcessing = false
                    goBtn.isEnabled = true
                    goBtn.text = "REBASS IT"
                    status.text = "Out of memory. Try a shorter clip or lower quality file."
                    status.setTextColor(Color.parseColor("#FF5252"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isProcessing = false
                    goBtn.isEnabled = true
                    goBtn.text = "REBASS IT"
                    status.text = "Error: ${e.message ?: "processing failed"}"
                    status.setTextColor(Color.parseColor("#FF5252"))
                }
            }
        }
        t.priority = Thread.NORM_PRIORITY - 1
        t.start()
    }

    /** Save a copy into public Downloads so the user can see it in Files app */
    private fun saveToDownloads(source: File): Uri? {
        return try {
            val name = source.name
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Download/ReBass")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = contentResolver.insert(collection, values) ?: return null

            contentResolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(source).use { input ->
                    input.copyTo(out)
                }
            }

            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            contentResolver.update(itemUri, values, null, null)

            itemUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun shareResult() {
        val f = lastOutput
        if (f == null || !f.exists()) {
            Toast.makeText(this, "No file ready yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // Prefer the Downloads copy if we have it
            val shareUri = lastDownloadsUri ?: FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                f
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share ReBass WAV"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ───────── Audio pipeline ─────────

    data class FullResult(
        val file: File,
        val peaks: List<BassPeak>,
        val newHz: List<Float>
    )

    private fun processAnyAudio(u: Uri, target: Float, amount: Float): FullResult {
        val pfd = contentResolver.openFileDescriptor(u, "r")
        val magic = ByteArray(12)
        pfd?.use { FileInputStream(it.fileDescriptor).use { input -> input.read(magic) } }
        val isWav = magic.size >= 12 && String(magic, 0, 4) == "RIFF" && String(magic, 8, 4) == "WAVE"

        return if (isWav) processWavLike(u, target, amount)
        else {
            val decoded = decodeToPcm(u)
            if (decoded.samples.isEmpty()) error("No audio track found")
            val processed = processInterleaved(decoded.samples, decoded.sampleRate, decoded.channels, target, amount)
            val f = writeWav(processed.samples, decoded.sampleRate, decoded.channels)
            FullResult(f, processed.peaks, processed.newHz)
        }
    }

    data class InterleavedResult(
        val samples: FloatArray,
        val peaks: List<BassPeak>,
        val newHz: List<Float>
    )

    private fun processInterleaved(
        interleaved: FloatArray,
        sampleRate: Int,
        channels: Int,
        target: Float,
        amount: Float
    ): InterleavedResult {
        val frames = interleaved.size / channels

        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var s = 0f
            for (c in 0 until channels) s += interleaved[i * channels + c]
            mono[i] = s / channels
        }

        val chunkFrames = sampleRate * 8
        val outMono = FloatArray(frames)
        var allPeaks = emptyList<BassPeak>()
        var allNewHz = emptyList<Float>()

        var offset = 0
        while (offset < frames) {
            val end = minOf(offset + chunkFrames, frames)
            val chunk = mono.copyOfRange(offset, end)
            val result = RebassProcessor.processBlock(chunk, sampleRate, target, amount)
            result.samples.copyInto(outMono, offset)
            if (allPeaks.isEmpty()) allPeaks = result.originalPeaks
            if (allNewHz.isEmpty()) allNewHz = result.newHz
            offset = end
            if (frames > sampleRate * 60) System.gc()
        }

        val out = FloatArray(interleaved.size)
        for (i in 0 until frames) {
            val delta = outMono[i] - mono[i]
            for (c in 0 until channels) {
                out[i * channels + c] = (interleaved[i * channels + c] + delta).coerceIn(-1f, 1f)
            }
        }
        return InterleavedResult(out, allPeaks, allNewHz)
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
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
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

    private fun processWavLike(u: Uri, target: Float, amount: Float): FullResult {
        val input = contentResolver.openInputStream(u) ?: error("Cannot open file")
        val bytes = input.readBytes()
        input.close()
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE")
            error("Invalid WAV")

        val channels = le16(bytes, 22)
        val rate = le32(bytes, 24)
        val bits = le16(bytes, 34)
        if (bits != 16) error("Only 16-bit PCM WAV is supported for direct path.")

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

        val processed = processInterleaved(samples, rate, channels, target, amount)
        val f = writeWav(processed.samples, rate, channels)
        return FullResult(f, processed.peaks, processed.newHz)
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

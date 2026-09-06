package com.rebass.app

import kotlin.math.*

data class BassPeak(val hz: Float, val magnitude: Float)

data class ProcessResult(
    val samples: FloatArray,
    val originalPeaks: List<BassPeak>,
    val newHz: List<Float>
)

object RebassProcessor {

    /**
     * Proper rebass: remove original low end, add dynamic subharmonics.
     * Works on chunks so long songs don't OOM / crash.
     */
    fun processBlock(
        input: FloatArray,
        sampleRate: Int,
        targetHz: Float,
        amount: Float
    ): ProcessResult {
        if (input.isEmpty()) return ProcessResult(input.copyOf(), emptyList(), emptyList())

        val peaks = detectBass(input, sampleRate)
        val cleaned = highPass(input, sampleRate, 55f)

        if (amount <= 0f || peaks.isEmpty()) {
            return ProcessResult(cleaned, peaks, emptyList())
        }

        val envelope = lowEnergyEnvelope(input, sampleRate, 120f)
        val gain = amount.coerceIn(0f, 1f) * 0.90f
        val usedPeaks = peaks.take(4)
        val phases = DoubleArray(usedPeaks.size) { 0.0 }
        val newHzList = mutableListOf<Float>()

        val out = FloatArray(input.size)
        for (i in input.indices) {
            var sub = 0.0
            val env = envelope[i].toDouble()

            for (p in usedPeaks.indices) {
                val peak = usedPeaks[p]
                val hz = subharmonicFrequency(peak.hz, targetHz)
                if (hz < 12f || hz > 80f) continue
                if (i == 0) newHzList.add(hz)

                val amp = gain * peak.magnitude * env * 0.60
                phases[p] += 2.0 * Math.PI * hz / sampleRate
                if (phases[p] > 2.0 * Math.PI) phases[p] -= 2.0 * Math.PI
                sub += sin(phases[p]) * amp
            }

            var sample = cleaned[i] + sub.toFloat()
            sample = (tanh(sample * 1.30f) / tanh(1.30f)).toFloat()
            out[i] = sample.coerceIn(-1f, 1f)
        }

        return ProcessResult(out, peaks, newHzList.distinct())
    }

    fun subharmonicFrequency(inputHz: Float, targetHz: Float): Float {
        if (targetHz > 0f) return targetHz
        return (inputHz / 2f).coerceIn(15f, 50f)
    }

    fun detectBass(samples: FloatArray, sampleRate: Int): List<BassPeak> {
        val peaks = ArrayList<BassPeak>()
        val n = samples.size
        if (n < 512) return peaks

        // Cap analysis length to avoid huge memory / CPU on long tracks
        val maxAnalyze = min(n, sampleRate * 45) // ~45 seconds max for detection
        val analyze = if (n > maxAnalyze) samples.copyOfRange(0, maxAnalyze) else samples
        val an = analyze.size

        val maxHz = min(150, sampleRate / 2 - 1)
        val minHz = 18
        val mags = FloatArray(maxHz + 1)

        var maxMag = 0.0
        val step = max(1, an / 10000)

        for (f in minHz..maxHz) {
            val w = 2.0 * Math.PI * f / sampleRate
            var s1 = 0.0
            var s2 = 0.0
            var count = 0
            var i = 0
            while (i < an) {
                val x = analyze[i].toDouble()
                val s0 = x + 2.0 * cos(w) * s1 - s2
                s2 = s1
                s1 = s0
                i += step
                count++
            }
            if (count < 1) continue
            val mag = sqrt(max(0.0, s1 * s1 + s2 * s2 - 2.0 * cos(w) * s1 * s2)) / count
            mags[f] = mag.toFloat()
            maxMag = max(maxMag, mag)
        }
        if (maxMag <= 1e-9) return peaks

        for (f in minHz + 1 until maxHz) {
            if (mags[f] > mags[f - 1] && mags[f] >= mags[f + 1] &&
                mags[f] > maxMag * 0.06f
            ) {
                peaks.add(BassPeak(f.toFloat(), (mags[f] / maxMag).toFloat()))
            }
        }
        return peaks.sortedByDescending { it.magnitude }.take(10)
    }

    private fun highPass(input: FloatArray, sampleRate: Int, cutoff: Float): FloatArray {
        val out = FloatArray(input.size)
        val c = tan(Math.PI * cutoff / sampleRate)
        val a1 = 1.0 / (1.0 + Math.sqrt(2.0) * c + c * c)
        val a2 = -2.0 * a1
        val a3 = a1
        val b1 = 2.0 * a1 * (c * c - 1.0)
        val b2 = a1 * (1.0 - Math.sqrt(2.0) * c + c * c)

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        for (i in input.indices) {
            val x0 = input[i].toDouble()
            val y0 = a1 * x0 + a2 * x1 + a3 * x2 - b1 * y1 - b2 * y2
            out[i] = y0.toFloat()
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        return out
    }

    private fun lowEnergyEnvelope(input: FloatArray, sampleRate: Int, cutoff: Float): FloatArray {
        val env = FloatArray(input.size)
        val alpha = exp(-2.0 * Math.PI * cutoff / sampleRate).toFloat()
        var low = 0f
        var smooth = 0f

        for (i in input.indices) {
            low = low + alpha * (input[i] - low)
            val energy = abs(low)
            if (energy > smooth) {
                smooth = smooth * 0.90f + energy * 0.10f
            } else {
                smooth = smooth * 0.997f + energy * 0.003f
            }
            env[i] = smooth
        }

        var maxE = 0.0001f
        for (v in env) if (v > maxE) maxE = v
        for (i in env.indices) env[i] = (env[i] / maxE).coerceIn(0f, 1f)
        return env
    }
}

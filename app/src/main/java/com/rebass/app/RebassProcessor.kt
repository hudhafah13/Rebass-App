package com.rebass.app

import kotlin.math.*

data class BassPeak(val hz: Float, val magnitude: Float)

object RebassProcessor {

    /**
     * Proper rebass style processing used on YouTube car-audio tracks:
     * 1. Detect dominant bass peaks (25–120 Hz) with Goertzel.
     * 2. High-pass the original signal to REMOVE the old bass.
     * 3. Generate new lower subharmonics (or target Hz) that follow the
     *    energy envelope of the original low end so it stays musical.
     * 4. Mix the new sub in and apply a soft limiter.
     */
    fun processBlock(
        input: FloatArray,
        sampleRate: Int,
        targetHz: Float,
        amount: Float
    ): FloatArray {
        if (input.isEmpty() || amount <= 0f) return input.copyOf()

        val n = input.size
        val out = FloatArray(n)

        // 1. Detect peaks on the whole block
        val peaks = detectBass(input, sampleRate)
        if (peaks.isEmpty()) {
            // still high-pass a little so it doesn't stay muddy
            return highPass(input, sampleRate, 55f)
        }

        // 2. Remove original bass (high-pass ~50-60 Hz)
        val cleaned = highPass(input, sampleRate, 55f)

        // 3. Build dynamic subharmonics that follow original low-end energy
        val envelope = lowEnergyEnvelope(input, sampleRate, 120f)
        val gain = amount.coerceIn(0f, 1f) * 0.85f

        // Use up to 3 strongest peaks
        val usedPeaks = peaks.take(3)
        val phases = DoubleArray(usedPeaks.size) { 0.0 }

        for (i in 0 until n) {
            var sub = 0.0
            val env = envelope[i].toDouble()

            for (p in usedPeaks.indices) {
                val peak = usedPeaks[p]
                val hz = subharmonicFrequency(peak.hz, targetHz)
                if (hz < 12f || hz > 55f) continue

                // amplitude follows original bass energy + peak strength
                val amp = gain * peak.magnitude * env * 0.55
                phases[p] += 2.0 * Math.PI * hz / sampleRate
                if (phases[p] > 2.0 * Math.PI) phases[p] -= 2.0 * Math.PI
                sub += sin(phases[p]) * amp
            }

            // mix cleaned original + new sub
            var sample = cleaned[i] + sub.toFloat()

            // soft limiter / gentle saturation
            sample = (tanh(sample * 1.25f) / tanh(1.25f)).toFloat()
            out[i] = sample.coerceIn(-1f, 1f)
        }

        return out
    }

    fun subharmonicFrequency(inputHz: Float, targetHz: Float): Float {
        if (targetHz > 0f) return targetHz
        // AUTO mode – drop one octave, keep it musical
        return (inputHz / 2f).coerceIn(15f, 40f)
    }

    fun detectBass(samples: FloatArray, sampleRate: Int): List<BassPeak> {
        val peaks = ArrayList<BassPeak>()
        val n = samples.size
        if (n < 512) return peaks

        val maxHz = min(120, sampleRate / 2 - 1)
        val minHz = 22
        val mags = FloatArray(maxHz + 1)

        var maxMag = 0.0
        for (f in minHz..maxHz) {
            val w = 2.0 * Math.PI * f / sampleRate
            var s1 = 0.0
            var s2 = 0.0
            // use a windowed subset for speed on long files
            val step = max(1, n / 12000)
            var count = 0
            var i = 0
            while (i < n) {
                val x = samples[i].toDouble()
                val s0 = x + 2.0 * cos(w) * s1 - s2
                s2 = s1
                s1 = s0
                i += step
                count++
            }
            val mag = sqrt(max(0.0, s1 * s1 + s2 * s2 - 2.0 * cos(w) * s1 * s2)) / count
            mags[f] = mag.toFloat()
            maxMag = max(maxMag, mag)
        }
        if (maxMag <= 1e-8) return peaks

        for (f in minHz + 1 until maxHz) {
            if (mags[f] > mags[f - 1] && mags[f] >= mags[f + 1] &&
                mags[f] > maxMag * 0.07f
            ) {
                peaks.add(BassPeak(f.toFloat(), (mags[f] / maxMag).toFloat()))
            }
        }
        return peaks.sortedByDescending { it.magnitude }.take(8)
    }

    /** Simple 2nd-order high-pass (removes old bass) */
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

    /** Smooth envelope of low-frequency energy so the new sub follows the song */
    private fun lowEnergyEnvelope(input: FloatArray, sampleRate: Int, cutoff: Float): FloatArray {
        val env = FloatArray(input.size)
        val alpha = exp(-2.0 * Math.PI * cutoff / sampleRate).toFloat()
        var low = 0f
        var smooth = 0f

        for (i in input.indices) {
            // simple one-pole low-pass
            low = low + alpha * (input[i] - low)
            val energy = abs(low)
            // attack / release style smoothing
            if (energy > smooth) {
                smooth = smooth * 0.92f + energy * 0.08f
            } else {
                smooth = smooth * 0.995f + energy * 0.005f
            }
            env[i] = smooth
        }

        // normalise envelope so peak is ~1
        var maxE = 0.0001f
        for (v in env) if (v > maxE) maxE = v
        for (i in env.indices) env[i] = (env[i] / maxE).coerceIn(0f, 1f)
        return env
    }
}

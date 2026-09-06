package com.rebass.app

import android.media.*
import java.io.*
import kotlin.math.*

data class BassPeak(val hz: Float, val magnitude: Float)

object RebassProcessor {
    /*
     * Real-time/file DSP:
     * 1. Decode PCM using MediaExtractor/MediaCodec.
     * 2. Analyze low frequencies with a Goertzel bank.
     * 3. For each detected bass peak, generate a subharmonic at peak/2
     *    (or the selected target when targetHz > 0).
     * 4. Mix generated sine components into the original.
     * 5. Apply a soft limiter.
     *
     * This file contains the DSP math used by the app. Android's media
     * codec layer is intentionally kept separate so compressed formats can
     * be decoded without third-party libraries.
     */

    fun detectBass(samples: FloatArray, sampleRate: Int): List<BassPeak> {
        val peaks = ArrayList<BassPeak>()
        val n = samples.size
        if (n < 256) return peaks

        val maxHz = min(150, sampleRate / 2)
        val minHz = 25
        val mags = FloatArray(maxHz + 1)

        var maxMag = 0.0
        for (f in minHz..maxHz) {
            val w = 2.0 * Math.PI * f / sampleRate
            var s1 = 0.0
            var s2 = 0.0
            for (x in samples) {
                val s0 = x + 2.0 * cos(w) * s1 - s2
                s2 = s1
                s1 = s0
            }
            val mag = sqrt(max(0.0, s1 * s1 + s2 * s2 - 2.0 * cos(w) * s1 * s2)) / n
            mags[f] = mag.toFloat()
            maxMag = max(maxMag, mag)
        }
        if (maxMag <= 0) return peaks

        for (f in minHz + 1 until maxHz) {
            if (mags[f] > mags[f - 1] && mags[f] >= mags[f + 1] &&
                mags[f] > maxMag * 0.08f
            ) {
                peaks.add(BassPeak(f.toFloat(), mags[f] / maxMag.toFloat()))
            }
        }
        return peaks.sortedByDescending { it.magnitude }.take(12)
    }

    fun subharmonicFrequency(inputHz: Float, targetHz: Float): Float {
        if (targetHz > 0f) return targetHz
        return inputHz / 2f
    }

    fun processBlock(
        input: FloatArray,
        sampleRate: Int,
        targetHz: Float,
        amount: Float
    ): FloatArray {
        if (input.isEmpty() || amount <= 0f) return input.copyOf()
        val out = input.copyOf()

        val peaks = detectBass(input, sampleRate)
        if (peaks.isEmpty()) return out

        val gain = amount.coerceIn(0f, 1f) * 0.75f
        for (peak in peaks.take(4)) {
            val hz = subharmonicFrequency(peak.hz, targetHz)
            if (hz < 15f || hz > 60f) continue
            val amp = gain * peak.magnitude * 0.20f
            for (i in out.indices) {
                val t = i.toDouble() / sampleRate
                out[i] = (out[i] + (sin(2.0 * Math.PI * hz * t) * amp).toFloat()).coerceIn(-1f, 1f)
            }
        }

        for (i in out.indices) {
            out[i] = (tanh(out[i] * 1.15f) / tanh(1.15f)).toFloat()
        }
        return out
    }
}

package ir.shahed.pahpad.core

import java.util.Random
import kotlin.math.*

/**
 * Procedural model of the real Shahed-136 engine sound — no recording, but
 * built from published acoustic measurements of the aircraft: an MD-550
 * two-stroke piston engine (Limbach L550E clone) with a pusher propeller.
 * Measured signature: fundamental ≈ 83 Hz, a dense harmonic series reaching
 * ~4 kHz, spectral centre of energy around 630–670 Hz (exhaust resonance),
 * and exhaust noise chopped at the firing rate — the famous "moped" buzz.
 * The loop is exactly one second; every periodic component completes an
 * integer number of cycles so the wrap is seamless. GameAudio scales the
 * playback rate 0.8–1.2× with throttle, i.e. ≈ 66–100 Hz, matching the
 * fundamental range seen in real recordings.
 */
object EngineWave {
    const val RATE = 22050
    fun create(): ShortArray {
        val n = RATE // 83 complete cycles in one second at nominal pitch.
        val data = DoubleArray(n)
        val random = Random(136550L)
        val f0 = 83.0
        // Harmonic gains of a two-stroke exhaust pulse train: slow 1/h^0.8
        // rolloff (energy still present near 4 kHz) with a formant boost at
        // the ~650 Hz exhaust resonance.
        val harmonics = 46
        val gains = DoubleArray(harmonics + 1)
        for (h in 1..harmonics) {
            val f = f0 * h
            val formant = 1.0 + 1.6 * exp(-((f - 650.0) / 260.0).pow(2))
            gains[h] = formant / h.toDouble().pow(0.8)
        }
        // Exhaust/prop noise: deterministic sine bands at integer Hz (seamless
        // wrap) across the 300–3000 Hz band, chopped by the firing pulse below.
        val bands = IntArray(22) { 300 + random.nextInt(2700) }
        val bandGains = DoubleArray(22) { (random.nextDouble() * 2.0 - 1.0) * 0.016 }
        var peak = 0.0
        for (i in data.indices) {
            val t = i.toDouble() / n
            // RPM roughness as timing jitter shared by every harmonic (a real
            // two-stroke never holds an exact cycle): ±0.4 rad at 5 Hz and
            // ±0.15 rad at 11 Hz, both periodic over the loop.
            val jitter = 0.4 * sin(2 * PI * 5 * t) + 0.15 * sin(2 * PI * 11 * t)
            val base = 2.0 * PI * f0 * t
            val phase = base + jitter
            var pulse = 0.0
            for (h in 1..harmonics) pulse += sin(base * h + jitter) * gains[h]
            // Noise bursts once per firing cycle, aligned with the pulse.
            val burst = 0.25 + 0.75 * (0.5 + 0.5 * sin(phase - PI / 2)).pow(2)
            var noise = 0.0
            for (b in bands.indices) noise += sin(2 * PI * bands[b] * t) * bandGains[b]
            val sample = pulse * 0.58 + noise * burst * 6.0 + sin(phase) * 0.12
            data[i] = sample
            peak = max(peak, abs(sample))
        }
        val gain = 19000.0 / peak.coerceAtLeast(1.0)
        return ShortArray(n) { (data[it] * gain).roundToInt().coerceIn(-30000, 30000).toShort() }
    }
}

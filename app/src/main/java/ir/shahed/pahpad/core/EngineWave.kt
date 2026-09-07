package ir.shahed.pahpad.core

import java.util.Random
import kotlin.math.*

/** Periodic game audio, not a recording or an acoustic model of a real aircraft. */
object EngineWave {
    const val RATE = 22050
    fun create(): ShortArray {
        val n = RATE // 100 complete cycles in one second at nominal pitch.
        val data = DoubleArray(n)
        val random = Random(131136L)
        val bands = IntArray(18) { 240 + random.nextInt(1300) }
        val gains = DoubleArray(18) { (random.nextDouble() * 2.0 - 1.0) * 0.010 }
        var peak = 0.0
        for (i in data.indices) {
            val t = i.toDouble() / n
            // Periodic FM: both phase and slope remain continuous across the wrap.
            val phase = 2.0 * PI * (100.0 * t + .12 * sin(2 * PI * 3 * t) + .045 * sin(2 * PI * 7 * t))
            var saw = 0.0
            for (h in 1..6) saw += sin(phase * h) * (1.0 - h / 7.0) / h
            val blade = sin(phase * 2)
            val pulse = blade * blade * blade * blade * blade
            var noise = 0.0
            for (b in bands.indices) noise += sin(2 * PI * bands[b] * t) * gains[b]
            val sample = saw * .62 + pulse * .24 + sin(phase) * .25 + noise
            data[i] = sample
            peak = max(peak, abs(sample))
        }
        val gain = 19000.0 / peak.coerceAtLeast(1.0)
        return ShortArray(n) { (data[it] * gain).roundToInt().coerceIn(-30000, 30000).toShort() }
    }
}

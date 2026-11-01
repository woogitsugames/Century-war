package com.centurywar.engine
import org.junit.Test
class TmpRatioProbeTest {
    @Test fun probe() {
        val sb = StringBuilder()
        for (seed in intArrayOf(55, 103, 7)) for (era in 0..8) {
            val calm = MusicSynth(seed); val busy = MusicSynth(seed)
            calm.setEra(era); busy.setEra(era); calm.skipFade(); busy.skipFade()
            calm.setIntensity(0.05f); busy.setIntensity(1f)
            val a = FloatArray(1024); val b = FloatArray(1024)
            var ce = 0.0; var be = 0.0
            repeat(140) { calm.render(a); busy.render(b); if (it > 25) for (i in a.indices) { ce += a[i]*a[i]; be += b[i]*b[i] } }
            sb.append("seed=$seed era=$era ratio=${be/ce}\n")
        }
        java.io.File("/tmp/claude-0/-workspace-Century-war/0c7447d9-d378-51ee-acd4-0fbeedee887e/scratchpad/ratio.txt").writeText(sb.toString())
    }
}

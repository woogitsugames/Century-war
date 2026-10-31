package com.centurywar.game.art

import com.centurywar.game.UnitDef
import com.centurywar.game.Role

/**
 * Rozpoznawalne dodatki do jednostek, wież i baz czterech gałęzi.
 * Wykorzystuje współdzielony Pen, bez tworzenia obiektów podczas rysowania.
 */
class BranchArt(private val p: Pen) {
    private val diesel = 0xFF77726A.toInt()
    private val copper = 0xFFD39348.toInt()
    private val electric = 0xFF70EFFA.toInt()
    private val cyber = 0xFFEBCE44.toInt()
    private val bio = 0xFF7CE891.toInt()
    private val dark = 0xFF31434A.toInt()

    fun unit(def: UnitDef, team: Int) {
        if (def.variant < 0) return
        when {
            def.era == 4 && def.variant == 0 -> {
                // Silniki diesla: opancerzone płyty i rury wydechowe.
                p.rr(-15f, -48f, 10f, -38f, 3f, diesel)
                p.rectF(-10f, -46f, 5f, -43f, team)
                if (p.hi) {
                    p.seg(-16f, -42f, -23f, -57f, 4f, copper)
                    p.circleF(-23f, -59f, 4f, 0xFF47413A.toInt())
                    p.rivets(-9f, -40f, 7f, 3, 1.5f, copper)
                }
            }
            def.era == 4 -> {
                // Cewki Tesli i jasne przewody.
                p.circle(0f, -50f, 7f, electric)
                p.circleF(0f, -50f, 3f, 0xFFFFFFFF.toInt())
                p.segF(-19f, -38f, -8f, -51f, 3f, electric)
                p.segF(8f, -51f, 22f, -40f, 3f, electric)
                if (p.hi) {
                    p.segF(-22f, -61f, -12f, -67f, 2f, copper)
                    p.segF(12f, -67f, 21f, -59f, 2f, copper)
                }
            }
            def.era == 6 && def.variant == 0 -> {
                // Cyfrowy rdzeń drona, wirniki i ostre znaczniki roju.
                p.rr(-14f, -42f, 12f, -28f, 4f, team)
                p.rectF(-9f, -39f, 7f, -35f, cyber)
                p.seg(-23f, -28f, -14f, -34f, 2f, dark)
                p.seg(12f, -34f, 22f, -28f, 2f, dark)
                if (p.hi) {
                    p.circleF(-23f, -27f, 4f, electric)
                    p.circleF(22f, -27f, 4f, electric)
                }
            }
            def.era == 6 -> {
                // Mutant biomechaniczny: narastające płytki i zielone żyły.
                p.rr(-12f, -55f, 14f, -38f, 8f, team)
                p.circleF(0f, -47f, 6f, bio)
                p.segF(-10f, -40f, -21f, -24f, 4f, bio)
                p.segF(11f, -40f, 22f, -24f, 4f, bio)
                if (def.role == Role.ELITE && p.hi) {
                    p.tri(-17f, -53f, -10f, -78f, -4f, -55f); p.shape(bio)
                    p.tri(7f, -55f, 15f, -78f, 21f, -52f); p.shape(bio)
                }
            }
        }
    }

    /** Nagrywane w Picture razem z bazą, więc nie obciąża kolejnych klatek. */
    fun base(era: Int, variant: Int, team: Int) {
        when {
            era == 4 && variant == 0 -> {
                p.rect(-304f, -255f, -266f, -75f, diesel)
                p.rect(-245f, -318f, -218f, -80f, diesel)
                p.rr(-318f, -132f, -85f, -65f, 8f, team)
                p.rectF(-300f, -116f, -107f, -107f, copper)
                if (p.hi) for (i in 0..4) p.circleF(-286f + i * 36f, -90f, 7f, 0xFF3E3F45.toInt())
            }
            era == 4 && variant == 1 -> {
                p.seg(-252f, -100f, -252f, -320f, 12f, copper)
                p.seg(-130f, -100f, -130f, -350f, 12f, copper)
                for (i in 0..3) {
                    p.oval(-281f, -142f - i * 42f, -222f, -127f - i * 42f, electric)
                    p.oval(-160f, -161f - i * 42f, -99f, -146f - i * 42f, electric)
                }
                p.circle(-191f, -205f, 23f, team)
                p.circleF(-191f, -205f, 9f, electric)
            }
            era == 6 && variant == 0 -> {
                p.seg(-254f, -65f, -254f, -375f, 10f, dark)
                p.seg(-254f, -365f, -305f, -308f, 5f, electric)
                p.seg(-254f, -365f, -202f, -308f, 5f, electric)
                for (i in 0..3) {
                    val x = -312f + i * 65f
                    p.rr(x, -130f, x + 40f, -93f, 5f, team)
                    p.rectF(x + 7f, -118f, x + 33f, -110f, cyber)
                }
            }
            era == 6 && variant == 1 -> {
                p.oval(-316f, -296f, -100f, -58f, 0xFF487B63.toInt())
                p.oval(-288f, -271f, -128f, -81f, team)
                p.oval(-260f, -242f, -154f, -102f, bio)
                for (i in 0..3) {
                    val x = -292f + i * 58f
                    p.seg(x, -70f, x + 15f, -130f, 7f, bio)
                    p.circleF(x + 15f, -133f, 7f, electric)
                }
            }
        }
    }

    fun turret(era: Int, variant: Int, x: Float, y: Float) {
        when {
            era == 4 && variant == 0 -> {
                p.rr(x - 12f, y - 26f, x + 24f, y - 7f, 4f, diesel)
                p.seg(x + 15f, y - 17f, x + 57f, y - 19f, 7f, copper)
            }
            era == 4 && variant == 1 -> {
                p.seg(x + 5f, y - 10f, x + 5f, y - 56f, 6f, copper)
                p.circle(x + 5f, y - 53f, 11f, electric)
            }
            era == 6 && variant == 0 -> {
                p.circle(x + 5f, y - 36f, 14f, cyber)
                p.segF(x - 24f, y - 33f, x + 34f, y - 33f, 4f, electric)
            }
            era == 6 && variant == 1 -> {
                p.oval(x - 19f, y - 52f, x + 29f, y - 12f, bio)
                p.circleF(x + 5f, y - 33f, 10f, teamGreen())
            }
        }
    }

    private fun teamGreen(): Int = 0xFF2E845F.toInt()
}

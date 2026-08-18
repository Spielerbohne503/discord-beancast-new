package uk.spielerbohne.petodo.domain.pet

import uk.spielerbohne.petodo.domain.backup.Json
import uk.spielerbohne.petodo.domain.backup.JsonValue

/**
 * Das Skin-Format (Projektplan, Abschnitt 8.2) — festgelegt, bevor es Sprites gibt.
 *
 * v1 zeigt eine Statustafel und kein einziges Sprite. Das Format steht trotzdem, weil es
 * zu den Einbahnstraßen gehört: Die Krankheitsstufe ist eine **eigene Dimension** mit
 * Rückfall auf gesund. Fehlt `idle_sick`, wird `idle` genommen — so lässt sich klein
 * anfangen, statt vor dem ersten Sprite alles zeichnen zu müssen.
 */
data class SkinAnimation(
    val file: String,
    val frames: Int,
    val loop: Boolean,
)

data class SkinManifest(
    val id: String,
    val name: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val scale: Int,
    val fps: Int,
    val animations: Map<String, SkinAnimation>,
) {

    /**
     * Die Animation für einen Zustand in einer Krankheitsstufe.
     *
     * Auflösungsregel: erst `idle_sick`, dann `idle`. Ein glaubwürdig krankes Pet braucht
     * keine kranke Fütter-Animation.
     */
    fun resolve(state: String, stage: HealthStage): SkinAnimation? {
        suffixFor(stage)?.let { suffix ->
            animations["${state}_$suffix"]?.let { return it }
        }
        return animations[state]
    }

    /** Der Name, unter dem eine Stufe zuerst gesucht wird. */
    fun resolvedKey(state: String, stage: HealthStage): String? {
        suffixFor(stage)?.let { suffix ->
            val key = "${state}_$suffix"
            if (animations.containsKey(key)) return key
        }
        return if (animations.containsKey(state)) state else null
    }

    companion object {
        /** Gesund hat keinen Zusatz — das ist die Grundform. */
        fun suffixFor(stage: HealthStage): String? = when (stage) {
            HealthStage.HEALTHY -> null
            HealthStage.WEAKENED -> "weak"
            HealthStage.SICK -> "sick"
            HealthStage.MISERABLE -> "miserable"
        }

        /**
         * Liest ein `skin.json`. Gibt `null` zurück, wenn die Datei unbrauchbar ist —
         * beschädigte oder fehlende Dateien verhindern den Start nicht (Projektplan,
         * Abschnitt 13).
         */
        fun parse(text: String): SkinManifest? {
            val root = Json.parse(text) as? JsonValue.Object ?: return null
            val id = root.string("id")?.takeIf { it.isNotBlank() } ?: return null
            val animations = (root["animations"] as? JsonValue.Object)?.entries.orEmpty()

            val parsed = animations.mapNotNull { (key, value) ->
                val entry = value as? JsonValue.Object ?: return@mapNotNull null
                val file = entry.string("file")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                key to SkinAnimation(
                    file = file,
                    frames = entry.int("frames")?.coerceAtLeast(1) ?: 1,
                    loop = entry.boolean("loop") ?: true,
                )
            }.toMap()

            return SkinManifest(
                id = id,
                name = root.string("name") ?: id,
                frameWidth = root.int("frameWidth")?.coerceAtLeast(1) ?: DEFAULT_FRAME,
                frameHeight = root.int("frameHeight")?.coerceAtLeast(1) ?: DEFAULT_FRAME,
                scale = root.int("scale")?.coerceAtLeast(1) ?: DEFAULT_SCALE,
                fps = root.int("fps")?.coerceAtLeast(1) ?: DEFAULT_FPS,
                animations = parsed,
            )
        }

        private const val DEFAULT_FRAME = 32
        private const val DEFAULT_SCALE = 4
        private const val DEFAULT_FPS = 8
    }
}

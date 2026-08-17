package uk.spielerbohne.petodo.domain.notify

/**
 * Deterministische Benachrichtigungs- und PendingIntent-IDs aus der Task-UUID.
 *
 * Einbahnstraße aus dem Projektplan (Abschnitt 6.7): Ohne stabile ID lässt sich eine
 * bestehende Meldung weder aktualisieren noch zurücknehmen — die App sammelt dann
 * Karteileichen im Benachrichtigungsschirm an.
 *
 * `String.hashCode()` wäre naheliegend, ist aber ein Implementierungsdetail der
 * Plattform. Hier steht deshalb FNV-1a ausgeschrieben: derselbe Text ergibt auf jedem
 * Gerät und in jeder Version dieselbe Zahl.
 */
object NotificationIds {

    /** Sammelmeldung für überfällige Aufgaben. */
    const val SUMMARY_OVERDUE = 1

    /** Statuszeile des Fokus-Timers (Phase 3). */
    const val FOCUS_STATUS = 2

    /** Pet-Meldungen (Phase 4). */
    const val PET = 3

    /** IDs bis hierher sind fest vergeben; abgeleitete IDs beginnen darüber. */
    const val RESERVED_MAX = 15

    private const val FNV_OFFSET_BASIS = -2128831035 // 2166136261 als Int
    private const val FNV_PRIME = 16777619

    /** Notification-ID einer Aufgabe. Immer positiv und immer oberhalb der festen IDs. */
    fun forTask(taskId: String): Int = spread(fnv1a(taskId))

    /**
     * Request-Code für einen PendingIntent. Jede Aktion braucht einen eigenen, sonst
     * überschreibt Android die Extras der zuvor erzeugten Intents.
     */
    fun requestCode(taskId: String, action: TaskNotificationAction): Int =
        spread(fnv1a("$taskId#${action.name}"))

    /** Request-Code des wiederkehrenden Alarms einer Aufgabe. */
    fun alarmRequestCode(taskId: String): Int = spread(fnv1a("$taskId#alarm"))

    /** Request-Code für einen Knopf der Fokus-Statuszeile. */
    fun requestCodeForFocus(action: String): Int = spread(fnv1a("focus#$action"))

    private fun fnv1a(text: String): Int {
        var hash = FNV_OFFSET_BASIS
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toInt() and 0xFF)
            hash *= FNV_PRIME
        }
        return hash
    }

    /** Auf den positiven Bereich oberhalb der reservierten IDs abbilden. */
    private fun spread(hash: Int): Int =
        (hash and Int.MAX_VALUE) % (Int.MAX_VALUE - RESERVED_MAX) + RESERVED_MAX + 1
}

/** Die Aktionen, die aus einer Meldung heraus möglich sind. */
enum class TaskNotificationAction {
    /** Meldung antippen: App öffnen. */
    OPEN,

    /** Erledigt. */
    DONE,

    /** +1 Std. */
    SNOOZE_HOUR,

    /** Morgen. */
    TOMORROW,

    /** Aufräum-Frage: Neu terminieren. */
    RESCHEDULE,

    /** Aufräum-Frage: Löschen. */
    DELETE,
}

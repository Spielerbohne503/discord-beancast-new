package uk.spielerbohne.petodo.domain.nag

import uk.spielerbohne.petodo.domain.Balance
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Ruhezeit (Projektplan, Abschnitt 6.5).
 *
 * Fällige Nags werden ans Fensterende **geschoben**, nicht verworfen. Ein verworfener
 * Nag ist eine verpasste Aufgabe, und genau dafür benutzt man die App nicht.
 */
data class QuietHours(
    val start: LocalTime,
    val end: LocalTime,
    val enabled: Boolean = true,
) {

    /** Fenster über Mitternacht (23:00–08:00) ebenso wie am Tag (13:00–14:00). */
    fun contains(time: LocalTime): Boolean {
        if (!enabled || start == end) return false
        return if (start < end) {
            time >= start && time < end
        } else {
            time >= start || time < end
        }
    }

    companion object {
        val DEFAULT = QuietHours(
            start = LocalTime.parse(Balance.QUIET_HOURS_DEFAULT_START),
            end = LocalTime.parse(Balance.QUIET_HOURS_DEFAULT_END),
        )

        val OFF = DEFAULT.copy(enabled = false)
    }
}

object QuietHoursPolicy {

    /**
     * Verschiebt [at] ans Ende der Ruhezeit, falls es hineinfällt. Sonst unverändert.
     *
     * Über Mitternacht hinweg landet ein Nag um 23:30 damit am nächsten Morgen um 08:00,
     * einer um 02:00 ebenfalls — beide am selben Fensterende.
     */
    fun shiftOutOfQuietHours(at: Instant, zone: ZoneId, quietHours: QuietHours): Instant {
        val zoned = at.atZone(zone)
        if (!quietHours.contains(zoned.toLocalTime())) return at

        val endsToday = zoned.toLocalDate().atTime(quietHours.end).atZone(zone)
        val endsTomorrow = zoned.toLocalDate().plusDays(1).atTime(quietHours.end).atZone(zone)

        return if (endsToday.toInstant() > at) endsToday.toInstant() else endsTomorrow.toInstant()
    }
}

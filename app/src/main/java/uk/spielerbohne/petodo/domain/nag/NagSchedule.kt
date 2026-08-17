package uk.spielerbohne.petodo.domain.nag

import uk.spielerbohne.petodo.domain.Balance
import uk.spielerbohne.petodo.domain.model.Task
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Wann der nächste Nag fällig ist.
 *
 * Die eine Regel, an der selbstgebaute Aufgaben-Apps scheitern: Der nächste Termin wird
 * über `LocalDate.plusDays(1).atTime(uhrzeit)` berechnet, **niemals** über
 * `+ 86_400_000 ms`. Sonst wandert die 8-Uhr-Erinnerung bei jeder Zeitumstellung um eine
 * Stunde — zweimal im Jahr, und danach für immer schief.
 */
object NagSchedule {

    /**
     * Die lokale Uhrzeit, an der die Nag-Kette hängt.
     *
     * Vorrang hat `dueTimeLocal` — genau dafür existiert das Feld. Fehlt es, gilt bei
     * einer Aufgabe mit Uhrzeit deren Uhrzeit, bei einem Tagestermin die Vorgabe.
     */
    fun anchorTime(task: Task, zone: ZoneId): LocalTime =
        task.dueTimeLocal
            ?: task.dueAt?.takeIf { task.hasTime }?.atZone(zone)?.toLocalTime()
            ?: LocalTime.of(Balance.DEFAULT_DUE_HOUR, 0)

    /**
     * Der erste Alarm einer Aufgabe: zur Fälligkeit selbst, bei Tagesterminen am
     * Fälligkeitstag zur Ankeruhrzeit. Ohne Fälligkeit gibt es keinen Alarm.
     */
    fun firstNagAt(task: Task, zone: ZoneId): Instant? {
        val due = task.dueAt ?: return null
        if (task.hasTime) return due
        return due.atZone(zone).toLocalDate().atTime(anchorTime(task, zone)).atZone(zone).toInstant()
    }

    /**
     * Der Termin am Folgetag zur selben lokalen Uhrzeit.
     *
     * An einer Zeitumstellung liegen zwischen zwei Nags 23 bzw. 25 Stunden — die
     * Uhrzeit bleibt trotzdem dieselbe. Fällt die Ankeruhrzeit in die Stunde, die es
     * beim Vorstellen der Uhr nicht gibt, schiebt `java.time` sie nach vorn; die
     * Erinnerung fällt also nicht aus.
     */
    fun nextNagAt(after: Instant, zone: ZoneId, anchor: LocalTime): Instant =
        after.atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atTime(anchor)
            .atZone(zone)
            .toInstant()

    /** Der nächste Nag einer konkreten Aufgabe. */
    fun nextNagAt(task: Task, after: Instant, zone: ZoneId): Instant =
        nextNagAt(after, zone, anchorTime(task, zone))

    /**
     * "Morgen" aus der Benachrichtigung: die Fälligkeit wandert einen Kalendertag weiter,
     * die Uhrzeit bleibt. Auch hier keine Millisekunden-Arithmetik.
     */
    fun postponeToTomorrow(task: Task, now: Instant, zone: ZoneId): Instant {
        val basis = task.dueAt ?: now
        val date = maxOf(basis, now).atZone(zone).toLocalDate().plusDays(1)
        return date.atTime(anchorTime(task, zone)).atZone(zone).toInstant()
    }
}

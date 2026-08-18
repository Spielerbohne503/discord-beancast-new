package uk.spielerbohne.petodo.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import uk.spielerbohne.petodo.R
import uk.spielerbohne.petodo.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * Anzeige der Fälligkeit. Reine Darstellungsfrage — die Einordnung in überfällig / heute /
 * später trifft `domain/today/TodayGrouping`, nicht dieser Code.
 */
@Composable
fun Task.dueLabel(now: Instant, zone: ZoneId): String? {
    val due = dueAt ?: return null
    val dueZoned = due.atZone(zone)
    val dueDate = dueZoned.toLocalDate()
    val today = now.atZone(zone).toLocalDate()

    val timePart = if (hasTime) {
        dueZoned.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    } else {
        ""
    }

    return when (ChronoUnit.DAYS.between(today, dueDate)) {
        0L -> stringResource(R.string.due_today_at, timePart).trim()
        1L -> stringResource(R.string.due_tomorrow_at, timePart).trim()
        -1L -> stringResource(R.string.due_yesterday_at, timePart).trim()
        else -> stringResource(
            R.string.due_date_at,
            dueDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            timePart,
        ).trim()
    }
}

/** Zusatzzeile für überfällige Aufgaben: wie lange schon. */
@Composable
fun Task.overdueLabel(now: Instant, zone: ZoneId): String? {
    val days = overdueDays(now, zone)
    return when {
        days <= 0L -> null
        days == 1L -> stringResource(R.string.due_overdue_one_day)
        else -> stringResource(R.string.due_overdue_days, days.toInt())
    }
}

/**
 * Der Tag, an dem etwas erledigt wurde — für die Tagesüberschriften im Rückblick.
 *
 * „Gestern“ statt eines Datums, solange es eines gibt: Ein Datum zwingt zum Rechnen,
 * ein Wochentag nicht.
 */
@Composable
fun completedDayLabel(tag: LocalDate, now: Instant, zone: ZoneId): String {
    val heute = now.atZone(zone).toLocalDate()

    return when (ChronoUnit.DAYS.between(tag, heute)) {
        1L -> stringResource(R.string.done_day_yesterday)
        // Innerhalb der Woche trägt der Wochentag mehr als das Datum.
        in 2L..6L -> tag.format(DateTimeFormatter.ofPattern("EEEE"))
        else -> tag.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

package uk.spielerbohne.petodo.domain.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NotificationIdsTest {

    @Test
    fun dieselbe_uuid_ergibt_immer_dieselbe_id() {
        val id = "6f1d0e6a-9c2b-4c1e-9a1f-0b2f3c4d5e6f"
        assertEquals(NotificationIds.forTask(id), NotificationIds.forTask(id))
    }

    @Test
    fun die_id_ist_fest_verdrahtet_und_darf_sich_nie_aendern() {
        // Ändert sich dieser Wert, verlieren alle bestehenden Meldungen ihren Bezug:
        // Sie lassen sich weder aktualisieren noch zurücknehmen.
        assertEquals(857_686_332, NotificationIds.forTask("6f1d0e6a-9c2b-4c1e-9a1f-0b2f3c4d5e6f"))
    }

    @Test
    fun ids_sind_immer_positiv_und_kollidieren_nie_mit_den_festen_ids() {
        repeat(10_000) {
            val id = NotificationIds.forTask(UUID.randomUUID().toString())
            assertTrue("$id ist nicht positiv", id > 0)
            assertTrue("$id liegt im reservierten Bereich", id > NotificationIds.RESERVED_MAX)
        }
    }

    @Test
    fun zehntausend_uuids_ergeben_zehntausend_verschiedene_ids() {
        val ids = (1..10_000).map { NotificationIds.forTask(UUID.randomUUID().toString()) }.toSet()
        assertEquals(10_000, ids.size)
    }

    @Test
    fun jede_aktion_bekommt_einen_eigenen_request_code() {
        val taskId = UUID.randomUUID().toString()
        val codes = TaskNotificationAction.entries.map { NotificationIds.requestCode(taskId, it) }

        assertEquals(TaskNotificationAction.entries.size, codes.toSet().size)
        codes.forEach { assertTrue(it > NotificationIds.RESERVED_MAX) }
    }

    @Test
    fun alarm_und_meldung_benutzen_verschiedene_codes() {
        val taskId = UUID.randomUUID().toString()
        assertNotEquals(NotificationIds.alarmRequestCode(taskId), NotificationIds.forTask(taskId))
    }

    @Test
    fun verschiedene_aufgaben_teilen_sich_keinen_alarm_code() {
        val codes = (1..10_000).map { NotificationIds.alarmRequestCode(UUID.randomUUID().toString()) }
        assertEquals(10_000, codes.toSet().size)
    }

    @Test
    fun die_festen_ids_sind_verschieden() {
        val feste = setOf(
            NotificationIds.SUMMARY_OVERDUE,
            NotificationIds.FOCUS_STATUS,
            NotificationIds.PET,
        )
        assertEquals(3, feste.size)
        feste.forEach { assertTrue(it <= NotificationIds.RESERVED_MAX) }
    }
}

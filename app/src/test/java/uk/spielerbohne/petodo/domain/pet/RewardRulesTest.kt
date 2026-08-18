package uk.spielerbohne.petodo.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.spielerbohne.petodo.domain.Balance
import java.time.Duration
import java.time.Instant

/**
 * Sperrzeiten (Projektplan, Abschnitt 5.5).
 *
 * Ohne Sperrzeiten tippt man sich aus jeder Krankheit heraus, und die Kopplung an die
 * Arbeit ist wertlos. Das ist die einzige Stelle, an der die App „nein“ sagt.
 */
class RewardRulesTest {

    private val jetzt: Instant = Instant.parse("2026-08-17T10:00:00Z")

    @Test
    fun ohne_vorherige_handlung_ist_alles_erlaubt() {
        RewardType.entries.forEach { typ ->
            assertTrue("$typ", RewardRules.isAllowed(typ, lastAt = null, now = jetzt))
            assertEquals(Duration.ZERO, RewardRules.remainingCooldown(typ, null, jetzt))
        }
    }

    @Test
    fun fuettern_spielen_und_streicheln_haben_sperrzeiten_die_arbeit_nicht() {
        assertEquals(4 * 60L, RewardType.FEED.cooldownMinutes)
        assertEquals(2 * 60L, RewardType.PLAY.cooldownMinutes)
        assertEquals(30L, RewardType.PAT.cooldownMinutes)

        // Was aus Arbeit entsteht, wird nie gesperrt — sonst bestraft die App Fleiß.
        assertNull(RewardType.TASK_DONE.cooldownMinutes)
        assertNull(RewardType.TASK_CLEANED.cooldownMinutes)
        assertNull(RewardType.FOCUS_DONE.cooldownMinutes)
        assertNull(RewardType.TASK_CREATED.cooldownMinutes)
    }

    @Test
    fun die_sperre_endet_genau_nach_der_sperrzeit_nicht_frueher() {
        val gefuettert = jetzt
        val sperre = Duration.ofMinutes(RewardType.FEED.cooldownMinutes!!)

        assertFalse(RewardRules.isAllowed(RewardType.FEED, gefuettert, gefuettert))
        assertFalse(
            RewardRules.isAllowed(RewardType.FEED, gefuettert, gefuettert.plus(sperre).minusSeconds(1))
        )
        assertTrue(RewardRules.isAllowed(RewardType.FEED, gefuettert, gefuettert.plus(sperre)))
        assertTrue(
            RewardRules.isAllowed(RewardType.FEED, gefuettert, gefuettert.plus(sperre).plusSeconds(1))
        )
    }

    @Test
    fun die_restliche_sperrzeit_zaehlt_herunter_und_bleibt_bei_null_stehen() {
        val gestreichelt = jetzt
        assertEquals(Duration.ofMinutes(30), RewardRules.remainingCooldown(RewardType.PAT, gestreichelt, jetzt))
        assertEquals(
            Duration.ofMinutes(10),
            RewardRules.remainingCooldown(RewardType.PAT, gestreichelt, jetzt.plusSeconds(20 * 60)),
        )
        assertEquals(
            Duration.ZERO,
            RewardRules.remainingCooldown(RewardType.PAT, gestreichelt, jetzt.plusSeconds(60 * 60)),
        )
    }

    @Test
    fun an_einem_tag_laesst_sich_hoechstens_sechsmal_fuettern() {
        // Der Punkt der Sperrzeit: Pflege allein trägt einen Tag nicht.
        var zeit = jetzt
        val ende = jetzt.plus(Duration.ofHours(24))
        var zuletzt: Instant? = null
        var mahlzeiten = 0

        while (zeit.isBefore(ende)) {
            if (RewardRules.isAllowed(RewardType.FEED, zuletzt, zeit)) {
                mahlzeiten++
                zuletzt = zeit
            }
            zeit = zeit.plusSeconds(60)
        }

        assertEquals(6, mahlzeiten)
    }

    @Test
    fun pflege_allein_haelt_gegen_volle_last_nicht_mit() {
        // Sechs Mahlzeiten, zwölf Spiele, achtundvierzig Streicheleinheiten am Tag —
        // gegen die volle Überfälligkeitslast reicht das nicht.
        val gewinn = 6 * RewardType.FEED.dSatiety +
            12 * RewardType.PLAY.dSatiety +
            48 * RewardType.PAT.dSatiety
        val verlust = Balance.VALUE_MAX / Balance.DECAY_HOURS_SATIETY *
            Balance.DECAY_MAX_ELAPSED_HOURS * OverdueLoad.baseMultiplier(Balance.LOAD_CAP)

        assertTrue(
            "Pflege gleicht die volle Last aus ($gewinn gegen $verlust) — dann ist die Kopplung wertlos",
            gewinn < verlust,
        )
    }

    @Test
    fun erfassungs_xp_sind_bei_zehn_am_tag_gedeckelt() {
        assertTrue(RewardRules.isTaskCreationRewardAllowed(0))
        assertTrue(RewardRules.isTaskCreationRewardAllowed(9))
        assertFalse(RewardRules.isTaskCreationRewardAllowed(10))
        assertFalse(RewardRules.isTaskCreationRewardAllowed(99))
    }

    @Test
    fun erfassen_gibt_nur_xp_und_keine_werte() {
        // Sonst füttert man das Pet, indem man Aufgaben anlegt, die man nie erledigt.
        assertEquals(0, RewardType.TASK_CREATED.dEnergy)
        assertEquals(0, RewardType.TASK_CREATED.dSatiety)
        assertEquals(0, RewardType.TASK_CREATED.dMood)
        assertTrue(RewardType.TASK_CREATED.dXp > 0)
    }

    @Test
    fun aufraeumen_zaehlt_wie_erledigen_damit_ehrlichkeit_nicht_bestraft_wird() {
        assertTrue(RewardType.TASK_CLEANED.dSatiety > 0)
        assertTrue(RewardType.TASK_CLEANED.dMood > 0)
    }

    @Test
    fun unbekannte_typen_aus_der_datenbank_werden_uebergangen_nicht_geworfen() {
        assertEquals(RewardType.FEED, RewardType.parse("FEED"))
        assertNull(RewardType.parse("TASK_TELEPORTED"))
        assertNull(RewardType.parse(null))
    }

    @Test
    fun ein_ereignis_uebernimmt_die_werte_seines_typs() {
        val ereignis = RewardEvent.of("e1", jetzt, RewardType.FOCUS_DONE, refId = "s1")
        assertEquals(RewardType.FOCUS_DONE.dEnergy, ereignis.dEnergy)
        assertEquals(RewardType.FOCUS_DONE.dSatiety, ereignis.dSatiety)
        assertEquals(RewardType.FOCUS_DONE.dMood, ereignis.dMood)
        assertEquals(RewardType.FOCUS_DONE.dXp, ereignis.dXp)
        assertEquals("s1", ereignis.refId)
    }
}

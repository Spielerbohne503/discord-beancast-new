package uk.spielerbohne.petodo.domain.model

import java.time.Instant

/**
 * Etikett. Eine Aufgabe kann beliebig viele tragen — deshalb eine eigene Tabelle mit
 * Zuordnung, keine Spalte auf der Aufgabe.
 */
data class Tag(
    val id: String,
    val name: String,
    val colorArgb: Int? = null,
    val sortKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    companion object {
        /** Etikettennamen ohne führendes #, ohne Leerzeichen am Rand, nie leer. */
        fun normalizeName(raw: String): String? =
            raw.trim().removePrefix("#").trim().takeIf { it.isNotEmpty() }
    }
}

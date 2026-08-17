package uk.spielerbohne.petodo.domain.model

import java.time.Instant

/**
 * Liste, in der eine Aufgabe liegt. v1 zeigt nur die Inbox — das Feld existiert trotzdem
 * ab Tag eins (Einbahnstraße), samt [excludeFromNag] als Druckventil.
 */
data class TaskList(
    val id: String,
    val name: String,
    val colorArgb: Int? = null,
    val sortKey: String,
    val excludeFromNag: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    companion object {
        /** Feste IDs der beiden Listen, die beim ersten Start entstehen. */
        const val ID_INBOX = "inbox"
        const val ID_SOMEDAY = "irgendwann"
    }
}

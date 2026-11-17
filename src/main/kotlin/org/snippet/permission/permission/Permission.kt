package org.snippet.permission.permission

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

enum class Role {
    OWNER,
    READ,
    ;

    /** `READ` is allowed to both roles; `WRITE` only to the owner. */
    fun allows(action: Action): Boolean =
        when (action) {
            Action.READ -> true
            Action.WRITE -> this == OWNER
        }
}

enum class Action {
    READ,
    WRITE,
}

/**
 * Which of the user's snippets to list: the ones they own, the ones shared with them, or both.
 * Lowercase because that is how the contract spells them; binding is exact, so `OWNED` is a 400.
 */
@Suppress("ktlint:standard:enum-entry-name-case", "EnumEntryName")
enum class Relation(
    val role: Role?,
) {
    owned(Role.OWNER),
    shared(Role.READ),
    all(null),
}

@Embeddable
data class PermissionId(
    val snippetId: UUID,
    val userId: String,
) : Serializable

/** What a user can do on a snippet. Rows are only inserted and deleted, never updated. */
@Entity
@Table(name = "permission")
class Permission(
    @EmbeddedId
    val id: PermissionId,
    @Enumerated(EnumType.STRING)
    val role: Role,
    val createdAt: Instant,
)

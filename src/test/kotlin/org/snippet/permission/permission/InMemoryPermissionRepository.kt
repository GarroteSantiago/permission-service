package org.snippet.permission.permission

import java.time.Instant
import java.util.UUID

/** Behaves like the Postgres-backed repository, for tests that run without Docker. Not a bean: import it. */
class InMemoryPermissionRepository : PermissionRepository {
    private val permissions = mutableListOf<Permission>()

    override fun find(
        snippetId: UUID,
        userId: String,
    ): Permission? = permissions.find { it.id == PermissionId(snippetId, userId) }

    override fun findByUser(userId: String): List<Permission> = permissions.filter { it.id.userId == userId }

    override fun findByUserAndRole(
        userId: String,
        role: Role,
    ): List<Permission> = findByUser(userId).filter { it.role == role }

    override fun findBySnippet(snippetId: UUID): List<Permission> = permissions.filter { it.id.snippetId == snippetId }

    override fun insertIfAbsent(
        snippetId: UUID,
        userId: String,
        role: String,
    ): Int {
        if (find(snippetId, userId) != null) return 0
        permissions += Permission(PermissionId(snippetId, userId), Role.valueOf(role), Instant.now())
        return 1
    }

    override fun deleteBySnippet(snippetId: UUID): Int {
        val before = permissions.size
        permissions.removeAll { it.id.snippetId == snippetId }
        return before - permissions.size
    }
}

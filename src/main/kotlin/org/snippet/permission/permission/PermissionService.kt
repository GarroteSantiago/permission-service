package org.snippet.permission.permission

import org.snippet.permission.common.error.ConflictException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PermissionService(
    private val repository: PermissionRepository,
) {
    @Transactional
    fun grant(
        snippetId: UUID,
        userId: String,
        role: Role,
    ) {
        if (repository.insertIfAbsent(snippetId, userId, role.name) == 0) {
            throw ConflictException("User $userId already has a permission on snippet $snippetId")
        }
    }

    @Transactional(readOnly = true)
    fun check(
        snippetId: UUID,
        userId: String,
        action: Action,
    ): CheckResponse {
        val role = repository.find(snippetId, userId)?.role
        return CheckResponse(allowed = role?.allows(action) ?: false, role = role)
    }

    @Transactional(readOnly = true)
    fun accessibleSnippets(
        userId: String,
        relation: Relation,
    ): List<Permission> =
        relation.role
            ?.let { repository.findByUserAndRole(userId, it) }
            ?: repository.findByUser(userId)

    @Transactional(readOnly = true)
    fun snippetPermissions(snippetId: UUID): List<Permission> = repository.findBySnippet(snippetId)

    @Transactional
    fun revokeAll(snippetId: UUID) {
        repository.deleteBySnippet(snippetId)
    }
}

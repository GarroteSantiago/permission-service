package org.snippet.permission.permission

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import java.util.UUID

interface PermissionRepository : Repository<Permission, PermissionId> {
    @Query("select p from Permission p where p.id.snippetId = :snippetId and p.id.userId = :userId")
    fun find(
        snippetId: UUID,
        userId: String,
    ): Permission?

    @Query("select p from Permission p where p.id.userId = :userId order by p.createdAt, p.id.snippetId")
    fun findByUser(userId: String): List<Permission>

    @Query(
        "select p from Permission p where p.id.userId = :userId and p.role = :role order by p.createdAt, p.id.snippetId",
    )
    fun findByUserAndRole(
        userId: String,
        role: Role,
    ): List<Permission>

    @Query("select p from Permission p where p.id.snippetId = :snippetId order by p.createdAt, p.id.userId")
    fun findBySnippet(snippetId: UUID): List<Permission>

    /** Returns 0 when the user already has a permission on the snippet. Atomic, so concurrent grants can't both win. */
    @Modifying
    @Query(
        "insert into permission (snippet_id, user_id, role) values (:snippetId, :userId, :role) on conflict do nothing",
        nativeQuery = true,
    )
    fun insertIfAbsent(
        snippetId: UUID,
        userId: String,
        role: String,
    ): Int

    @Modifying
    @Query("delete from Permission p where p.id.snippetId = :snippetId")
    fun deleteBySnippet(snippetId: UUID): Int
}

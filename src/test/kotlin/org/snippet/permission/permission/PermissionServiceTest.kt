package org.snippet.permission.permission

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.snippet.permission.common.error.ConflictException
import java.util.UUID
import kotlin.test.assertEquals

class PermissionServiceTest {
    private val service = PermissionService(InMemoryPermissionRepository())
    private val snippet = UUID.randomUUID()

    @Test
    fun `owner can read and write`() {
        service.grant(snippet, "alice", Role.OWNER)

        assertEquals(CheckResponse(true, Role.OWNER), service.check(snippet, "alice", Action.READ))
        assertEquals(CheckResponse(true, Role.OWNER), service.check(snippet, "alice", Action.WRITE))
    }

    @Test
    fun `reader can read but not write`() {
        service.grant(snippet, "bob", Role.READ)

        assertEquals(CheckResponse(true, Role.READ), service.check(snippet, "bob", Action.READ))
        assertEquals(CheckResponse(false, Role.READ), service.check(snippet, "bob", Action.WRITE))
    }

    @Test
    fun `without a permission nothing is allowed and there is no role`() {
        service.grant(snippet, "alice", Role.OWNER)

        assertEquals(CheckResponse(false, null), service.check(snippet, "bob", Action.READ))
        assertEquals(CheckResponse(false, null), service.check(UUID.randomUUID(), "alice", Action.READ))
    }

    @Test
    fun `a second permission for the same user and snippet is a conflict`() {
        service.grant(snippet, "alice", Role.OWNER)

        assertThrows<ConflictException> { service.grant(snippet, "alice", Role.READ) }
        assertEquals(Role.OWNER, service.check(snippet, "alice", Action.READ).role)
    }

    @Test
    fun `lists accessible snippets by relation`() {
        val shared = UUID.randomUUID()
        service.grant(snippet, "alice", Role.OWNER)
        service.grant(shared, "bob", Role.OWNER)
        service.grant(shared, "alice", Role.READ)

        assertEquals(listOf(snippet), service.accessibleSnippets("alice", Relation.owned).map { it.id.snippetId })
        assertEquals(listOf(shared), service.accessibleSnippets("alice", Relation.shared).map { it.id.snippetId })
        assertEquals(
            setOf(snippet to Role.OWNER, shared to Role.READ),
            service.accessibleSnippets("alice", Relation.all).map { it.id.snippetId to it.role }.toSet(),
        )
        assertEquals(emptyList(), service.accessibleSnippets("carol", Relation.all))
    }

    @Test
    fun `revoking a snippet removes every permission on it and nothing else`() {
        val other = UUID.randomUUID()
        service.grant(snippet, "alice", Role.OWNER)
        service.grant(snippet, "bob", Role.READ)
        service.grant(other, "alice", Role.OWNER)

        service.revokeAll(snippet)
        service.revokeAll(snippet)

        assertEquals(emptyList(), service.snippetPermissions(snippet))
        assertEquals(listOf("alice"), service.snippetPermissions(other).map { it.id.userId })
    }
}

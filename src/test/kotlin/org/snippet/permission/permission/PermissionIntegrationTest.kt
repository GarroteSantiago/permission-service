package org.snippet.permission.permission

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/** The whole flow against a real Postgres: migration, schema validation and the repository queries. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Needs Docker for Postgres: skipped where there is none, runs in CI.
@Testcontainers(disabledWithoutDocker = true)
class PermissionIntegrationTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    // The database is shared between tests, so every test uses its own ids.
    private val snippet = UUID.randomUUID()
    private val alice = "auth0|alice-${UUID.randomUUID()}"
    private val bob = "auth0|bob-${UUID.randomUUID()}"

    private fun grant(
        snippetId: UUID,
        userId: String,
        role: Role,
    ) = mockMvc.post("/permissions") {
        with(jwt())
        contentType = MediaType.APPLICATION_JSON
        content = """{"snippetId":"$snippetId","userId":"$userId","role":"$role"}"""
    }

    private fun check(
        userId: String,
        action: Action,
    ) = mockMvc.get("/permissions/check") {
        with(jwt())
        param("snippetId", "$snippet")
        param("userId", userId)
        param("action", "$action")
    }

    @Test
    fun `grant, check and revoke`() {
        grant(snippet, alice, Role.OWNER).andExpect { status { isCreated() } }
        grant(snippet, bob, Role.READ).andExpect { status { isCreated() } }

        check(alice, Action.WRITE).andExpect { content { json("""{"allowed":true,"role":"OWNER"}""", JsonCompareMode.STRICT) } }
        check(bob, Action.WRITE).andExpect { content { json("""{"allowed":false,"role":"READ"}""", JsonCompareMode.STRICT) } }
        check(bob, Action.READ).andExpect { content { json("""{"allowed":true,"role":"READ"}""", JsonCompareMode.STRICT) } }

        mockMvc.delete("/permissions/snippets/$snippet") { with(jwt()) }.andExpect { status { isNoContent() } }
        mockMvc.delete("/permissions/snippets/$snippet") { with(jwt()) }.andExpect { status { isNoContent() } }

        check(alice, Action.READ).andExpect { content { json("""{"allowed":false,"role":null}""", JsonCompareMode.STRICT) } }
        mockMvc.get("/permissions/snippets/$snippet") { with(jwt()) }.andExpect { content { json("[]", JsonCompareMode.STRICT) } }
    }

    @Test
    fun `a second permission for the same user and snippet is a conflict and keeps the first one`() {
        grant(snippet, alice, Role.OWNER).andExpect { status { isCreated() } }

        grant(snippet, alice, Role.READ).andExpect {
            status { isConflict() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        }
        check(alice, Action.WRITE).andExpect { content { json("""{"allowed":true,"role":"OWNER"}""", JsonCompareMode.STRICT) } }
    }

    @Test
    fun `lists snippets by relation and permissions by snippet`() {
        val shared = UUID.randomUUID()
        grant(snippet, alice, Role.OWNER)
        grant(shared, bob, Role.OWNER)
        grant(shared, alice, Role.READ)

        val owned = """{"snippetId":"$snippet","role":"OWNER"}"""
        val read = """{"snippetId":"$shared","role":"READ"}"""
        mapOf(
            "owned" to "[$owned]",
            "shared" to "[$read]",
            "all" to "[$owned,$read]",
        ).forEach { (relation, snippets) ->
            mockMvc
                .get("/permissions/snippets") {
                    with(jwt())
                    param("userId", alice)
                    param("relation", relation)
                }.andExpect { content { json("""{"snippets":$snippets}""", JsonCompareMode.LENIENT) } }
        }

        mockMvc.get("/permissions/snippets/$shared") { with(jwt()) }.andExpect {
            content {
                json(
                    """
                    [{"snippetId":"$shared","userId":"$bob","role":"OWNER"},
                     {"snippetId":"$shared","userId":"$alice","role":"READ"}]
                    """,
                    JsonCompareMode.LENIENT,
                )
            }
        }
    }
}

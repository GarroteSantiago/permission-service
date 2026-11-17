package org.snippet.permission.permission

import org.junit.jupiter.api.Test
import org.snippet.permission.common.security.SecurityConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/** The HTTP contract, backed by the real service over an in-memory repository. */
@WebMvcTest(controllers = [PermissionController::class])
@Import(SecurityConfig::class, PermissionService::class, InMemoryPermissionRepository::class)
@ActiveProfiles("test")
class PermissionControllerTest(
    @param:Autowired private val mockMvc: MockMvc,
) {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    // The context (and its repository) is shared between tests, so every test uses its own ids.
    private val snippet = UUID.randomUUID()
    private val alice = "auth0|alice-${UUID.randomUUID()}"
    private val bob = "auth0|bob-${UUID.randomUUID()}"

    private fun grant(body: String): ResultActionsDsl =
        mockMvc.post("/permissions") {
            with(jwt())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun grant(
        snippetId: UUID,
        userId: String,
        role: Role,
    ) = grant("""{"snippetId":"$snippetId","userId":"$userId","role":"$role"}""")

    private fun check(vararg params: Pair<String, String>): ResultActionsDsl =
        mockMvc.get("/permissions/check") {
            with(jwt())
            params.forEach { (name, value) -> param(name, value) }
        }

    private fun ResultActionsDsl.andExpectProblem(status: Int) =
        andExpect {
            status { isEqualTo(status) }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(status) }
        }

    @Test
    fun `requires a token`() {
        mockMvc.get("/permissions/check") { param("snippetId", "$snippet") }.andExpect { status { isUnauthorized() } }
        mockMvc.delete("/permissions/snippets/$snippet").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `grant returns 201 with the permission`() {
        grant(snippet, alice, Role.OWNER).andExpect {
            status { isCreated() }
            content { json("""{"snippetId":"$snippet","userId":"$alice","role":"OWNER"}""", JsonCompareMode.STRICT) }
        }
    }

    @Test
    fun `grant returns 409 when the user already has a permission on the snippet`() {
        grant(snippet, alice, Role.OWNER).andExpect { status { isCreated() } }

        grant(snippet, alice, Role.READ).andExpectProblem(409)
    }

    @Test
    fun `grant rejects malformed bodies with 400`() {
        grant("""{"snippetId":"$snippet","userId":"$alice","role":"WRITE"}""").andExpectProblem(400)
        grant("""{"snippetId":"not-a-uuid","userId":"$alice","role":"OWNER"}""").andExpectProblem(400)
        grant("""{"snippetId":"$snippet","role":"OWNER"}""").andExpectProblem(400)
        grant("""{"snippetId":"$snippet","userId":"$alice","role":null}""").andExpectProblem(400)
        grant("not json").andExpectProblem(400)
    }

    @Test
    fun `check follows the role rules`() {
        grant(snippet, alice, Role.OWNER)
        grant(snippet, bob, Role.READ)

        fun expect(
            userId: String,
            action: Action,
            json: String,
        ) = check("snippetId" to "$snippet", "userId" to userId, "action" to "$action").andExpect {
            status { isOk() }
            content { json(json, JsonCompareMode.STRICT) }
        }

        expect(alice, Action.READ, """{"allowed":true,"role":"OWNER"}""")
        expect(alice, Action.WRITE, """{"allowed":true,"role":"OWNER"}""")
        expect(bob, Action.READ, """{"allowed":true,"role":"READ"}""")
        expect(bob, Action.WRITE, """{"allowed":false,"role":"READ"}""")
        expect("auth0|nobody", Action.READ, """{"allowed":false,"role":null}""")
    }

    @Test
    fun `check rejects missing or invalid parameters with 400`() {
        check("snippetId" to "$snippet", "userId" to alice).andExpectProblem(400)
        check("snippetId" to "$snippet", "action" to "READ").andExpectProblem(400)
        check("userId" to alice, "action" to "READ").andExpectProblem(400)
        check("snippetId" to "$snippet", "userId" to alice, "action" to "DELETE").andExpectProblem(400)
        check("snippetId" to "nope", "userId" to alice, "action" to "READ").andExpectProblem(400)
    }

    @Test
    fun `lists accessible snippets filtered by relation`() {
        val shared = UUID.randomUUID()
        grant(snippet, alice, Role.OWNER)
        grant(shared, bob, Role.OWNER)
        grant(shared, alice, Role.READ)

        fun expect(
            relation: String?,
            json: String,
        ) = mockMvc
            .get("/permissions/snippets") {
                with(jwt())
                param("userId", alice)
                relation?.let { param("relation", it) }
            }.andExpect {
                status { isOk() }
                content { json(json, JsonCompareMode.LENIENT) }
            }

        val owned = """{"snippetId":"$snippet","role":"OWNER"}"""
        val read = """{"snippetId":"$shared","role":"READ"}"""
        expect("owned", """{"snippets":[$owned]}""")
        expect("shared", """{"snippets":[$read]}""")
        expect("all", """{"snippets":[$owned,$read]}""")
        expect(null, """{"snippets":[$owned,$read]}""")
    }

    @Test
    fun `accessible snippets of a user without permissions is an empty list`() {
        mockMvc
            .get("/permissions/snippets") {
                with(jwt())
                param("userId", "auth0|nobody")
            }.andExpect {
                status { isOk() }
                content { json("""{"snippets":[]}""", JsonCompareMode.STRICT) }
            }
    }

    @Test
    fun `accessible snippets rejects missing or invalid parameters with 400`() {
        mockMvc.get("/permissions/snippets") { with(jwt()) }.andExpectProblem(400)
        mockMvc
            .get("/permissions/snippets") {
                with(jwt())
                param("userId", alice)
                param("relation", "OWNED")
            }.andExpectProblem(400)
    }

    @Test
    fun `lists who has access to a snippet`() {
        grant(snippet, alice, Role.OWNER)
        grant(snippet, bob, Role.READ)

        mockMvc.get("/permissions/snippets/$snippet") { with(jwt()) }.andExpect {
            status { isOk() }
            content {
                json(
                    """
                    [{"snippetId":"$snippet","userId":"$alice","role":"OWNER"},
                     {"snippetId":"$snippet","userId":"$bob","role":"READ"}]
                    """,
                    JsonCompareMode.LENIENT,
                )
            }
        }
        mockMvc.get("/permissions/snippets/${UUID.randomUUID()}") { with(jwt()) }.andExpect {
            status { isOk() }
            content { json("[]", JsonCompareMode.STRICT) }
        }
        mockMvc.get("/permissions/snippets/nope") { with(jwt()) }.andExpectProblem(400)
    }

    @Test
    fun `revoking all permissions is idempotent`() {
        grant(snippet, alice, Role.OWNER)
        grant(snippet, bob, Role.READ)

        repeat(2) {
            mockMvc.delete("/permissions/snippets/$snippet") { with(jwt()) }.andExpect { status { isNoContent() } }
        }
        check("snippetId" to "$snippet", "userId" to alice, "action" to "READ").andExpect {
            content { json("""{"allowed":false,"role":null}""", JsonCompareMode.STRICT) }
        }
        mockMvc.delete("/permissions/snippets/nope") { with(jwt()) }.andExpectProblem(400)
    }
}

package org.snippet.permission.permission

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Internal API, only called by the Snippet Service. Contract: `contracts/permission-service.openapi.yaml`. */
@RestController
@RequestMapping("/permissions")
class PermissionController(
    private val service: PermissionService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun grant(
        @RequestBody permission: PermissionDto,
    ): PermissionDto {
        service.grant(permission.snippetId, permission.userId, permission.role)
        return permission
    }

    @GetMapping("/check")
    fun check(
        @RequestParam snippetId: UUID,
        @RequestParam userId: String,
        @RequestParam action: Action,
    ): CheckResponse = service.check(snippetId, userId, action)

    @GetMapping("/snippets")
    fun accessibleSnippets(
        @RequestParam userId: String,
        @RequestParam(defaultValue = "all") relation: Relation,
    ): AccessibleSnippetsResponse =
        AccessibleSnippetsResponse(
            service.accessibleSnippets(userId, relation).map { AccessibleSnippet(it.id.snippetId, it.role) },
        )

    @GetMapping("/snippets/{snippetId}")
    fun snippetPermissions(
        @PathVariable snippetId: UUID,
    ): List<PermissionDto> = service.snippetPermissions(snippetId).map(PermissionDto::from)

    @DeleteMapping("/snippets/{snippetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeAll(
        @PathVariable snippetId: UUID,
    ) = service.revokeAll(snippetId)
}

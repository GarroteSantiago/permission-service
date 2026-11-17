package org.snippet.permission.permission

import java.util.UUID

data class PermissionDto(
    val snippetId: UUID,
    val userId: String,
    val role: Role,
) {
    companion object {
        fun from(permission: Permission) = PermissionDto(permission.id.snippetId, permission.id.userId, permission.role)
    }
}

/** `role` is null (and serialized as such) when the user has no permission on the snippet. */
data class CheckResponse(
    val allowed: Boolean,
    val role: Role?,
)

data class AccessibleSnippet(
    val snippetId: UUID,
    val role: Role,
)

data class AccessibleSnippetsResponse(
    val snippets: List<AccessibleSnippet>,
)

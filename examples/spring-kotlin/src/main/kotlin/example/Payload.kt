package example

import java.util.UUID

data class Payload(
    val version: Int,
    val userId: String?,
    val sessionId: String?,
    val parameter1: String,
    val parameter2: Int,
    val uuid: UUID,
)

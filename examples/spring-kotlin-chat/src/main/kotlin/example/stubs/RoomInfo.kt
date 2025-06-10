package example.stubs

/**
 * Represents information about a chat room.
 *
 * @param messageCount The total number of messages for this room.
 * @param uniqueUsersCount The number of unique users who have posted.
 */
data class RoomInfo(val messageCount: Int, val uniqueUsersCount: Long)

package com.spark.dating.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── User ─────────────────────────────────────────────────────────────────────

@Serializable
data class User(
    val id: String,
    val email: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("onboarding_complete") val onboardingComplete: Boolean = false,
    val role: UserRole = UserRole.USER,
    @SerialName("is_suspended") val isSuspended: Boolean = false,
    @SerialName("is_banned") val isBanned: Boolean = false,
)

@Serializable
enum class UserRole {
    @SerialName("user") USER,
    @SerialName("moderator") MODERATOR,
    @SerialName("support") SUPPORT,
    @SerialName("analyst") ANALYST,
    @SerialName("super_admin") SUPER_ADMIN,
}

// ── Profile ───────────────────────────────────────────────────────────────────

@Serializable
data class Profile(
    val id: String,                         // = user id
    @SerialName("first_name") val firstName: String = "",
    @SerialName("date_of_birth") val dateOfBirth: String = "",  // ISO date
    val age: Int = 0,                       // computed server-side
    val gender: Gender = Gender.UNSPECIFIED,
    val bio: String = "",
    val occupation: String = "",
    val education: String = "",
    val height: Int? = null,                // cm, optional
    val latitude: Double? = null,           // approximate
    val longitude: Double? = null,
    @SerialName("distance_km") val distanceKm: Double? = null, // from discovery query
    @SerialName("relationship_intent") val relationshipIntent: RelationshipIntent = RelationshipIntent.UNSPECIFIED,
    val photos: List<ProfilePhoto> = emptyList(),
    val interests: List<Interest> = emptyList(),
    val prompts: List<ProfilePrompt> = emptyList(),
    val languages: List<String> = emptyList(),
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
)

@Serializable
enum class Gender {
    @SerialName("man") MAN,
    @SerialName("woman") WOMAN,
    @SerialName("non_binary") NON_BINARY,
    @SerialName("other") OTHER,
    @SerialName("unspecified") UNSPECIFIED,
}

@Serializable
enum class RelationshipIntent {
    @SerialName("long_term") LONG_TERM,
    @SerialName("casual") CASUAL,
    @SerialName("friendship") FRIENDSHIP,
    @SerialName("unsure") UNSURE,
    @SerialName("unspecified") UNSPECIFIED,
}

// ── Profile Photo ─────────────────────────────────────────────────────────────

@Serializable
data class ProfilePhoto(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    val url: String,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String,
)

// ── Interest ──────────────────────────────────────────────────────────────────

@Serializable
data class Interest(
    val id: String,
    val name: String,
    val emoji: String = "",
    val category: String = "",
)

// ── Profile Prompt ────────────────────────────────────────────────────────────

@Serializable
data class ProfilePrompt(
    val id: String,
    val question: String,
    val answer: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

// ── Preferences ───────────────────────────────────────────────────────────────

@Serializable
data class Preferences(
    @SerialName("user_id") val userId: String,
    @SerialName("gender_preference") val genderPreference: List<Gender> = emptyList(),
    @SerialName("min_age") val minAge: Int = 18,
    @SerialName("max_age") val maxAge: Int = 99,
    @SerialName("max_distance_km") val maxDistanceKm: Int = 100,
    @SerialName("show_in_discovery") val showInDiscovery: Boolean = true,
    @SerialName("show_distance") val showDistance: Boolean = true,
    @SerialName("show_age") val showAge: Boolean = true,
)

// ── Like / Pass ───────────────────────────────────────────────────────────────

@Serializable
data class Like(
    val id: String,
    @SerialName("liker_id") val likerId: String,
    @SerialName("liked_id") val likedId: String,
    @SerialName("is_super_like") val isSuperLike: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

// ── Match ─────────────────────────────────────────────────────────────────────

@Serializable
data class Match(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user1_id") val user1Id: String,
    @SerialName("user2_id") val user2Id: String,
    @SerialName("created_at") val createdAt: String,
    val profile: Profile? = null,   // the other person's profile, joined in query
)

// ── Conversation ──────────────────────────────────────────────────────────────

@Serializable
data class Conversation(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_message") val lastMessage: Message? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    val partner: Profile? = null,   // the other person, joined in query
)

// ── Message ───────────────────────────────────────────────────────────────────

@Serializable
data class Message(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val content: String,
    @SerialName("message_type") val messageType: MessageType = MessageType.TEXT,
    @SerialName("attachment_url") val attachmentUrl: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
enum class MessageType {
    @SerialName("text") TEXT,
    @SerialName("image") IMAGE,
    @SerialName("gif") GIF,
}

// ── Report ────────────────────────────────────────────────────────────────────

@Serializable
data class Report(
    val id: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("reported_id") val reportedId: String,
    val category: ReportCategory,
    val details: String = "",
    val status: ReportStatus = ReportStatus.PENDING,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
enum class ReportCategory {
    @SerialName("harassment") HARASSMENT,
    @SerialName("spam") SPAM,
    @SerialName("fake_profile") FAKE_PROFILE,
    @SerialName("inappropriate_content") INAPPROPRIATE_CONTENT,
    @SerialName("scam_fraud") SCAM_FRAUD,
    @SerialName("impersonation") IMPERSONATION,
    @SerialName("underage_concern") UNDERAGE_CONCERN,
    @SerialName("other") OTHER,
}

@Serializable
enum class ReportStatus {
    @SerialName("pending") PENDING,
    @SerialName("under_review") UNDER_REVIEW,
    @SerialName("resolved") RESOLVED,
    @SerialName("dismissed") DISMISSED,
}

// ── Subscription ──────────────────────────────────────────────────────────────

@Serializable
data class Subscription(
    val id: String,
    @SerialName("user_id") val userId: String,
    val tier: SubscriptionTier,
    val status: SubscriptionStatus,
    @SerialName("expires_at") val expiresAt: String?,
    @SerialName("purchase_token") val purchaseToken: String,
    @SerialName("product_id") val productId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
enum class SubscriptionTier {
    @SerialName("free") FREE,
    @SerialName("plus") PLUS,
    @SerialName("gold") GOLD,
    @SerialName("platinum") PLATINUM,
}

@Serializable
enum class SubscriptionStatus {
    @SerialName("active") ACTIVE,
    @SerialName("cancelled") CANCELLED,
    @SerialName("expired") EXPIRED,
    @SerialName("grace_period") GRACE_PERIOD,
    @SerialName("on_hold") ON_HOLD,
}

// ── Notification ──────────────────────────────────────────────────────────────

@Serializable
data class SparkNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
enum class NotificationType {
    @SerialName("new_match") NEW_MATCH,
    @SerialName("new_message") NEW_MESSAGE,
    @SerialName("new_like") NEW_LIKE,
    @SerialName("new_super_like") NEW_SUPER_LIKE,
    @SerialName("security_alert") SECURITY_ALERT,
    @SerialName("moderation") MODERATION,
}

// ── Discovery result ──────────────────────────────────────────────────────────

@Serializable
data class DiscoveryResult(
    val profiles: List<Profile>,
    val cursor: String? = null,     // for pagination
)

// ── API response wrapper ──────────────────────────────────────────────────────

@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val error: ApiError, val httpCode: Int = 0) : Result<Nothing>
    data object Loading : Result<Nothing>
}

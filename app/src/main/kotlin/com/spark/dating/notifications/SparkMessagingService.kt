package com.spark.dating.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.spark.dating.MainActivity
import com.spark.dating.R
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SparkMessagingService : FirebaseMessagingService() {

    @Inject lateinit var postgrest: Postgrest

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: "general"
        val title = message.notification?.title ?: data["title"] ?: "Spark"
        val body = message.notification?.body ?: data["body"] ?: ""

        showNotification(
            type = type,
            title = title,
            body = body,
            conversationId = data["conversation_id"],
        )
    }

    override fun onNewToken(token: String) {
        // Register the new FCM token with our backend
        serviceScope.launch {
            try {
                val userId = com.google.firebase.auth.FirebaseAuth.getInstance()
                    .currentUser?.uid ?: return@launch
                postgrest["devices"].upsert(
                    mapOf(
                        "user_id" to userId,
                        "fcm_token" to token,
                        "platform" to "android",
                    )
                )
            } catch (_: Exception) { /* non-fatal */ }
        }
    }

    private fun showNotification(
        type: String,
        title: String,
        body: String,
        conversationId: String?,
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = getChannelId(type)

        createNotificationChannels(notificationManager)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            conversationId?.let { putExtra("conversation_id", it) }
            putExtra("notification_type", type)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }

    private fun getChannelId(type: String) = when (type) {
        "new_message" -> CHANNEL_MESSAGES
        "new_match"   -> CHANNEL_MATCHES
        "new_like"    -> CHANNEL_LIKES
        else          -> CHANNEL_GENERAL
    }

    private fun createNotificationChannels(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channels = listOf(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "New message notifications" },
            NotificationChannel(CHANNEL_MATCHES, "Matches", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "New match notifications" },
            NotificationChannel(CHANNEL_LIKES, "Likes", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Like notifications (Premium)" },
            NotificationChannel(CHANNEL_GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "General notifications" },
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        private const val CHANNEL_MESSAGES = "spark_messages"
        private const val CHANNEL_MATCHES  = "spark_matches"
        private const val CHANNEL_LIKES    = "spark_likes"
        private const val CHANNEL_GENERAL  = "spark_general"
    }
}

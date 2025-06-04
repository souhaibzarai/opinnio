package com.example.opinnio.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Notification(
  var id: String = "",
  val commentId: String? = null,
  @ServerTimestamp val createdAt: Date? = null,
  val fromUserId: String = "",
  val postId: String? = null,
  val toUserId: String = "",
  val type: String = "",
  val read: Boolean = false,
  var fromUserName: String? = null,
  var fromUserAvatarUrl: String? = null,
)

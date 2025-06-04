package com.example.opinnio.adapters

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.opinnio.R
import com.example.opinnio.activities.CommentsActivity
import com.example.opinnio.activities.PostDetailsActivity
import com.example.opinnio.models.Notification
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NotificationAdapter :
  ListAdapter<Notification, NotificationAdapter.ViewHolder>(NotificationDiffCallback()) {

  private val firestore = FirebaseFirestore.getInstance()
  private val userCache = mutableMapOf<String, Pair<String, String?>>()

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val notificationText: TextView = itemView.findViewById(R.id.notification_text)
    val notificationTime: TextView = itemView.findViewById(R.id.notification_time)
    val avatarView: ImageView = itemView.findViewById(R.id.notificationAvatar)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view =
      LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val notification = getItem(position)

    // Initially set placeholders
    holder.notificationText.text = "Someone performed an action"
    Glide.with(holder.avatarView.context).load(R.drawable.default_avatar).circleCrop()
      .into(holder.avatarView)

    // If username & avatarUrl already available
    if (!notification.fromUserName.isNullOrBlank()) {
      bindNotification(
        holder, notification.fromUserName!!, notification.fromUserAvatarUrl, notification
      )
    } else {
      // Fetch from Firestore (and cache!)
      fetchUserData(notification.fromUserId) { username, imageUrl ->
        if (holder.adapterPosition == position) {
          bindNotification(holder, username ?: "Someone", imageUrl, notification)
        }
      }
    }

    // Set timestamp dynamically
    notification.createdAt?.let {
      val timestamp = Timestamp(it)
      holder.notificationTime.text = formatTimestamp(timestamp)
    } ?: run {
      holder.notificationTime.text = "Just now"
    }

    // Click to view post or comment
    holder.itemView.setOnClickListener {
      val context = holder.itemView.context
      val intent = if (notification.type == "comment") {
        Intent(context, CommentsActivity::class.java).apply {
          putExtra("postId", notification.postId)
        }
      } else {
        Intent(context, PostDetailsActivity::class.java).apply {
          putExtra("postId", notification.postId)
        }
      }
      context.startActivity(intent)
    }
  }

  private fun bindNotification(
    holder: ViewHolder, userName: String, avatarUrl: String?, notification: Notification
  ) {
    holder.notificationText.text = when (notification.type) {
      "like" -> "$userName liked your post"
      "comment" -> "$userName commented on your post"
      else -> "$userName performed an action: ${notification.type}"
    }

    if (!avatarUrl.isNullOrBlank()) {
      Glide.with(holder.avatarView.context).load(avatarUrl).circleCrop()
        .placeholder(R.drawable.default_avatar).into(holder.avatarView)
    } else {
      holder.avatarView.setImageResource(R.drawable.default_avatar)
    }
  }

  private fun fetchUserData(userId: String, callback: (String?, String?) -> Unit) {
    if (userCache.containsKey(userId)) {
      Log.d("NotificationAdapter", "Using cached data for user $userId")
      callback(userCache[userId]?.first, userCache[userId]?.second)
      return
    }

    firestore.collection("users").document(userId).get().addOnSuccessListener { document ->
      if (document.exists()) {
        val username = document.getString("username") ?: "Someone"
        val avatarUrl = document.getString("imgUrl")
        userCache[userId] = Pair(username, avatarUrl)
        callback(username, avatarUrl)
      } else {
        Log.w("NotificationAdapter", "User $userId does not exist")
        userCache[userId] = Pair("Someone", null)
        callback("Someone", null)
      }
    }.addOnFailureListener { exception ->
      Log.e("NotificationAdapter", "Failed to fetch user data for $userId", exception)
      callback("Someone", null)
    }
  }

  private fun formatTimestamp(timestamp: Timestamp): String {
    val now = System.currentTimeMillis()
    val postTime = timestamp.toDate().time
    val diff = now - postTime

    return when {
      diff < 60_000 -> "Just now"
      diff < 3600_000 -> "${diff / 60_000}m ago"
      diff < 86400_000 -> "${diff / 3600_000}h ago"
      else -> "${diff / 86400_000}d ago"
    }
  }
}



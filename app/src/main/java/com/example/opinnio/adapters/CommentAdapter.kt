package com.example.opinnio.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.opinnio.R
import com.example.opinnio.models.Comment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CommentAdapter(
  private var comments: MutableList<Comment>,
  private val onEditClicked: (Comment) -> Unit,
  private val onDeleteClicked: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

  private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
  private val firestore = FirebaseFirestore.getInstance()
  private val userCache = mutableMapOf<String, Pair<String, String?>>()

  class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val commentContent: TextView = itemView.findViewById(R.id.commentContent)
    val commentTimestamp: TextView = itemView.findViewById(R.id.commentTimestamp)
    val commentAuthor: TextView = itemView.findViewById(R.id.commentAuthor)
    val commentAvatar: ImageView = itemView.findViewById(R.id.commentAvatar)
    val commentActions: ViewGroup = itemView.findViewById(R.id.commentActions)
    val editButton: Button = itemView.findViewById(R.id.editCommentButton)
    val deleteButton: Button = itemView.findViewById(R.id.deleteCommentButton)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
    return CommentViewHolder(view)
  }

  override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
    val comment = comments[position]
    holder.commentContent.text = comment.content

    val updatedLabel = if (comment.updatedAt != null && comment.updatedAt != comment.createdAt) {
      " (updated)"
    } else {
      ""
    }

    // Set timestamp (use updatedAt if available, otherwise createdAt)
    val timestamp = comment.updatedAt ?: comment.createdAt
    val formattedTime = if (timestamp != null) formatTimestamp(timestamp) else "Just now"

    "$formattedTime$updatedLabel".also { holder.commentTimestamp.text = it }
    // Set default values
    holder.commentAuthor.text = "By User"
    Glide.with(holder.commentAvatar.context).load(R.drawable.default_avatar).circleCrop()
      .into(holder.commentAvatar)

    // Fetch username and imageUrl
    fetchUserData(comment.authorId) { username, imageUrl ->
      // Check if the View is still bound to the same position to avoid recycling issues
      if (holder.adapterPosition == position) {
        val displayName = username ?: "User"
        holder.commentAuthor.text = displayName
        if (imageUrl != null && imageUrl.isNotBlank()) {
          Log.d("CommentAdapter", "Loading image for user ${comment.authorId}: $imageUrl")
          Glide.with(holder.commentAvatar.context).load(imageUrl).circleCrop()
            .placeholder(R.drawable.default_avatar).error(R.drawable.default_avatar)
            .into(holder.commentAvatar)
        } else {
          Log.d("CommentAdapter", "No imageUrl for user ${comment.authorId}")
        }
        // Update content description for accessibility
        holder.commentAvatar.contentDescription = "Profile picture of $displayName"
      }
    }

    // Show/hide edit and delete buttons
    if (currentUserId == comment.authorId) {
      holder.commentActions.visibility = View.VISIBLE
      holder.editButton.setOnClickListener { onEditClicked(comment) }
      holder.deleteButton.setOnClickListener { onDeleteClicked(comment) }
    } else {
      holder.commentActions.visibility = View.GONE
    }
  }

  override fun getItemCount(): Int = comments.size

  private fun fetchUserData(userId: String, callback: (String?, String?) -> Unit) {
    // Check cache first
    if (userCache.containsKey(userId)) {
      Log.d("CommentAdapter", "Using cached data for user $userId")
      callback(userCache[userId]?.first, userCache[userId]?.second)
      return
    }

    // Fetch from Firestore
    firestore.collection("users").document(userId).get().addOnSuccessListener { document ->
        if (document.exists()) {
          val username = document.getString("username")?.takeIf { it.isNotBlank() } ?: "User"
          val imageUrl = document.getString("imgUrl")
          Log.d("CommentAdapter", "Fetched user $userId: username=$username, imageUrl=$imageUrl")
          userCache[userId] = Pair(username, imageUrl)
          callback(username, imageUrl)
        } else {
          Log.w("CommentAdapter", "User document $userId does not exist")
          userCache[userId] = Pair("User", null)
          callback("User", null)
        }
      }.addOnFailureListener { exception ->
        Log.e("CommentAdapter", "Failed to fetch user $userId", exception)
        callback("User", null)
      }
  }

  fun updateComments(newComments: List<Comment>) {
    comments.clear()
    comments.addAll(newComments)
    notifyDataSetChanged()
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
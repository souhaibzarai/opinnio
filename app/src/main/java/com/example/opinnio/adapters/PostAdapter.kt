package com.example.opinnio.adapters

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.example.opinnio.models.Post
import com.example.opinnio.R
import com.example.opinnio.activities.PostDetailsActivity

class PostAdapter(
  private var posts: MutableList<Post>,
  private val onLikeClicked: (Post) -> Unit,
  private val onCommentClicked: (Post) -> Unit,
  private val onEditClicked: (Post) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

  private val firestore = FirebaseFirestore.getInstance()
  private val userCache = mutableMapOf<String, Pair<String, String?>>() // Cache username and imgUrl

  class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val userAvatar: ShapeableImageView = itemView.findViewById(R.id.userAvatar)
    val userName: TextView = itemView.findViewById(R.id.userName)
    val postDate: TextView = itemView.findViewById(R.id.postDate)
    val postTitle: TextView = itemView.findViewById(R.id.postTitle)
    val postImage: ShapeableImageView = itemView.findViewById(R.id.postImage)
    val postBody: TextView = itemView.findViewById(R.id.postBody)
    val likeContainer: LinearLayout = itemView.findViewById(R.id.likeContainer)
    val commentContainer: LinearLayout = itemView.findViewById(R.id.commentContainer)
    val likeButton: ImageView = itemView.findViewById(R.id.likeButton)
    val likeCount: TextView = itemView.findViewById(R.id.likeCount)
    val commentButton: ImageView = itemView.findViewById(R.id.commentButton)
    val commentCount: TextView = itemView.findViewById(R.id.commentCount)
    val menuButton: ImageButton = itemView.findViewById(R.id.editPostButton)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
    return PostViewHolder(view)
  }

  override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
    val post = posts[position]

    holder.itemView.setOnClickListener {
      val context = holder.itemView.context
      val intent = Intent(context, PostDetailsActivity::class.java)
      intent.putExtra("postId", post.id)
      context.startActivity(intent)
    }

    // Bind basic post data
    holder.postTitle.text = post.title
    holder.postBody.text = post.body

    // Format and display date
    holder.postDate.text = if (post.createdAt != null) {
      formatTimestamp(post.createdAt)
    } else {
      "Just now"
    }

    // Set default values for user data
    holder.userName.text = "User"
    Glide.with(holder.userAvatar.context)
      .load(R.drawable.default_avatar)
      .circleCrop()
      .into(holder.userAvatar)

    // Fetch user data from Firestore
    fetchUserData(post.authorId) { username, imgUrl ->
      if (holder.adapterPosition == position) { // Prevent View recycling issues
        val displayName = username ?: "User"
        holder.userName.text = displayName
        if (imgUrl != null && imgUrl.isNotBlank()) {
          Log.d("PostAdapter", "Loading user avatar for user ${post.authorId}: $imgUrl")
          Glide.with(holder.userAvatar.context)
            .load(imgUrl)
            .circleCrop()
            .placeholder(R.drawable.default_avatar)
            .error(R.drawable.default_avatar)
            .into(holder.userAvatar)
        } else {
          Log.d("PostAdapter", "No imgUrl for user ${post.authorId}, using default")
          Glide.with(holder.userAvatar.context)
            .load(R.drawable.default_avatar)
            .circleCrop()
            .into(holder.userAvatar)
        }
        holder.userAvatar.contentDescription = "Profile picture of $displayName"
      }
    }

    // Load post image if available
    if (!post.imageUrl.isNullOrEmpty()) {
      holder.postImage.visibility = View.VISIBLE
      Log.d("PostAdapter", "Loading post image for post ${post.id}: ${post.imageUrl}")
      Glide.with(holder.postImage.context)
        .load(post.imageUrl)
        .centerCrop()
        .placeholder(R.drawable.loading)
        .error(R.drawable.image_error)
        .into(holder.postImage)
    } else {
      Log.d("PostAdapter", "No imageUrl for post ${post.id}, hiding postImage")
      holder.postImage.visibility = View.GONE
    }

    // Set click listeners for like, comment, and edit
    holder.likeContainer.setOnClickListener { onLikeClicked(post) }
    holder.commentContainer.setOnClickListener { onCommentClicked(post) }

    // Show menu button only for post author
    val currentUserId = getCurrentUserId()
    holder.menuButton.visibility =
      if (post.authorId == currentUserId && currentUserId.isNotEmpty()) {
        View.VISIBLE
      } else {
        View.GONE
      }
    holder.menuButton.setOnClickListener { onEditClicked(post) }

    // Update counts with real data
    holder.likeCount.text = post.likeCount.toString()

    // Fetch real comment count
    fetchCommentCount(post.id, holder.commentCount) { commentCount ->
      if (holder.adapterPosition == position) {
        holder.commentCount.text = commentCount.toString()
      }
    }

    // Update like button state
    updateLikeButtonState(post, holder) { isLiked ->
      if (holder.adapterPosition == position) {
        holder.likeButton.setImageResource(
          if (isLiked) R.drawable.baseline_favorite_24
          else R.drawable.baseline_favorite_border_24
        )
      }
    }
  }

  override fun getItemCount(): Int = posts.size

  fun updatePosts(newPosts: List<Post>) {
    posts.clear()
    posts.addAll(newPosts)
    notifyDataSetChanged()
  }

  private fun fetchUserData(authorId: String, callback: (String?, String?) -> Unit) {
    if (authorId.isEmpty()) {
      callback("Anonymous", null)
      return
    }

    // Check cache first
    if (userCache.containsKey(authorId)) {
      Log.d("PostAdapter", "Using cached data for user $authorId: ${userCache[authorId]}")
      callback(userCache[authorId]?.first, userCache[authorId]?.second)
      return
    }

    // Fetch from Firestore
    firestore.collection("users").document(authorId)
      .get()
      .addOnSuccessListener { document ->
        if (document.exists()) {
          val username = document.getString("username")?.takeIf { it.isNotBlank() } ?: "User"
          val imgUrl = document.getString("imgUrl") // Match CommentAdapter field name
          Log.d("PostAdapter", "Fetched user $authorId: username=$username, imgUrl=$imgUrl")
          userCache[authorId] = Pair(username, imgUrl)
          callback(username, imgUrl)
        } else {
          Log.w("PostAdapter", "User document $authorId does not exist")
          userCache[authorId] = Pair("User", null)
          callback("User", null)
        }
      }
      .addOnFailureListener { e ->
        Log.e("PostAdapter", "Error fetching user data for $authorId: ${e.message}")
        callback("User", null)
      }
  }

  private fun fetchCommentCount(
    postId: String,
    commentCountView: TextView,
    callback: (Int) -> Unit
  ) {
    if (postId.isEmpty()) {
      commentCountView.text = "0"
      callback(0)
      return
    }

    firestore.collection("posts").document(postId).collection("comments")
      .get()
      .addOnSuccessListener { querySnapshot ->
        val count = querySnapshot.size()
        callback(count)
      }
      .addOnFailureListener { e ->
        Log.e("PostAdapter", "Error fetching comment count: ${e.message}")
        commentCountView.text = "0"
        callback(0)
      }
  }

  private fun updateLikeButtonState(
    post: Post,
    holder: PostViewHolder,
    callback: (Boolean) -> Unit
  ) {
    val currentUserId = getCurrentUserId()
    if (currentUserId.isEmpty()) {
      callback(false)
      return
    }

    firestore.collection("posts").document(post.id)
      .collection("likes").document(currentUserId)
      .get()
      .addOnSuccessListener { document ->
        val isLiked = document.exists() && document.getBoolean("liked") == true
        callback(isLiked)
      }
      .addOnFailureListener { e ->
        Log.e("PostAdapter", "Error checking like status: ${e.message}")
        callback(false)
      }
  }

  private fun getCurrentUserId(): String {
    return FirebaseAuth.getInstance().currentUser?.uid ?: ""
  }

  fun clearUserCacheFor(userId: String) {
    userCache.remove(userId)
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
package com.example.opinnio.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.opinnio.R
import com.example.opinnio.models.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PostDetailsActivity : AppCompatActivity() {

  private val db = FirebaseFirestore.getInstance()
  private val auth = FirebaseAuth.getInstance()
  private val TAG = "PostDetailsActivity"

  private lateinit var userAvatar: ImageView
  private lateinit var userName: TextView
  private lateinit var postTitle: TextView
  private lateinit var postBody: TextView
  private lateinit var postImage: ImageView
  private lateinit var likeButton: ImageView
  private lateinit var likeCount: TextView
  private lateinit var commentContainer: LinearLayout
  private lateinit var commentCount: TextView

  private var postId: String = ""
  private var currentPost: Post? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_post_details)

    
    val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.title = "Post Details"

    
    userAvatar = findViewById(R.id.userAvatar)
    userName = findViewById(R.id.userName)
    postTitle = findViewById(R.id.postTitle)
    postBody = findViewById(R.id.postBody)
    postImage = findViewById(R.id.postImage)
    likeButton = findViewById(R.id.likeButton)
    likeCount = findViewById(R.id.likeCount)
    commentContainer = findViewById(R.id.commentContainer)
    commentCount = findViewById(R.id.commentCount)

    
    postId = intent.getStringExtra("postId") ?: ""
    if (postId.isEmpty()) {
      Toast.makeText(this, "Invalid post", Toast.LENGTH_SHORT).show()
      finish()
      return
    }

    
    loadPostDetails()

    
    likeButton.setOnClickListener {
      currentPost?.let { post ->
        toggleLike(post)
      }
    }

    
    commentContainer.setOnClickListener {
      val intent = Intent(this, CommentsActivity::class.java)
      intent.putExtra("postId", postId)
      startActivity(intent)
    }
  }

  private fun loadPostDetails() {
    db.collection("posts").document(postId).get()
      .addOnSuccessListener { document ->
        if (document.exists()) {
          val post = document.toObject(Post::class.java)?.copy(id = document.id)
          if (post != null) {
            currentPost = post
            postTitle.text = post.title
            postBody.text = post.body

            if (!post.imageUrl.isNullOrEmpty()) {
              postImage.visibility = View.VISIBLE
              Glide.with(this).load(post.imageUrl)
                .placeholder(R.drawable.loading)
                .error(R.drawable.image_error)
                .centerCrop()
                .into(postImage)
            } else {
              postImage.visibility = View.GONE
            }

            likeCount.text = post.likeCount.toString()
            updateLikeButtonState(post)
            loadAuthorInfo(post.authorId)
            loadCommentCount(post.id)
          }
        }
      }
      .addOnFailureListener { e ->
        Toast.makeText(this, "Failed to load post: ${e.message}", Toast.LENGTH_SHORT).show()
      }
  }

  private fun loadAuthorInfo(authorId: String) {
    db.collection("users").document(authorId).get()
      .addOnSuccessListener { document ->
        if (document.exists()) {
          val username = document.getString("username") ?: "User"
          val imgUrl = document.getString("imgUrl")
          userName.text = username
          Glide.with(this).load(imgUrl ?: R.drawable.default_avatar)
            .placeholder(R.drawable.default_avatar)
            .error(R.drawable.default_avatar)
            .circleCrop()
            .into(userAvatar)
        }
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Failed to load author: ${e.message}")
      }
  }

  private fun loadCommentCount(postId: String) {
    db.collection("posts").document(postId).collection("comments")
      .get()
      .addOnSuccessListener { snapshot ->
        commentCount.text = snapshot.size().toString()
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Failed to load comment count: ${e.message}")
      }
  }

  private fun updateLikeButtonState(post: Post) {
    val currentUserId = auth.currentUser?.uid ?: return
    db.collection("posts").document(post.id).collection("likes").document(currentUserId)
      .get()
      .addOnSuccessListener { document ->
        val isLiked = document.exists() && document.getBoolean("liked") == true
        likeButton.setImageResource(
          if (isLiked) R.drawable.baseline_favorite_24
          else R.drawable.baseline_favorite_border_24
        )
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Failed to check like status: ${e.message}")
      }
  }

  private fun toggleLike(post: Post) {
    val currentUserId = auth.currentUser?.uid
    if (currentUserId.isNullOrEmpty()) {
      Toast.makeText(this, "Please log in to like posts", Toast.LENGTH_SHORT).show()
      return
    }

    val postRef = db.collection("posts").document(post.id)
    val likeRef = postRef.collection("likes").document(currentUserId)

    db.runTransaction { transaction ->
      val likeDoc = transaction.get(likeRef)
      val postDoc = transaction.get(postRef)

      if (!postDoc.exists()) throw Exception("Post does not exist")

      val currentLikeCount = postDoc.getLong("likeCount")?.toInt() ?: 0
      if (likeDoc.exists() && likeDoc.getBoolean("liked") == true) {
        transaction.delete(likeRef)
        transaction.update(postRef, "likeCount", currentLikeCount - 1)
        false
      } else {
        transaction.set(likeRef, mapOf("liked" to true))
        transaction.update(postRef, "likeCount", currentLikeCount + 1)
        true
      }
    }.addOnSuccessListener { liked ->
      likeButton.setImageResource(
        if (liked) R.drawable.baseline_favorite_24
        else R.drawable.baseline_favorite_border_24
      )
      val newCount = (likeCount.text.toString().toIntOrNull() ?: 0) + if (liked) 1 else -1
      likeCount.text = newCount.toString()

      val postAuthorId = post.authorId
      val currentUserId = auth.currentUser?.uid
      if (liked && postAuthorId != null && postAuthorId != currentUserId) {
        val notificationId = db.collection("notifications").document().id
        val notification = hashMapOf(
          "id" to notificationId,
          "toUserId" to postAuthorId,
          "fromUserId" to currentUserId,
          "type" to "like",
          "postId" to post.id,
          "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("notifications").document(notificationId)
          .set(notification)
          .addOnSuccessListener {
            Log.d(TAG, "Notification created for post author: $postAuthorId")
          }
          .addOnFailureListener { e ->
            Log.e(TAG, "Failed to create like notification: ${e.message}", e)
          }
      }
    }
  }


  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        finish()
        true
      }

      else -> super.onOptionsItemSelected(item)
    }
  }
}
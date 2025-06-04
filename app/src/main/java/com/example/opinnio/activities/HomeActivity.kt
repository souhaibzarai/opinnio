package com.example.opinnio.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opinnio.R
import com.example.opinnio.adapters.PostAdapter
import com.example.opinnio.models.Post
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HomeActivity : AppCompatActivity() {

  private lateinit var postsRecyclerView: RecyclerView
  private lateinit var fab: FloatingActionButton
  private lateinit var postAdapter: PostAdapter
  private lateinit var loadingProgressBar: ProgressBar
  private lateinit var toolbar: Toolbar
  private var postsListener: ListenerRegistration? = null
  private var notificationListener: ListenerRegistration? = null

  private val db = FirebaseFirestore.getInstance()
  private val auth = FirebaseAuth.getInstance()
  private val TAG = "HomeActivity"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_home)

    toolbar = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.title = "Opinnio"
    Log.d(TAG, "HomeActivity created, toolbar set")

    postsRecyclerView = findViewById(R.id.postsRecyclerView)
    fab = findViewById(R.id.fab)
    loadingProgressBar = findViewById(R.id.loadingProgressBar)

    postAdapter = PostAdapter(
      mutableListOf(), this::onLikeClicked, this::onCommentClicked, this::onEditClicked
    )
    postsRecyclerView.layoutManager = LinearLayoutManager(this)
    postsRecyclerView.adapter = postAdapter

    setupPostsListener()
    setupNotificationListener()
    Log.d(TAG, "Listeners set up")

    fab.setOnClickListener {
      if (auth.currentUser == null) {
        Toast.makeText(this, "Please log in to create a post", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      startActivity(Intent(this, CreatePostActivity::class.java))
    }
  }

  private fun setupPostsListener() {
    postsListener?.remove()
    loadingProgressBar.visibility = View.VISIBLE
    postsRecyclerView.visibility = View.GONE

    postsListener = db.collection("posts").orderBy("createdAt", Query.Direction.DESCENDING)
      .addSnapshotListener { querySnapshot, error ->
        if (error != null) {
          Log.e(TAG, "Failed to listen for posts: ${error.message}", error)
          Toast.makeText(this, "Failed to load posts: ${error.message}", Toast.LENGTH_SHORT).show()
          loadingProgressBar.visibility = View.GONE
          postsRecyclerView.visibility = View.VISIBLE
          return@addSnapshotListener
        }

        if (querySnapshot != null) {
          val posts = querySnapshot.documents.mapNotNull { document ->
            document.toObject(Post::class.java)?.copy(id = document.id)
          }
          postAdapter.updatePosts(posts)
          Log.d(TAG, "Posts updated: ${posts.size} posts loaded")
          loadingProgressBar.visibility = View.GONE
          postsRecyclerView.visibility = View.VISIBLE
        } else {
          Log.w(TAG, "No posts found")
          postAdapter.updatePosts(emptyList())
          loadingProgressBar.visibility = View.GONE
          postsRecyclerView.visibility = View.VISIBLE
        }
      }
  }

  private fun setupNotificationListener() {
    val currentUser = auth.currentUser
    if (currentUser == null) {
      Log.w(TAG, "No current user, skipping notification listener setup")
      return
    }
    notificationListener = db.collection("users")
      .document(currentUser.uid)
      .collection("notifications")
      .whereEqualTo("read", false)
      .addSnapshotListener { snapshot, e ->
        if (e != null || snapshot == null) {
          Log.e(TAG, "Failed to listen for notifications: ${e?.message}", e)
          return@addSnapshotListener
        }
        val unreadCount = snapshot.size()
        Log.d(TAG, "Unread notifications count: $unreadCount")
        
        
      }
  }

  private fun markNotificationsAsRead() {
    CoroutineScope(Dispatchers.IO).launch {
      val currentUser = auth.currentUser ?: return@launch
      try {
        val notifications = db.collection("users")
          .document(currentUser.uid)
          .collection("notifications")
          .whereEqualTo("read", false)
          .get()
          .await()

        val batch = db.batch()
        notifications.documents.forEach { doc ->
          batch.update(doc.reference, "read", true)
        }
        batch.commit().await()
        Log.d(TAG, "Notifications marked as read")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to mark notifications as read: ${e.message}", e)
      }
    }
  }

  private fun onLikeClicked(post: Post) {
    val currentUserId = auth.currentUser?.uid
    if (currentUserId == null) {
      Toast.makeText(this, "Please log in to like posts", Toast.LENGTH_SHORT).show()
      return
    }

    val postRef = db.collection("posts").document(post.id)
    val likeRef = postRef.collection("likes").document(currentUserId)

    db.runTransaction { transaction ->
      val likeDoc = transaction.get(likeRef)
      val postDoc = transaction.get(postRef)

      if (!postDoc.exists()) {
        throw Exception("Post does not exist")
      }

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
      Toast.makeText(
        this, if (liked) "Post liked" else "Post unliked", Toast.LENGTH_SHORT
      ).show()
      Log.d(TAG, "Like toggled for post ${post.id}: liked=$liked")
      setupPostsListener()

      
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
          "createdAt" to FieldValue.serverTimestamp(),
          "read" to false
        )
        db.collection("notifications").document(notificationId).set(notification)
          .addOnSuccessListener {
            db.collection("users").document(postAuthorId)
              .collection("notifications").document(notificationId)
              .set(notification)
              .addOnSuccessListener {
                Log.d(TAG, "Notification created for post author: $postAuthorId")
              }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to create user notification: ${e.message}", e)
              }
          }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to create like notification: ${e.message}", e)
          }
      }
    }
  }

  private fun onCommentClicked(post: Post) {
    val intent = Intent(this, CommentsActivity::class.java)
    intent.putExtra("postId", post.id)
    startActivity(intent)
  }

  private fun onEditClicked(post: Post) {
    val currentUserId = auth.currentUser?.uid
    if (currentUserId == null) {
      Toast.makeText(this, "Please log in to edit posts", Toast.LENGTH_SHORT).show()
      return
    }

    if (currentUserId != post.authorId) {
      Toast.makeText(this, "You can only edit your own posts", Toast.LENGTH_SHORT).show()
      return
    }

    val intent = Intent(this, EditPostActivity::class.java)
    intent.putExtra("postId", post.id)
    intent.putExtra("postTitle", post.title)
    intent.putExtra("postBody", post.body)
    intent.putExtra("postImageUrl", post.imageUrl)
    startActivity(intent)
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_home, menu)
    Log.d(TAG, "Menu inflated: ${menu?.size()} items")
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_notifications -> {
        Log.d(TAG, "Notifications menu item clicked")
        if (auth.currentUser == null) {
          Log.d(TAG, "User not logged in, redirecting to LoginActivity")
          Toast.makeText(this, "Please log in to view notifications", Toast.LENGTH_SHORT).show()
          startActivity(Intent(this, LoginActivity::class.java))
        } else {
          Log.d(
            TAG,
            "User logged in, marking notifications as read and opening NotificationsActivity"
          )
          markNotificationsAsRead()
          try {
            startActivity(Intent(this, NotificationsActivity::class.java))
            Log.d(TAG, "Intent started for NotificationsActivity")
          } catch (e: Exception) {
            Log.e(TAG, "Failed to start NotificationsActivity: ${e.message}", e)
            Toast.makeText(this, "Error opening notifications", Toast.LENGTH_SHORT).show()
          }
        }
        true
      }

      R.id.action_profile -> {
        Log.d(TAG, "Profile menu item clicked")
        if (auth.currentUser == null) {
          Toast.makeText(this, "Please log in to view profile", Toast.LENGTH_SHORT).show()
          startActivity(Intent(this, LoginActivity::class.java))
        } else {
          startActivity(Intent(this, ProfileActivity::class.java))
        }
        true
      }

      R.id.action_logout -> {
        Log.d(TAG, "Logout menu item clicked")
        auth.signOut()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
        true
      }

      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onResume() {
    super.onResume()
    Log.d(TAG, "HomeActivity resumed")
    val sharedPrefs = getSharedPreferences("opinnio_prefs", MODE_PRIVATE)
    if (sharedPrefs.getBoolean("refreshPosts", false)) {
      sharedPrefs.edit().putBoolean("refreshPosts", false).apply()
      val currentUserId = auth.currentUser?.uid
      if (!currentUserId.isNullOrEmpty()) {
        postAdapter.clearUserCacheFor(currentUserId)
        postAdapter.notifyDataSetChanged()
      }
    }
    setupNotificationListener()
  }

  override fun onDestroy() {
    super.onDestroy()
    postsListener?.remove()
    notificationListener?.remove()
    Log.d(TAG, "HomeActivity destroyed")
  }
}
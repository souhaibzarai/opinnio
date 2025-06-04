package com.example.opinnio.activities

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opinnio.R
import com.example.opinnio.adapters.CommentAdapter
import com.example.opinnio.models.Comment
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.UUID

class CommentsActivity : AppCompatActivity() {

  private lateinit var toolbar: Toolbar
  private lateinit var commentsRecyclerView: RecyclerView
  private lateinit var emptyCommentsText: TextView
  private lateinit var commentInput: TextInputEditText
  private lateinit var sendCommentButton: Button
  private lateinit var commentAdapter: CommentAdapter

  private val db = FirebaseFirestore.getInstance()
  private val auth = FirebaseAuth.getInstance()
  private val TAG = "CommentsActivity"

  private var postId: String? = null
  private var commentsListener: ListenerRegistration? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_comments)

    
    postId = intent.getStringExtra("postId")
    if (postId.isNullOrEmpty()) {
      Toast.makeText(this, "Invalid post ID", Toast.LENGTH_SHORT).show()
      finish()
      return
    }

    
    toolbar = findViewById(R.id.toolbar)
    commentsRecyclerView = findViewById(R.id.commentsRecyclerView)
    emptyCommentsText = findViewById(R.id.empty_comments_text)
    commentInput = findViewById(R.id.commentInput)
    sendCommentButton = findViewById(R.id.sendCommentButton)

    
    setupToolbar()

    
    commentAdapter = CommentAdapter(
      mutableListOf(), this::onEditCommentClicked, this::onDeleteCommentClicked
    )
    commentsRecyclerView.layoutManager = LinearLayoutManager(this)
    commentsRecyclerView.adapter = commentAdapter

    
    setupCommentsListener()

    
    sendCommentButton.setOnClickListener {
      addComment()
    }
  }

  private fun setupToolbar() {
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setDisplayShowHomeEnabled(true)
      title = "Comments"
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home -> {
        onBackPressed()
        true
      }

      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun setupCommentsListener() {
    commentsListener?.remove()
    commentsListener = db.collection("posts").document(postId!!).collection("comments")
      .orderBy("createdAt", Query.Direction.ASCENDING).addSnapshotListener { querySnapshot, error ->
        if (error != null) {
          Log.e(TAG, "Failed to listen for comments: ${error.message}", error)
          Toast.makeText(this, "Failed to load comments: ${error.message}", Toast.LENGTH_SHORT)
            .show()
          return@addSnapshotListener
        }

        if (querySnapshot != null) {
          val comments = querySnapshot.documents.mapNotNull { document ->
            document.toObject(Comment::class.java)?.copy(id = document.id, postId = postId!!)
          }
          commentAdapter.updateComments(comments)

          
          if (comments.isEmpty()) {
            emptyCommentsText.visibility = View.VISIBLE
            commentsRecyclerView.visibility = View.GONE
          } else {
            emptyCommentsText.visibility = View.GONE
            commentsRecyclerView.visibility = View.VISIBLE
            
            commentsRecyclerView.scrollToPosition(comments.size - 1)
          }
          Log.d(TAG, "Comments updated: ${comments.size} comments loaded")
        }
      }
  }

  private fun addComment() {
    val content = commentInput.text.toString().trim()
    if (content.isEmpty()) {
      Toast.makeText(this, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
      return
    }

    val userId = auth.currentUser?.uid
    if (userId == null) {
      Toast.makeText(this, "Please log in to comment", Toast.LENGTH_SHORT).show()
      return
    }

    sendCommentButton.isEnabled = false

    val commentId = UUID.randomUUID().toString()
    val newComment = hashMapOf(
      "id" to commentId,
      "postId" to postId!!,
      "authorId" to userId,
      "content" to content,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )

    db.collection("posts").document(postId!!).collection("comments").document(commentId)
      .set(newComment).addOnSuccessListener {
        Log.d(TAG, "Comment added: $commentId")
        commentInput.setText("")
        commentInput.clearFocus()
        Toast.makeText(this, "Comment added successfully", Toast.LENGTH_SHORT).show()

        
        db.collection("posts").document(postId!!).get().addOnSuccessListener { postSnapshot ->
          val postAuthorId = postSnapshot.getString("authorId")
          if (postAuthorId != null && postAuthorId != userId) {
            val notificationId = db.collection("notifications").document().id
            val notification = hashMapOf(
              "id" to notificationId,
              "toUserId" to postAuthorId,
              "fromUserId" to userId,
              "type" to "comment",
              "postId" to postId,
              "commentId" to commentId,
              "createdAt" to FieldValue.serverTimestamp(),
              "read" to false
            )
            db.collection("notifications").document(notificationId).set(notification)
              .addOnSuccessListener {
                Log.d(TAG, "Notification created for post author: $postAuthorId")
              }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to create notification: ${e.message}", e)
              }
          } else {
            Log.d(TAG, "Post author is same as comment author. No notification needed.")
          }
        }
      }.addOnFailureListener { e ->
        Log.e(TAG, "Failed to add comment: ${e.message}", e)
        Toast.makeText(this, "Failed to add comment: ${e.message}", Toast.LENGTH_SHORT).show()
      }.addOnCompleteListener {
        sendCommentButton.isEnabled = true
      }
  }

  private fun onEditCommentClicked(comment: Comment) {
    val dialogView = layoutInflater.inflate(R.layout.dialog_edit_comment, null)
    val editCommentInput = dialogView.findViewById<TextInputEditText>(R.id.editCommentInput)
    editCommentInput.setText(comment.content)

    AlertDialog.Builder(this).setTitle("Edit Comment").setView(dialogView)
      .setPositiveButton("Save") { _, _ ->
        val newContent = editCommentInput.text.toString().trim()
        if (newContent.isEmpty()) {
          Toast.makeText(this, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
          return@setPositiveButton
        }

        val updatedComment = hashMapOf<String, Any>(
          "content" to newContent, "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("posts").document(postId!!).collection("comments").document(comment.id)
          .update(updatedComment).addOnSuccessListener {
            Log.d(TAG, "Comment updated: ${comment.id}")
            Toast.makeText(this, "Comment updated successfully", Toast.LENGTH_SHORT).show()
          }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to update comment: ${e.message}", e)
            Toast.makeText(this, "Failed to update comment: ${e.message}", Toast.LENGTH_SHORT)
              .show()
          }
      }.setNegativeButton("Cancel", null).show()
  }

  private fun onDeleteCommentClicked(comment: Comment) {
    AlertDialog.Builder(this).setTitle("Delete Comment")
      .setMessage("Are you sure you want to delete this comment?")
      .setPositiveButton("Delete") { _, _ ->
        val commentRef =
          db.collection("posts").document(postId!!).collection("comments").document(comment.id)
        val notificationsQuery =
          db.collection("notifications").whereEqualTo("commentId", comment.id)

        commentRef.delete().addOnSuccessListener {
          Log.d(TAG, "Comment deleted: ${comment.id}")

          notificationsQuery.get().addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
              val batch = db.batch()
              snapshot.forEach { doc ->
                batch.delete(doc.reference)
              }
              batch.commit().addOnSuccessListener {
                Log.d(TAG, "Deleted notifications for comment ${comment.id}")
              }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to delete related notifications: ${e.message}", e)
              }
            } else {
              Log.d(TAG, "No related notifications for comment ${comment.id}")
            }

            Toast.makeText(this, "Comment deleted successfully", Toast.LENGTH_SHORT).show()
          }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to query notifications: ${e.message}", e)
            Toast.makeText(
              this, "Comment deleted, but failed to delete notifications.", Toast.LENGTH_SHORT
            ).show()
          }
        }.addOnFailureListener { e ->
          Log.e(TAG, "Failed to delete comment: ${e.message}", e)
          Toast.makeText(this, "Failed to delete comment: ${e.message}", Toast.LENGTH_SHORT)
            .show()
        }
      }.setNegativeButton("Cancel", null).show()
  }

  override fun onDestroy() {
    super.onDestroy()
    commentsListener?.remove()
  }
}
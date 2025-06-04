package com.example.opinnio.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opinnio.R
import com.example.opinnio.adapters.NotificationAdapter
import com.example.opinnio.models.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationsActivity : AppCompatActivity() {

  private lateinit var recyclerView: RecyclerView
  private lateinit var emptyStateText: TextView
  private lateinit var adapter: NotificationAdapter
  private var notificationListener: ListenerRegistration? = null
  private val db = FirebaseFirestore.getInstance()
  private val auth = FirebaseAuth.getInstance()
  private val TAG = "NotificationsActivity"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_notifications)
    Log.d(TAG, "NotificationsActivity started")

    val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.title = getString(R.string.notifications)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    recyclerView = findViewById(R.id.notificationsRecyclerView)
    emptyStateText = findViewById(R.id.emptyStateText)
    recyclerView.layoutManager = LinearLayoutManager(this)
    adapter = NotificationAdapter()
    recyclerView.adapter = adapter

    loadNotifications()
    markNotificationsAsRead()
  }

  private fun loadNotifications() {
    val currentUser = auth.currentUser
    if (currentUser == null) {
      emptyStateText.text = "Please log in to view notifications"
      emptyStateText.visibility = View.VISIBLE
      recyclerView.visibility = View.GONE
      return
    }

    notificationListener = db.collection("notifications")
      .whereEqualTo("toUserId", currentUser.uid)
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .addSnapshotListener { snapshot, e ->
        if (e != null) {
          emptyStateText.text = "Error loading notifications: ${e.message}"
          emptyStateText.visibility = View.VISIBLE
          recyclerView.visibility = View.GONE
          return@addSnapshotListener
        }

        if (snapshot == null || snapshot.isEmpty) {
          emptyStateText.text = "No notifications available"
          emptyStateText.visibility = View.VISIBLE
          recyclerView.visibility = View.GONE
          return@addSnapshotListener
        }

        val notifications = snapshot.documents.mapNotNull { doc ->
          doc.toObject(Notification::class.java)?.apply { id = doc.id }
        }

        adapter.submitList(notifications)

        emptyStateText.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
      }
  }

  private fun markNotificationsAsRead() {
    CoroutineScope(Dispatchers.IO).launch {
      val currentUser = auth.currentUser ?: return@launch
      try {
        val snapshot = db.collection("notifications")
          .whereEqualTo("toUserId", currentUser.uid)
          .whereEqualTo("read", false)
          .get()
          .await()
        val batch = db.batch()
        snapshot.forEach { doc ->
          batch.update(doc.reference, "read", true)
        }
        batch.commit().await()
        Log.d(TAG, "Marked ${snapshot.size()} notifications as read")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to mark notifications as read: ${e.message}", e)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    notificationListener?.remove()
    Log.d(TAG, "NotificationsActivity destroyed")
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }
}

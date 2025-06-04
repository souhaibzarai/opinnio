package com.example.opinnio.activities

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.example.opinnio.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class EditPostActivity : AppCompatActivity() {
  private lateinit var titleEditText: TextInputEditText
  private lateinit var bodyEditText: TextInputEditText
  private lateinit var postImage: ImageView
  private lateinit var changeImageButton: MaterialButton
  private lateinit var saveButton: Button
  private lateinit var cancelButton: Button
  private lateinit var toolbar: Toolbar

  private val db = FirebaseFirestore.getInstance()
  private val okHttpClient = OkHttpClient()
  private val TAG = "EditPostActivity"

  private var postId: String? = null
  private var currentImageUrl: String? = null
  private var newImageUri: Uri? = null
  private var loadingDialog: AlertDialog? = null

  private val cloudName = "ddzlyix55"
  private val uploadPreset = "hez1zpwr"
  private val cloudinaryUrl = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"


  private val pickImageLauncher =
    registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      uri?.let {
        newImageUri = it
        postImage.visibility = View.VISIBLE
        Glide.with(this).load(it).placeholder(R.drawable.loading).error(R.drawable.image_error)
          .into(postImage)
        Log.d(TAG, "Image selected: $it")
      } ?: run {
        Log.d(TAG, "Image selection cancelled")
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_edit_post)

    toolbar = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayShowHomeEnabled(true)
    supportActionBar?.title = "Modifier le Post"


    titleEditText = findViewById(R.id.titleEditText)
    bodyEditText = findViewById(R.id.bodyEditText)
    postImage = findViewById(R.id.postImage)
    changeImageButton = findViewById(R.id.changeImageButton)
    saveButton = findViewById(R.id.saveButton)
    cancelButton = findViewById(R.id.cancelButton)


    postId = intent.getStringExtra("postId")
    val postTitle = intent.getStringExtra("postTitle")
    val postBody = intent.getStringExtra("postBody")
    currentImageUrl = intent.getStringExtra("postImageUrl")

    Log.d(
      TAG,
      "Received postId: $postId, title: $postTitle, body: $postBody, imageUrl: $currentImageUrl"
    )


    if (postId.isNullOrEmpty()) {
      Toast.makeText(this, "Invalid post ID", Toast.LENGTH_SHORT).show()
      Log.e(TAG, "onCreate: Invalid post ID")
      finish()
      return
    }


    titleEditText.setText(postTitle)
    bodyEditText.setText(postBody)


    if (!currentImageUrl.isNullOrEmpty()) {
      postImage.visibility = View.VISIBLE
      Glide.with(this).load(currentImageUrl).placeholder(R.drawable.loading)
        .error(R.drawable.image_error).into(postImage)
    } else {
      postImage.visibility = View.GONE
    }


    changeImageButton.setOnClickListener {
      pickImageLauncher.launch("image/*")
    }

    saveButton.setOnClickListener {
      savePost()
    }

    cancelButton.setOnClickListener {
      if (loadingDialog?.isShowing == true) {
        Toast.makeText(this, "Please wait, saving in progress...", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
      }
      finish()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.edit_post_menu, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        if (loadingDialog?.isShowing == true) {
          Toast.makeText(this, "Please wait, saving in progress...", Toast.LENGTH_SHORT).show()
          return true
        }
        finish()
        return true
      }

      R.id.action_delete -> {
        showDeleteConfirmationDialog()
        return true
      }
    }
    return super.onOptionsItemSelected(item)
  }

  private fun showDeleteConfirmationDialog() {
    AlertDialog.Builder(this).setTitle("Delete Post")
      .setMessage("Are you sure you want to delete this post? This action cannot be undone.")
      .setPositiveButton("Delete") { _, _ ->
        deletePost()
      }.setNegativeButton("Cancel", null).setIcon(R.drawable.warning).show()
  }

  private fun deletePost() {
    if (postId.isNullOrEmpty()) {
      Toast.makeText(this, "Invalid post ID", Toast.LENGTH_SHORT).show()
      Log.e(TAG, "deletePost: Invalid post ID")
      return
    }

    showLoadingDialog()

    val postRef = db.collection("posts").document(postId!!)
    val notificationsRef = db.collection("notifications").whereEqualTo("postId", postId)


    postRef.delete().addOnSuccessListener {
      Log.d(TAG, "Post deleted successfully: $postId")


      notificationsRef.get().addOnSuccessListener { snapshot ->
        if (!snapshot.isEmpty) {
          val batch = db.batch()
          snapshot.forEach { doc ->
            batch.delete(doc.reference)
          }
          batch.commit().addOnSuccessListener {
            Log.d(TAG, "Deleted related notifications for post $postId")
            Toast.makeText(this, "Post deleted successfully", Toast.LENGTH_SHORT).show()
            dismissLoadingDialog()
            setResult(RESULT_OK)
            finish()
          }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to delete related notifications: ${e.message}", e)
            Toast.makeText(
              this, "Post deleted, but failed to delete notifications.", Toast.LENGTH_SHORT
            ).show()
            dismissLoadingDialog()
            setResult(RESULT_OK)
            finish()
          }
        } else {
          Log.d(TAG, "No related notifications found for post $postId")
          Toast.makeText(this, "Post deleted successfully", Toast.LENGTH_SHORT).show()
          dismissLoadingDialog()
          setResult(RESULT_OK)
          finish()
        }
      }.addOnFailureListener { e ->
        Log.e(TAG, "Failed to fetch related notifications: ${e.message}", e)
        Toast.makeText(
          this, "Post deleted, but failed to check notifications.", Toast.LENGTH_SHORT
        ).show()
        dismissLoadingDialog()
        setResult(RESULT_OK)
        finish()
      }
    }.addOnFailureListener { e ->
      Log.e(TAG, "Failed to delete post: ${e.message}", e)
      Toast.makeText(this, "Failed to delete post: ${e.message}", Toast.LENGTH_SHORT).show()
      dismissLoadingDialog()
    }
  }


  private fun showLoadingDialog() {
    if (loadingDialog == null) {
      val dialogView = layoutInflater.inflate(R.layout.dialog_loading, null)
      loadingDialog = AlertDialog.Builder(this).setView(dialogView)
        .setCancelable(false)
        .create()
    }
    loadingDialog?.show()

    cancelButton.isEnabled = false
  }

  private fun dismissLoadingDialog() {
    loadingDialog?.dismiss()

    cancelButton.isEnabled = true
  }

  override fun onBackPressed() {
    if (loadingDialog?.isShowing == true) {
      Toast.makeText(this, "Please wait, saving in progress...", Toast.LENGTH_SHORT).show()
      return
    }
    super.onBackPressed()
  }

  private fun savePost() {
    if (postId.isNullOrEmpty()) {
      Toast.makeText(this, "Invalid post ID", Toast.LENGTH_SHORT).show()
      Log.e(TAG, "savePost: Invalid post ID")
      dismissLoadingDialog()
      return
    }

    val title = titleEditText.text.toString().trim()
    val body = bodyEditText.text.toString().trim()


    if (title.isEmpty() || body.isEmpty()) {
      Toast.makeText(this, "Title and content cannot be empty", Toast.LENGTH_SHORT).show()
      Log.e(TAG, "savePost: Title or body empty")
      return
    }


    showLoadingDialog()


    db.collection("posts").document(postId!!).get().addOnSuccessListener { document ->
      if (!document.exists()) {
        Toast.makeText(this, "Post does not exist", Toast.LENGTH_SHORT).show()
        Log.e(TAG, "savePost: Post $postId does not exist")
        dismissLoadingDialog()
        finish()
        return@addOnSuccessListener
      }


      if (newImageUri == null) {
        updatePost(postId!!, title, body, currentImageUrl)
        return@addOnSuccessListener
      }


      newImageUri?.let { uri ->
        uploadImageToCloudinary(uri) { imageUrl, error ->
          if (imageUrl != null) {
            Log.d(TAG, "Image uploaded: $imageUrl")
            updatePost(postId!!, title, body, imageUrl)
          } else {
            Toast.makeText(this, "Failed to upload image: $error", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to upload image: $error")
            dismissLoadingDialog()
          }
        }
      }
    }.addOnFailureListener { e ->
      Log.e(TAG, "Failed to check post existence: ${e.message}", e)
      Toast.makeText(this, "Failed to verify post: ${e.message}", Toast.LENGTH_SHORT).show()
      dismissLoadingDialog()
    }
  }

  private fun uploadImageToCloudinary(uri: Uri, callback: (String?, String?) -> Unit) {

    val file = uriToFile(uri) ?: run {
      callback(null, "Failed to convert image to file")
      dismissLoadingDialog()
      return
    }

    try {
      val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
        "file", file.name, file.asRequestBody("image/*".toMediaType())
      ).addFormDataPart("upload_preset", uploadPreset).build()

      val request = Request.Builder().url(cloudinaryUrl).post(requestBody).build()


      Thread {
        try {
          val response = okHttpClient.newCall(request).execute()
          if (response.isSuccessful) {
            val json = JSONObject(response.body?.string() ?: "{}")
            val secureUrl = json.optString("secure_url")
            if (secureUrl.isNotEmpty()) {
              runOnUiThread { callback(secureUrl, null) }
            } else {
              runOnUiThread { callback(null, "No secure_url in response") }
            }
          } else {
            runOnUiThread { callback(null, "Upload failed: ${response.code}") }
          }
          response.close()
        } catch (e: Exception) {
          runOnUiThread { callback(null, "Upload error: ${e.message}") }
        }
      }.start()
    } catch (e: Exception) {
      callback(null, "Failed to prepare upload: ${e.message}")
      dismissLoadingDialog()
    }
  }

  private fun uriToFile(uri: Uri): File? {
    return try {
      val inputStream = contentResolver.openInputStream(uri)
      val file = File(cacheDir, "temp_image_${UUID.randomUUID()}.jpg")
      FileOutputStream(file).use { outputStream ->
        inputStream?.copyTo(outputStream)
      }
      inputStream?.close()
      file
    } catch (e: Exception) {
      Log.e(TAG, "Failed to convert Uri to File: ${e.message}")
      null
    }
  }

  private fun updatePost(postId: String, title: String, body: String, imageUrl: String?) {
    val updatedPost = mutableMapOf<String, Any>(
      "title" to title, "body" to body, "updatedAt" to FieldValue.serverTimestamp()
    )
    imageUrl?.let { updatedPost["imageUrl"] = it } ?: run { updatedPost["imageUrl"] = "" }

    db.collection("posts").document(postId).update(updatedPost).addOnSuccessListener {
      Log.d(TAG, "Post updated: $postId")
      Toast.makeText(this, "Post updated successfully", Toast.LENGTH_SHORT).show()
      dismissLoadingDialog()
      finish()
    }.addOnFailureListener { e ->
      Log.e(TAG, "Failed to update post: ${e.message}")
      Toast.makeText(this, "Failed to update post: ${e.message}", Toast.LENGTH_SHORT).show()
      dismissLoadingDialog()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    dismissLoadingDialog()
  }
}
package com.example.opinnio.activities

import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.example.opinnio.models.Post
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
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

class CreatePostActivity : AppCompatActivity() {
  private lateinit var titleEditText: TextInputEditText
  private lateinit var bodyEditText: TextInputEditText
  private lateinit var postImage: ImageView
  private lateinit var changeImageButton: MaterialButton
  private lateinit var saveButton: Button
  private lateinit var cancelButton: Button
  private lateinit var toolbar: Toolbar

  private val db = FirebaseFirestore.getInstance()
  private val okHttpClient = OkHttpClient()
  private val TAG = "CreatePostActivity"

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
    setContentView(R.layout.activity_create_post)

    toolbar = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayShowHomeEnabled(true)
    supportActionBar?.title = "Ajouter Un Post"

    titleEditText = findViewById(R.id.titleEditText)
    bodyEditText = findViewById(R.id.bodyEditText)
    postImage = findViewById(R.id.postImage)
    changeImageButton = findViewById(R.id.changeImageButton)
    saveButton = findViewById(R.id.saveButton)
    cancelButton = findViewById(R.id.cancelButton)

    
    postImage.visibility = View.GONE

    
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

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
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
    val title = titleEditText.text.toString().trim()
    val body = bodyEditText.text.toString().trim()

    
    if (title.isEmpty() || body.isEmpty()) {
      Toast.makeText(this, "Title and content cannot be empty", Toast.LENGTH_SHORT).show()
      Log.e(TAG, "savePost: Title or body empty")
      return
    }

    val authorId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
      Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
      Log.e(TAG, "savePost: User not authenticated")
      return
    }

    val postId = UUID.randomUUID().toString()

    
    val newPost = Post(
      id = postId,
      authorId = authorId,
      title = title,
      body = body,
      imageUrl = "", 
      createdAt = null,
      updatedAt = null
    )

    
    showLoadingDialog()

    
    if (newImageUri != null) {
      newImageUri?.let { uri ->
        uploadImageToCloudinary(uri) { uploadedImageUrl, error ->
          if (uploadedImageUrl != null) {
            Log.d(TAG, "Image uploaded: $uploadedImageUrl")
            val updatedPost = newPost.copy(imageUrl = uploadedImageUrl)
            savePostToFirestore(updatedPost)
          } else {
            Toast.makeText(this, "Failed to upload image: $error", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to upload image: $error")
            dismissLoadingDialog()
          }
        }
      }
    } else {
      savePostToFirestore(newPost)
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

  private fun savePostToFirestore(post: Post) {
    val postData = hashMapOf(
      "id" to post.id,
      "authorId" to post.authorId,
      "title" to post.title,
      "body" to post.body,
      "imageUrl" to post.imageUrl,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
      "likeCount" to 0
    )

    db.collection("posts").document(post.id).set(postData).addOnSuccessListener {
      Log.d(TAG, "Post created: ${post.id}")
      Toast.makeText(this, "Post created successfully", Toast.LENGTH_SHORT).show()
      dismissLoadingDialog()
      finish()
    }.addOnFailureListener { e ->
      Log.e(TAG, "Failed to create post: ${e.message}")
      Toast.makeText(this, "Failed to create post: ${e.message}", Toast.LENGTH_SHORT).show()
      dismissLoadingDialog()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    
    dismissLoadingDialog()
  }
}
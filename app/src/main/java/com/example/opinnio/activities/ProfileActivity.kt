package com.example.opinnio.activities

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.example.opinnio.R
import com.example.opinnio.databinding.ActivityProfileBinding
import com.example.opinnio.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.*

class ProfileActivity : AppCompatActivity() {

  private lateinit var binding: ActivityProfileBinding
  private lateinit var toolbar: Toolbar

  private val db = FirebaseFirestore.getInstance()
  private val auth = FirebaseAuth.getInstance()
  private val okHttpClient = OkHttpClient()
  private val TAG = "ProfileActivity"

  private var newImageUri: Uri? = null
  private var loadingDialog: AlertDialog? = null

  private val cloudName = "ddzlyix55"
  private val uploadPreset = "hez1zpwr"
  private val cloudinaryUrl = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"

  private val pickImageLauncher =
    registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
      uri?.let {
        newImageUri = it
        uploadImageToCloudinary(it) { uploadedImageUrl, error ->
          if (uploadedImageUrl != null) {
            updateUserProfileImage(uploadedImageUrl)
          } else {
            Toast.makeText(this, "Failed to upload image: $error", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to upload image: $error")
            dismissLoadingDialog()
          }
        }
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityProfileBinding.inflate(layoutInflater)
    setContentView(binding.root)

    toolbar = findViewById(R.id.toolbar)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayShowHomeEnabled(true)
    supportActionBar?.title = "Mon Profile"

    loadUserProfile()

    binding.editPhotoFab.setOnClickListener {
      pickImageLauncher.launch("image/*")
    }

    binding.editProfileBtn.setOnClickListener {
      showEditUsernameDialog()
    }
  }

  private fun loadUserProfile() {
    val currentUser = auth.currentUser
    if (currentUser == null) {
      Toast.makeText(this, "Aucun utilisateur connecté", Toast.LENGTH_SHORT).show()
      finish()
      return
    }

    val userId = currentUser.uid
    db.collection("users").document(userId).get().addOnSuccessListener { document ->
      if (document.exists()) {
        val user = document.toObject(User::class.java)
        if (user != null) {
          binding.nomcomplet.text = user.username
          binding.emaill.text = user.email
          binding.locationn.text = user.location

          Glide.with(this).load(user.profileImageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image).circleCrop().into(binding.profileImg)
        }
      } else {
        Toast.makeText(this, "Utilisateur introuvable", Toast.LENGTH_SHORT).show()
        finish()
      }
    }.addOnFailureListener { e ->
      Toast.makeText(this, "Erreur lors du chargement: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  private fun showLoadingDialog() {
    if (loadingDialog == null) {
      val dialogView = layoutInflater.inflate(R.layout.dialog_loading, null)
      loadingDialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
    }
    loadingDialog?.show()
  }

  private fun dismissLoadingDialog() {
    loadingDialog?.dismiss()
  }

  private fun uploadImageToCloudinary(uri: Uri, callback: (String?, String?) -> Unit) {
    showLoadingDialog()

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

  private fun updateUserProfileImage(imageUrl: String) {
    val currentUser = auth.currentUser ?: return
    db.collection("users").document(currentUser.uid).update("imgUrl", imageUrl)
      .addOnSuccessListener {
        Glide.with(this).load(imageUrl).placeholder(android.R.drawable.ic_menu_gallery)
          .error(android.R.drawable.ic_menu_report_image).circleCrop().into(binding.profileImg)
        Toast.makeText(this, "Photo de profil mise à jour", Toast.LENGTH_SHORT).show()
        notifyUserProfileUpdated()
        dismissLoadingDialog()
      }.addOnFailureListener { e ->
        Toast.makeText(this, "Erreur lors de la mise à jour: ${e.message}", Toast.LENGTH_SHORT)
          .show()
        dismissLoadingDialog()
      }
  }

  private fun showEditUsernameDialog() {
    val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
    builder.setTitle("Modifier le nom d'utilisateur")
    val dialogView = layoutInflater.inflate(R.layout.dialog_edit_username, null)
    val input = dialogView.findViewById<EditText>(R.id.usernameInput)
    input.setText(binding.nomcomplet.text)
    builder.setView(dialogView)
    builder.setPositiveButton("OK") { _, _ ->
      val newUsername = input.text.toString().trim()
      if (newUsername.isEmpty()) {
        Toast.makeText(this, "Le nom ne peut pas être vide", Toast.LENGTH_SHORT).show()
      } else if (newUsername.length < 3) {
        Toast.makeText(this, "Min 3 caractères", Toast.LENGTH_SHORT).show()
      } else {
        val currentUser = auth.currentUser
        if (currentUser != null) {
          db.collection("users").document(currentUser.uid).update("username", newUsername)
            .addOnSuccessListener {
              binding.nomcomplet.text = newUsername
              Toast.makeText(this, getString(R.string.nom_mis_jour), Toast.LENGTH_SHORT).show()
              notifyUserProfileUpdated()
            }.addOnFailureListener { e ->
              Toast.makeText(this, getString(R.string.erreur, e.message), Toast.LENGTH_SHORT).show()
            }
        }
      }
    }
    builder.setNegativeButton(getString(R.string.annuler)) { dialog, _ -> dialog.cancel() }
    val dialog = builder.create()
    dialog.show()
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.colorPrimary))
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(R.color.colorError))
  }

  private fun notifyUserProfileUpdated() {
    val sharedPrefs = getSharedPreferences("opinnio_prefs", MODE_PRIVATE)
    sharedPrefs.edit().putBoolean("refreshPosts", true).apply()
  }

  override fun onSupportNavigateUp(): Boolean {
    onBackPressed()
    return true
  }
}

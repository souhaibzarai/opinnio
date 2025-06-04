package com.example.opinnio.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.opinnio.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SignUpActivity : AppCompatActivity() {

  private lateinit var usernameEditText: EditText
  private lateinit var emailEditText: EditText
  private lateinit var passwordEditText: EditText
  private lateinit var signupButton: Button
  private lateinit var fusedLocationClient: FusedLocationProviderClient
  private var loadingDialog: AlertDialog? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_signup)

    usernameEditText = findViewById(R.id.usernameEditText)
    emailEditText = findViewById(R.id.emailEditText)
    passwordEditText = findViewById(R.id.passwordEditText)
    signupButton = findViewById(R.id.signupButton)

    val loginTextView = findViewById<TextView>(R.id.loginTextView)
    loginTextView.setOnClickListener {
      val intent = Intent(this, LoginActivity::class.java)
      intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
      startActivity(intent)
      finish()
    }

    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    signupButton.setOnClickListener { signup() }
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

  private fun signup() {
    val username = usernameEditText.text.toString().trim()
    val email = emailEditText.text.toString().trim()
    val password = passwordEditText.text.toString().trim()

    if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
      Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
      return
    }

    if (ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
      )
      return
    }

    showLoadingDialog()
    getLocationAndSignUp(username, email, password)
  }

  private fun getLocationAndSignUp(username: String, email: String, password: String) {
    if (ActivityCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
      )
      dismissLoadingDialog()
      return
    }

    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
      if (location != null) {
        val lat = location.latitude
        val lng = location.longitude

        lifecycleScope.launch {
          val cityName = getCityName(lat, lng)
          createUser(username, email, password, cityName, lat, lng)
        }
      } else {
        Toast.makeText(this, "Could not get location", Toast.LENGTH_SHORT).show()
        dismissLoadingDialog()
      }
    }.addOnFailureListener {
      Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
      dismissLoadingDialog()
    }
  }

  private suspend fun getCityName(lat: Double, lng: Double): String {
    return withContext(Dispatchers.IO) {
      try {
        val geocoder = Geocoder(this@SignUpActivity, Locale.getDefault())
        val addresses: List<Address> = geocoder.getFromLocation(lat, lng, 1) ?: emptyList()
        if (addresses.isNotEmpty()) {
          addresses[0].locality ?: "Unknown"
        } else {
          "Unknown"
        }
      } catch (e: Exception) {
        e.printStackTrace()
        "Unknown"
      }
    }
  }

  private fun createUser(
    username: String, email: String, password: String, city: String, lat: Double, lng: Double
  ) {
    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
      .addOnCompleteListener { task ->
        if (task.isSuccessful) {
          val user = FirebaseAuth.getInstance().currentUser
          val userRef = FirebaseFirestore.getInstance().collection("users").document(user!!.uid)

          val userMap = hashMapOf(
            "id" to user.uid,
            "email" to email,
            "username" to username,
            "location" to city,
            "latitude" to lat,
            "longitude" to lng,
            "createdAt" to FieldValue.serverTimestamp()
          )

          userRef.set(userMap).addOnSuccessListener {
            dismissLoadingDialog()
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
          }.addOnFailureListener { e ->
            dismissLoadingDialog()
            Toast.makeText(this, "Failed to save user: ${e.message}", Toast.LENGTH_SHORT).show()
          }
        } else {
          dismissLoadingDialog()
          Toast.makeText(this, "Sign up failed: ${task.exception?.message}", Toast.LENGTH_LONG)
            .show()
        }
      }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<out String>, grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      signup()
    } else {
      Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    dismissLoadingDialog()
  }
}

package com.example.opinnio.activities

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.example.opinnio.R
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

  private lateinit var emailEditText: EditText
  private lateinit var passwordEditText: EditText
  private lateinit var loginButton: Button
  private lateinit var signupTextView: TextView
  private lateinit var resetPasswordTextView: TextView
  private var loadingDialog: AlertDialog? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_login)

    emailEditText = findViewById(R.id.emailEditText)
    passwordEditText = findViewById(R.id.passwordEditText)
    loginButton = findViewById(R.id.loginButton)
    signupTextView = findViewById(R.id.signupTextView)
    resetPasswordTextView = findViewById(R.id.resetPasswordTextView)

    loginButton.setOnClickListener { login() }
    signupTextView.setOnClickListener {
      startActivity(Intent(this, SignUpActivity::class.java))
    }
    resetPasswordTextView.setOnClickListener {
      startActivity(Intent(this, ResetPasswordActivity::class.java))
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

  private fun login() {
    val email = emailEditText.text.toString().trim()
    val password = passwordEditText.text.toString().trim()

    if (email.isEmpty() || password.isEmpty()) {
      Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
      return
    }

    showLoadingDialog()
    FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
      .addOnCompleteListener { task ->
        if (task.isSuccessful) {
          dismissLoadingDialog()
          startActivity(Intent(this, HomeActivity::class.java))
          finish()
        } else {
          dismissLoadingDialog()
          Toast.makeText(
            this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG
          ).show()
        }
      }
  }

  private var backPressedTime: Long = 0
  private val backPressedInterval = 2000L

  override fun onBackPressed() {
    if (backPressedTime + backPressedInterval > System.currentTimeMillis()) {
      super.onBackPressed()
      finishAffinity()
    } else {
      Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
    }
    backPressedTime = System.currentTimeMillis()
  }

  override fun onDestroy() {
    super.onDestroy()
    dismissLoadingDialog()
  }
}

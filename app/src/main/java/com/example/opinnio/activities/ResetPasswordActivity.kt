package com.example.opinnio.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.opinnio.R
import com.google.firebase.auth.FirebaseAuth

class ResetPasswordActivity : AppCompatActivity() {

  private lateinit var emailEditText: EditText
  private lateinit var sendResetButton: Button

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_forgot_password)

    emailEditText = findViewById(R.id.emailEditText)
    sendResetButton = findViewById(R.id.sendResetButton)

    val loginTextView = findViewById<TextView>(R.id.backToLoginTextView)
    loginTextView.setOnClickListener {
      val intent = Intent(this, LoginActivity::class.java)
      intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      startActivity(intent)
      finish()
    }

    sendResetButton.setOnClickListener { sendResetEmail() }
  }

  private fun sendResetEmail() {
    val email = emailEditText.text.toString().trim()

    if (email.isEmpty()) {
      Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
      return
    }

    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
      .addOnCompleteListener { task ->
        if (task.isSuccessful) {
          Toast.makeText(this, "Reset email sent", Toast.LENGTH_SHORT).show()
          finish()
        } else {
          Toast.makeText(this, "Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
        }
      }
  }
}

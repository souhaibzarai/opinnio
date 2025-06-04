package com.example.opinnio.activities

import android.animation.*
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.view.animation.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import com.example.opinnio.R
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

  private lateinit var logoContainer: RelativeLayout
  private lateinit var logoPulseCircle: View
  private lateinit var logoIcon: TextView
  private lateinit var appNameContainer: LinearLayout
  private lateinit var appNameText: TextView
  private lateinit var typingCursor: View
  private lateinit var taglineText: TextView
  private lateinit var loadingContainer: LinearLayout
  private lateinit var loadingProgressBar: ProgressBar
  private lateinit var loadingText: TextView
  private lateinit var floatingDot1: View
  private lateinit var floatingDot2: View
  private lateinit var floatingDot3: View

  private val splashDuration = 4000L 
  private val currentUser = FirebaseAuth.getInstance().currentUser

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    
    window.setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    )

    setContentView(R.layout.activity_main)

    initViews()
    startAnimationSequence()
  }

  private fun initViews() {
    logoContainer = findViewById(R.id.logoContainer)
    logoPulseCircle = findViewById(R.id.logoPulseCircle)
    logoIcon = findViewById(R.id.logoIcon)
    appNameContainer = findViewById(R.id.appNameContainer)
    appNameText = findViewById(R.id.appNameText)
    typingCursor = findViewById(R.id.typingCursor)
    taglineText = findViewById(R.id.taglineText)
    loadingContainer = findViewById(R.id.loadingContainer)
    loadingProgressBar = findViewById(R.id.loadingProgressBar)
    loadingText = findViewById(R.id.loadingText)
    floatingDot1 = findViewById(R.id.floatingDot1)
    floatingDot2 = findViewById(R.id.floatingDot2)
    floatingDot3 = findViewById(R.id.floatingDot3)
  }

  private fun startAnimationSequence() {
    setInitialVisibility()

    
    animateLogo().doOnEnd {
      animateAppName()
    }
  }

  private fun setInitialVisibility() {
    logoContainer.alpha = 0f
    logoContainer.scaleX = 0.3f
    logoContainer.scaleY = 0.3f
    appNameText.alpha = 0f
    typingCursor.alpha = 0f
    taglineText.alpha = 0f
    loadingContainer.alpha = 0f
  }

  private fun animateLogo(): Animator {
    val logoAnimSet = AnimatorSet()

    
    val logoScale = ObjectAnimator.ofPropertyValuesHolder(
      logoContainer,
      PropertyValuesHolder.ofFloat("scaleX", 0.3f, 1.2f, 1f),
      PropertyValuesHolder.ofFloat("scaleY", 0.3f, 1.2f, 1f),
      PropertyValuesHolder.ofFloat("alpha", 0f, 1f)
    ).apply {
      duration = 800
      interpolator = DecelerateInterpolator()
    }

    
    val pulseAnimation = ObjectAnimator.ofPropertyValuesHolder(
      logoPulseCircle,
      PropertyValuesHolder.ofFloat("scaleX", 1f, 1.3f, 1f),
      PropertyValuesHolder.ofFloat("scaleY", 1f, 1.3f, 1f),
      PropertyValuesHolder.ofFloat("alpha", 0.3f, 0.1f, 0.3f)
    ).apply {
      duration = 1500
      repeatCount = ValueAnimator.INFINITE
      repeatMode = ValueAnimator.REVERSE
    }

    
    val iconBounce = ObjectAnimator.ofFloat(logoIcon, "translationY", 0f, -10f, 0f).apply {
      duration = 600
      startDelay = 400
      interpolator = BounceInterpolator()
    }

    logoAnimSet.playTogether(logoScale, pulseAnimation, iconBounce)
    logoAnimSet.start()

    
    animateFloatingDots()

    return logoScale
  }

  private fun animateAppName() {
    val appName = "Opinnio"
    appNameText.text = ""
    appNameText.alpha = 1f
    typingCursor.alpha = 1f

    
    val cursorBlink = ObjectAnimator.ofFloat(typingCursor, "alpha", 1f, 0f).apply {
      duration = 500
      repeatCount = ValueAnimator.INFINITE
      repeatMode = ValueAnimator.REVERSE
    }
    cursorBlink.start()

    
    val handler = Handler(Looper.getMainLooper())
    for (i in 0..appName.length) {
      handler.postDelayed({
        if (i < appName.length) {
          appNameText.text = appName.substring(0, i + 1)
        } else {
          
          typingCursor.alpha = 0f
          animateTagline()
        }
      }, i * 50L) 
    }
  }

  private fun animateTagline() {
    val taglineAnim = ObjectAnimator.ofFloat(taglineText, "alpha", 0f, 0.8f).apply {
      duration = 500
      startDelay = 300
    }

    taglineAnim.doOnEnd {
      animateLoadingSection()
    }

    taglineAnim.start()
  }

  private fun animateLoadingSection() {
    val loadingAnim = ObjectAnimator.ofFloat(loadingContainer, "alpha", 0f, 1f).apply {
      duration = 400
      startDelay = 500
    }

    loadingAnim.doOnEnd {
      
      Handler(Looper.getMainLooper()).postDelayed({
        navigateToNextActivity()
      }, 20)
    }

    loadingAnim.start()
  }

  private fun animateFloatingDots() {
    animateFloatingDot(floatingDot1, 2000, 0)
    animateFloatingDot(floatingDot2, 2500, 500)
    animateFloatingDot(floatingDot3, 1800, 1000)
  }

  private fun animateFloatingDot(dot: View, duration: Long, startDelay: Long) {
    val floatAnim = ObjectAnimator.ofPropertyValuesHolder(
      dot,
      PropertyValuesHolder.ofFloat("translationY", 0f, -30f, 0f),
      PropertyValuesHolder.ofFloat("alpha", 0.6f, 0.2f, 0.6f)
    ).apply {
      this.duration = duration
      this.startDelay = startDelay
      repeatCount = ValueAnimator.INFINITE
      repeatMode = ValueAnimator.REVERSE
      interpolator = AccelerateDecelerateInterpolator()
    }
    floatAnim.start()
  }

  private fun navigateToNextActivity() {
    val intent = if (currentUser != null) {
      
      updateLoadingText("Welcome back!")
      Intent(this, HomeActivity::class.java)
    } else {
      
      updateLoadingText("Setting up...")
      Intent(this, LoginActivity::class.java)
    }

    startActivity(intent)
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    finish()
  }

  private fun updateLoadingText(text: String) {
    loadingText.text = text
  }

  @SuppressLint("MissingSuperCall")
  override fun onBackPressed() {

  }

  companion object {
    private const val TAG = "MainActivity"
  }
}
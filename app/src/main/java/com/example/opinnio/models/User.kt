package com.example.opinnio.models

import com.google.firebase.firestore.PropertyName

data class User(
  val id: String = "",
  val email: String = "",
  val username: String = "",
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  val location: String = "",
  @get:PropertyName("imgUrl") @set:PropertyName("imgUrl")
  var profileImageUrl: String = ""
)

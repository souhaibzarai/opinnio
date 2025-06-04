package com.example.opinnio.models

import com.google.firebase.Timestamp

data class Post(
  val id: String = "",
  val authorId: String = "",
  val title: String = "",
  val body: String = "",
  var imageUrl: String = "",
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
  val likeCount: Int = 0,
)

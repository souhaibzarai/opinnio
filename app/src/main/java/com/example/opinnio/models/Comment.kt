package com.example.opinnio.models

import com.google.firebase.Timestamp
import java.util.Date

data class Comment(
  val id: String = "",
  val postId: String = "",
  val authorId: String = "",
  val content: String = "",
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)
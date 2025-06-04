package com.example.opinnio.adapters

import androidx.recyclerview.widget.DiffUtil
import com.example.opinnio.models.Notification

class NotificationDiffCallback : DiffUtil.ItemCallback<Notification>() {
  override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
    return oldItem.id == newItem.id
  }

  override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
    return oldItem == newItem
  }
}
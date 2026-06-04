package com.failureludo.data.auth

data class UserProfile(
    val uid: String,
    val name: String,
    val isGuest: Boolean
)

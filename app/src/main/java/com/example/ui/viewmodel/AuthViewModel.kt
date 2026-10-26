package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppStoreApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth = AppStoreApplication.auth

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(if (auth.currentUser != null) AuthState.Authenticated else AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            if (firebaseAuth.currentUser != null) {
                _authState.value = AuthState.Authenticated
            }
        }
    }

    fun emailLogin(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Fill all fields"
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _authError.value = null
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                val msg = when (e.message) {
                    else -> e.message ?: "Login failed"
                }
                _authError.value = msg
                _authState.value = AuthState.Error(msg)
            }
        }
    }

    fun emailRegister(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _authError.value = "Fill all fields"
            return
        }
        if (password.length < 6) {
            _authError.value = "Password min 6 chars"
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                result.user?.updateProfile(
                    com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                )?.await()
                _authError.value = null
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("already") == true -> "Already registered. Please log in."
                    else -> e.message ?: "Registration failed"
                }
                _authError.value = msg
                _authState.value = AuthState.Error(msg)
            }
        }
    }

    fun googleLogin(idToken: String) {
        if (idToken.isBlank()) {
            _authError.value = "Google sign-in cancelled"
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
                _authError.value = null
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                val msg = e.message ?: "Google login failed"
                _authError.value = msg
                _authState.value = AuthState.Error(msg)
            }
        }
    }

    fun sendResetEmail(email: String) {
        if (email.isBlank()) {
            _authError.value = "Enter email"
            return
        }
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                _authError.value = null
            } catch (e: Exception) {
                _authError.value = e.message ?: "Reset failed"
            }
        }
    }

    fun signOut() {
        try {
            auth.signOut()
            _authState.value = AuthState.Idle
            _authError.value = null
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Sign out failed", e)
        }
    }

    fun deleteAccount() {
        val user = auth.currentUser ?: return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                user.delete().await()
                _authError.value = null
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("recent") || msg.contains("reauthenticate") || msg.contains("10")) {
                    _authError.value = "REAUTH_NEEDED"
                    _authState.value = AuthState.Authenticated
                } else {
                    _authError.value = e.message ?: "Delete failed"
                    _authState.value = AuthState.Authenticated
                }
            }
        }
    }

    fun reauthAndDelete(idToken: String) {
        val user = auth.currentUser ?: return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                user.reauthenticateAndRetrieveData(credential).await()
                user.delete().await()
                _authError.value = null
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                _authError.value = e.message ?: "Delete failed"
                _authState.value = AuthState.Authenticated
            }
        }
    }

    fun reauthWithEmailPassword(email: String, password: String) {
        val user = auth.currentUser ?: return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val emailCredential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                user.reauthenticateAndRetrieveData(emailCredential).await()
                user.delete().await()
                _authError.value = null
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                _authError.value = e.message ?: "Delete failed"
                _authState.value = AuthState.Authenticated
            }
        }
    }

    fun clearError() { _authError.value = null }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

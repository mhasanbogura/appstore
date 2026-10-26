package com.example.ui.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private val iconShape = RoundedCornerShape(24.dp)

private fun buildGoogleSignInClient(context: android.content.Context): GoogleSignInClient {
    // requestServerAuthCode(..., true) forces Google to show its OWN consent screen
    // ("You're signing in to App Store" + privacy policy / ToS) after the account is
    // chosen, instead of silently accepting a previously granted scope. The auth code
    // itself is unused - we only need the consent step. Same setup as Meter Manager.
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestServerAuthCode(context.getString(R.string.default_web_client_id), true)
        .requestEmail()
        .build()
    return GoogleSignIn.getClient(context, gso)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthWelcomeScreen(
    onLoginClick: () -> Unit,
    onGoogleLoginClick: (idToken: String) -> Unit,
    onRegisterClick: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var googleError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    // Google's own consent screen already appeared during sign-in
                    // (forceConsent in buildGoogleSignInClient) - log in directly.
                    onGoogleLoginClick(idToken)
                } else {
                    googleError = "No ID token received"
                }
            } catch (e: ApiException) {
                val msg = when (e.statusCode) {
                    12501 -> "Sign-in cancelled"
                    12500 -> "Sign-in configuration error"
                    12502 -> "Sign-in already in progress"
                    else -> "Google sign-in failed (${e.statusCode})"
                }
                googleError = msg
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.app_store_icon),
            contentDescription = "App Store",
            modifier = Modifier.size(100.dp).clip(iconShape)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("App Store", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Browse, download & install apps", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(40.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Welcome", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Log in to sync your apps across devices", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.surface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Login with Email", color = MaterialTheme.colorScheme.surface)
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val client = buildGoogleSignInClient(context)
                        client.signOut()
                        launcher.launch(client.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Image(painter = painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Login with Google", fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Don't have an account? ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Sign up", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onRegisterClick() })
                }
            }
        }

        if (!googleError.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp)) {
                Text(text = googleError!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthLoginScreen(
    onLoginSuccess: (email: String, password: String) -> Unit,
    onGoogleLoginClick: (idToken: String) -> Unit,
    onForgotPassword: () -> Unit,
    onRegisterClick: () -> Unit,
    onBack: () -> Unit,
    authError: String?
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var googleError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    // Google's own consent screen already appeared during sign-in
                    // (forceConsent in buildGoogleSignInClient) - log in directly.
                    onGoogleLoginClick(idToken)
                } else {
                    googleError = "No ID token received"
                }
            } catch (e: ApiException) {
                val msg = when (e.statusCode) {
                    12501 -> "Sign-in cancelled"
                    12500 -> "Sign-in configuration error"
                    12502 -> "Sign-in already in progress"
                    else -> "Google sign-in failed (${e.statusCode})"
                }
                googleError = msg
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painter = painterResource(R.drawable.app_store_icon), contentDescription = null, modifier = Modifier.size(64.dp).clip(iconShape))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Welcome Back", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email address") }, leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onLoginSuccess(email, password) }), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { focusManager.clearFocus(); onLoginSuccess(email, password) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)) {
                    Text("Log in", color = MaterialTheme.colorScheme.surface)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    val client = buildGoogleSignInClient(context)
                    client.signOut()
                    launcher.launch(client.signInIntent)
                }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Image(painter = painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Login with Google", fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Forgot Password?", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onForgotPassword() })
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Not registered? Sign Up", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onRegisterClick() })
            }
        }

        val displayError = googleError ?: authError
        if (!displayError.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp)) {
                Text(text = displayError, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthRegisterScreen(
    onRegisterSuccess: (name: String, email: String, password: String) -> Unit,
    onGoogleLoginClick: (idToken: String) -> Unit,
    onLoginClick: () -> Unit,
    onBack: () -> Unit,
    authError: String?
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var googleError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    // Google's own consent screen already appeared during sign-in
                    // (forceConsent in buildGoogleSignInClient) - log in directly.
                    onGoogleLoginClick(idToken)
                } else {
                    googleError = "No ID token received"
                }
            } catch (e: ApiException) {
                val msg = when (e.statusCode) {
                    12501 -> "Sign-in cancelled"
                    12500 -> "Sign-in configuration error"
                    12502 -> "Sign-in already in progress"
                    else -> "Google sign-in failed (${e.statusCode})"
                }
                googleError = msg
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painter = painterResource(R.drawable.app_store_icon), contentDescription = null, modifier = Modifier.size(64.dp).clip(iconShape))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Create Account", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email address") }, leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (min 6 chars)") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onRegisterSuccess(name, email, password) }), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { focusManager.clearFocus(); onRegisterSuccess(name, email, password) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)) {
                    Text("SIGN UP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surface)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    val client = buildGoogleSignInClient(context)
                    client.signOut()
                    launcher.launch(client.signInIntent)
                }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Image(painter = painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign up with Google", fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Already have account? Log In", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onLoginClick() })
            }
        }

        val displayError = googleError ?: authError
        if (!displayError.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp)) {
                Text(text = displayError, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthForgotPasswordScreen(
    onSendReset: (email: String) -> Unit,
    onBackToLogin: () -> Unit,
    onBack: () -> Unit,
    authError: String?
) {
    BackHandler { onBack() }
    val focusManager = LocalFocusManager.current
    var email by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painter = painterResource(R.drawable.app_store_icon), contentDescription = null, modifier = Modifier.size(64.dp).clip(iconShape))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Forgot Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text("We'll send you a reset link", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Your email address") }, leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onSendReset(email) }), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { focusManager.clearFocus(); onSendReset(email); sent = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("Send Reset Link")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Back to Login", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onBackToLogin() })
            }
        }

        if (sent && authError == null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) {
                Text("Reset link sent! Check your email.", color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        } else if (!authError.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp)) {
                Text(text = authError, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }
    }
}

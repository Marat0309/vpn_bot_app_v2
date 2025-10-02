package com.guardx.vpnbot.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guardx.vpnbot.AppConfig
import com.guardx.vpnbot.R
import com.guardx.vpnbot.api.ServerSyncManager
import com.guardx.vpnbot.databinding.ActivityAuthBinding
import kotlinx.coroutines.launch

/**
 * Authentication Activity
 * Handles Telegram login and deep link token reception
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    // Your Telegram bot username
    private val TELEGRAM_BOT_USERNAME = "xuiseller_bot"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if already authenticated (but not if we have a deep link with new token)
        val hasDeepLink = intent?.data != null &&
                         intent.data?.scheme == "guardxvpn" &&
                         intent.data?.host == "auth"

        if (ServerSyncManager.isAuthenticated() && !hasDeepLink) {
            navigateToMain()
            return
        }

        // Handle deep link if activity was opened via deep link
        handleDeepLink(intent)

        // Check clipboard for token on resume (when user returns from Telegram)
        checkClipboardForToken()

        // Login button click
        binding.btnLoginTelegram.setOnClickListener {
            // Check if user entered token manually
            val manualToken = binding.etToken.text.toString().trim()
            if (manualToken.isNotEmpty()) {
                Log.d(AppConfig.TAG, "GuardX: Using manually entered token")
                handleTokenReceived(manualToken)
            } else {
                openTelegramBot()
            }
        }

        // Monitor clipboard for token paste
        binding.etToken.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Auto-paste from clipboard if it looks like a token
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                    if (text.startsWith("eyJ")) { // JWT tokens start with "eyJ"
                        binding.etToken.setText(text)
                        Log.d(AppConfig.TAG, "GuardX: Auto-pasted token from clipboard")
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        // Check clipboard when user returns to app (e.g., from Telegram)
        checkClipboardForToken()
    }

    /**
     * Check clipboard for JWT token and auto-login if found
     */
    private fun checkClipboardForToken() {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString() ?: ""
                // JWT tokens start with "eyJ"
                if (text.startsWith("eyJ") && text.length > 100) {
                    Log.d(AppConfig.TAG, "GuardX: Found token in clipboard, auto-logging in...")
                    // Auto-login with clipboard token
                    handleTokenReceived(text)
                }
            }
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "GuardX: Error checking clipboard", e)
        }
    }

    /**
     * Handle deep link from Telegram bot
     * Format: guardxvpn://auth?token=eyJhbGci...
     */
    private fun handleDeepLink(intent: Intent?) {
        Log.d(AppConfig.TAG, "GuardX: Checking deep link. Intent: ${intent?.data}")

        val data: Uri? = intent?.data

        if (data != null && data.scheme == "guardxvpn" && data.host == "auth") {
            val token = data.getQueryParameter("token")

            if (token != null) {
                Log.d(AppConfig.TAG, "GuardX: Received token via deep link: ${token.take(20)}...")
                handleTokenReceived(token)
            } else {
                Log.e(AppConfig.TAG, "GuardX: Deep link missing token parameter")
                Toast.makeText(this, "Ошибка авторизации: токен не получен", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.d(AppConfig.TAG, "GuardX: No valid deep link found")
        }
    }

    /**
     * Open Telegram bot for authentication
     */
    private fun openTelegramBot() {
        try {
            // Try to open Telegram app directly
            val telegramIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://t.me/$TELEGRAM_BOT_USERNAME?start=mobile_auth")
            }
            startActivity(telegramIntent)

            Toast.makeText(
                this,
                "Нажмите START в боте и вернитесь в приложение",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "GuardX: Error opening Telegram", e)
            Toast.makeText(this, "Не удалось открыть Telegram", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handle received JWT token
     * Save token and sync servers
     */
    private fun handleTokenReceived(token: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLoginTelegram.isEnabled = false

        lifecycleScope.launch {
            try {
                // Save token
                ServerSyncManager.saveToken(token)

                // Sync servers from API
                val importedCount = ServerSyncManager.syncServersFromApi(token)

                binding.progressBar.visibility = View.GONE

                if (importedCount > 0) {
                    Log.d(AppConfig.TAG, "GuardX: Successfully synced $importedCount servers")
                    Toast.makeText(
                        this@AuthActivity,
                        "Авторизация успешна! Загружено $importedCount серверов",
                        Toast.LENGTH_LONG
                    ).show()
                    navigateToMain()
                } else {
                    Log.e(AppConfig.TAG, "GuardX: Failed to sync servers")
                    Toast.makeText(
                        this@AuthActivity,
                        "Ошибка загрузки серверов. Проверьте подключение к интернету",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnLoginTelegram.isEnabled = true
                }

            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "GuardX: Error handling token", e)
                binding.progressBar.visibility = View.GONE
                binding.btnLoginTelegram.isEnabled = true
                Toast.makeText(
                    this@AuthActivity,
                    "Ошибка авторизации: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Navigate to MainActivity
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

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
    private val TELEGRAM_BOT_USERNAME = "your_guardx_bot" // CHANGE THIS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if already authenticated
        if (ServerSyncManager.isAuthenticated()) {
            navigateToMain()
            return
        }

        // Handle deep link if activity was opened via deep link
        handleDeepLink(intent)

        // Login button click
        binding.btnLoginTelegram.setOnClickListener {
            openTelegramBot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Handle deep link from Telegram bot
     * Format: guardxvpn://auth?token=eyJhbGci...
     */
    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data

        if (data != null && data.scheme == "guardxvpn" && data.host == "auth") {
            val token = data.getQueryParameter("token")

            if (token != null) {
                Log.d(AppConfig.TAG, "GuardX: Received token via deep link")
                handleTokenReceived(token)
            } else {
                Log.e(AppConfig.TAG, "GuardX: Deep link missing token parameter")
                Toast.makeText(this, "Ошибка авторизации: токен не получен", Toast.LENGTH_LONG).show()
            }
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

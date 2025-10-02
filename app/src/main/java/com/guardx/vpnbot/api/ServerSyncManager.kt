package com.guardx.vpnbot.api

import android.util.Log
import com.guardx.vpnbot.AppConfig
import com.guardx.vpnbot.handler.AngConfigManager
import com.guardx.vpnbot.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager for syncing servers from GuardX backend API
 */
object ServerSyncManager {

    /**
     * Load servers from API and import them into v2rayNG
     * @param token JWT token (without "Bearer " prefix)
     * @return Number of servers imported, or -1 on error
     */
    suspend fun syncServersFromApi(token: String): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(AppConfig.TAG, "GuardX: Syncing servers from API...")

            val response = ApiClient.apiService.getServers("Bearer $token")

            if (!response.isSuccessful) {
                Log.e(AppConfig.TAG, "GuardX: API error: ${response.code()} ${response.message()}")
                return@withContext -1
            }

            val servers = response.body()
            if (servers == null || servers.isEmpty()) {
                Log.e(AppConfig.TAG, "GuardX: Empty response body")
                return@withContext -1
            }

            Log.d(AppConfig.TAG, "GuardX: Received ${servers.size} servers from API")

            var importedCount = 0
            for (server in servers) {
                try {
                    // Convert server to vless:// URL
                    val vlessUrl = server.toVlessUrl()
                    Log.d(AppConfig.TAG, "GuardX: Generated vless URL for ${server.name}")

                    // Import vless:// URL using existing v2rayNG mechanism
                    // importBatchConfig returns Pair<Int, Int> (configCount, subCount)
                    val (configCount, _) = AngConfigManager.importBatchConfig(
                        vlessUrl,
                        "guardx_mobile", // subid for GuardX servers
                        append = true
                    )
                    if (configCount > 0) {
                        importedCount++
                        Log.d(AppConfig.TAG, "GuardX: Imported server ${server.name}")
                    } else {
                        Log.w(AppConfig.TAG, "GuardX: Failed to import server ${server.name}")
                    }
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "GuardX: Error importing server ${server.name}", e)
                }
            }

            Log.d(AppConfig.TAG, "GuardX: Successfully imported $importedCount servers")
            return@withContext importedCount

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "GuardX: Error syncing servers from API", e)
            return@withContext -1
        }
    }

    /**
     * Get user subscription info
     * @param token JWT token (without "Bearer " prefix)
     * @return SubscriptionInfo or null on error
     */
    suspend fun getSubscriptionInfo(token: String): SubscriptionInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(AppConfig.TAG, "GuardX: Getting subscription info...")

            val response = ApiClient.apiService.getSubscriptions("Bearer $token")

            if (!response.isSuccessful) {
                Log.e(AppConfig.TAG, "GuardX: Subscription API error: ${response.code()}")
                return@withContext null
            }

            val subscriptionResponse = response.body()
            return@withContext subscriptionResponse?.subscription

        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "GuardX: Error getting subscription info", e)
            return@withContext null
        }
    }

    /**
     * Save JWT token to storage
     */
    fun saveToken(token: String) {
        MmkvManager.encodeSettings("guardx_jwt_token", token)
        Log.d(AppConfig.TAG, "GuardX: Token saved")
    }

    /**
     * Get saved JWT token
     * @return Token or null if not found
     */
    fun getToken(): String? {
        return MmkvManager.decodeSettingsString("guardx_jwt_token")
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return !getToken().isNullOrEmpty()
    }

    /**
     * Clear saved token (logout)
     */
    fun clearToken() {
        MmkvManager.encodeSettings("guardx_jwt_token", null)
        Log.d(AppConfig.TAG, "GuardX: Token cleared")
    }
}

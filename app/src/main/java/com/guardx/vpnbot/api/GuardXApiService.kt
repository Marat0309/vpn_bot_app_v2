package com.guardx.vpnbot.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * GuardX Backend API Service
 *
 * Endpoints:
 * - GET /api/mobile/servers - Get available VPN servers
 * - GET /api/mobile/subscriptions - Get user subscriptions
 */
interface GuardXApiService {

    /**
     * Get list of available VPN servers
     * @param authorization Bearer JWT token
     * @return List of server configurations
     */
    @GET("/api/mobile/servers")
    suspend fun getServers(
        @Header("Authorization") authorization: String
    ): Response<List<ServerItem>>

    /**
     * Get user subscriptions
     * @param authorization Bearer JWT token
     * @return List of subscription details
     */
    @GET("/api/mobile/subscriptions")
    suspend fun getSubscriptions(
        @Header("Authorization") authorization: String
    ): Response<List<SubscriptionInfo>>
}

/**
 * Individual server configuration from API
 */
data class ServerItem(
    val name: String,
    val country_flag: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val security: String,
    val flow: String?,
    val public_key: String?,
    val short_id: String?,
    val sni: String?,
    val fp: String?,
    // xhttp transport parameters
    val type: String?,
    val encryption: String?,
    val host: String?,
    val mode: String?,
    val path: String?,
    val spx: String?
) {
    /**
     * Convert to vless:// URL format for v2rayNG
     */
    fun toVlessUrl(): String {
        // vless://uuid@address:port?params#name
        val params = buildString {
            append("security=$security")

            // xhttp transport parameters
            if (!type.isNullOrEmpty()) append("&type=$type")
            if (!encryption.isNullOrEmpty()) append("&encryption=$encryption")
            if (!host.isNullOrEmpty()) append("&host=$host")
            if (!mode.isNullOrEmpty()) append("&mode=$mode")
            if (!path.isNullOrEmpty()) append("&path=${java.net.URLEncoder.encode(path, "UTF-8")}")
            if (!spx.isNullOrEmpty()) append("&spx=${java.net.URLEncoder.encode(spx, "UTF-8")}")

            // Reality parameters
            if (!public_key.isNullOrEmpty()) append("&pbk=$public_key")
            if (!short_id.isNullOrEmpty()) append("&sid=$short_id")
            if (!sni.isNullOrEmpty()) append("&sni=$sni")
            if (!fp.isNullOrEmpty()) append("&fp=$fp")
            if (!flow.isNullOrEmpty()) append("&flow=$flow")
        }

        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        return "vless://$uuid@$address:$port?$params#$encodedName"
    }
}

/**
 * User subscription information
 */
data class SubscriptionInfo(
    val subscription_id: String,
    val active: Boolean,
    val plan_name: String,
    val expires_at: String?,
    val traffic_limit_gb: Double,
    val traffic_used_gb: Double,
    val traffic_remaining_gb: Double,
    val days_remaining: Int?
)

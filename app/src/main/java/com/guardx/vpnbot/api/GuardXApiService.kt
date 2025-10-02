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
     * @return Subscription details
     */
    @GET("/api/mobile/subscriptions")
    suspend fun getSubscriptions(
        @Header("Authorization") authorization: String
    ): Response<SubscriptionsResponse>
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
    val flow: String,
    val public_key: String,
    val short_id: String,
    val sni: String,
    val fp: String
) {
    /**
     * Convert to vless:// URL format for v2rayNG
     */
    fun toVlessUrl(): String {
        // vless://uuid@address:port?security=reality&pbk=public_key&sid=short_id&sni=sni&fp=fp#name
        val params = buildString {
            append("security=$security")
            if (public_key.isNotEmpty()) append("&pbk=$public_key")
            if (short_id.isNotEmpty()) append("&sid=$short_id")
            if (sni.isNotEmpty()) append("&sni=$sni")
            if (fp.isNotEmpty()) append("&fp=$fp")
            if (flow.isNotEmpty()) append("&flow=$flow")
        }

        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        return "vless://$uuid@$address:$port?$params#$encodedName"
    }
}

/**
 * Response from /api/mobile/subscriptions
 */
data class SubscriptionsResponse(
    val subscription: SubscriptionInfo
)

/**
 * User subscription information
 */
data class SubscriptionInfo(
    val status: String,
    val expires_at: String?,
    val traffic_total: Long?,
    val traffic_used: Long?,
    val traffic_remaining: Long?
)

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
    ): Response<ServersResponse>

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
 * Response from /api/mobile/servers
 */
data class ServersResponse(
    val servers: List<ServerItem>
)

/**
 * Individual server configuration
 */
data class ServerItem(
    val id: String,
    val name: String,
    val country_code: String,
    val city: String?,
    val vless_url: String,  // Ready-to-use vless:// URL
    val is_online: Boolean,
    val load: Int? // Server load percentage
)

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

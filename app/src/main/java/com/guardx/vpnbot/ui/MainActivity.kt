package com.guardx.vpnbot.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayout
import com.guardx.vpnbot.AppConfig
import com.guardx.vpnbot.AppConfig.VPN
import com.guardx.vpnbot.R
import com.guardx.vpnbot.databinding.ActivityMainBinding
import com.guardx.vpnbot.dto.EConfigType
import com.guardx.vpnbot.extension.toast
import com.guardx.vpnbot.extension.toastError
import com.guardx.vpnbot.handler.AngConfigManager
import com.guardx.vpnbot.handler.MigrateManager
import com.guardx.vpnbot.handler.MmkvManager
import com.guardx.vpnbot.helper.SimpleItemTouchHelperCallback
import com.guardx.vpnbot.handler.V2RayServiceManager
import com.guardx.vpnbot.util.Utils
import com.guardx.vpnbot.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        android.util.Log.d(AppConfig.TAG, "GuardX: VPN permission result: resultCode=${it.resultCode}, RESULT_OK=$RESULT_OK")
        if (it.resultCode == RESULT_OK) {
            android.util.Log.d(AppConfig.TAG, "GuardX: VPN permission granted, starting V2Ray")
            startV2Ray()
        } else {
            android.util.Log.e(AppConfig.TAG, "GuardX: VPN permission DENIED by user!")
        }
    }
    private val requestSubSettingActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        initGroupTab()
    }
    private val tabGroupListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab?) {
            val selectId = tab?.tag.toString()
            if (selectId != mainViewModel.subscriptionId) {
                mainViewModel.subscriptionIdChanged(selectId)
            }
        }

        override fun onTabUnselected(tab: TabLayout.Tab?) {
        }

        override fun onTabReselected(tab: TabLayout.Tab?) {
        }
    }
    private var mItemTouchHelper: ItemTouchHelper? = null
    val mainViewModel: MainViewModel by viewModels()

    // register activity result for requesting permission
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                when (pendingAction) {
                    Action.POST_NOTIFICATIONS -> {}
                    else -> {}
                }
            } else {
                toast(R.string.toast_permission_denied)
            }
            pendingAction = Action.NONE
        }

    private var pendingAction: Action = Action.NONE

    enum class Action {
        NONE,
        POST_NOTIFICATIONS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // GuardX: Check authentication
        if (!com.guardx.vpnbot.api.ServerSyncManager.isAuthenticated()) {
            android.util.Log.d(AppConfig.TAG, "GuardX: User not authenticated, redirecting to AuthActivity")
            startActivity(android.content.Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        setContentView(binding.root)
        title = getString(R.string.title_server)
        setSupportActionBar(binding.toolbar)

        // GuardX: Auto-sync servers on startup
        lifecycleScope.launch(Dispatchers.IO) {
            val token = com.guardx.vpnbot.api.ServerSyncManager.getToken()
            if (token != null) {
                android.util.Log.d(AppConfig.TAG, "GuardX: Auto-syncing servers on startup...")
                val importedCount = com.guardx.vpnbot.api.ServerSyncManager.syncServersFromApi(token)
                if (importedCount > 0) {
                    launch(Dispatchers.Main) {
                        android.util.Log.d(AppConfig.TAG, "GuardX: Auto-sync completed, reloading server list")
                        mainViewModel.reloadServerList()
                    }
                }
            }
        }

        binding.fab.setOnClickListener {
            android.util.Log.d(AppConfig.TAG, "GuardX: FAB clicked")
            if (mainViewModel.isRunning.value == true) {
                android.util.Log.d(AppConfig.TAG, "GuardX: VPN is running, stopping")
                V2RayServiceManager.stopVService(this)
            } else if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
                android.util.Log.d(AppConfig.TAG, "GuardX: VPN mode detected")
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    android.util.Log.d(AppConfig.TAG, "GuardX: VPN permission already granted, starting")
                    startV2Ray()
                } else {
                    android.util.Log.d(AppConfig.TAG, "GuardX: Requesting VPN permission")
                    requestVpnPermission.launch(intent)
                }
            } else {
                android.util.Log.d(AppConfig.TAG, "GuardX: Proxy mode, starting")
                startV2Ray()
            }
        }
        // Test connection on status text click
        binding.tvTestState.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            } else {
                toast(getString(R.string.connection_not_connected))
            }
        }

        binding.recyclerView.setHasFixedSize(true)
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)) {
            binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        } else {
            binding.recyclerView.layoutManager = GridLayoutManager(this, 1)
        }
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)

        // Removed ActionBarDrawerToggle - no hamburger icon
        // Drawer can still be opened by swipe or settings button
        binding.navView.setNavigationItemSelectedListener(this)

        // GuardX: Load subscription info when drawer is opened
        binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: android.view.View) {
                loadSubscriptionInfo()
            }
            override fun onDrawerClosed(drawerView: android.view.View) {}
            override fun onDrawerSlide(drawerView: android.view.View, slideOffset: Float) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        initGroupTab()
        setupViewModel()
        migrateLegacy()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pendingAction = Action.POST_NOTIFICATIONS
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (isRunning) {
                binding.fab.text = "ОТКЛЮЧИТЬСЯ"
                binding.fab.setBackgroundResource(R.drawable.bg_connect_button_inactive)
                setTestState(getString(R.string.connection_connected))
            } else {
                binding.fab.text = "ПОДКЛЮЧИТЬСЯ"
                binding.fab.setBackgroundResource(R.drawable.bg_connect_button_active)
                setTestState(getString(R.string.connection_not_connected))
            }
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun migrateLegacy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = MigrateManager.migrateServerConfig2Profile()
            launch(Dispatchers.Main) {
                if (result) {
                    toast(getString(R.string.migration_success))
                    mainViewModel.reloadServerList()
                } else {
                    //toast(getString(R.string.migration_fail))
                }
            }

        }
    }

    private fun initGroupTab() {
        binding.tabGroup.removeOnTabSelectedListener(tabGroupListener)
        binding.tabGroup.removeAllTabs()
        binding.tabGroup.isVisible = false

        val (listId, listRemarks) = mainViewModel.getSubscriptions(this)
        if (listId == null || listRemarks == null) {
            return
        }

        for (it in listRemarks.indices) {
            val tab = binding.tabGroup.newTab()
            tab.text = listRemarks[it]
            tab.tag = listId[it]
            binding.tabGroup.addTab(tab)
        }
        val selectIndex =
            listId.indexOf(mainViewModel.subscriptionId).takeIf { it >= 0 } ?: (listId.count() - 1)
        binding.tabGroup.selectTab(binding.tabGroup.getTabAt(selectIndex))
        binding.tabGroup.addOnTabSelectedListener(tabGroupListener)
        binding.tabGroup.isVisible = true
    }

    private fun startV2Ray() {
        android.util.Log.d(AppConfig.TAG, "GuardX: startV2Ray called")
        val selectedServer = MmkvManager.getSelectServer()
        android.util.Log.d(AppConfig.TAG, "GuardX: Selected server in startV2Ray: $selectedServer")
        if (selectedServer.isNullOrEmpty()) {
            android.util.Log.e(AppConfig.TAG, "GuardX: No server selected in startV2Ray!")
            toast(R.string.title_file_chooser)
            return
        }
        android.util.Log.d(AppConfig.TAG, "GuardX: Calling V2RayServiceManager.startVService")
        V2RayServiceManager.startVService(this)
    }

    private fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    /**
     * Sync servers from GuardX bot
     */
    private fun syncServersFromBot() {
        binding.pbWaiting.show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = com.guardx.vpnbot.api.ServerSyncManager.getToken()
                if (token == null) {
                    launch(Dispatchers.Main) {
                        toast("Ошибка: токен не найден. Выполните повторную авторизацию")
                        binding.pbWaiting.hide()
                    }
                    return@launch
                }

                Log.d(AppConfig.TAG, "GuardX: Syncing servers from bot...")
                val importedCount = com.guardx.vpnbot.api.ServerSyncManager.syncServersFromApi(token)

                launch(Dispatchers.Main) {
                    if (importedCount > 0) {
                        toast("Загружено серверов: $importedCount")
                        mainViewModel.reloadServerList()
                    } else {
                        toast("Не удалось загрузить серверы. Проверьте подключение к интернету")
                    }
                    binding.pbWaiting.hide()
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "GuardX: Error syncing servers", e)
                launch(Dispatchers.Main) {
                    toast("Ошибка синхронизации: ${e.message}")
                    binding.pbWaiting.hide()
                }
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.sync_servers -> {
            syncServersFromBot()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.open_settings_menu -> {
            // Open navigation drawer when settings button clicked
            binding.drawerLayout.openDrawer(GravityCompat.START)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }


    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

//    val mConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName?) {
//        }
//
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
//        }
//    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.per_app_proxy_settings -> startActivity(Intent(this, PerAppProxyActivity::class.java))
            R.id.routing_setting -> requestSubSettingActivity.launch(Intent(this, RoutingSettingActivity::class.java))
            R.id.user_asset_setting -> startActivity(Intent(this, UserAssetActivity::class.java))
            R.id.settings -> startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra("isRunning", mainViewModel.isRunning.value == true)
            )
            R.id.about -> startActivity(Intent(this, AboutActivity::class.java))
            R.id.logout -> showLogoutConfirmDialog()
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    /**
     * Show logout confirmation dialog
     */
    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton("Выйти") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Perform logout - clear token and redirect to auth
     */
    private fun performLogout() {
        // Stop VPN if running
        if (mainViewModel.isRunning.value == true) {
            V2RayServiceManager.stopVService(this)
        }

        // Clear authentication token
        com.guardx.vpnbot.api.ServerSyncManager.clearToken()

        // Show success message
        toast(R.string.logout_success)

        // Redirect to AuthActivity
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }

    /**
     * Load and display subscription information in navigation drawer
     */
    private fun loadSubscriptionInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = com.guardx.vpnbot.api.ServerSyncManager.getToken()
                if (token == null) {
                    launch(Dispatchers.Main) {
                        showNoSubscription()
                    }
                    return@launch
                }

                val subscriptionInfo = com.guardx.vpnbot.api.ServerSyncManager.getSubscriptionInfo(token)

                launch(Dispatchers.Main) {
                    if (subscriptionInfo != null && subscriptionInfo.active) {
                        displaySubscriptionInfo(subscriptionInfo)
                    } else {
                        showNoSubscription()
                    }
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "GuardX: Error loading subscription info", e)
                launch(Dispatchers.Main) {
                    showNoSubscription()
                }
            }
        }
    }

    /**
     * Display subscription information in navigation header
     */
    private fun displaySubscriptionInfo(info: com.guardx.vpnbot.api.SubscriptionInfo) {
        val headerView = binding.navView.getHeaderView(0)
        val statsCard = headerView.findViewById<androidx.cardview.widget.CardView>(R.id.subscription_stats_card)
        val noSubText = headerView.findViewById<android.widget.TextView>(R.id.tv_no_subscription)
        val planNameText = headerView.findViewById<android.widget.TextView>(R.id.tv_plan_name)
        val statusBadge = headerView.findViewById<android.widget.TextView>(R.id.tv_status_badge)
        val expiryDateText = headerView.findViewById<android.widget.TextView>(R.id.tv_expiry_date)
        val daysRemainingText = headerView.findViewById<android.widget.TextView>(R.id.tv_days_remaining)
        val trafficStatsText = headerView.findViewById<android.widget.TextView>(R.id.tv_traffic_stats)
        val trafficPercentText = headerView.findViewById<android.widget.TextView>(R.id.tv_traffic_percent)
        val trafficProgress = headerView.findViewById<android.widget.ProgressBar>(R.id.progress_traffic)
        val telegramIdText = headerView.findViewById<android.widget.TextView>(R.id.tv_telegram_id)
        val subscriptionActions = headerView.findViewById<android.widget.LinearLayout>(R.id.subscription_actions)
        val btnRenew = headerView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_renew_subscription)
        val btnTrial = headerView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_trial_period)
        val btnBalance = headerView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_balance)

        // Show stats card, hide "no subscription" message
        statsCard.visibility = android.view.View.VISIBLE
        noSubText.visibility = android.view.View.GONE
        subscriptionActions.visibility = android.view.View.VISIBLE

        // Set plan name
        planNameText.text = info.plan_name

        // Set status badge
        val (badgeText, badgeColor) = when {
            !info.active -> Pair("🔴 Истекла", android.graphics.Color.parseColor("#FF0066"))
            info.days_remaining != null && info.days_remaining <= 7 -> Pair("🟡 Истекает", android.graphics.Color.parseColor("#FFA500"))
            else -> Pair("🟢 Активна", android.graphics.Color.parseColor("#00FF88"))
        }
        statusBadge.text = badgeText
        statusBadge.setTextColor(badgeColor)

        // Set expiry date (parse ISO date and format as dd.MM.yyyy)
        val expiryDate = try {
            if (info.expires_at != null) {
                val isoDate = java.time.ZonedDateTime.parse(info.expires_at)
                val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                isoDate.format(formatter)
            } else {
                "Безлимит"
            }
        } catch (e: Exception) {
            Log.w(AppConfig.TAG, "GuardX: Error parsing expiry date: ${info.expires_at}", e)
            "—"
        }
        expiryDateText.text = expiryDate

        // Set days remaining
        val daysText = when {
            info.days_remaining == null -> "Безлимит"
            info.days_remaining == 0 -> "Истекает сегодня"
            info.days_remaining == 1 -> "1 день"
            info.days_remaining in 2..4 -> "${info.days_remaining} дня"
            else -> "${info.days_remaining} дней"
        }
        daysRemainingText.text = daysText

        // Set days color based on remaining time
        val daysColor = when {
            info.days_remaining == null -> android.graphics.Color.parseColor("#00FF88") // Green for unlimited
            info.days_remaining <= 3 -> android.graphics.Color.parseColor("#FF0066") // Red for critical
            info.days_remaining <= 7 -> android.graphics.Color.parseColor("#FFA500") // Orange for warning
            else -> android.graphics.Color.parseColor("#00FF88") // Green for normal
        }
        daysRemainingText.setTextColor(daysColor)

        // Set traffic stats
        trafficStatsText.text = String.format("%.1f / %.1f ГБ",
            info.traffic_remaining_gb,
            info.traffic_limit_gb)

        // Calculate traffic percentage remaining
        val percentRemaining = if (info.traffic_limit_gb > 0) {
            ((info.traffic_remaining_gb / info.traffic_limit_gb) * 100).toInt()
        } else {
            100 // Unlimited
        }

        trafficProgress.progress = percentRemaining
        trafficPercentText.text = "$percentRemaining%"

        // Set progress bar color based on remaining traffic
        val progressColor = when {
            percentRemaining >= 50 -> android.graphics.Color.parseColor("#00FF88") // Green
            percentRemaining >= 20 -> android.graphics.Color.parseColor("#FFA500") // Orange
            else -> android.graphics.Color.parseColor("#FF0066") // Red
        }
        trafficProgress.progressTintList = android.content.res.ColorStateList.valueOf(progressColor)
        trafficPercentText.setTextColor(progressColor)

        // Set Telegram ID (get from auth storage)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Extract telegram_id from JWT token
                val token = com.guardx.vpnbot.api.ServerSyncManager.getToken()
                if (token != null) {
                    val telegramId = extractTelegramIdFromToken(token)
                    launch(Dispatchers.Main) {
                        telegramIdText.text = "ID: $telegramId"
                    }
                }
            } catch (e: Exception) {
                Log.w(AppConfig.TAG, "GuardX: Error extracting telegram_id", e)
            }
        }

        // Setup button click listeners
        btnRenew.text = if (info.active) "🔁 Продлить подписку" else "💳 Купить подписку"
        btnRenew.setOnClickListener {
            openTelegramBot(if (info.active) "renew" else "buy")
        }

        btnBalance.setOnClickListener {
            openTelegramBot("balance")
        }

        // Trial button is hidden by default, shown only if needed
        btnTrial.visibility = android.view.View.GONE
    }

    /**
     * Extract telegram_id from JWT token payload
     */
    private fun extractTelegramIdFromToken(token: String): String {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
                val jsonObject = org.json.JSONObject(payload)
                jsonObject.optString("telegram_id", "—")
            } else {
                "—"
            }
        } catch (e: Exception) {
            Log.w(AppConfig.TAG, "GuardX: Error parsing JWT token", e)
            "—"
        }
    }

    /**
     * Open Telegram bot with deep link to specific action
     */
    private fun openTelegramBot(action: String) {
        try {
            // Get bot username from settings or use default
            val botUsername = "xuiseller_bot"

            // Create deep link
            val deepLink = when (action) {
                "buy" -> "https://t.me/$botUsername?start=buy"
                "renew" -> "https://t.me/$botUsername?start=renew"
                "trial" -> "https://t.me/$botUsername?start=trial"
                "balance" -> "https://t.me/$botUsername?start=balance"
                else -> "https://t.me/$botUsername"
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            startActivity(intent)

            Log.d(AppConfig.TAG, "GuardX: Opening Telegram bot with action: $action")
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "GuardX: Error opening Telegram bot", e)
            toast("Ошибка открытия бота. Убедитесь что Telegram установлен")
        }
    }

    /**
     * Show "no subscription" message and buttons for getting subscription
     */
    private fun showNoSubscription() {
        val headerView = binding.navView.getHeaderView(0)
        val statsCard = headerView.findViewById<androidx.cardview.widget.CardView>(R.id.subscription_stats_card)
        val noSubText = headerView.findViewById<android.widget.TextView>(R.id.tv_no_subscription)
        val subscriptionActions = headerView.findViewById<android.widget.LinearLayout>(R.id.subscription_actions)
        val btnRenew = headerView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_renew_subscription)
        val btnTrial = headerView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_trial_period)
        val btnBalance = headerView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_balance)
        val telegramIdText = headerView.findViewById<android.widget.TextView>(R.id.tv_telegram_id)

        statsCard.visibility = android.view.View.GONE
        noSubText.visibility = android.view.View.VISIBLE
        subscriptionActions.visibility = android.view.View.VISIBLE

        // Show "Buy" instead of "Renew"
        btnRenew.text = "💳 Купить подписку"
        btnRenew.setOnClickListener {
            openTelegramBot("buy")
        }

        // Show trial button for new users
        btnTrial.visibility = android.view.View.VISIBLE
        btnTrial.setOnClickListener {
            openTelegramBot("trial")
        }

        btnBalance.setOnClickListener {
            openTelegramBot("balance")
        }

        // Set Telegram ID even without subscription
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = com.guardx.vpnbot.api.ServerSyncManager.getToken()
                if (token != null) {
                    val telegramId = extractTelegramIdFromToken(token)
                    launch(Dispatchers.Main) {
                        telegramIdText.text = "ID: $telegramId"
                        telegramIdText.visibility = android.view.View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.w(AppConfig.TAG, "GuardX: Error extracting telegram_id", e)
            }
        }
    }
}
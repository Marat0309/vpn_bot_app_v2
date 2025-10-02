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
        lifecycleScope.launch {
            val token = com.guardx.vpnbot.api.ServerSyncManager.getToken()
            if (token != null) {
                android.util.Log.d(AppConfig.TAG, "GuardX: Auto-syncing servers...")
                com.guardx.vpnbot.api.ServerSyncManager.syncServersFromApi(token)
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
        // Test connection on button long press
        binding.fab.setOnLongClickListener {
            if (mainViewModel.isRunning.value == true) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            }
            true
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

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

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
        }

        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
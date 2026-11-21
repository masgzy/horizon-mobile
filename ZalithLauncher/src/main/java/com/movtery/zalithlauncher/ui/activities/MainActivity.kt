/*
 * Zalith Launcher 2 (Horizon fork)
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.movtery.guide.GuideHost
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.context.COPY_LABEL_LINK
import com.movtery.zalithlauncher.filemanager.FileManagerLauncher
import com.movtery.zalithlauncher.filemanager.events.FileManagerEvent
import com.movtery.zalithlauncher.filemanager.events.FileManagerEventRegistrar
import com.movtery.zalithlauncher.notification.NotificationManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.path.URL_SUPPORT
import com.movtery.zalithlauncher.setting.LauncherSettings
import com.movtery.zalithlauncher.setting.MiscSettings
import com.movtery.zalithlauncher.ui.base.BaseAppCompatActivity
import com.movtery.zalithlauncher.ui.base.ObserveFullScreenSetting
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.guide.GuideKeys
import com.movtery.zalithlauncher.ui.guide.GuideProgress
import com.movtery.zalithlauncher.ui.guide.NextTipLabel
import com.movtery.zalithlauncher.ui.guide.rememberAppGuides
import com.movtery.zalithlauncher.ui.screens.main.MainScreen
import com.movtery.zalithlauncher.ui.theme.ZalithLauncherTheme
import com.movtery.zalithlauncher.ui.theme.festivals.FestivalEffects
import com.movtery.zalithlauncher.ui.theme.festivals.FestivalTapObserver
import com.movtery.zalithlauncher.upgrade.TooFrequentOperationException
import com.movtery.zalithlauncher.upgrade.UpgradePrefs
import com.movtery.zalithlauncher.utils.copyText
import com.movtery.zalithlauncher.ui.toAndroidString
import com.movtery.zalithlauncher.utils.festival.getTodayFestivals
import com.movtery.zalithlauncher.ui.theme.showThemed
import com.movtery.zalithlauncher.utils.compareLangTag
import com.movtery.zalithlauncher.utils.isChinese
import com.movtery.zalithlauncher.utils.network.openLinkInternal
import com.movtery.zalithlauncher.utils.network.openLink
import com.movtery.zalithlauncher.viewmodel.BackgroundViewModel
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.LauncherUpgradeOperation
import com.movtery.zalithlauncher.viewmodel.LauncherUpgradeViewModel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import com.movtery.zalithlauncher.viewmodel.SponsorsDialog
import com.movtery.zalithlauncher.viewmodel.SponsorsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : BaseAppCompatActivity() {
    override fun isIgnoreNotch(): Boolean = LauncherSettings.launcherFullScreen.getValue()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        FestivalTapObserver.observe(event)
        return super.dispatchTouchEvent(event)
    }

    /**
     * 屏幕堆栈管理ViewModel
     */
    private val screenBackStackModel: ScreenBackStackViewModel by viewModels()

    /**
     * 错误信息ViewModel
     */
    private val errorViewModel: ErrorViewModel by viewModels()

    /**
     * 与Compose交互的事件ViewModel
     */
    val eventViewModel: EventViewModel by viewModels()

    /**
     * 启动器背景内容管理 ViewModel
     */
    val backgroundViewModel: BackgroundViewModel by viewModels()

    /**
     * 启动器更新状态 ViewModel
     */
    val launcherUpgradeViewModel: LauncherUpgradeViewModel by viewModels()

    /**
     * 当月赞助者 ViewModel
     */
    private val sponsorsViewModel: SponsorsViewModel by viewModels()

    /**
     * 文件管理器事件监听
     */
    private var fmEventRegistrar: FileManagerEventRegistrar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Horizon 引擎的更新提示路由到引擎管理页
        if (intent?.getBooleanExtra("open_horizon_manager", false) == true) {
            intent.removeExtra("open_horizon_manager")
            startActivity(Intent(this, HorizonManagerActivity::class.java))
        }

        refreshData()

        //注册文件管理器事件监听
        fmEventRegistrar = FileManagerEventRegistrar(this, ::onFileManagerEvent).also { it.start() }

        //初始化通知管理（创建渠道）
        NotificationManager.initManager(this)

        //检查更新
        if (launcherUpgradeViewModel.operation == LauncherUpgradeOperation.None) {
            lifecycleScope.launch {
                launcherUpgradeViewModel.checkOnAppStart()
            }
        }

        //拉取当月赞助者数据
        lifecycleScope.launch {
            sponsorsViewModel.loadOnStart(
                showLastMonth = GuideProgress.isPlayed(GuideKeys.Main)
            )
        }

        //错误信息展示
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                errorViewModel.errorEvents.collect { tm ->
                    errorViewModel.showErrorDialog(
                        context = this@MainActivity,
                        tm = tm
                    )
                }
            }
        }

        //事件处理
        lifecycleScope.launch {
            eventViewModel.events.collect { event ->
                when (event) {
                    is EventViewModel.Event.OpenLink -> {
                        val url = event.url
                        withContext(Dispatchers.Main) {
                            this@MainActivity.openLink(url)
                        }
                    }
                    is EventViewModel.Event.CheckUpdate -> {
                        checkUpdate()
                    }
                    is EventViewModel.Event.ShowSponsors -> {
                        sponsorsViewModel.openDialog()
                    }
                    is EventViewModel.Event.KeepScreen -> {
                        keepScreen(event.on)
                    }
                    is EventViewModel.Event.DownloadPlugins -> {
                        showDownloadPlugins(event.link)
                    }
                    is EventViewModel.Event.ShowToast -> {
                        Toast.makeText(
                            this@MainActivity,
                            event.text.toAndroidString(this@MainActivity),
                            event.duration
                        ).show()
                    }
                    is EventViewModel.Event.OpenFileManager -> {
                        FileManagerLauncher.launch(
                            context = this@MainActivity,
                            rootPath = event.rootPath,
                            currentPath = event.currentPath,
                            logsDir = PathManager.DIR_LAUNCHER_LOGS.absolutePath
                        )
                    }
                    else -> {
                        //忽略
                    }
                }
            }
        }

        val finishedGame = MiscSettings.finishedGame
        val showSponsorship = MiscSettings.showSponsorship

        val festivals = getTodayFestivals(
            containsChinese = isChinese(this@MainActivity)
        )

        setContent {
            ZalithLauncherTheme(
                backgroundViewModel = backgroundViewModel,
                festivals = festivals
            ) {
                ObserveFullScreenSetting(LauncherSettings.launcherFullScreen.state)

                val guides = rememberAppGuides(eventViewModel)
                GuideHost(
                    guides.mainScreen,
                    nextTip = { NextTipLabel(it) }
                ) {
                    MainScreen(
                        screenBackStackModel = screenBackStackModel,
                        eventViewModel = eventViewModel,
                        submitError = {
                            errorViewModel.showError(it)
                        }
                    )

                    //节日彩蛋效果层
                    FestivalEffects(
                        modifier = Modifier.fillMaxSize(),
                        festivals = festivals
                    )
                }

                //显示赞助支持的小弹窗
                if (finishedGame.state >= 100 && showSponsorship.state) {
                    SimpleAlertDialog(
                        title = stringResource(R.string.about_sponsor),
                        text = stringResource(R.string.game_saponsorship_finished_game, finishedGame.state),
                        dismissText = stringResource(R.string.generic_close),
                        onDismiss = {
                            showSponsorship.save(false)
                        },
                        onConfirm = {
                            showSponsorship.save(false)
                            eventViewModel.sendEvent(
                                EventViewModel.Event.OpenLink(URL_SUPPORT)
                            )
                        }
                    )
                }

                //检查更新操作流程
                LauncherUpgradeOperation(
                    operation = launcherUpgradeViewModel.operation,
                    onChanged = { launcherUpgradeViewModel.operation = it },
                    onIgnoredClick = { ver ->
                        UpgradePrefs.lastIgnoredVersion = ver
                    },
                    onLinkClick = { eventViewModel.sendEvent(EventViewModel.Event.OpenLink(it)) }
                )

                //当月赞助者对话框 / 上月赞助者结算报告
                SponsorsDialog(
                    viewModel = sponsorsViewModel,
                    onSupportClick = {
                        eventViewModel.sendEvent(EventViewModel.Event.OpenLink(URL_SUPPORT))
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_horizon_manager", false)) {
            intent.removeExtra("open_horizon_manager")
            startActivity(Intent(this, HorizonManagerActivity::class.java))
        }
    }

    override fun onDestroy() {
        fmEventRegistrar?.stop()
        fmEventRegistrar = null
        super.onDestroy()
    }

    /**
     * 文件管理器文件变更事件处理（Horizon 时代：仅记录，引擎自管它的目录）
     */
    private fun onFileManagerEvent(event: FileManagerEvent) {
        // no-op: 游戏目录由 Horizon 引擎自行管理
    }

    /**
     * 检查启动器更新
     */
    private fun checkUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = launcherUpgradeViewModel.checkManually(
                    onInProgress = {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.generic_in_progress), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onIsLatest = {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.upgrade_is_latest), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                if (!success) throw RuntimeException()
            } catch (_: TooFrequentOperationException) {
                //太频繁了
                return@launch
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.upgrade_get_remote_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
        }
    }

    /**
     * 是否保持屏幕不熄屏
     */
    private suspend fun keepScreen(on: Boolean) {
        withContext(Dispatchers.Main) {
            window?.apply {
                if (on) {
                    addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    /**
     * 弹出下载插件的链接提示对话框
     */
    private suspend fun showDownloadPlugins(link: EventViewModel.Event.DownloadPlugins.Links) {
        //匹配当前系统语言可见的网盘链接
        val locale = Locale.getDefault()
        val cloudDrive = link.cloudDrives.sortedByDescending {
            it.language.contains("_")
        }.find { drive ->
            locale.compareLangTag(drive.language)
        }

        withContext(Dispatchers.Main) {
            val builder = MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.plugin_download_title)
                .setMessage(R.string.plugin_download_summary)
                .setPositiveButton("Github") { dialog, _ ->
                    openLinkInternal(link.github)
                    dialog.dismiss()
                }

            cloudDrive?.link?.let { link ->
                builder.setNegativeButton(R.string.upgrade_cloud_drive) { dialog, _ ->
                    openLinkInternal(link)
                    dialog.dismiss()
                }
            }

            builder.showThemed()
        }
    }
}

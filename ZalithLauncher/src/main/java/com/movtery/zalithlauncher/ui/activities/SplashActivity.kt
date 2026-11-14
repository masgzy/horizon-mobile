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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.context.readAssetFile
import com.movtery.zalithlauncher.legal.AvailableAgreement
import com.movtery.zalithlauncher.path.GLOBAL_JSON
import com.movtery.zalithlauncher.setting.launcherMMKV
import com.movtery.zalithlauncher.ui.base.BaseAppCompatActivity
import com.movtery.zalithlauncher.ui.screens.navigateOnce
import com.movtery.zalithlauncher.ui.screens.splash.SplashOperation
import com.movtery.zalithlauncher.ui.screens.splash.SplashScreen
import com.movtery.zalithlauncher.ui.theme.ZalithLauncherTheme
import com.movtery.zalithlauncher.ui.theme.backgroundColor
import com.movtery.zalithlauncher.ui.theme.onBackgroundColor
import com.movtery.zalithlauncher.ui.theme.showThemed
import com.movtery.zalithlauncher.utils.device.Architecture
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.viewmodel.SplashBackStackViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SplashActivity"

private const val WELCOME_VERSION_KEY = "welcomeVersionKey"
private const val WELCOME_VERSION_VALUE = 1
private const val AGREEMENT_DATA_PATH = "legal/data.json"
private const val ACCEPTED_AGREEMENT_VERSION_KEY = "acceptedAgreementVersion"

/**
 * 首启流程（Horizon 外壳）：架构检查 → 欢迎/协议 → 主页。
 * Java 版时代的 JRE/组件解包环节已随其依赖移除——引擎、原版资源与 OpenJDK 运行时
 * 均由 HorizonManagerActivity 按需下载与准备。
 */
@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : BaseAppCompatActivity() {
    private val backStackViewModel: SplashBackStackViewModel by viewModels()
    private val _availableAgreement = MutableStateFlow<AvailableAgreement?>(null)
    private val availableAgreement = _availableAgreement.asStateFlow()

    private fun switchScreen(screen: SplashOperation) {
        backStackViewModel.splashScreen.backStack.navigateOnce(screen)
    }

    private suspend fun loadAvailableAgreement(): AvailableAgreement? = runCatching {
        withContext(Dispatchers.IO) {
            GLOBAL_JSON.decodeFromString(
                AvailableAgreement.serializer(),
                this@SplashActivity.readAssetFile(AGREEMENT_DATA_PATH)
            )
        }
    }.onFailure {
        Logger.warning(TAG, "Failed to load agreement data", it)
    }.getOrNull()

    private fun requiresAgreementConsent(): Boolean {
        val version = availableAgreement.value?.version ?: return false
        return launcherMMKV().getString(ACCEPTED_AGREEMENT_VERSION_KEY, null) != version
    }

    private fun acceptCurrentAgreement() {
        availableAgreement.value?.version?.let {
            launcherMMKV().putString(ACCEPTED_AGREEMENT_VERSION_KEY, it).apply()
        }
    }

    private fun showAgreementUpdateDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.agreement_update_title)
            .setMessage(R.string.agreement_update_message)
            .setPositiveButton(R.string.generic_confirm) { dialog, _ -> dialog.dismiss() }
            .showThemed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            ZalithLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor(),
                    contentColor = onBackgroundColor()
                ) {
                    SplashScreen(
                        onWelcomeFinished = {
                            launcherMMKV().putInt(WELCOME_VERSION_KEY, WELCOME_VERSION_VALUE).apply()
                            acceptCurrentAgreement()
                            switchScreen(SplashOperation.Loading)
                            swapToMain()
                        },
                        onExitClick = { finishAffinity() },
                        availableAgreement = availableAgreement,
                        screenViewModel = backStackViewModel
                    )
                }
            }
        }

        lifecycleScope.launch(Dispatchers.Main) {
            switchScreen(SplashOperation.Loading)

            // 检查安装包架构与设备不匹配
            if (!Architecture.isBuildArchCompatible(BuildKeys.BUILD_ARCH, Architecture.getDeviceArchitecture())) {
                switchScreen(SplashOperation.ArchMismatch)
                return@launch
            }

            withContext(Dispatchers.IO) {
                val agreement = loadAvailableAgreement()
                _availableAgreement.update { agreement }
            }

            // 检查协议同意情况
            val acceptedAgreementVersion = launcherMMKV().getString(ACCEPTED_AGREEMENT_VERSION_KEY, null)
            val agreementConsentRequired = requiresAgreementConsent()
            if (
                agreementConsentRequired ||
                launcherMMKV().getInt(WELCOME_VERSION_KEY, 0) < WELCOME_VERSION_VALUE
            ) {
                switchScreen(SplashOperation.Welcome)
                // 曾同意过旧版的协议，发布对话框提醒
                if (acceptedAgreementVersion != null && agreementConsentRequired) {
                    showAgreementUpdateDialog()
                }
            } else {
                swapToMain()
            }
        }
    }

    private fun swapToMain() {
        val forward = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(forward)
        finish()
    }
}

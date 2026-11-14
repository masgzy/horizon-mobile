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

package com.movtery.zalithlauncher.ui.screens.content

import android.content.Intent
import android.system.Os
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.guide.guideNode
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.horizon.EngineRepository
import com.movtery.zalithlauncher.game.horizon.HorizonDirs
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.components.ScalingActionButton
import com.movtery.zalithlauncher.ui.guide.GuideKeys
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.navigateTo
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import io.github.juanluci.starling.EngineHost
import io.github.juanluci.starling.GameActivity

/**
 * Horizon 外壳的主页：启动游戏、引擎与资源管理入口。
 * 引擎包安装/更新、原版资源镜像下载与 JRE 预置都在 HorizonManagerActivity 完成。
 */
@Composable
fun LauncherScreen(
    backStackViewModel: ScreenBackStackViewModel,
    onOpenLink: (String) -> Unit,
    startGuideOnce: (GuideKeys.Keys) -> Unit,
) {
    val context = LocalContext.current
    val engine = HorizonDirs.current(context)
    val refreshing = engine == null

    LaunchedEffect(Unit) {
        //发起新手引导
        startGuideOnce(GuideKeys.Main)
    }

    BaseScreen(
        screenKey = NormalNavKey.LauncherMain,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            //引擎状态卡片
            BackgroundCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .guideNode(GuideKeys.Main.Step.CardTip)
            ) {
                Column(
                    modifier = Modifier.padding(all = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.horizon_home_engine_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = engine?.manifest?.let {
                            stringResource(
                                R.string.horizon_home_engine_installed,
                                it.engineVersion, it.mcVersion
                            )
                        } ?: stringResource(R.string.horizon_home_engine_missing),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            //启动游戏
            ScalingActionButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !refreshing,
                onClick = {
                    runCatching {
                        val current = HorizonDirs.current(context)
                            ?: error("no engine installed")
                        val so = current.libraryFile()
                        EngineHost.engineLibraryPath = so.absolutePath
                        Os.setenv("HORIZON_ENGINE_LIBRARY", so.absolutePath, true)
                        context.startActivity(Intent(context, GameActivity::class.java))
                    }.onFailure { e ->
                        //没有安装引擎或启动失败：进入引擎管理页
                        context.startActivity(
                            Intent(context, com.movtery.zalithlauncher.ui.activities.HorizonManagerActivity::class.java)
                        )
                    }
                },
                content = {
                    Text(text = stringResource(R.string.horizon_home_launch))
                }
            )

            //引擎管理
            ScalingActionButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    context.startActivity(
                        Intent(context, com.movtery.zalithlauncher.ui.activities.HorizonManagerActivity::class.java)
                    )
                },
                content = {
                    Text(text = stringResource(R.string.horizon_home_manage))
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScalingActionButton(
                    modifier = Modifier.padding(bottom = 8.dp),
                    onClick = { onOpenLink(HORIZON_SITE_URL) },
                    content = { Text(text = stringResource(R.string.horizon_home_website)) }
                )
                ScalingActionButton(
                    modifier = Modifier.padding(bottom = 8.dp),
                    onClick = { onOpenLink(HORIZON_DOCS_URL) },
                    content = { Text(text = stringResource(R.string.horizon_home_docs)) }
                )
            }
        }
    }
}

/** Horizon 官网与文档 */
private const val HORIZON_SITE_URL = "https://playhorizon.dev"
private const val HORIZON_DOCS_URL = "https://playhorizon.dev/docs/"

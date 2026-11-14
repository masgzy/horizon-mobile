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

package com.movtery.zalithlauncher.ui.screens

import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.ui.androidText
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * 普通的屏幕（Horizon 外壳：账号、版本管理、下载、联机与控件路由已随 Java 版支持移除，
 * 引擎与资源管理在 HorizonManagerActivity）
 */
sealed interface NormalNavKey : TitledNavKey {
    @Contextual override val title: AndroidStringText?
        get() = null

    /** 启动器主页屏幕 */
    @Serializable data object LauncherMain : NormalNavKey
    /** Web屏幕 */
    @Serializable data class WebScreen(val url: String) : NormalNavKey
    /** 文件选择屏幕 */
    @Serializable data class FileSelector(
        val startPath: String,
        val selectFile: Boolean,
        val saveKey: TitledNavKey,
        val onSelected: (path: String) -> Unit
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.page_title_select_files)
    }

    /** 查看日志屏幕 */
    @Serializable data class LogView(
        val logPath: String
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.versions_overview_log)
    }

    /** 设置嵌套子屏幕 */
    sealed interface Settings : NormalNavKey {
        /** 启动器设置屏幕 */
        @Serializable data object Launcher : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_launcher)
        }
        /** 关于屏幕 */
        @Serializable data object AboutInfo : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_info_about)
        }
    }

    /** 协议展示屏幕 */
    @Serializable data class License(
        val raw: Int
    ): NormalNavKey
}

/*
 * Zalith Launcher 2
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

package com.movtery.zalithlauncher.viewmodel

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.ui.guide.GuideKeys
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class EventViewModel : ViewModel() {
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    /**
     * 发送一个事件
     */
    fun sendEvent(event: Event) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    sealed interface Event {
        /** 检查更新 */
        data object CheckUpdate : Event
        /** 查看当月赞助者 */
        data object ShowSponsors : Event
        /** 在浏览器访问链接 */
        data class OpenLink(val url: String) : Event
        /** 让 MainActivity 防止熄屏 */
        data class KeepScreen(val on: Boolean) : Event
        /** 打开下载插件的链接窗口 */
        data class DownloadPlugins(val link: Links): Event {
            data class Links(
                val github: String,
                val cloudDrives: List<CloudDrive> = emptyList()
            )
            /**
             * 网盘链接，按语言区分
             * @param language 语言标识
             * @param link 网盘链接
             */
            data class CloudDrive(
                val language: String,
                val link: String
            )
        }
        /** 在 MainActivity 中显示 Toast */
        data class ShowToast(
            val text: AndroidStringText,
            val duration: Int = Toast.LENGTH_SHORT
        ) : Event

        /** 打开文件管理器 */
        data class OpenFileManager(
            val rootPath: String,
            val currentPath: String? = null,
        ) : Event

        sealed interface Guide : Event {
            /** 启动一组引导 */
            data class StartGuide(val group: GuideKeys.Keys) : Guide
            /** 仅启动一组引导一次 */
            data class StartGuideOnce(val group: GuideKeys.Keys) : Guide
        }
    }
}

fun EventViewModel.sendKeepScreen(
    on: Boolean
) {
    sendEvent(EventViewModel.Event.KeepScreen(on))
}

fun EventViewModel.sendToast(
    text: AndroidStringText,
    duration: Int = Toast.LENGTH_SHORT,
) {
    sendEvent(EventViewModel.Event.ShowToast(text, duration))
}

fun EventViewModel.sendDLPlugin(
    githubLink: String,
    cloudDrives: List<EventViewModel.Event.DownloadPlugins.CloudDrive> = emptyList()
) {
    sendEvent(
        EventViewModel.Event.DownloadPlugins(
            link = EventViewModel.Event.DownloadPlugins.Links(
                github = githubLink,
                cloudDrives = cloudDrives
            )
        )
    )
}
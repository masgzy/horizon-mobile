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

package com.movtery.zalithlauncher.path

import android.content.Context
import android.os.Environment
import com.movtery.zalithlauncher.utils.file.ensureDirectorySilently
import com.movtery.zalithlauncher.utils.logging.Logger
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "PathManager"

class PathManager {
    companion object {
        lateinit var DIR_FILES_PRIVATE: File
        lateinit var DIR_FILES_EXTERNAL: File
        lateinit var DIR_CACHE: File
        lateinit var DIR_NATIVE_LIB: String

        lateinit var DIR_GAME: File
        lateinit var DIR_DATA_BASES: File
        lateinit var DIR_BACKGROUND: File
        lateinit var DIR_CACHE_HOME_PAGE: File
        lateinit var DIR_LAUNCHER_LOGS: File
        lateinit var DIR_NATIVE_LOGS: File
        lateinit var DIR_IMAGE_CACHE: File
        lateinit var DIR_STYLES: File

        lateinit var FILE_CRASH_REPORT: File
        lateinit var FILE_SETTINGS: File
        lateinit var FILE_LAUNCHER_BACKGROUND: File
        lateinit var FILE_TERRACOTTA_LOG: File
        lateinit var FILE_SPONSORS_CACHE: File

        fun refreshPaths(context: Context) {
            DIR_FILES_PRIVATE = context.filesDir
            DIR_FILES_EXTERNAL = context.getExternalFilesDir(null) ?: run {
                //from FCL (commit 744156a)
                val externalDir = File(Environment.getExternalStorageDirectory(), "Android/data/${context.packageName}/files")
                if (externalDir.ensureDirectorySilently()) externalDir
                else {
                    Logger.warning(TAG, "External files dir is inaccessible, falling back to internal storage: ${externalDir.absolutePath}")
                    File(DIR_FILES_PRIVATE, "data_files")
                }
            }
            DIR_CACHE = context.cacheDir
            DIR_NATIVE_LIB = context.applicationInfo.nativeLibraryDir

            DIR_DATA_BASES = File(DIR_FILES_PRIVATE.parentFile, "databases")
            DIR_GAME = File(DIR_FILES_PRIVATE, "games")
            DIR_BACKGROUND = File(DIR_FILES_PRIVATE, "background")
            DIR_CACHE_HOME_PAGE = File(DIR_CACHE, "remote_homepage")
            DIR_LAUNCHER_LOGS = File(DIR_FILES_EXTERNAL, "logs")
            DIR_NATIVE_LOGS = File(DIR_LAUNCHER_LOGS, "native")
            DIR_IMAGE_CACHE = File(DIR_CACHE, "images")
            DIR_STYLES = File(DIR_FILES_PRIVATE, "special_styles")

            FILE_CRASH_REPORT = File(DIR_LAUNCHER_LOGS, "launcher_crash.log")
            FILE_SETTINGS = File(DIR_FILES_PRIVATE, "settings.json")
            FILE_LAUNCHER_BACKGROUND = File(DIR_BACKGROUND, "background01.file")
            FILE_SPONSORS_CACHE = File(DIR_CACHE, "monthly_sponsors.json")

            createDirs()
            handleLegacy()
        }

        private fun createDirs() {
            listOfNotNull(
                DIR_GAME, DIR_BACKGROUND,
                DIR_CACHE_HOME_PAGE,
                DIR_LAUNCHER_LOGS, DIR_NATIVE_LOGS, DIR_IMAGE_CACHE,
                DIR_STYLES
            ).forEach { dir ->
                if (!dir.exists() && !dir.mkdirs()) {
                    Logger.warning(TAG, "Failed to create directory: ${dir.absolutePath}")
                }
            }
        }

        /**
         * 处理历史遗留的旧文件
         */
        private fun handleLegacy() {
            //Horizon 外壳：Java 版的历史遗留目录不再处理
        }
    }
}
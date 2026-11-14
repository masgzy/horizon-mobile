/*
 * Zalith Launcher 2 (Horizon fork)
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.game.horizon

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 引擎包的磁盘布局与当前激活状态。
 *
 * 目录约定（均在应用私有目录，与 starling 引擎的 internal_data_path 对齐）：
 * - files/engines/<engine_version>/   已安装的引擎包（libhorizon_android.so、prepare-kit.zip、manifest.json、SHA256SUMS）
 * - files/engines/active              单行文本：当前激活的 engine_version（原子重命名写入）
 * - files/engine-updates/             更新包的临时下载目录
 * - files/minecraft/                  引擎的下载根（引擎侧 files_root = private_home()/minecraft，
 *   private_home 未被设置时即 internal_data_path），预填充按官方启动器布局落盘
 */
object HorizonDirs {
    const val ENGINE_LIBRARY_NAME = "libhorizon_android.so"
    const val PREPARE_KIT_NAME = "prepare-kit.zip"
    const val MANIFEST_NAME = "manifest.json"
    const val SUMS_NAME = "SHA256SUMS"
    const val ACTIVE_NAME = "active"

    fun enginesRoot(context: Context): File = File(context.filesDir, "engines")
    fun activeMarker(context: Context): File = File(enginesRoot(context), ACTIVE_NAME)
    fun updatesRoot(context: Context): File = File(context.filesDir, "engine-updates")
    fun engineDir(context: Context, engineVersion: String): File = File(enginesRoot(context), engineVersion)
    fun engineLibrary(context: Context, engineVersion: String): File =
        File(engineDir(context, engineVersion), ENGINE_LIBRARY_NAME)

    /** 引擎侧的 Mojang 文件下载根：files/minecraft（官方启动器布局） */
    fun minecraftRoot(context: Context): File = File(context.filesDir, "minecraft")

    /** 当前激活的引擎；无安装或 active 指向缺失目录时为 null */
    fun current(context: Context): InstalledEngine? {
        val marker = activeMarker(context)
        val version = marker.takeIf { it.isFile }?.readText()?.trim().takeUnless { it.isNullOrEmpty() }
            ?: return null
        return installed(context).firstOrNull { it.manifest.engineVersion == version }
    }

    /** 全部已安装引擎，按安装目录名列出（解析失败的目录跳过） */
    fun installed(context: Context): List<InstalledEngine> {
        val root = enginesRoot(context)
        if (!root.isDirectory) return emptyList()
        return root.listFiles { f -> f.isDirectory }?.mapNotNull { dir ->
            parseManifest(dir)?.let { InstalledEngine(dir.name, dir, it) }
        }?.sortedBy { it.manifest.engineVersion } ?: emptyList()
    }

    fun parseManifest(engineDir: File): EngineManifest? = runCatching {
        EngineManifest.parse(File(engineDir, MANIFEST_NAME).readText())
    }.getOrNull()

    /**
     * 原子写入 active 标记：先写同目录临时文件再重命名，杜绝半写状态。
     */
    fun setActive(context: Context, engineVersion: String) {
        val root = enginesRoot(context).apply { mkdirs() }
        val tmp = File(root, "$ACTIVE_NAME.tmp")
        tmp.writeText(engineVersion)
        if (!tmp.renameTo(activeMarker(context))) {
            tmp.delete()
            throw IllegalStateException("Failed to activate engine $engineVersion")
        }
    }
}

/** 一个已安装并解析过清单的引擎 */
data class InstalledEngine(
    val dirName: String,
    val dir: File,
    val manifest: EngineManifest
) {
    fun libraryFile(): File = File(dir, HorizonDirs.ENGINE_LIBRARY_NAME)
    fun prepareKit(): File = File(dir, HorizonDirs.PREPARE_KIT_NAME)
    fun sumsWithVerify(): Boolean = File(dir, HorizonDirs.SUMS_NAME).isFile

    /**
     * 从 prepare-kit.zip 里读 research-lock.json 的 minecraft 段（版本钉定），
     * 预填充按它取版本 JSON 与 client/server jar。
     */
    fun readMinecraftPins(): MinecraftPins? = runCatching {
        java.util.zip.ZipFile(prepareKit()).use { zip ->
            val entry = zip.getEntry("research-lock.json") ?: return@use null
            val text = zip.getInputStream(entry).bufferedReader().readText()
            val obj = Json.parseToJsonElement(text).jsonObjectOrNull() ?: return@use null
            val mc = obj["minecraft"]?.jsonObjectOrNull() ?: return@use null
            MinecraftPins(
                version = mc["version"]?.jsonPrimitiveOrNull() ?: return@use null,
                manifestSha1 = mc["manifest_sha1"]?.jsonPrimitiveOrNull() ?: return@use null,
                clientSha1 = mc["client_sha1"]?.jsonPrimitiveOrNull(),
                serverSha1 = mc["server_sha1"]?.jsonPrimitiveOrNull(),
                javaMajor = mc["java_major"]?.jsonPrimitiveOrNull()?.toIntOrNull() ?: 25
            )
        }
    }.getOrNull()
}

/** research-lock.json 的 minecraft 钉定段 */
data class MinecraftPins(
    val version: String,
    val manifestSha1: String,
    val clientSha1: String?,
    val serverSha1: String?,
    val javaMajor: Int
)

// —— kotlinx JsonElement 的轻量解包，避免整棵树映射 ——

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull() =
    this as? kotlinx.serialization.json.JsonObject

private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        ?: (this as? kotlinx.serialization.json.JsonPrimitive)?.content

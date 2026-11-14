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
import android.os.Build
import com.movtery.zalithlauncher.BuildConfig
import com.movtery.zalithlauncher.game.download.engine.DownloadRequest
import com.movtery.zalithlauncher.game.download.engine.DownloadEngine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 引擎包的安装、激活、回滚与清理。
 *
 * 安装流程：下载 zip（多源）→ 解包到暂存目录 → 校验 SHA256SUMS（包内全文件）→
 * 清单协商（schema、ABI、min_shell_version）→ 原子落到 engines/<版本>/ → 原子置 active。
 * 保留最近 [KEEP_VERSIONS] 个版本，其余可清理；更新失败自动回退到原 active。
 */
object EngineRepository {
    const val SUPPORTED_SCHEMA = 1
    const val KEEP_VERSIONS = 2

    /** 安装进度：phase 为下载/校验/安装，ratio 0..1 */
    data class Progress(val phase: String, val ratio: Float)

    class InstallException(message: String) : RuntimeException(message)

    /** 当前已激活引擎的 so 绝对路径，供 io.github.juanluci.starling.EngineHost 使用 */
    fun activeLibraryPath(context: Context): String? =
        HorizonDirs.current(context)?.takeIf { it.libraryFile().isFile }?.libraryFile()?.absolutePath

    /** 本机首选 ABI（引擎包按 ABI 一一对应） */
    fun deviceAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    /**
     * 下载并安装 [asset] 指向的引擎包，成功后激活并返回已安装引擎。
     */
    suspend fun install(
        context: Context,
        asset: EngineReleaseClient.Asset,
        onProgress: (Progress) -> Unit = {}
    ): InstalledEngine {
        val updates = HorizonDirs.updatesRoot(context).apply { mkdirs() }
        val zip = File(updates, asset.name)

        onProgress(Progress("download", 0f))
        DownloadEngine.download(
            DownloadRequest(
                urls = EngineReleaseClient.assetUrls(asset),
                targetFile = zip,
                expectedSize = asset.size
            )
        ) { delta ->
            if (asset.size > 0) onProgress(Progress("download", (zip.length().toFloat() / asset.size).coerceIn(0f, 1f)))
            else onProgress(Progress("download", 0f))
            Unit
        }

        val installed = installLocalZip(context, zip, onProgress)
        zip.delete()
        return installed
    }

    /**
     * 从本地 zip 安装（手动导入场景）：校验与激活逻辑一致。
     */
    fun installLocalZip(
        context: Context,
        zip: File,
        onProgress: (Progress) -> Unit = {}
    ): InstalledEngine {
        onProgress(Progress("verify", 0f))
        val staging = File(HorizonDirs.enginesRoot(context), ".staging-${System.currentTimeMillis()}")
        staging.mkdirs()
        val previousActive = HorizonDirs.activeMarker(context).takeIf { it.isFile }?.readText()?.trim()

        try {
            unzip(zip, staging)
            val manifest = HorizonDirs.parseManifest(staging)
                ?: throw InstallException("engine bundle has no valid manifest.json")
            if (manifest.schema > SUPPORTED_SCHEMA) {
                throw InstallException("engine bundle schema ${manifest.schema} is newer than this shell supports (${SUPPORTED_SCHEMA})")
            }
            if (!manifest.isCompatibleWith(Build.SUPPORTED_ABIS, BuildConfig.VERSION_NAME)) {
                throw InstallException("engine bundle ${manifest.abi} does not match this device, or needs a newer shell (min ${manifest.minShellVersion})")
            }
            verifySums(staging)
            onProgress(Progress("install", 0f))

            val target = HorizonDirs.engineDir(context, manifest.engineVersion)
            if (target.exists()) target.deleteRecursively()
            if (!staging.renameTo(target)) {
                staging.copyRecursively(target, overwrite = true)
                staging.deleteRecursively()
            }
            HorizonDirs.setActive(context, manifest.engineVersion)
            cleanup(context)
            onProgress(Progress("install", 1f))
            return InstalledEngine(manifest.engineVersion, target, manifest)
        } catch (e: Throwable) {
            // 安装失败：active 保持不变（或恢复为安装前的指向），不留半成品
            staging.deleteRecursively()
            previousActive?.let { runCatching { HorizonDirs.setActive(context, it) } }
            throw e
        }
    }

    /** 激活指定版本（回滚） */
    fun activate(context: Context, engine: InstalledEngine) {
        require(engine.libraryFile().isFile) { "engine ${engine.dirName} is missing its library" }
        HorizonDirs.setActive(context, engine.manifest.engineVersion)
    }

    /** 删除指定版本；正在激活的版本不允许删 */
    fun remove(context: Context, engine: InstalledEngine) {
        val current = HorizonDirs.current(context)
        check(current?.dirName != engine.dirName) { "cannot remove the active engine" }
        engine.dir.deleteRecursively()
    }

    /** 只保留最近 KEEP_VERSIONS 个版本（按版本名排序，激活的版本始终保留） */
    fun cleanup(context: Context) {
        val active = HorizonDirs.current(context)?.dirName
        val versions = HorizonDirs.installed(context)
            .map { it.dirName }
            .filter { it != active }
        versions.dropLast(KEEP_VERSIONS).forEach { name ->
            HorizonDirs.engineDir(context, name).deleteRecursively()
        }
    }

    /** 包内 SHA256SUMS 逐行校验（含 so、kit、java 包装器），任一不符即失败 */
    private fun verifySums(stage: File) {
        val sums = File(stage, HorizonDirs.SUMS_NAME).takeIf { it.isFile }
            ?: throw InstallException("engine bundle has no ${HorizonDirs.SUMS_NAME}")
        sums.readLines().filter { it.isNotBlank() }.forEach { line ->
            val (expected, name) = line.split("  ", limit = 2).map { it.trim() }
            val file = File(stage, name)
            if (!file.isFile) throw InstallException("engine bundle misses $name")
            val actual = sha256Hex(file)
            if (!actual.equals(expected, ignoreCase = true)) {
                throw InstallException("engine bundle file $name failed checksum")
            }
        }
    }

    private fun unzip(zip: File, into: File) {
        ZipInputStream(FileInputStream(zip).buffered()).use { input ->
            val canonicalInto = into.canonicalFile
            while (true) {
                val entry: ZipEntry = input.nextEntry ?: break
                val out = File(into, entry.name)
                // 防 zip 路径穿越：解出的文件必须还在暂存目录内
                if (!out.canonicalFile.path.startsWith(canonicalInto.path)) {
                    throw InstallException("engine bundle entry escapes the staging dir: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                input.closeEntry()
            }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

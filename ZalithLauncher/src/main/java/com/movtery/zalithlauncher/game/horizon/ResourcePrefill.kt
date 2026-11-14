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
import com.movtery.zalithlauncher.game.addons.mirror.mapBMCLMirrorUrls
import com.movtery.zalithlauncher.game.download.engine.BatchDownloader
import com.movtery.zalithlauncher.game.download.engine.DownloadRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest

/**
 * 首次启动前的资源预填充：按引擎包 prepare-kit 里 research-lock.json 的钉定，
 * 把 Mojang 的版本 JSON、client/server jar、libraries、assets（index + 全部 objects）
 * 以官方启动器布局落盘到引擎的下载根（files/minecraft）。
 *
 * 全部下载走 BMCLAPI 镜像映射（ZL2 三档设置语义：AUTO 档大陆镜像优先、失败回退官方），
 * 并逐文件 SHA-1 校验；引擎自身对「已存在且哈希一致」的文件会跳过，因此预填充完成后
 * 引擎首启的下载步退化为一次校验遍历，缺失文件由引擎自行回官方源补齐。
 */
object ResourcePrefill {

    data class PrefillProgress(
        val stage: Stage,
        val filesDone: Int,
        val filesTotal: Int
    )

    enum class Stage { VERSION_JSON, INDEX, FILES }

    class PrefillException(message: String) : RuntimeException(message)

    /**
     * 执行预填充。已存在且 SHA-1 一致的文件直接跳过（与引擎下载语义一致，可中断续跑）。
     */
    suspend fun prefill(
        context: Context,
        engine: InstalledEngine,
        onProgress: (PrefillProgress) -> Unit = {},
        skipServerJar: Boolean = false
    ) {
        val pins = engine.readMinecraftPins()
            ?: throw PrefillException("prepare-kit.zip has no research-lock.json minecraft pins")
        val root = HorizonDirs.minecraftRoot(context)
        val v = pins.version
        val versionDir = File(root, "versions/$v").apply { mkdirs() }

        // 1. 版本 JSON：按钉定的 manifest sha1 取包（piston-meta → BMCLAPI）
        val manifestFile = File(versionDir, "$v.json")
        if (!manifestFile.isFile || sha1Hex(manifestFile) != pins.manifestSha1) {
            onProgress(PrefillProgress(Stage.VERSION_JSON, 0, 1))
            val pinnedUrl = "https://piston-meta.mojang.com/v1/packages/${pins.manifestSha1}/$v.json"
            com.movtery.zalithlauncher.game.download.engine.Fetcher.downloadFile(
                urls = pinnedUrl.mapBMCLMirrorUrls(),
                targetFile = manifestFile,
                sha1 = pins.manifestSha1
            )
            onProgress(PrefillProgress(Stage.VERSION_JSON, 1, 1))
        }
        val manifest = Json.parseToJsonElement(manifestFile.readText()).jsonObject

        // 2. 组装批量下载清单：client / server / libraries / assets index + objects
        val requests = mutableListOf<DownloadRequest>()

        val downloads = manifest["downloads"]?.jsonObject
            ?: throw PrefillException("version JSON lists no downloads")
        fun jarRequest(key: String, target: File, pinnedSha1: String?): DownloadRequest? {
            if (pinnedSha1 == null) return null
            val d = downloads[key]?.jsonObject ?: throw PrefillException("version JSON lists no $key download")
            val url = d["url"]?.jsonPrimitive?.content ?: throw PrefillException("version JSON lists no $key url")
            val sha1 = d["sha1"]?.jsonPrimitive?.content
            if (sha1 != pinnedSha1) throw PrefillException("version JSON lists another $key jar than the engine pins")
            return request(url, target, sha1, d["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L)
        }

        jarRequest("client", File(versionDir, "$v.jar"), pins.clientSha1)?.let(requests::add)
        if (!skipServerJar) {
            jarRequest("server", File(versionDir, "$v-server.jar"), pins.serverSha1)?.let(requests::add)
        }

        // libraries：官方 rules（os=linux）过滤，路径取 downloads.artifact.path
        manifest["libraries"]?.jsonArray?.forEach { lib ->
            val obj = lib.jsonObject
            if (!libraryApplies(obj)) return@forEach
            val artifact = obj["downloads"]?.jsonObject?.get("artifact")?.jsonObject ?: return@forEach
            val path = artifact["path"]?.jsonPrimitive?.content ?: return@forEach
            if (path.contains("..")) return@forEach
            val url = artifact["url"]?.jsonPrimitive?.content ?: return@forEach
            val sha1 = artifact["sha1"]?.jsonPrimitive?.content ?: return@forEach
            val size = artifact["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L
            request(url, File(root, "libraries").resolve(path), sha1, size)?.let(requests::add)
        }

        onProgress(PrefillProgress(Stage.INDEX, 0, 1))
        val index = manifest["assetIndex"]?.jsonObject
            ?: throw PrefillException("version JSON lists no assetIndex")
        val indexId = index["id"]?.jsonPrimitive?.content ?: throw PrefillException("assetIndex has no id")
        val indexSha1 = index["sha1"]?.jsonPrimitive?.content ?: throw PrefillException("assetIndex has no sha1")
        val indexFile = File(root, "assets/indexes/$indexId.json").apply { parentFile?.mkdirs() }
        if (!indexFile.isFile || sha1Hex(indexFile) != indexSha1) {
            val indexUrl = index["url"]?.jsonPrimitive?.content ?: throw PrefillException("assetIndex has no url")
            com.movtery.zalithlauncher.game.download.engine.Fetcher.downloadFile(
                urls = indexUrl.mapBMCLMirrorUrls(),
                targetFile = indexFile,
                sha1 = indexSha1
            )
        }
        onProgress(PrefillProgress(Stage.INDEX, 1, 1))

        // assets objects：<resources>/<2>/<hash> → assets/objects/<2>/<hash>
        val objects = Json.parseToJsonElement(indexFile.readText())
            .jsonObject["objects"]?.jsonObject ?: throw PrefillException("asset index lists no objects")
        objects.values.forEach { value ->
            val obj = value.jsonObject
            val hash = obj["hash"]?.jsonPrimitive?.content
                ?.takeIf { it.length == 40 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
                ?: return@forEach
            val size = obj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L
            val resources = "https://resources.download.minecraft.net/${hash.substring(0, 2)}/$hash"
            request(resources, File(root, "assets/objects/${hash.substring(0, 2)}/$hash"), hash, size)
                ?.let(requests::add)
        }

        // 3. 批量下载（跳过已存在且哈希一致的文件）
        val pending = requests.filter { needsDownload(it.targetFile, it.sha1) }
        onProgress(PrefillProgress(Stage.FILES, requests.size - pending.size, requests.size))

        val batch = BatchDownloader(pending)
        var done = requests.size - pending.size
        batch.onFileSuccess = { _ ->
            synchronized(this) { done++ }
            onProgress(PrefillProgress(Stage.FILES, done, requests.size))
        }
        batch.onFailureFilter = { _, _ -> false }
        batch.run()
        batch.lastRunFailures.takeIf { it.isNotEmpty() }?.let { failures ->
            val names = failures.keys.joinToString(", ")
            throw PrefillException("Failed to download ${failures.size} file(s): $names")
        }
    }

    /** 引擎包清单里钉定的 JRE（当前架构），无对应构建（x86 32 位）时为 null */
    fun jrePin(engine: InstalledEngine): EngineManifest.JrePin? {
        val abi = EngineRepository.deviceAbi()
        return engine.manifest.jre[abi] ?: when (abi) {
            "arm64-v8a" -> engine.manifest.jre["arm64"]
            "armeabi-v7a" -> engine.manifest.jre["armeabi-v7a"]
            "x86_64" -> engine.manifest.jre["x86_64"]
            else -> null
        }
    }

    /**
     * 预置 OpenJDK 25 Android 构建：引擎侧的流程是「已解包（带 .starling-runtime 标记）则跳过，
     * 否则下载 → 解包 → 写标记 → 删包」。外壳代为执行整条链：镜像加速下载 → sha256 校验 →
     * 解包到 files/preparer/jre25 → 写标记。引擎随后检测标记命中即跳过自己的下载。
     */
    suspend fun prefillJre(context: Context, engine: InstalledEngine): Boolean {
        val pin = jrePin(engine) ?: return false
        val dir = File(context.filesDir, "preparer/jre25")
        val marker = File(dir, ".starling-runtime")
        if (marker.isFile && marker.readText().trim() == pin.sha256) return true

        val archive = File(context.cacheDir, pin.url.substringAfterLast('/'))
        val urls = EngineReleaseClient.mirrorPrefix()
            .takeIf { it.isNotEmpty() && pin.url.startsWith("https://github.com/") }
            ?.let { listOf("${it}${pin.url}", pin.url) }
            ?: listOf(pin.url)
        com.movtery.zalithlauncher.game.download.engine.Fetcher.downloadFile(urls, archive)
        if (sha256Hex(archive) != pin.sha256) {
            archive.delete()
            throw PrefillException("The fetched OpenJDK runtime archive failed its sha256 check")
        }

        val canonicalDir = dir.canonicalFile
        org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
            org.apache.commons.compress.compressors.xz.XZCompressorInputStream(archive.inputStream().buffered())
        ).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val out = File(dir, entry.name)
                if (!out.canonicalFile.path.startsWith(canonicalDir.path)) {
                    throw PrefillException("The runtime archive escapes its folder: ${entry.name}")
                }
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it) }
                    // owner-exec 位（0o100）：解包出可执行的 bin/*，与引擎解包语义一致
                    if (entry.mode and 64 != 0) out.setExecutable(true)
                }
            }
        }
        marker.writeText(pin.sha256)
        archive.delete()
        return true
    }

    /** 构造多源下载请求（官方 → BMCLAPI 镜像映射，AUTO 档大陆镜像优先） */
    private fun request(url: String, target: File, sha1: String, size: Long): DownloadRequest? =
        DownloadRequest(urls = url.mapBMCLMirrorUrls(), targetFile = target, sha1 = sha1, expectedSize = size)

    /** 引擎语义：已存在且 SHA-1 一致的文件跳过 */
    private fun needsDownload(target: File, sha1: String?): Boolean =
        !(target.isFile && sha1 != null && sha1Hex(target) == sha1)

    /** Mojang 库的 rules 过滤（os=linux），与引擎侧 files.rs 的 library_applies 对齐 */
    private fun libraryApplies(library: JsonObject): Boolean {
        val rules = library["rules"]?.jsonArray ?: return true
        var allowed = true
        for (rule in rules) {
            val obj = rule.jsonObject
            val action = obj["action"]?.jsonPrimitive?.content ?: continue
            val os = obj["os"]?.jsonObject
            val matches = when {
                os == null -> true
                os["name"]?.jsonPrimitive?.content == "linux" -> true
                else -> false
            }
            if (matches) allowed = action == "allow"
        }
        return allowed
    }

    private fun sha1Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
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

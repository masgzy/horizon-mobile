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

import com.movtery.zalithlauncher.setting.launcherMMKV
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Horizon 引擎包发布源：GitHub Releases（horizon-mobile 仓库的 engine-bundle 工作流）。
 * API 走直连（国内一般可达）；release 资产下载走多源：可配置镜像前缀 + 直连回退，
 * 下载本身复用启动器的多源下载引擎。
 */
object EngineReleaseClient {
    /** 引擎包所在的仓库（owner/repo）；可被设置覆盖 */
    const val DEFAULT_REPO = "masgzy/horizon-mobile"

    private val JSON = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Release(
        @SerialName("tag_name") val tagName: String,
        @SerialName("name") val name: String? = null,
        @SerialName("prerelease") val prerelease: Boolean = false,
        @SerialName("draft") val draft: Boolean = false,
        @SerialName("assets") val assets: List<Asset> = emptyList()
    )

    @Serializable
    data class Asset(
        @SerialName("name") val name: String,
        @SerialName("browser_download_url") val url: String,
        @SerialName("size") val size: Long = 0
    )

    /** MMKV 里配置的仓库（空则用默认） */
    fun configuredRepo(): String = launcherMMKV().decodeString("horizon_release_repo", "").orEmpty()
        .ifBlank { DEFAULT_REPO }

    /** MMKV 里配置的 GitHub 下载加速前缀（如 ghproxy 类），空则只走直连 */
    fun mirrorPrefix(): String = launcherMMKV().decodeString("horizon_github_mirror", "").orEmpty().trim().also {
        if (it.isNotEmpty() && !it.endsWith("/")) launcherMMKV().encode("horizon_github_mirror", "$it/")
    }

    private fun apiRoot(): String = launcherMMKV().decodeString("horizon_github_api", "").orEmpty()
        .ifBlank { "https://api.github.com" }

    /**
     * 拉取 releases 列表（含预发布，不含草稿）。写缓存文件后解析。
     */
    suspend fun fetchReleases(cacheDir: File): List<Release> {
        val repo = configuredRepo()
        val target = File(cacheDir, "horizon-releases.json")
        val apiRootOverride = apiRoot()
        val urls = buildList {
            add("$apiRootOverride/repos/$repo/releases?per_page=30")
            mirrorPrefix().takeIf { it.isNotEmpty() }?.let { add("${it}https://api.github.com/repos/$repo/releases?per_page=30") }
        }
        com.movtery.zalithlauncher.game.download.engine.Fetcher.downloadFile(urls, target)
        val text = target.readText()
        return JSON.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Release.serializer()), text)
    }

    /**
     * 找到最新一个提供本机 ABI 引擎包的 release；[includePrerelease] 为 false 时跳过预发布。
     */
    fun latestBundle(releases: List<Release>, abi: String, includePrerelease: Boolean): Pair<Release, Asset>? {
        for (release in releases) {
            if (release.draft) continue
            if (release.prerelease && !includePrerelease) continue
            val asset = release.assets.firstOrNull {
                it.name == EngineManifest.bundleName(release.tagName, abi)
            }
            if (asset != null) return release to asset
        }
        return null
    }

    /** 指定 release 里本机 ABI 的引擎包资产；没有则返回 null（“选版本”安装用） */
    fun bundleIn(release: Release, abi: String): Asset? =
        release.assets.firstOrNull { it.name == EngineManifest.bundleName(release.tagName, abi) }

    /** release 资产的多源下载地址：镜像前缀优先，直连兜底 */
    fun assetUrls(asset: Asset): List<String> {
        val prefix = mirrorPrefix()
        return buildList {
            if (prefix.isNotEmpty()) add(prefix + asset.url)
            add(asset.url)
        }
    }
}

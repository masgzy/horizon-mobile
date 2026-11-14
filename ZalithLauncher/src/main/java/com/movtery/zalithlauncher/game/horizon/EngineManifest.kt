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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Horizon 引擎包清单（manifest.json，schema 1）。由 horizon-mobile 仓库的
 * engine-bundle CI 生成（packaging/android/build_engine_bundle.py），随引擎包发布。
 * 外壳以此与引擎包协商兼容性，并校验包内容完整性。
 */
@Serializable
data class EngineManifest(
    /** 清单结构版本：高于外壳认识的结构时必须拒绝，而不是猜 */
    @SerialName("schema") val schema: Int,
    /** 引擎版本名（nightly-<date>-<commit>） */
    @SerialName("engine_version") val engineVersion: String,
    /** 引擎对应的 Minecraft 版本（当前钉死 26.3） */
    @SerialName("mc_version") val mcVersion: String,
    /** prepare-kit 的资源清单版本（OUTPUT_VERSION），变化时需重新准备资源 */
    @SerialName("kit_output_version") val kitOutputVersion: Int,
    /** 对外壳的最低版本要求，低于它时先升级外壳 */
    @SerialName("min_shell_version") val minShellVersion: String,
    /** 引擎库的 ABI */
    @SerialName("abi") val abi: String,
    /** 外壳 APK 必须随包携带的 java/javac 启动包装器 */
    @SerialName("java_launchers") val javaLaunchers: List<String> = emptyList(),
    /** 各架构的 OpenJDK 25 运行时钉定信息（x86 32 位为 null：无 javac，走 PC 导入） */
    @SerialName("jre") val jre: Map<String, JrePin?> = emptyMap(),
    /** 包内关键文件的 sha256 */
    @SerialName("sha256") val sha256: Map<String, String> = emptyMap()
) {
    @Serializable
    data class JrePin(
        @SerialName("url") val url: String,
        @SerialName("sha256") val sha256: String,
        @SerialName("size_bytes") val sizeBytes: Long
    )

    /** 清单是否与本机兼容（ABI 与外壳版本；schema 由调用方单独判断） */
    fun isCompatibleWith(deviceAbis: Array<String>, shellVersion: String): Boolean {
        if (deviceAbis.none { it == abi }) return false
        return compareVersions(shellVersion, minShellVersion) >= 0
    }

    companion object {
        val JSON = Json { ignoreUnknownKeys = true }

        fun parse(text: String): EngineManifest =
            JSON.decodeFromString(serializer(), text)

        /** 逐段比较的版本号比较（1.2.10 > 1.2.9），非数字段按字典序 */
        fun compareVersions(a: String, b: String): Int {
            val sa = a.split('.').map { it.trim() }
            val sb = b.split('.').map { it.trim() }
            for (i in 0 until maxOf(sa.size, sb.size)) {
                val va = sa.getOrNull(i) ?: "0"
                val vb = sb.getOrNull(i) ?: "0"
                val na = va.toIntOrNull()
                val nb = vb.toIntOrNull()
                val c = if (na != null && nb != null) na.compareTo(nb) else va.compareTo(vb)
                if (c != 0) return c
            }
            return 0
        }

        /** 引擎包名：engine-<版本>-<abi>.zip */
        fun bundleName(engineVersion: String, abi: String) = "engine-$engineVersion-$abi.zip"
    }
}

/** 文件 sha256 十六进制小写 */
fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
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

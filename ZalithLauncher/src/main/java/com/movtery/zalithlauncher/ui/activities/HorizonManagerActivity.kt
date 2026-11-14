/*
 * Zalith Launcher 2 (Horizon fork)
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.ui.activities

import android.content.Intent
import android.os.Bundle
import android.system.Os
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.movtery.zalithlauncher.game.horizon.EngineReleaseClient
import com.movtery.zalithlauncher.game.horizon.EngineRepository
import com.movtery.zalithlauncher.game.horizon.HorizonDirs
import com.movtery.zalithlauncher.game.horizon.ResourcePrefill
import com.movtery.zalithlauncher.setting.launcherMMKV
import com.movtery.zalithlauncher.ui.base.BaseAppCompatActivity
import io.github.juanluci.starling.EngineHost
import io.github.juanluci.starling.GameActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Horizon 引擎管理页：引擎包的检查、下载安装、激活与回滚，首次资源预填充（BMCLAPI 镜像加速），
 * OpenJDK 25 预置，以及启动游戏。引擎内的更新提示（installUpdate 回调）也路由到这里。
 */
class HorizonManagerActivity : BaseAppCompatActivity() {

    private class UiState {
        var busy by mutableStateOf(false)
        var message by mutableStateOf("")
        var progress by mutableStateOf<Float?>(null)
        var currentVersion by mutableStateOf<String?>(null)
        var currentInfo by mutableStateOf("")
        var engines by mutableStateOf<List<String>>(emptyList())
        var latestAssetName by mutableStateOf<String?>(null)
        var releases by mutableStateOf<List<EngineReleaseClient.Release>>(emptyList())
        var jreReady by mutableStateOf<Boolean?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HorizonManagerScreen()
                }
            }
        }
    }

    @Composable
    private fun HorizonManagerScreen() {
        val state = remember { UiState() }
        var repo by remember { mutableStateOf(launcherMMKV().decodeString("horizon_release_repo", "")) }
        var mirror by remember { mutableStateOf(launcherMMKV().decodeString("horizon_github_mirror", "")) }
        val scope = rememberCoroutineScope()

        suspend fun refresh() = withContextIo {
            val current = HorizonDirs.current(this@HorizonManagerActivity)
            state.currentVersion = current?.manifest?.engineVersion
            state.currentInfo = current?.manifest?.let {
                "Minecraft ${it.mcVersion} ｜ 资源清单 v${it.kitOutputVersion} ｜ ${it.abi}"
            } ?: "先安装引擎包，再下载并准备原版资源"
            state.engines = HorizonDirs.installed(this@HorizonManagerActivity).map { it.dirName }
            state.jreReady = current?.let {
                File(File(filesDir, "preparer/jre25"), ".starling-runtime").isFile
            }
            // 发布列表拉一次，“最新包”提示与“选版本”共用
            val releases = runCatching { EngineReleaseClient.fetchReleases(cacheDir) }
            state.latestAssetName = releases.fold(
                onSuccess = { list ->
                    EngineReleaseClient.latestBundle(
                        list,
                        EngineRepository.deviceAbi(),
                        includePrerelease = true
                    )?.second?.name
                },
                onFailure = { "获取失败 (${it.message})" }
            )
            state.releases = releases.getOrDefault(emptyList())
            Unit
        }

        LaunchedEffect(Unit) { refresh() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Horizon 引擎管理", style = MaterialTheme.typography.headlineMedium)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("当前引擎：${state.currentVersion ?: "未安装"}")
                    Text(state.currentInfo, style = MaterialTheme.typography.bodySmall)
                    state.latestAssetName?.let {
                        Text("最新发布包：$it", style = MaterialTheme.typography.bodySmall)
                    }
                    state.jreReady?.let {
                        Text(
                            if (it) "OpenJDK 25 运行时已预置" else "OpenJDK 25 运行时未预置（准备资源时会自动预置）",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    state.progress?.let {
                        LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    }
                    if (state.message.isNotBlank()) {
                        Text(state.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !state.busy,
                    onClick = {
                        scope.launch(Dispatchers.Main) {
                            runStep(state, "安装引擎") {
                                val (release, asset) = EngineReleaseClient.latestBundle(
                                    EngineReleaseClient.fetchReleases(cacheDir),
                                    EngineRepository.deviceAbi(),
                                    includePrerelease = true
                                ) ?: error("发布列表里没有本机（${EngineRepository.deviceAbi()}）的引擎包")
                                EngineRepository.install(this@HorizonManagerActivity, asset) { p ->
                                    state.progress = p.ratio
                                    state.message = "安装引擎（${p.phase}）"
                                }
                                "已安装 ${release.tagName} 的引擎包"
                            }
                            refresh()
                        }
                    }
                ) { Text("安装最新引擎") }

                OutlinedButton(
                    enabled = !state.busy && state.currentVersion != null,
                    onClick = {
                        scope.launch(Dispatchers.Main) {
                            runStep(state, "下载并准备原版资源") {
                                val engine = HorizonDirs.current(this@HorizonManagerActivity)
                                    ?: error("没有已安装的引擎")
                                ResourcePrefill.prefill(this@HorizonManagerActivity, engine) { p ->
                                    state.progress =
                                        if (p.filesTotal > 0) p.filesDone.toFloat() / p.filesTotal else 0f
                                    state.message = "准备资源：${p.stage}（${p.filesDone}/${p.filesTotal}）"
                                }
                                ResourcePrefill.prefillJre(this@HorizonManagerActivity, engine)
                                "资源已就绪（files/minecraft），首次进入游戏会快速校验并完成 prepare"
                            }
                            refresh()
                        }
                    }
                ) { Text("下载并准备资源") }

                OutlinedButton(
                    enabled = !state.busy && state.currentVersion != null,
                    onClick = {
                        runCatching {
                            val engine = HorizonDirs.current(this@HorizonManagerActivity)
                                ?: error("没有已安装的引擎")
                            EngineRepository.activate(this@HorizonManagerActivity, engine)
                            val so = engine.libraryFile()
                            EngineHost.engineLibraryPath = so.absolutePath
                            Os.setenv("HORIZON_ENGINE_LIBRARY", so.absolutePath, true)
                            startActivity(Intent(this@HorizonManagerActivity, GameActivity::class.java))
                        }.onFailure { e -> state.message = "启动失败：${e.message}" }
                    }
                ) { Text("启动 Horizon") }
            }

            if (state.engines.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("已安装的引擎")
                        state.engines.forEach { name ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(name, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodySmall)
                                if (name != state.currentVersion) {
                                    OutlinedButton(onClick = {
                                        scope.launch(Dispatchers.Main) {
                                            runStep(state, "激活 $name") {
                                                val engine = HorizonDirs.installed(this@HorizonManagerActivity)
                                                    .firstOrNull { it.dirName == name }
                                                    ?: error("版本不存在")
                                                EngineRepository.activate(this@HorizonManagerActivity, engine)
                                                "已切换到 $name"
                                            }
                                            refresh()
                                        }
                                    }) { Text("激活") }
                                    OutlinedButton(onClick = {
                                        scope.launch(Dispatchers.Main) {
                                            runStep(state, "删除 $name") {
                                                val engine = HorizonDirs.installed(this@HorizonManagerActivity)
                                                    .firstOrNull { it.dirName == name }
                                                    ?: error("版本不存在")
                                                EngineRepository.remove(this@HorizonManagerActivity, engine)
                                                "已删除 $name"
                                            }
                                            refresh()
                                        }
                                    }) { Text("删除") }
                                }
                            }
                        }
                    }
                }
            }

            if (state.releases.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("从发布列表安装（选版本）", style = MaterialTheme.typography.titleSmall)
                        val abi = EngineRepository.deviceAbi()
                        state.releases.forEach { release ->
                            val asset = EngineReleaseClient.bundleIn(release, abi) ?: return@forEach
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${release.tagName}${if (release.prerelease) "（预发布）" else ""}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(enabled = !state.busy, onClick = {
                                    scope.launch(Dispatchers.Main) {
                                        runStep(state, "安装 ${release.tagName}") {
                                            EngineRepository.install(this@HorizonManagerActivity, asset) { p ->
                                                state.progress = p.ratio
                                                state.message = "安装引擎（${p.phase}）"
                                            }
                                            "已安装 ${release.tagName} 的引擎包"
                                        }
                                        refresh()
                                    }
                                }) { Text("安装") }
                            }
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("发布源与加速", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it },
                        label = { Text("GitHub 仓库（owner/repo）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mirror,
                        onValueChange = { mirror = it },
                        label = { Text("GitHub 下载加速前缀（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedButton(onClick = {
                        launcherMMKV().encode("horizon_release_repo", repo.trim())
                        launcherMMKV().encode("horizon_github_mirror", mirror.trim())
                        state.message = "已保存发布源设置"
                        scope.launch(Dispatchers.Main) { refresh() }
                    }) { Text("保存") }
                }
            }
        }
    }

    /** 统一执行并回写 busy/进度/错误状态；block 返回成功消息 */
    private suspend fun runStep(state: UiState, title: String, block: suspend () -> String) {
        state.busy = true
        state.progress = null
        state.message = title
        try {
            state.message = block()
        } catch (e: Throwable) {
            state.message = "$title 失败：${e.message}"
        } finally {
            state.busy = false
            state.progress = null
        }
    }

    private suspend fun withContextIo(block: suspend () -> Unit) =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }
}

/*
 * Zalith Launcher 2 (Horizon fork)
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.setting

import com.movtery.zalithlauncher.setting.enums.ResolutionRule

/**
 * Horizon 外壳仅保留分辨率相关的渲染设置：渲染器/图形 API/驱动选择随 Java 版
 * 支持移除，由引擎包自身的配置接管。
 */
object RendererSettings : SettingsRegistry() {
    /**
     * 分辨率百分比
     */
    val resolutionRatio = intSetting("resolutionRatio", 100, 25..300)

    /**
     * 分辨率规则
     */
    val resolutionRule = enumSetting("resolutionRule", ResolutionRule.PERCENTAGE)

    /**
     * 自定义分辨率宽度，0 表示尚未初始化
     */
    val customResolutionWidth = intSetting("customResolutionWidth", 0)

    /**
     * 自定义分辨率高度，0 表示尚未初始化
     */
    val customResolutionHeight = intSetting("customResolutionHeight", 0)
}

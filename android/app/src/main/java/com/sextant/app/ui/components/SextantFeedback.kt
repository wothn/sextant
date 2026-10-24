package com.sextant.app.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 应用级 Snackbar 宿主。
 *
 * 保存、删除、停用这类操作散落在多个页面和弹窗里，但反馈应该出现在同一个位置。
 * `SextantApp` 在 Scaffold 上提供宿主，页面通过这个 CompositionLocal 拿到的状态发消息即可。
 * 默认给一个空状态，保证 Preview 或独立组合的组件不会因为缺少宿主直接崩溃。
 */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

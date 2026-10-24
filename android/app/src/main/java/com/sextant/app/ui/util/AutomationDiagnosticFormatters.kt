package com.sextant.app.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun diagnosticTimestampLabel(timestamp: Long): String =
  SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA).format(Date(timestamp))

fun diagnosticChannelLabel(channel: String): String = when (channel) {
  "notification" -> "通知"
  "page" -> "页面"
  "worker" -> "记账任务"
  else -> "服务"
}

fun diagnosticStageLabel(stage: String): String = when (stage) {
  "service" -> "服务状态"
  "receive" -> "接收"
  "capture" -> "捕获"
  "queue" -> "调度"
  "process" -> "处理"
  "parse" -> "解析"
  "post" -> "入账"
  "scan" -> "页面扫描"
  "retry" -> "重试"
  "undo" -> "撤销"
  else -> stage
}

fun diagnosticReasonLabel(reason: String): String = when (reason) {
  "listener_connected" -> "通知监听已连接"
  "listener_disconnected" -> "通知监听已断开"
  "page_service_connected" -> "页面读取服务已连接"
  "page_service_interrupted" -> "页面读取服务被中断"
  "page_service_destroyed" -> "页面读取服务已停止"
  "group_summary" -> "收到分组汇总，沿用子通知处理"
  "financial_notification" -> "收到候选支付通知"
  "not_financial" -> "未发现明确支付信息，已跳过"
  "automation_disabled" -> "自动记账或对应读取方式未开启"
  "capture_duplicate" -> "相同来源信息已捕获，未重复提交"
  "captured" -> "已捕获，等待记账任务"
  "enqueue_requested" -> "已请求安排记账任务"
  "process_started" -> "记账任务开始处理"
  "retry_queued" -> "已重新提交处理"
  "retry_rejected" -> "原始信息不可用或不满足重试条件"
  "already_processed" -> "该事件已经处理"
  "payload_unavailable" -> "原始信息无法解密或已被清理"
  "scan_started" -> "开始读取页面"
  "root_unavailable" -> "系统未提供可读取的页面窗口"
  "window_changed" -> "当前窗口已变化，停止本次读取"
  "scan_cancelled" -> "页面读取已取消，可能发生窗口切换"
  "scan_exhausted" -> "多次读取后仍未识别，原因序列见导出日志"
  "page_repeated" -> "同一支付页面已识别，未重复提交"
  "access_revoked" -> "页面读取权限不可用"
  "scan_error" -> "页面读取出现异常，详细类型见导出日志"
  "unsupported_source" -> "不支持的支付应用"
  "page_text_incomplete" -> "页面可读文字为空、不完整或超出限制"
  "page_not_completed" -> "页面不是已完成的单笔支付"
  "unsupported_direction", "income_page" -> "页面收支方向暂不支持"
  "success_missing" -> "未找到支付成功标志"
  "payment_fields_missing" -> "缺少必要支付字段或结果页结束按钮"
  "amount_missing", "amount_missing_or_ambiguous" -> "缺少可确定的支付金额"
  "amount_ambiguous", "amount_conflict" -> "页面包含多个或互相冲突的金额"
  "amount_invalid" -> "支付金额格式不受支持或金额无效"
  "direction_missing" -> "无法确定收支方向"
  "unsupported_kind" -> "页面交易类型暂不支持，如退款、转账或外币"
  "merchant_invalid", "merchant_missing" -> "缺少可识别的商户信息"
  "time_invalid" -> "交易时间格式不受支持或时间无效"
  "time_missing" -> "账单详情缺少完整交易时间"
  "time_future" -> "交易时间晚于当前时间"
  "parsed" -> "解析成功"
  "posted" -> "已入账"
  "payment_merged" -> "已关联到已有流水，未新增记账"
  "provider_reference_duplicate", "duplicate" -> "交易已记录，已去重"
  "payment_match_ambiguous" -> "存在相近交易，需要核对账本"
  "undone" -> "已撤销自动入账"
  "undo_rejected" -> "撤销条件不满足"
  "log_unreadable" -> "该条日志无法解密"
  else -> reason
}

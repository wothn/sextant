package com.sextant.app.ui.util

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.sextant.app.data.LedgerFilter
import com.sextant.app.data.TransactionType

/**
 * [LedgerFilter] 的存档器，供 `rememberSaveable` 使用。
 *
 * 筛选条件属于「用户在哪儿」的一部分：旋转屏幕、分屏改变窗口大小、切换深浅色都会重建
 * Activity，只用 `remember` 存的条件会跟着一起消失，用户回来时看到的是全部流水，而不是
 * 刚才筛出来的那一批。
 *
 * 存档只写基本类型，枚举存名字而不是序号：往后往 [TransactionType] 中间插入新值，旧存档
 * 也不会被解读成另一种类型。
 */
val LedgerFilterSaver: Saver<LedgerFilter, Any> = listSaver(
  save = { filter ->
    listOf(
      filter.rangeStart,
      filter.rangeEnd,
      filter.query,
      filter.categoryId,
      filter.paymentMethodId,
      filter.type?.name,
      filter.minAmountCents,
      filter.maxAmountCents,
    )
  },
  restore = { saved ->
    LedgerFilter(
      rangeStart = saved[0] as Long,
      rangeEnd = saved[1] as Long,
      query = saved[2] as String,
      categoryId = saved[3] as String?,
      paymentMethodId = saved[4] as String?,
      type = (saved[5] as String?)?.let { name ->
        TransactionType.entries.firstOrNull { it.name == name }
      },
      minAmountCents = saved[6] as Long?,
      maxAmountCents = saved[7] as Long?,
    )
  },
)

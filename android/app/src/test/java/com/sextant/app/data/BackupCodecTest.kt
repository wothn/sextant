package com.sextant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
  @Test
  fun roundTripPreservesCompleteBackup() {
    val document = sampleDocument()

    val decoded = BackupCodec.decodeAndValidate(BackupCodec.encode(document))

    assertEquals(document, decoded)
    assertEquals(BackupSummary(1, 2, 1, 1), decoded.summary())
  }

  @Test
  fun rejectsBackupFromFutureDatabaseVersion() {
    val content = BackupCodec.encode(
      sampleDocument().copy(databaseVersion = SextantDatabase.DATABASE_VERSION + 1),
    )

    val error = assertThrows(BackupException::class.java) {
      BackupCodec.decodeAndValidate(content)
    }

    assertEquals("备份来自更新版本的 Sextant", error.message)
  }

  @Test
  fun rejectsBrokenReferencesBeforeRestore() {
    val content = BackupCodec.encode(
      sampleDocument().copy(
        transactions = sampleDocument().transactions.map { it.copy(categoryId = "missing") },
      ),
    )

    val error = assertThrows(BackupException::class.java) {
      BackupCodec.decodeAndValidate(content)
    }

    assertEquals("交易分类引用无效", error.message)
  }

  @Test
  fun rejectsCategoryCycles() {
    val document = sampleDocument()
    val cyclicCategories = document.categories.map { category ->
      when (category.id) {
        "food" -> category.copy(parentCategoryId = "meal")
        else -> category
      }
    }

    val error = assertThrows(BackupException::class.java) {
      BackupCodec.decodeAndValidate(BackupCodec.encode(document.copy(categories = cyclicCategories)))
    }

    assertEquals("分类层级存在循环", error.message)
  }

  private fun sampleDocument() = BackupDocument(
    exportedAt = 1_700_000_000_000,
    databaseVersion = SextantDatabase.DATABASE_VERSION,
    categories = listOf(
      BackupCategory("food", "餐饮", "expense", 0xFFFF7043, true, null, true, true, 1),
      BackupCategory("meal", "正餐", "expense", 0xFFFF7043, true, "food", true, true, 2),
    ),
    paymentMethods = listOf(
      BackupPaymentMethod("cash", "现金", 0xFFD97706, true, true, 3),
    ),
    transactions = listOf(
      BackupTransaction("transaction", "meal", "cash", 1_250, "expense", "午饭", 10, 11, 12),
    ),
    budgets = listOf(
      BackupBudget("budget", "food", 100_000, "month", 1, 0.8, true),
    ),
    settings = listOf(BackupSetting("monthly_budget_cents", "300000")),
  )
}

package com.sextant.app.data

import java.util.Locale

/** Exact merchant identity, shared across payment providers. Never derived from editable notes. */
fun normalizeMerchant(name: String): String = name.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
  .replace('（', '(').replace('）', ')').lowercase(Locale.ROOT)

internal class MerchantClassifier(private val database: SextantDatabase) {
  suspend fun classify(merchant: String?, type: TransactionType, kind: AutomationKind?): String? {
    if (merchant.isNullOrBlank() || type != TransactionType.Expense || kind != AutomationKind.Purchase) return null
    val key = normalizeMerchant(merchant)
    val categories = database.categoryDao().listActive().filter { it.type == TransactionType.Expense }
    val saved = database.merchantCategoryRuleDao().find(key)
    // An archived or repurposed user category must not silently fall back to a different built-in choice.
    if (saved != null) return categories.singleOrNull { it.id == saved.categoryId }?.id
    val target = builtInMerchantCategory(key) ?: return null
    val parent = categories.singleOrNull { it.isBuiltIn && it.parentCategoryId == null && it.name == target.first }
      ?: return null
    return categories.singleOrNull { it.isBuiltIn && it.parentCategoryId == parent.id && it.name == target.second }?.id
  }
}

/** Only exact brands or an explicit parenthesized branch suffix; no arbitrary substring matching. */
internal fun builtInMerchantCategory(merchant: String): Pair<String, String>? {
  val key = normalizeMerchant(merchant)
  return builtInMerchants.entries.firstOrNull { (brand, _) ->
    key == brand || (key.startsWith("$brand(") && key.endsWith(")") &&
      key.removePrefix("$brand(").removeSuffix(")").let { it.isNotBlank() && '(' !in it && ')' !in it })
  }?.value
}

private val builtInMerchants = buildMap {
  listOf("星巴克", "瑞幸咖啡", "luckincoffee", "starbucks", "蜜雪冰城", "喜茶", "奈雪的茶", "霸王茶姬")
    .forEach { put(it, "餐饮" to "奶茶咖啡") }
  listOf("肯德基", "麦当劳", "必胜客", "汉堡王", "kfc", "mcdonald's")
    .forEach { put(it, "餐饮" to "正餐") }
  listOf("滴滴出行", "滴滴快车", "曹操出行", "t3出行")
    .forEach { put(it, "交通" to "打车") }
}

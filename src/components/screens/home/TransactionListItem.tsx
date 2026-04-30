import { Text, XStack, YStack, useTheme } from "tamagui";

import { formatSignedCurrency } from "@/src/lib/format";
import type { TransactionListItem } from "@/src/features/transactions/transaction.service";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

function getTransactionCategoryLabel(item: TransactionListItem): string {
  if (item.categoryName) {
    return item.categoryName;
  }

  return item.type === "transfer" ? "转移记录" : "未分类";
}

interface TransactionListItemProps {
  item: TransactionListItem;
  onPress: (item: TransactionListItem) => void;
}

export function TransactionListItem({ item, onPress }: TransactionListItemProps) {
  const colors = getThemeColors(useTheme());
  const categoryName = getTransactionCategoryLabel(item);
  const description = item.description.trim();
  const paymentMethodName = item.paymentMethodName?.trim() ?? "";

  return (
    <YStack
      accessibilityRole="button"
      accessibilityLabel={`${categoryName} 交易详情`}
      onPress={() => onPress(item)}
      borderRadius={12}
      paddingVertical={6}
      paddingHorizontal={4}
      pressStyle={{ backgroundColor: colors.surfaceAlt }}
    >
      <XStack justifyContent="space-between" gap={12}>
        <YStack flex={1} gap={description ? 2 : 1}>
          <Text style={TEXT_VARIANTS.titleMedium}>{categoryName}</Text>
          {description ? (
            <Text style={[TEXT_VARIANTS.bodyMedium, { color: colors.textMuted }]}>
              {description}
            </Text>
          ) : null}
          {paymentMethodName ? (
            <Text style={[TEXT_VARIANTS.bodyMedium, { color: colors.textMuted }]}>
              {paymentMethodName}
            </Text>
          ) : null}
        </YStack>
        <YStack alignItems="flex-end" gap={4}>
          <Text
            style={[
              TEXT_VARIANTS.titleMedium,
              {
                color:
                  item.type === "expense"
                    ? colors.danger
                    : item.type === "income"
                      ? colors.success
                      : colors.accent,
                fontWeight: "700",
                fontVariant: ["tabular-nums"],
              },
            ]}
          >
            {formatSignedCurrency(item.type === "expense" ? -item.amount : item.amount)}
          </Text>
          <Text style={[TEXT_VARIANTS.bodyMedium, { color: colors.textMuted }]}>
            {new Date(item.transactionDate).toLocaleTimeString("zh-CN", {
              hour: "2-digit",
              minute: "2-digit",
            })}
          </Text>
        </YStack>
      </XStack>
    </YStack>
  );
}

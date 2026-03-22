import { Pressable, View } from "react-native";

import { formatSignedCurrency } from "@/src/lib/format";
import type { TransactionListItem } from "@/src/features/transactions/transaction.service";
import { Text, useTheme } from "@/src/ui";

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
  const theme = useTheme();
  const categoryName = getTransactionCategoryLabel(item);
  const description = item.description.trim();
  const paymentMethodName = item.paymentMethodName?.trim() ?? "";

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${categoryName} 交易详情`}
      onPress={() => onPress(item)}
      style={({ pressed }) => [
        {
          borderRadius: 12,
          paddingVertical: 6,
          paddingHorizontal: 4,
        },
        pressed ? { backgroundColor: theme.colors.surfaceAlt } : null,
      ]}
    >
      <View style={{ flexDirection: "row", justifyContent: "space-between", gap: 12 }}>
        <View style={{ flex: 1, gap: description ? 2 : 1 }}>
          <Text variant="titleMedium">{categoryName}</Text>
          {description ? (
            <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
              {description}
            </Text>
          ) : null}
          {paymentMethodName ? (
            <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
              {paymentMethodName}
            </Text>
          ) : null}
        </View>
        <View style={{ alignItems: "flex-end", gap: 4 }}>
          <Text
            variant="titleMedium"
            style={{
              color:
                item.type === "expense"
                  ? theme.colors.danger
                  : item.type === "income"
                    ? theme.colors.success
                    : theme.colors.accent,
              fontWeight: "700",
            }}
            tabularNums
          >
            {formatSignedCurrency(item.type === "expense" ? -item.amount : item.amount)}
          </Text>
          <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
            {new Date(item.transactionDate).toLocaleTimeString("zh-CN", {
              hour: "2-digit",
              minute: "2-digit",
            })}
          </Text>
        </View>
      </View>
    </Pressable>
  );
}

import { View } from "react-native";

import { formatCurrency, formatDateGroupTitle } from "@/src/lib/format";
import type {
  DailyTransactionGroup,
  TransactionListItem,
} from "@/src/features/transactions/transaction.service";
import { Card, Divider, Text, useTheme } from "@/src/ui";
import { TransactionListItem as TransactionRow } from "@/src/components/screens/home/TransactionListItem";

interface TransactionGroupCardProps {
  group: DailyTransactionGroup;
  onSelectTransaction: (item: TransactionListItem) => void;
}

export function TransactionGroupCard({ group, onSelectTransaction }: TransactionGroupCardProps) {
  const theme = useTheme();

  return (
    <Card style={{ borderRadius: 18 }}>
      <Card.Content style={{ gap: 8 }}>
        <View
          style={{
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "flex-start",
          }}
        >
          <View style={{ gap: 4, flex: 1 }}>
            <Text variant="titleSmall">{formatDateGroupTitle(group.dateKey)}</Text>
            <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
              共 {group.transactionCount} 笔
            </Text>
          </View>
          <View
            style={{
              flexDirection: "row",
              alignItems: "center",
              gap: 6,
              flexWrap: "wrap",
              justifyContent: "flex-end",
            }}
          >
            <Text variant="labelMedium" style={{ color: theme.colors.danger }} tabularNums>
              支 {formatCurrency(group.totalExpense)}
            </Text>
            <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
              ·
            </Text>
            <Text variant="labelMedium" style={{ color: theme.colors.success }} tabularNums>
              收 {formatCurrency(group.totalIncome)}
            </Text>
          </View>
        </View>

        {group.transactions.map((item, index) => (
          <View key={item.id}>
            {index > 0 ? <Divider style={{ marginVertical: 6 }} /> : null}
            <TransactionRow item={item} onPress={onSelectTransaction} />
          </View>
        ))}
      </Card.Content>
    </Card>
  );
}

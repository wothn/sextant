import { Card, Separator, Text, XStack, YStack, useTheme } from "tamagui";

import { formatCurrency, formatDateGroupTitle } from "@/src/lib/format";
import type {
  DailyTransactionGroup,
  TransactionListItem,
} from "@/src/features/transactions/transaction.service";
import { TransactionListItem as TransactionRow } from "@/src/components/screens/home/TransactionListItem";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface TransactionGroupCardProps {
  group: DailyTransactionGroup;
  onSelectTransaction: (item: TransactionListItem) => void;
}

export function TransactionGroupCard({ group, onSelectTransaction }: TransactionGroupCardProps) {
  const colors = getThemeColors(useTheme());

  return (
    <Card borderRadius={18} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surface} padding={16}>
      <YStack gap={8}>
        <XStack justifyContent="space-between" alignItems="flex-start">
          <YStack gap={4} flex={1}>
            <Text style={TEXT_VARIANTS.titleSmall}>{formatDateGroupTitle(group.dateKey)}</Text>
            <Text style={[TEXT_VARIANTS.labelSmall, { color: colors.textMuted }]}>
              共 {group.transactionCount} 笔
            </Text>
          </YStack>
          <XStack alignItems="center" gap={6} flexWrap="wrap" justifyContent="flex-end">
            <Text
              style={[
                TEXT_VARIANTS.labelMedium,
                { color: colors.danger, fontVariant: ["tabular-nums"] },
              ]}
            >
              支 {formatCurrency(group.totalExpense)}
            </Text>
            <Text style={[TEXT_VARIANTS.labelMedium, { color: colors.textMuted }]}>
              ·
            </Text>
            <Text
              style={[
                TEXT_VARIANTS.labelMedium,
                { color: colors.success, fontVariant: ["tabular-nums"] },
              ]}
            >
              收 {formatCurrency(group.totalIncome)}
            </Text>
          </XStack>
        </XStack>

        {group.transactions.map((item, index) => (
          <YStack key={item.id}>
            {index > 0 ? <Separator marginVertical={6} backgroundColor={colors.border} /> : null}
            <TransactionRow item={item} onPress={onSelectTransaction} />
          </YStack>
        ))}
      </YStack>
    </Card>
  );
}

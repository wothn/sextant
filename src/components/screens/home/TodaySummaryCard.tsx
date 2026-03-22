import { View } from "react-native";

import { formatCurrency, formatSignedCurrency } from "@/src/lib/format";
import type { TodaySummary } from "@/src/features/transactions/transaction.service";
import { Card, Text, useTheme } from "@/src/ui";

interface MonthSummary {
  income: number;
  expense: number;
  net: number;
}

interface TodaySummaryCardProps {
  todaySummary: TodaySummary;
  monthSummary: MonthSummary;
}

export function TodaySummaryCard({ todaySummary, monthSummary }: TodaySummaryCardProps) {
  const theme = useTheme();

  return (
    <Card style={{ borderRadius: 22 }}>
      <Card.Content style={{ gap: 12 }}>
        <View style={{ gap: 6 }}>
          <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
            今天已经花了
          </Text>
          <View
            style={{
              flexDirection: "row",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 12,
            }}
          >
            <Text
              variant="headlineSmall"
              tabularNums
              style={{ color: theme.colors.danger, fontWeight: "800" }}
            >
              {formatCurrency(todaySummary.expense)}
            </Text>
            <View style={{ alignItems: "flex-end", gap: 2 }}>
              <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
                净变化
              </Text>
              <Text variant="bodySmall" style={{ color: theme.colors.textMuted }} tabularNums>
                {formatSignedCurrency(todaySummary.net)}
              </Text>
            </View>
          </View>
        </View>

        <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
          <View style={{ flex: 1, gap: 4 }}>
            <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
              本月支出
            </Text>
            <Text variant="titleMedium" tabularNums style={{ color: theme.colors.danger }}>
              {formatCurrency(monthSummary.expense)}
            </Text>
          </View>
          <View style={{ flex: 1, gap: 4, alignItems: "flex-end" }}>
            <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
              本月收入
            </Text>
            <Text variant="titleMedium" tabularNums style={{ color: theme.colors.success }}>
              {formatCurrency(monthSummary.income)}
            </Text>
          </View>
        </View>
      </Card.Content>
    </Card>
  );
}

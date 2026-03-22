import { View } from "react-native";

import { DonutChart } from "@/src/components/charts/DonutChart";
import type { CategoryBreakdownItem } from "@/src/features/transactions/transaction.service";
import { formatCompactPercent, formatCurrency } from "@/src/lib/format";
import { Card, Text, useTheme } from "@/src/ui";

interface SpendingStructureSectionProps {
  categories: CategoryBreakdownItem[];
  error: Error | null;
}

export function SpendingStructureSection({ categories, error }: SpendingStructureSectionProps) {
  const theme = useTheme();
  const donutSegments = categories
    .slice(0, 5)
    .map((item) => ({ value: item.amount, color: item.categoryColor }));

  return (
    <Card style={{ borderRadius: 18 }}>
      <Card.Content style={{ gap: 14 }}>
        <Text variant="titleMedium">支出结构</Text>
        {!error && categories.length === 0 ? (
          <Text>本月还没有支出记录，结构图会在你花第一笔钱后出现。</Text>
        ) : (
          <View style={{ flexDirection: "row", gap: 16, alignItems: "center" }}>
            <DonutChart segments={donutSegments} trackColor={theme.colors.border} />
            <View style={{ flex: 1, gap: 10 }}>
              {categories.slice(0, 5).map((item) => (
                <View key={item.categoryName} style={{ gap: 4 }}>
                  <View
                    style={{
                      flexDirection: "row",
                      justifyContent: "space-between",
                      alignItems: "center",
                    }}
                  >
                    <View style={{ flexDirection: "row", alignItems: "center", gap: 8, flex: 1 }}>
                      <View
                        style={{
                          width: 10,
                          height: 10,
                          borderRadius: 5,
                          backgroundColor: item.categoryColor,
                        }}
                      />
                      <Text variant="bodyMedium">{item.categoryName}</Text>
                    </View>
                    <Text variant="labelLarge" tabularNums>
                      {formatCompactPercent(item.share)}
                    </Text>
                  </View>
                  <Text variant="bodySmall" style={{ color: theme.colors.textMuted }} tabularNums>
                    {formatCurrency(item.amount)} · {item.transactionCount} 笔
                  </Text>
                </View>
              ))}
            </View>
          </View>
        )}
      </Card.Content>
    </Card>
  );
}

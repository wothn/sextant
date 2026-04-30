import { XStack, useTheme } from "tamagui";

import { formatCurrency, formatSignedCurrency } from "@/src/lib/format";
import { MetricCard } from "@/src/components/screens/shared/MetricCard";
import { getThemeColors } from "@/src/lib/theme";

interface AnalyticsSummarySectionProps {
  summary: {
    income: number;
    expense: number;
    net: number;
  };
}

export function AnalyticsSummarySection({ summary }: AnalyticsSummarySectionProps) {
  const colors = getThemeColors(useTheme());

  return (
    <XStack gap={12} flexWrap="wrap">
      <MetricCard
        label="本月收入"
        value={formatCurrency(summary.income)}
        tone={colors.success}
        style={{ flexBasis: "31%", flexGrow: 1 }}
      />
      <MetricCard
        label="本月支出"
        value={formatCurrency(summary.expense)}
        tone={colors.danger}
        style={{ flexBasis: "31%", flexGrow: 1 }}
      />
      <MetricCard
        label="本月净额"
        value={formatSignedCurrency(summary.net)}
        tone={summary.net >= 0 ? colors.success : colors.danger}
        style={{ flexBasis: "31%", flexGrow: 1 }}
      />
    </XStack>
  );
}

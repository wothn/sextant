import { View } from "react-native";

import { formatCurrency, formatSignedCurrency } from "@/src/lib/format";
import { MetricCard } from "@/src/components/screens/shared/MetricCard";
import { useTheme } from "@/src/ui";

interface AnalyticsSummarySectionProps {
  summary: {
    income: number;
    expense: number;
    net: number;
  };
}

export function AnalyticsSummarySection({ summary }: AnalyticsSummarySectionProps) {
  const theme = useTheme();

  return (
    <View style={{ flexDirection: "row", gap: 12, flexWrap: "wrap" }}>
      <MetricCard
        label="本月收入"
        value={formatCurrency(summary.income)}
        tone={theme.colors.success}
        style={{ flexBasis: "31%", flexGrow: 1 }}
      />
      <MetricCard
        label="本月支出"
        value={formatCurrency(summary.expense)}
        tone={theme.colors.danger}
        style={{ flexBasis: "31%", flexGrow: 1 }}
      />
      <MetricCard
        label="本月净额"
        value={formatSignedCurrency(summary.net)}
        tone={summary.net >= 0 ? theme.colors.success : theme.colors.danger}
        style={{ flexBasis: "31%", flexGrow: 1 }}
      />
    </View>
  );
}

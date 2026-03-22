import type { ViewStyle } from "react-native";

import { Card, Text, useTheme } from "@/src/ui";

interface MetricCardProps {
  label: string;
  value: string;
  tone: string;
  align?: "start" | "end";
  style?: ViewStyle;
}

export function MetricCard({ label, value, tone, align = "start", style }: MetricCardProps) {
  const theme = useTheme();
  const alignItems = align === "end" ? "flex-end" : "flex-start";

  return (
    <Card style={[{ borderRadius: 16 }, style]}>
      <Card.Content style={{ gap: 8, alignItems }}>
        <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
          {label}
        </Text>
        <Text variant="titleLarge" tabularNums style={{ color: tone, fontWeight: "700" }}>
          {value}
        </Text>
      </Card.Content>
    </Card>
  );
}

import type { ViewStyle } from "react-native";
import { Card, Text, YStack, useTheme } from "tamagui";

import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface MetricCardProps {
  label: string;
  value: string;
  tone: string;
  align?: "start" | "end";
  style?: ViewStyle;
}

export function MetricCard({ label, value, tone, align = "start", style }: MetricCardProps) {
  const colors = getThemeColors(useTheme());
  const alignItems = align === "end" ? "flex-end" : "flex-start";

  return (
    <Card
      borderRadius={16}
      borderWidth={1}
      borderColor={colors.border}
      backgroundColor={colors.surface}
      padding={16}
      style={style}
    >
      <YStack gap={8} alignItems={alignItems}>
        <Text style={[TEXT_VARIANTS.labelLarge, { color: colors.textMuted }]}>
          {label}
        </Text>
        <Text style={[TEXT_VARIANTS.titleLarge, { color: tone, fontWeight: "700", fontVariant: ["tabular-nums"] }]}>
          {value}
        </Text>
      </YStack>
    </Card>
  );
}

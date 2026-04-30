import { Card, Text, YStack, useTheme } from "tamagui";

import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface EmptyStateCardProps {
  title: string;
  description: string;
}

export function EmptyStateCard({ title, description }: EmptyStateCardProps) {
  const colors = getThemeColors(useTheme());

  return (
    <Card borderRadius={18} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surface} padding={16}>
      <YStack gap={12}>
        <Text style={TEXT_VARIANTS.titleMedium}>{title}</Text>
        <Text style={[TEXT_VARIANTS.bodyMedium, { color: colors.textMuted }]}>
          {description}
        </Text>
      </YStack>
    </Card>
  );
}

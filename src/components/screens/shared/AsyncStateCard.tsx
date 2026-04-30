import { Button, Card, Text, YStack, useTheme } from "tamagui";

import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface AsyncStateCardProps {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function AsyncStateCard({ title, description, actionLabel, onAction }: AsyncStateCardProps) {
  const colors = getThemeColors(useTheme());

  return (
    <Card borderRadius={18} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surface} padding={16} gap={10}>
      <YStack gap={10}>
        <Text style={TEXT_VARIANTS.titleMedium}>{title}</Text>
        <Text style={[TEXT_VARIANTS.bodyMedium, { color: colors.textMuted }]}>
          {description}
        </Text>
        {actionLabel && onAction ? (
          <Button
            unstyled
            alignSelf="flex-start"
            minHeight={44}
            borderRadius={12}
            borderWidth={1.25}
            borderColor={colors.borderStrong}
            backgroundColor={colors.surface}
            paddingHorizontal={16}
            paddingVertical={10}
            onPress={onAction}
          >
            {actionLabel}
          </Button>
        ) : null}
      </YStack>
    </Card>
  );
}

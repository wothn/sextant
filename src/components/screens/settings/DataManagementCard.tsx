import { Button, Card, Text, YStack, useTheme } from "tamagui";

import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface DataManagementCardProps {
  onExportCsv: () => void;
}

export function DataManagementCard({ onExportCsv }: DataManagementCardProps) {
  const colors = getThemeColors(useTheme());

  return (
    <Card borderRadius={18} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surface} padding={16}>
      <YStack gap={10}>
        <Text style={TEXT_VARIANTS.titleMedium}>数据管理</Text>
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
          onPress={onExportCsv}
        >
          <Text style={[TEXT_VARIANTS.labelLarge, { color: colors.text }]}>导出交易 CSV</Text>
        </Button>
      </YStack>
    </Card>
  );
}

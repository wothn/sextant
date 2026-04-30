import type { Category } from "@/src/types/domain";
import { Button, Card, Input, Text, XStack, YStack, useTheme } from "tamagui";

import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface BudgetSettingsCardProps {
  categories: Category[];
  selectedCategory: string | null;
  selectedName: string;
  budgetText: string;
  onSelectCategory: (categoryId: string) => void;
  onChangeBudgetText: (value: string) => void;
  onSave: () => void;
}

export function BudgetSettingsCard({
  categories,
  selectedCategory,
  selectedName,
  budgetText,
  onSelectCategory,
  onChangeBudgetText,
  onSave,
}: BudgetSettingsCardProps) {
  const colors = getThemeColors(useTheme());

  return (
    <Card borderRadius={18} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surface} padding={16}>
      <YStack gap={10}>
        <Text style={TEXT_VARIANTS.titleMedium}>本月预算设置</Text>
        <Text style={TEXT_VARIANTS.bodyMedium}>当前分类：{selectedName}</Text>
        <XStack flexWrap="wrap" gap={8}>
          {categories.map((item) => (
            <Button
              unstyled
              key={item.id}
              accessibilityRole="button"
              borderRadius={10}
              borderWidth={1}
              paddingHorizontal={12}
              paddingVertical={8}
              backgroundColor={selectedCategory === item.id ? colors.accentSoft : colors.surfaceAlt}
              borderColor={selectedCategory === item.id ? colors.accentMuted : colors.borderStrong}
              onPress={() => onSelectCategory(item.id)}
            >
              <Text
                style={[
                  TEXT_VARIANTS.labelLarge,
                  { color: selectedCategory === item.id ? colors.accentStrong : colors.text },
                ]}
              >
                {item.name}
              </Text>
            </Button>
          ))}
        </XStack>
        <YStack gap={6}>
          <Text style={[TEXT_VARIANTS.labelMedium, { color: colors.textMuted }]}>预算金额</Text>
          <Input
            value={budgetText}
            onChangeText={onChangeBudgetText}
            accessibilityLabel="预算金额"
            keyboardType="numeric"
            minHeight={44}
            borderWidth={1}
            borderRadius={12}
            borderColor={colors.borderStrong}
            backgroundColor={colors.surface}
            color={colors.text}
            placeholderTextColor="$onSurfaceMuted"
            fontFamily="$body"
            fontSize={16}
            paddingHorizontal={12}
            paddingVertical={10}
          />
        </YStack>
        <Button
          unstyled
          alignSelf="flex-start"
          minHeight={44}
          borderRadius={12}
          backgroundColor={colors.accent}
          paddingHorizontal={16}
          paddingVertical={10}
          onPress={onSave}
        >
          <Text style={[TEXT_VARIANTS.labelLarge, { color: colors.onAccent }]}>保存预算</Text>
        </Button>
      </YStack>
    </Card>
  );
}

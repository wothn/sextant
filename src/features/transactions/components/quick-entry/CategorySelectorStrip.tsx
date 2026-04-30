import { Button, ScrollView, Text, XStack, YStack, useTheme } from "tamagui";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  getInitialLabel,
  getOptionIconName,
} from "@/src/features/transactions/components/quick-entry/utils";
import type { Category } from "@/src/types/domain";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface CategorySelectorStripProps {
  categories: Category[];
  selectedCategoryId: string | null;
  onSelectCategory: (categoryId: string) => void;
}

export function CategorySelectorStrip({
  categories,
  selectedCategoryId,
  onSelectCategory,
}: CategorySelectorStripProps) {
  const colors = getThemeColors(useTheme());

  return (
    <YStack style={styles.categorySection}>
      <XStack style={styles.sectionHeader}>
        <Text style={[TEXT_VARIANTS.titleSmall, { fontWeight: "700" }]}>
          分类
        </Text>
      </XStack>

      <ScrollView
        horizontal
        style={styles.categoryScroll}
        contentContainerStyle={styles.categoryRow}
        showsHorizontalScrollIndicator={false}
      >
        {categories.map((item) => {
          const selected = selectedCategoryId === item.id;
          const iconName = getOptionIconName(item.icon);

          return (
            <Button
              unstyled
              key={item.id}
              accessibilityRole="button"
              accessibilityLabel={`选择分类${item.name}`}
              onPress={() => onSelectCategory(item.id)}
              style={styles.categoryChip}
              backgroundColor={selected ? colors.accentSoft : colors.surface}
              borderColor={selected ? item.color : colors.borderStrong}
              pressStyle={{ opacity: 0.92 }}
            >
              <YStack
                style={[
                  styles.categoryIconWrapSm,
                  {
                    backgroundColor: selected ? item.color : colors.surfaceAlt,
                  },
                ]}
              >
                {iconName ? (
                  <MaterialCommunityIcons
                    name={iconName}
                    size={13}
                    color={selected ? colors.onAccent : item.color}
                  />
                ) : (
                  <Text
                    style={[
                      TEXT_VARIANTS.labelSmall,
                      { color: selected ? colors.onAccent : item.color, fontWeight: "800" },
                    ]}
                  >
                    {getInitialLabel(item.name)}
                  </Text>
                )}
              </YStack>
              <Text
                style={[
                  TEXT_VARIANTS.labelMedium,
                  { color: selected ? item.color : colors.text, fontWeight: "700" },
                ]}
              >
                {item.name}
              </Text>
            </Button>
          );
        })}
      </ScrollView>
    </YStack>
  );
}

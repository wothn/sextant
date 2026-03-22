import { Pressable, ScrollView, View } from "react-native";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  getInitialLabel,
  getOptionIconName,
} from "@/src/features/transactions/components/quick-entry/utils";
import type { Category } from "@/src/types/domain";
import { Text, useTheme } from "@/src/ui";

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
  const theme = useTheme();

  return (
    <View style={styles.categorySection}>
      <View style={styles.sectionHeader}>
        <Text variant="titleSmall" style={{ fontWeight: "700" }}>
          分类
        </Text>
      </View>

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
            <Pressable
              key={item.id}
              accessibilityRole="button"
              accessibilityLabel={`选择分类${item.name}`}
              onPress={() => onSelectCategory(item.id)}
              style={({ pressed }) => [
                styles.categoryChip,
                {
                  backgroundColor: selected ? theme.colors.accentSoft : theme.colors.surface,
                  borderColor: selected ? item.color : theme.colors.borderStrong,
                  opacity: pressed ? 0.92 : 1,
                },
              ]}
            >
              <View
                style={[
                  styles.categoryIconWrapSm,
                  {
                    backgroundColor: selected ? item.color : theme.colors.surfaceAlt,
                  },
                ]}
              >
                {iconName ? (
                  <MaterialCommunityIcons
                    name={iconName}
                    size={13}
                    color={selected ? theme.colors.onAccent : item.color}
                  />
                ) : (
                  <Text
                    variant="labelSmall"
                    style={{
                      color: selected ? theme.colors.onAccent : item.color,
                      fontWeight: "800",
                    }}
                  >
                    {getInitialLabel(item.name)}
                  </Text>
                )}
              </View>
              <Text
                variant="labelMedium"
                style={{
                  color: selected ? item.color : theme.colors.text,
                  fontWeight: "700",
                }}
              >
                {item.name}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
}

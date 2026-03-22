import type { Category } from "@/src/types/domain";
import { Button, Card, Chip, Text, TextInput } from "@/src/ui";
import { View } from "react-native";

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
  return (
    <Card>
      <Card.Content style={{ gap: 10 }}>
        <Text variant="titleMedium">本月预算设置</Text>
        <Text variant="bodyMedium">当前分类：{selectedName}</Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
          {categories.map((item) => (
            <Chip
              key={item.id}
              selected={selectedCategory === item.id}
              onPress={() => onSelectCategory(item.id)}
            >
              {item.name}
            </Chip>
          ))}
        </View>
        <TextInput
          mode="outlined"
          label="预算金额"
          accessibilityLabel="预算金额"
          keyboardType="numeric"
          value={budgetText}
          onChangeText={onChangeBudgetText}
        />
        <Button mode="contained" onPress={onSave}>
          保存预算
        </Button>
      </Card.Content>
    </Card>
  );
}

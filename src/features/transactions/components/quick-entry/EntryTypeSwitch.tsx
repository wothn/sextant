import { Pressable, View } from "react-native";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import type { EntryType } from "@/src/features/transactions/components/quick-entry/types";
import { Text, useTheme } from "@/src/ui";

interface EntryTypeSwitchProps {
  value: EntryType;
  onChange: (value: EntryType) => void;
}

export function EntryTypeSwitch({ value, onChange }: EntryTypeSwitchProps) {
  const theme = useTheme();
  const expenseSelected = value === "expense";
  const incomeSelected = value === "income";

  return (
    <View
      style={[
        styles.typeSwitch,
        {
          backgroundColor: theme.colors.surfaceAlt,
          borderColor: theme.colors.border,
        },
      ]}
    >
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="选择支出"
        onPress={() => onChange("expense")}
        style={[
          styles.typeButton,
          expenseSelected ? { backgroundColor: theme.colors.accentSoft } : null,
        ]}
      >
        <Text
          variant="labelLarge"
          style={{
            color: expenseSelected ? theme.colors.accentStrong : theme.colors.textMuted,
            fontWeight: "700",
          }}
        >
          支出
        </Text>
      </Pressable>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel="选择收入"
        onPress={() => onChange("income")}
        style={[
          styles.typeButton,
          incomeSelected ? { backgroundColor: theme.colors.accentSoft } : null,
        ]}
      >
        <Text
          variant="labelLarge"
          style={{
            color: incomeSelected ? theme.colors.accentStrong : theme.colors.textMuted,
            fontWeight: "700",
          }}
        >
          收入
        </Text>
      </Pressable>
    </View>
  );
}

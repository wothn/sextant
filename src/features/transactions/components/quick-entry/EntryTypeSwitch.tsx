import { Button, Text, XStack, useTheme } from "tamagui";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import type { EntryType } from "@/src/features/transactions/components/quick-entry/types";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface EntryTypeSwitchProps {
  value: EntryType;
  onChange: (value: EntryType) => void;
}

export function EntryTypeSwitch({ value, onChange }: EntryTypeSwitchProps) {
  const colors = getThemeColors(useTheme());
  const expenseSelected = value === "expense";
  const incomeSelected = value === "income";

  return (
    <XStack
      style={[
        styles.typeSwitch,
        {
          backgroundColor: colors.surfaceAlt,
          borderColor: colors.border,
        },
      ]}
    >
      <Button
        unstyled
        accessibilityRole="button"
        accessibilityLabel="选择支出"
        onPress={() => onChange("expense")}
        style={styles.typeButton}
        backgroundColor={expenseSelected ? colors.accentSoft : "transparent"}
      >
        <Text
          style={[
            TEXT_VARIANTS.labelLarge,
            {
              color: expenseSelected ? colors.accentStrong : colors.textMuted,
              fontWeight: "700",
            },
          ]}
        >
          支出
        </Text>
      </Button>

      <Button
        unstyled
        accessibilityRole="button"
        accessibilityLabel="选择收入"
        onPress={() => onChange("income")}
        style={styles.typeButton}
        backgroundColor={incomeSelected ? colors.accentSoft : "transparent"}
      >
        <Text
          style={[
            TEXT_VARIANTS.labelLarge,
            {
              color: incomeSelected ? colors.accentStrong : colors.textMuted,
              fontWeight: "700",
            },
          ]}
        >
          收入
        </Text>
      </Button>
    </XStack>
  );
}

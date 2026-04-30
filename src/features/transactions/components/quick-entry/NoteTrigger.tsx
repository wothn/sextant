import { Button, Text, useTheme } from "tamagui";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface NoteTriggerProps {
  description: string;
  onPress: () => void;
}

export function NoteTrigger({ description, onPress }: NoteTriggerProps) {
  const colors = getThemeColors(useTheme());

  return (
    <Button
      unstyled
      accessibilityRole="button"
      accessibilityLabel="备注"
      onPress={onPress}
      style={styles.noteDisplay}
      backgroundColor={colors.surfaceAlt}
      borderColor={colors.borderStrong}
      pressStyle={{ opacity: 0.92 }}
    >
      <Text
        numberOfLines={1}
        style={[
          TEXT_VARIANTS.labelLarge,
          {
            color: description ? colors.text : colors.textMuted,
            flex: 1,
            fontWeight: "500",
          },
        ]}
      >
        {description || "添加备注（选填）"}
      </Text>
    </Button>
  );
}

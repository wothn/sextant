import { Pressable } from "react-native";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { Text, useTheme } from "@/src/ui";

interface NoteTriggerProps {
  description: string;
  onPress: () => void;
}

export function NoteTrigger({ description, onPress }: NoteTriggerProps) {
  const theme = useTheme();

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="备注"
      onPress={onPress}
      style={({ pressed }) => [
        styles.noteDisplay,
        {
          backgroundColor: theme.colors.surfaceAlt,
          borderColor: theme.colors.borderStrong,
          opacity: pressed ? 0.92 : 1,
        },
      ]}
    >
      <Text
        variant="labelLarge"
        numberOfLines={1}
        style={{
          color: description ? theme.colors.text : theme.colors.textMuted,
          flex: 1,
          fontWeight: "500",
        }}
      >
        {description || "添加备注（选填）"}
      </Text>
    </Pressable>
  );
}

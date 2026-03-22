import type { PropsWithChildren } from "react";
import { Pressable, StyleSheet } from "react-native";
import type { StyleProp, ViewStyle } from "react-native";

import { Text } from "@/src/ui/Text";
import { useTheme } from "@/src/ui/theme";

interface ChipProps extends PropsWithChildren {
  selected?: boolean;
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
  showSelectedOverlay?: boolean;
}

export function Chip({ children, selected = false, onPress, style }: ChipProps) {
  const { colors } = useTheme();

  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.chip,
        {
          backgroundColor: selected ? colors.accentSoft : colors.surfaceAlt,
          borderColor: selected ? colors.accentMuted : colors.borderStrong,
          opacity: pressed ? 0.9 : 1,
        },
        style,
      ]}
    >
      <Text variant="labelLarge" style={{ color: selected ? colors.accentStrong : colors.text }}>
        {children}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 8,
    alignSelf: "flex-start",
  },
});

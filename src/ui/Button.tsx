import { MaterialCommunityIcons } from "@expo/vector-icons";
import type { ComponentProps, PropsWithChildren } from "react";
import { ActivityIndicator, Pressable, StyleSheet, View } from "react-native";
import type { StyleProp, TextStyle, ViewStyle } from "react-native";

import { Text } from "@/src/ui/Text";
import { useTheme } from "@/src/ui/theme";

type IconName = ComponentProps<typeof MaterialCommunityIcons>["name"];

interface ButtonProps extends PropsWithChildren {
  onPress?: () => void;
  mode?: "text" | "outlined" | "contained";
  compact?: boolean;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  contentStyle?: StyleProp<ViewStyle>;
  labelStyle?: StyleProp<TextStyle>;
  accessibilityLabel?: string;
  loading?: boolean;
  icon?: string;
  testID?: string;
}

export function Button({
  children,
  onPress,
  mode = "text",
  compact = false,
  disabled = false,
  style,
  contentStyle,
  labelStyle,
  accessibilityLabel,
  loading = false,
  icon,
  testID,
}: ButtonProps) {
  const { colors } = useTheme();
  const variantStyles = {
    text: {
      backgroundColor: "transparent",
      borderWidth: 0,
      borderColor: "transparent",
      textColor: colors.accent,
      shadowOpacity: 0,
    },
    outlined: {
      backgroundColor: colors.surface,
      borderWidth: 1.25,
      borderColor: colors.borderStrong,
      textColor: colors.text,
      shadowOpacity: 0,
    },
    contained: {
      backgroundColor: colors.accent,
      borderWidth: 0,
      borderColor: "transparent",
      textColor: colors.onAccent,
      shadowOpacity: 0.18,
    },
  } as const;
  const currentVariant = variantStyles[mode];
  const resolvedDisabled = disabled || loading;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      disabled={resolvedDisabled}
      onPress={resolvedDisabled ? undefined : onPress}
      testID={testID ?? "button"}
      style={({ pressed }) => [
        styles.buttonBase,
        compact ? styles.buttonCompact : styles.buttonRegular,
        {
          backgroundColor: currentVariant.backgroundColor,
          borderWidth: currentVariant.borderWidth,
          borderColor: currentVariant.borderColor,
          opacity: resolvedDisabled ? 0.5 : pressed ? 0.9 : 1,
          shadowColor: colors.shadow,
          shadowOpacity: currentVariant.shadowOpacity,
          shadowRadius: mode === "contained" ? 18 : 0,
          shadowOffset: mode === "contained" ? { width: 0, height: 8 } : { width: 0, height: 0 },
          elevation: mode === "contained" ? 6 : 0,
          transform: [{ scale: pressed ? 0.98 : 1 }],
        },
        style,
      ]}
    >
      <View style={[styles.buttonContent, contentStyle]}>
        {loading ? (
          <ActivityIndicator size="small" color={currentVariant.textColor} />
        ) : icon ? (
          <MaterialCommunityIcons
            name={icon as IconName}
            size={18}
            color={currentVariant.textColor}
          />
        ) : null}
        {children ? (
          <Text
            variant="labelLarge"
            style={[{ color: currentVariant.textColor, fontWeight: "700" }, labelStyle]}
          >
            {children}
          </Text>
        ) : null}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  buttonBase: {
    borderRadius: 12,
    alignSelf: "flex-start",
  },
  buttonRegular: {
    minHeight: 44,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  buttonCompact: {
    minHeight: 36,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  buttonContent: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
  },
});

import type { ComponentProps } from "react";
import { forwardRef } from "react";
import { TextInput as ReactNativeTextInput, StyleSheet, View } from "react-native";
import type { KeyboardTypeOptions, StyleProp, TextStyle, ViewStyle } from "react-native";

import { Text } from "@/src/ui/Text";
import { useTheme } from "@/src/ui/theme";
import { FONT_FAMILY } from "@/src/ui/typography";

interface TextInputProps extends Omit<
  ComponentProps<typeof ReactNativeTextInput>,
  "style" | "value" | "onChangeText"
> {
  label?: string;
  value: string;
  onChangeText: (text: string) => void;
  mode?: "outlined";
  keyboardType?: KeyboardTypeOptions;
  style?: StyleProp<ViewStyle>;
  inputStyle?: StyleProp<TextStyle>;
  accessibilityLabel?: string;
  placeholder?: string;
  testID?: string;
}

export const TextInput = forwardRef<ReactNativeTextInput, TextInputProps>(function TextInput(
  {
    label,
    value,
    onChangeText,
    keyboardType,
    style,
    inputStyle,
    accessibilityLabel,
    placeholder,
    testID,
    ...rest
  },
  ref,
) {
  const { colors } = useTheme();

  return (
    <View style={[styles.inputWrap, style]}>
      {label ? (
        <Text variant="labelMedium" style={{ color: colors.textMuted }}>
          {label}
        </Text>
      ) : null}
      <ReactNativeTextInput
        ref={ref}
        value={value}
        onChangeText={onChangeText}
        accessibilityLabel={accessibilityLabel}
        keyboardType={keyboardType}
        placeholder={placeholder}
        placeholderTextColor={colors.textSubtle}
        testID={testID}
        {...rest}
        style={[
          styles.input,
          {
            borderColor: colors.borderStrong,
            backgroundColor: colors.surface,
            color: colors.text,
            fontFamily: FONT_FAMILY,
          },
          inputStyle,
        ]}
      />
    </View>
  );
});

const styles = StyleSheet.create({
  inputWrap: {
    gap: 6,
  },
  input: {
    minHeight: 44,
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
  },
});

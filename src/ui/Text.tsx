import type { PropsWithChildren } from "react";
import { Text as ReactNativeText } from "react-native";
import type { StyleProp, TextStyle } from "react-native";

import { useTheme } from "@/src/ui/theme";
import { FONT_FAMILY, TEXT_VARIANTS, type TextVariant } from "@/src/ui/typography";

interface TextProps extends PropsWithChildren {
  variant?: TextVariant;
  style?: StyleProp<TextStyle>;
  accessibilityLabel?: string;
  numberOfLines?: number;
  adjustsFontSizeToFit?: boolean;
  ellipsizeMode?: "head" | "middle" | "tail" | "clip";
  tabularNums?: boolean;
  testID?: string;
}

export function Text({
  children,
  variant = "bodyMedium",
  style,
  tabularNums = false,
  ...rest
}: TextProps) {
  const { colors } = useTheme();

  return (
    <ReactNativeText
      style={[
        TEXT_VARIANTS[variant],
        { color: colors.text, fontFamily: FONT_FAMILY },
        tabularNums ? { fontVariant: ["tabular-nums"] } : null,
        style,
      ]}
      {...rest}
    >
      {children}
    </ReactNativeText>
  );
}

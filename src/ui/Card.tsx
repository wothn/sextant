import type { PropsWithChildren } from "react";
import { StyleSheet, View } from "react-native";
import type { StyleProp, ViewStyle } from "react-native";

import { Text } from "@/src/ui/Text";
import { useTheme } from "@/src/ui/theme";

interface CardProps extends PropsWithChildren {
  style?: StyleProp<ViewStyle>;
  mode?: "elevated" | "contained";
}

function CardBase({ children, style, mode = "elevated" }: CardProps) {
  const { colors } = useTheme();

  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: mode === "contained" ? colors.surfaceAlt : colors.surface,
          borderColor: colors.border,
          shadowColor: colors.shadow,
        },
        style,
      ]}
    >
      {children}
    </View>
  );
}

function CardContent({ children, style }: PropsWithChildren<{ style?: StyleProp<ViewStyle> }>) {
  return <View style={style}>{children}</View>;
}

interface CardTitleProps {
  title: string;
  subtitle?: string;
}

function CardTitle({ title, subtitle }: CardTitleProps) {
  const { colors } = useTheme();

  return (
    <View style={styles.titleWrap}>
      <Text variant="titleMedium">{title}</Text>
      {subtitle ? (
        <Text variant="bodyMedium" style={{ color: colors.textMuted }}>
          {subtitle}
        </Text>
      ) : null}
    </View>
  );
}

export const Card = Object.assign(CardBase, {
  Content: CardContent,
  Title: CardTitle,
});

const styles = StyleSheet.create({
  card: {
    borderRadius: 18,
    borderWidth: 1,
    padding: 16,
    gap: 10,
    shadowOpacity: 0.04,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 1,
  },
  titleWrap: {
    gap: 4,
  },
});

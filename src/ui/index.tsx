import { MaterialCommunityIcons } from "@expo/vector-icons";
import type { ComponentProps, PropsWithChildren, ReactNode } from "react";
import { Fragment, useRef } from "react";
import {
  ActivityIndicator,
  Animated,
  Modal as ReactNativeModal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text as ReactNativeText,
  TextInput as ReactNativeTextInput,
  View,
} from "react-native";
import type {
  KeyboardTypeOptions,
  StyleProp,
  TextStyle,
  ViewStyle,
} from "react-native";
import { useTheme as useTamaguiTheme } from "@tamagui/core";

const TEXT_VARIANTS = {
  displaySmall: {
    fontSize: 40,
    lineHeight: 46,
    fontWeight: "700" as const,
    letterSpacing: -0.6,
  },
  headlineMedium: {
    fontSize: 28,
    lineHeight: 34,
    fontWeight: "700" as const,
    letterSpacing: -0.3,
  },
  headlineSmall: {
    fontSize: 24,
    lineHeight: 30,
    fontWeight: "700" as const,
    letterSpacing: -0.2,
  },
  titleLarge: {
    fontSize: 20,
    lineHeight: 26,
    fontWeight: "600" as const,
    letterSpacing: -0.1,
  },
  titleMedium: { fontSize: 16, lineHeight: 22, fontWeight: "600" as const },
  titleSmall: { fontSize: 14, lineHeight: 20, fontWeight: "600" as const },
  bodyLarge: { fontSize: 16, lineHeight: 24, fontWeight: "400" as const },
  bodyMedium: { fontSize: 14, lineHeight: 22, fontWeight: "400" as const },
  bodySmall: { fontSize: 12, lineHeight: 18, fontWeight: "400" as const },
  labelLarge: { fontSize: 13, lineHeight: 18, fontWeight: "600" as const },
  labelMedium: { fontSize: 12, lineHeight: 16, fontWeight: "600" as const },
  labelSmall: { fontSize: 11, lineHeight: 14, fontWeight: "600" as const },
} as const;

type TextVariant = keyof typeof TEXT_VARIANTS;
type IconName = ComponentProps<typeof MaterialCommunityIcons>["name"];

type ThemeColors = {
  accent: string;
  accentStrong: string;
  accentSoft: string;
  accentMuted: string;
  onAccent: string;
  surface: string;
  surfaceAlt: string;
  surfaceStrong: string;
  background: string;
  backgroundSoft: string;
  text: string;
  textMuted: string;
  textSubtle: string;
  border: string;
  borderStrong: string;
  success: string;
  danger: string;
  warning: string;
  info: string;
  shadow: string;
  overlay: string;
  tabInactive: string;
};

export function useTheme(): { colors: ThemeColors } {
  const theme = useTamaguiTheme();

  return {
    colors: {
      accent: String(theme.accent?.val ?? "#3B82F6"),
      accentStrong: String(theme.accentStrong?.val ?? "#1D4ED8"),
      accentSoft: String(theme.accentSoft?.val ?? "#DBEAFE"),
      accentMuted: String(theme.accentMuted?.val ?? "#BFDBFE"),
      onAccent: String(theme.onAccent?.val ?? "#FFFFFF"),
      surface: String(theme.surface?.val ?? "#FFFFFF"),
      surfaceAlt: String(theme.surfaceAlt?.val ?? "#F1F5F9"),
      surfaceStrong: String(theme.surfaceStrong?.val ?? "#E2E8F0"),
      background: String(theme.background?.val ?? "#F8FAFC"),
      backgroundSoft: String(theme.backgroundSoft?.val ?? "#EEF2F7"),
      text: String(theme.onSurface?.val ?? theme.color?.val ?? "#0F172A"),
      textMuted: String(theme.onSurfaceVariant?.val ?? "#475569"),
      textSubtle: String(theme.onSurfaceMuted?.val ?? "#64748B"),
      border: String(theme.border?.val ?? "#E2E8F0"),
      borderStrong: String(theme.borderStrong?.val ?? "#CBD5E1"),
      success: String(theme.success?.val ?? "#22C55E"),
      danger: String(theme.danger?.val ?? "#EF4444"),
      warning: String(theme.warning?.val ?? "#F59E0B"),
      info: String(theme.info?.val ?? "#0EA5E9"),
      shadow: String(theme.shadowColor?.val ?? "#0B1220"),
      overlay: String(theme.overlay?.val ?? "rgba(15, 23, 42, 0.4)"),
      tabInactive: String(theme.tabInactive?.val ?? "#94A3B8"),
    },
  };
}

interface TextProps extends PropsWithChildren {
  variant?: TextVariant;
  style?: StyleProp<TextStyle>;
  accessibilityLabel?: string;
  numberOfLines?: number;
  adjustsFontSizeToFit?: boolean;
}

export function Text({
  children,
  variant = "bodyMedium",
  style,
  ...rest
}: TextProps) {
  const { colors } = useTheme();

  return (
    <ReactNativeText
      style={[TEXT_VARIANTS[variant], { color: colors.text }, style]}
      {...rest}
    >
      {children}
    </ReactNativeText>
  );
}

interface ScreenProps extends PropsWithChildren {
  style?: StyleProp<ViewStyle>;
  contentContainerStyle?: StyleProp<ViewStyle>;
  contentStyle?: StyleProp<ViewStyle>;
  scroll?: boolean;
}

export function Screen({
  children,
  style,
  contentContainerStyle,
  contentStyle,
  scroll = true,
}: ScreenProps) {
  const { colors } = useTheme();
  const enter = useRef(new Animated.Value(1)).current;

  const animatedStyle = {
    opacity: enter,
    transform: [
      {
        translateY: enter.interpolate({
          inputRange: [0, 1],
          outputRange: [12, 0],
        }),
      },
    ],
  };

  return (
    <View style={[styles.screen, { backgroundColor: colors.background }, style]}>
      <View
        pointerEvents="none"
        style={[styles.screenGlowTop, { backgroundColor: colors.accentSoft }]}
      />
      <View
        pointerEvents="none"
        style={[styles.screenGlowBottom, { backgroundColor: colors.info }]}
      />
      {scroll ? (
        <ScrollView
          style={styles.screenScroll}
          contentContainerStyle={[
            styles.screenContentContainer,
            contentContainerStyle,
          ]}
          showsVerticalScrollIndicator={false}
        >
          <Animated.View
            style={[styles.screenContent, animatedStyle, contentStyle]}
          >
            {children}
          </Animated.View>
        </ScrollView>
      ) : (
        <Animated.View
          style={[
            styles.screenContent,
            animatedStyle,
            contentContainerStyle,
            contentStyle,
          ]}
        >
          {children}
        </Animated.View>
      )}
    </View>
  );
}

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
          shadowOffset:
            mode === "contained" ? { width: 0, height: 8 } : { width: 0, height: 0 },
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
            style={[
              { color: currentVariant.textColor, fontWeight: "700" },
              labelStyle,
            ]}
          >
            {children}
          </Text>
        ) : null}
      </View>
    </Pressable>
  );
}

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
    <View style={{ gap: 4 }}>
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
      <Text
        variant="labelLarge"
        style={{ color: selected ? colors.accentStrong : colors.text }}
      >
        {children}
      </Text>
    </Pressable>
  );
}

interface TextInputProps {
  label?: string;
  value: string;
  onChangeText: (text: string) => void;
  mode?: "outlined";
  keyboardType?: KeyboardTypeOptions;
  style?: StyleProp<ViewStyle>;
}

export function TextInput({
  label,
  value,
  onChangeText,
  keyboardType,
  style,
}: TextInputProps) {
  const { colors } = useTheme();

  return (
    <View style={[styles.inputWrap, style]}>
      {label ? (
        <Text variant="labelMedium" style={{ color: colors.textMuted }}>
          {label}
        </Text>
      ) : null}
      <ReactNativeTextInput
        value={value}
        onChangeText={onChangeText}
        keyboardType={keyboardType}
        style={[
          styles.input,
          {
            borderColor: colors.borderStrong,
            backgroundColor: colors.surface,
            color: colors.text,
          },
        ]}
      />
    </View>
  );
}

interface DividerProps {
  style?: StyleProp<ViewStyle>;
}

export function Divider({ style }: DividerProps) {
  const { colors } = useTheme();
  return <View style={[styles.divider, { backgroundColor: colors.border }, style]} />;
}

interface ProgressBarProps {
  progress: number;
  color?: string;
  style?: StyleProp<ViewStyle>;
}

export function ProgressBar({ progress, color, style }: ProgressBarProps) {
  const { colors } = useTheme();
  const clampedProgress = Math.max(0, Math.min(progress, 1));

  return (
    <View
      style={[styles.progressTrack, { backgroundColor: colors.surfaceStrong }, style]}
    >
      <View
        style={[
          styles.progressFill,
          {
            width: `${clampedProgress * 100}%`,
            backgroundColor: color ?? colors.accent,
          },
        ]}
      />
    </View>
  );
}

interface SurfaceProps extends PropsWithChildren {
  style?: StyleProp<ViewStyle>;
  elevation?: number;
}

export function Surface({ children, style }: SurfaceProps) {
  const { colors } = useTheme();
  return (
    <View
      style={[
        styles.surface,
        {
          backgroundColor: colors.surface,
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

export function Portal({ children }: PropsWithChildren) {
  return <Fragment>{children}</Fragment>;
}

interface ModalProps extends PropsWithChildren {
  visible: boolean;
  onDismiss?: () => void;
  contentContainerStyle?: StyleProp<ViewStyle>;
}

export function Modal({ children, visible, onDismiss, contentContainerStyle }: ModalProps) {
  const { colors } = useTheme();

  return (
    <ReactNativeModal
      transparent
      visible={visible}
      animationType="fade"
      onRequestClose={onDismiss}
    >
      <View style={StyleSheet.absoluteFill}>
        <Pressable
          style={[styles.backdrop, { backgroundColor: colors.overlay }]}
          onPress={onDismiss}
        />
        <View pointerEvents="box-none" style={[StyleSheet.absoluteFill, contentContainerStyle]}>
          {children}
        </View>
      </View>
    </ReactNativeModal>
  );
}

interface DialogProps extends PropsWithChildren {
  visible: boolean;
  onDismiss?: () => void;
  style?: StyleProp<ViewStyle>;
}

function DialogBase({ children, visible, onDismiss, style }: DialogProps) {
  const { colors } = useTheme();

  return (
    <ReactNativeModal
      transparent
      visible={visible}
      animationType="fade"
      onRequestClose={onDismiss}
    >
      <View style={styles.dialogOverlay}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onDismiss} />
        <View
          style={[
            styles.dialogCard,
            {
              backgroundColor: colors.surface,
              borderColor: colors.border,
              shadowColor: colors.shadow,
            },
            style,
          ]}
        >
          {children}
        </View>
      </View>
    </ReactNativeModal>
  );
}

function DialogTitle({ children, accessibilityLabel }: PropsWithChildren<{ accessibilityLabel?: string }>) {
  return (
    <View style={styles.dialogTitleWrap}>
      <Text variant="titleLarge" accessibilityLabel={accessibilityLabel}>
        {children}
      </Text>
    </View>
  );
}

function DialogContent({ children }: PropsWithChildren) {
  return <View style={styles.dialogContent}>{children}</View>;
}

function DialogActions({ children }: PropsWithChildren) {
  return <View style={styles.dialogActions}>{children}</View>;
}

export const Dialog = Object.assign(DialogBase, {
  Title: DialogTitle,
  Content: DialogContent,
  Actions: DialogActions,
});

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    position: "relative",
  },
  screenScroll: {
    flex: 1,
  },
  screenContentContainer: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 120,
  },
  screenContent: {
    gap: 16,
  },
  screenGlowTop: {
    position: "absolute",
    top: -160,
    right: -120,
    width: 280,
    height: 280,
    borderRadius: 140,
    opacity: 0.38,
  },
  screenGlowBottom: {
    position: "absolute",
    bottom: -200,
    left: -160,
    width: 300,
    height: 300,
    borderRadius: 150,
    opacity: 0.18,
  },
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
  card: {
    borderRadius: 18,
    borderWidth: 1,
    padding: 16,
    gap: 10,
    shadowOpacity: 0.06,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
    elevation: 2,
  },
  chip: {
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 8,
    alignSelf: "flex-start",
  },
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
  divider: {
    height: 1,
    width: "100%",
  },
  progressTrack: {
    width: "100%",
    height: 6,
    borderRadius: 999,
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
    borderRadius: 999,
  },
  surface: {
    borderRadius: 20,
    borderWidth: 1,
    shadowOpacity: 0.1,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
    elevation: 3,
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
  },
  dialogOverlay: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 20,
  },
  dialogCard: {
    width: "100%",
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 20,
    paddingVertical: 18,
    gap: 12,
    maxWidth: 420,
    shadowOpacity: 0.16,
    shadowRadius: 22,
    shadowOffset: { width: 0, height: 12 },
    elevation: 7,
  },
  dialogTitleWrap: {
    marginBottom: 4,
  },
  dialogContent: {
    gap: 12,
  },
  dialogActions: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 8,
    marginTop: 4,
  },
});

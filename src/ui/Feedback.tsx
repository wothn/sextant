import type { PropsWithChildren } from "react";
import { StyleSheet, View } from "react-native";
import type { StyleProp, ViewStyle } from "react-native";

import { useTheme } from "@/src/ui/theme";

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
    <View style={[styles.progressTrack, { backgroundColor: colors.surfaceStrong }, style]}>
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

const styles = StyleSheet.create({
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
    shadowOpacity: 0.08,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
    elevation: 2,
  },
});

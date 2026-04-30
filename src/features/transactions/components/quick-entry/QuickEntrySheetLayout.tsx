import type { PropsWithChildren } from "react";
import { useEffect, useRef } from "react";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Button, Sheet, Text, XStack, YStack, useTheme } from "tamagui";

import { MaterialCommunityIcons } from "@expo/vector-icons";
import Animated, {
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withTiming,
} from "react-native-reanimated";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";
import {
  getEnterTimingConfig,
  MOTION_DURATION_BASE,
  MOTION_DURATION_SHEET_EXIT,
  MOTION_STAGGER_DELAY,
  useReducedMotion,
} from "@/src/lib/motion";

interface QuickEntrySheetLayoutProps extends PropsWithChildren {
  visible: boolean;
  saving: boolean;
  onDismiss: () => void;
  onExited?: () => void;
  sheetHeight: number;
}

interface SheetSectionProps extends PropsWithChildren {
  visible: boolean;
}

function SheetBody({ children, visible }: SheetSectionProps) {
  const reduceMotion = useReducedMotion();
  const progress = useSharedValue(reduceMotion ? 1 : 0);

  useEffect(() => {
    if (!visible) {
      progress.value = 0;
      return;
    }

    if (reduceMotion) {
      progress.value = 1;
      return;
    }

    progress.value = 0;
    progress.value = withDelay(
      MOTION_STAGGER_DELAY,
      withTiming(1, getEnterTimingConfig(MOTION_DURATION_BASE, reduceMotion)),
    );
  }, [progress, reduceMotion, visible]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
    transform: [
      {
        translateY: interpolate(progress.value, [0, 1], [6, 0]),
      },
    ],
  }));

  return <Animated.View style={animatedStyle}>{children}</Animated.View>;
}

export function QuickEntrySheetLayout({
  visible,
  saving,
  onDismiss,
  onExited,
  sheetHeight,
  children,
}: QuickEntrySheetLayoutProps) {
  const colors = getThemeColors(useTheme());
  const insets = useSafeAreaInsets();
  const wasVisibleRef = useRef(visible);

  useEffect(() => {
    if (!visible && wasVisibleRef.current) {
      const timer = setTimeout(() => {
        onExited?.();
      }, MOTION_DURATION_SHEET_EXIT);

      wasVisibleRef.current = visible;
      return () => clearTimeout(timer);
    }

    wasVisibleRef.current = visible;
    return undefined;
  }, [onExited, visible]);

  return (
    <Sheet
      open={visible}
      onOpenChange={(open: boolean) => {
        if (!open) {
          onDismiss();
        }
      }}
      modal
      dismissOnOverlayPress
      snapPoints={[sheetHeight]}
      snapPointsMode="constant"
      disableDrag
    >
      <Sheet.Overlay backgroundColor={colors.overlay} />
      <Sheet.Frame
        style={[
          styles.sheet,
          {
            backgroundColor: colors.surface,
            borderColor: colors.border,
            maxHeight: sheetHeight,
            paddingBottom: 24 + insets.bottom,
          },
        ]}
        marginHorizontal={Math.max(insets.left, insets.right)}
        alignSelf="center"
        elevation={2}
      >
        <YStack style={styles.dragHandleWrap}>
          <YStack style={[styles.dragHandle, { backgroundColor: colors.borderStrong }]} />
        </YStack>

        <XStack style={styles.headerRow}>
          <Text
            style={[
              TEXT_VARIANTS.titleSmall,
              { color: colors.textMuted, fontWeight: "700" },
            ]}
          >
            快速记账
          </Text>
          <Button
            unstyled
            accessibilityRole="button"
            accessibilityLabel="关闭记账弹窗"
            disabled={saving}
            onPress={onDismiss}
            style={styles.iconButton}
            backgroundColor={colors.surfaceAlt}
            borderColor={colors.border}
            opacity={saving ? 0.45 : 1}
            pressStyle={{ opacity: 0.9 }}
          >
            <MaterialCommunityIcons name="close" size={22} color={colors.text} />
          </Button>
        </XStack>

        <SheetBody visible={visible}>{children}</SheetBody>
      </Sheet.Frame>
    </Sheet>
  );
}

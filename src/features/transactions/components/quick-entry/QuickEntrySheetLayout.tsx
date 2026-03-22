import type { PropsWithChildren } from "react";
import { useEffect } from "react";
import { Pressable, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { MaterialCommunityIcons } from "@expo/vector-icons";
import Animated, {
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withTiming,
} from "react-native-reanimated";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  BottomSheetModal,
  Surface,
  Text,
  getEnterTimingConfig,
  useTheme,
  MOTION_DURATION_BASE,
  MOTION_STAGGER_DELAY,
  useReducedMotion,
} from "@/src/ui";

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
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <BottomSheetModal
      visible={visible}
      onDismiss={onDismiss}
      onExited={onExited}
      contentContainerStyle={[
        styles.modalContainer,
        {
          paddingTop: Math.max(insets.top, 16),
          paddingHorizontal: Math.max(insets.left, insets.right),
        },
      ]}
    >
      <Surface
        style={[
          styles.sheet,
          {
            backgroundColor: theme.colors.surface,
            borderColor: theme.colors.border,
            maxHeight: sheetHeight,
            paddingBottom: 24 + insets.bottom,
          },
        ]}
        elevation={2}
      >
        <View style={styles.dragHandleWrap}>
          <View style={[styles.dragHandle, { backgroundColor: theme.colors.borderStrong }]} />
        </View>

        <View style={styles.headerRow}>
          <Text variant="titleSmall" style={{ color: theme.colors.textMuted, fontWeight: "700" }}>
            快速记账
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="关闭记账弹窗"
            disabled={saving}
            onPress={onDismiss}
            style={({ pressed }) => [
              styles.iconButton,
              {
                backgroundColor: theme.colors.surfaceAlt,
                borderColor: theme.colors.border,
                opacity: saving ? 0.45 : pressed ? 0.9 : 1,
              },
            ]}
          >
            <MaterialCommunityIcons name="close" size={22} color={theme.colors.text} />
          </Pressable>
        </View>

        <SheetBody visible={visible}>{children}</SheetBody>
      </Surface>
    </BottomSheetModal>
  );
}

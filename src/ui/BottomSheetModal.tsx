import type { PropsWithChildren } from "react";
import { useCallback, useEffect, useState } from "react";
import { Modal as ReactNativeModal, Pressable, StyleSheet, View } from "react-native";
import type { StyleProp, ViewStyle } from "react-native";

import Animated, {
  createAnimatedComponent,
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import {
  getEnterTimingConfig,
  getExitTimingConfig,
  MOTION_DURATION_SHEET_ENTER,
  MOTION_DURATION_SHEET_EXIT,
  MOTION_SHEET_OFFSET,
  useReducedMotion,
} from "@/src/ui/motion";
import { useTheme } from "@/src/ui/theme";

interface BottomSheetModalProps extends PropsWithChildren {
  visible: boolean;
  onDismiss?: () => void;
  onEntered?: () => void;
  onExited?: () => void;
  onShow?: () => void;
  contentContainerStyle?: StyleProp<ViewStyle>;
}

const AnimatedPressable = createAnimatedComponent(Pressable);

export function BottomSheetModal({
  children,
  visible,
  onDismiss,
  onEntered,
  onExited,
  onShow,
  contentContainerStyle,
}: BottomSheetModalProps) {
  const { colors } = useTheme();
  const reduceMotion = useReducedMotion();
  const [rendered, setRendered] = useState(visible);
  const progress = useSharedValue(0);

  const handleExited = useCallback((): void => {
    setRendered(false);
    onExited?.();
  }, [onExited]);

  const handleEntered = useCallback((): void => {
    onEntered?.();
  }, [onEntered]);

  useEffect(() => {
    if (visible) {
      setRendered(true);
    }
  }, [visible]);

  useEffect(() => {
    if (!rendered) {
      return;
    }

    if (visible) {
      progress.value = 0;
      progress.value = withTiming(
        1,
        getEnterTimingConfig(MOTION_DURATION_SHEET_ENTER, reduceMotion),
        (finished) => {
          if (finished) {
            scheduleOnRN(handleEntered);
          }
        },
      );
      return;
    }

    progress.value = withTiming(
      0,
      getExitTimingConfig(MOTION_DURATION_SHEET_EXIT, reduceMotion),
      (finished) => {
        if (finished) {
          scheduleOnRN(handleExited);
        }
      },
    );
  }, [handleEntered, handleExited, progress, reduceMotion, rendered, visible]);

  const backdropStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 1], [0, 1]),
  }));

  const sheetStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 1], [0.88, 1]),
    transform: [
      {
        translateY: interpolate(progress.value, [0, 1], [MOTION_SHEET_OFFSET, 0]),
      },
      {
        scale: interpolate(progress.value, [0, 1], [0.985, 1]),
      },
    ],
  }));

  if (!rendered) {
    return null;
  }

  return (
    <ReactNativeModal
      transparent
      visible
      animationType="none"
      onRequestClose={onDismiss}
      onShow={onShow}
      statusBarTranslucent
    >
      <View style={StyleSheet.absoluteFill}>
        <AnimatedPressable
          style={[styles.backdrop, { backgroundColor: colors.overlay }, backdropStyle]}
          onPress={onDismiss}
        />
        <View pointerEvents="box-none" style={[StyleSheet.absoluteFill, contentContainerStyle]}>
          <Animated.View style={sheetStyle}>{children}</Animated.View>
        </View>
      </View>
    </ReactNativeModal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    position: "absolute",
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
  },
});

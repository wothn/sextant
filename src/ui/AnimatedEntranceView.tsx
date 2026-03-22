import type { PropsWithChildren } from "react";
import { useEffect, useRef } from "react";
import type { StyleProp, ViewStyle } from "react-native";

import Animated, {
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";

import {
  getEnterTimingConfig,
  MOTION_DURATION_BASE,
  MOTION_ENTER_OFFSET,
  useReducedMotion,
} from "@/src/ui/motion";

interface AnimatedEntranceViewProps extends PropsWithChildren {
  style?: StyleProp<ViewStyle>;
}

export function AnimatedEntranceView({ children, style }: AnimatedEntranceViewProps) {
  const reduceMotion = useReducedMotion();
  const hasAnimatedRef = useRef(false);
  const progress = useSharedValue(reduceMotion ? 1 : 0);

  useEffect(() => {
    if (hasAnimatedRef.current || reduceMotion) {
      progress.value = 1;
      hasAnimatedRef.current = true;
      return;
    }

    progress.value = 0;
    progress.value = withTiming(1, getEnterTimingConfig(MOTION_DURATION_BASE, reduceMotion));
    hasAnimatedRef.current = true;
  }, [progress, reduceMotion]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
    transform: [
      {
        translateY: interpolate(progress.value, [0, 1], [MOTION_ENTER_OFFSET, 0]),
      },
    ],
  }));

  return <Animated.View style={[style, animatedStyle]}>{children}</Animated.View>;
}

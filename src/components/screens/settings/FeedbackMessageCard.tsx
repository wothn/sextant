import { useEffect, useState } from "react";

import Animated, {
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import { Card, Text, YStack, useTheme } from "tamagui";

import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";
import {
  getEnterTimingConfig,
  getExitTimingConfig,
  MOTION_DURATION_BASE,
  MOTION_DURATION_FAST,
  useReducedMotion,
} from "@/src/lib/motion";

interface FeedbackMessageCardProps {
  message: string;
}

export function FeedbackMessageCard({ message }: FeedbackMessageCardProps) {
  const colors = getThemeColors(useTheme());
  const reduceMotion = useReducedMotion();
  const [displayMessage, setDisplayMessage] = useState(message);
  const progress = useSharedValue(0);

  useEffect(() => {
    if (message) {
      setDisplayMessage(message);
      progress.value = 0;
      progress.value = withTiming(1, getEnterTimingConfig(MOTION_DURATION_BASE, reduceMotion));
      return;
    }

    progress.value = withTiming(
      0,
      getExitTimingConfig(MOTION_DURATION_FAST, reduceMotion),
      (finished) => {
        if (finished) {
          scheduleOnRN(setDisplayMessage, "");
        }
      },
    );
  }, [message, progress, reduceMotion]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
    transform: [
      {
        translateY: interpolate(progress.value, [0, 1], [6, 0]),
      },
    ],
  }));

  if (!displayMessage) {
    return null;
  }

  return (
    <Animated.View style={animatedStyle}>
      <Card borderRadius={18} borderWidth={1} borderColor={colors.border} backgroundColor={colors.surfaceAlt} padding={16}>
        <YStack>
          <Text
            style={[
              TEXT_VARIANTS.bodyMedium,
              { color: displayMessage.includes("失败") ? colors.danger : colors.text },
            ]}
          >
            {displayMessage}
          </Text>
        </YStack>
      </Card>
    </Animated.View>
  );
}

import { useEffect, useState } from "react";

import Animated, {
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import { Card, Text, useTheme } from "@/src/ui";
import {
  getEnterTimingConfig,
  getExitTimingConfig,
  MOTION_DURATION_BASE,
  MOTION_DURATION_FAST,
  useReducedMotion,
} from "@/src/ui";

interface FeedbackMessageCardProps {
  message: string;
}

export function FeedbackMessageCard({ message }: FeedbackMessageCardProps) {
  const theme = useTheme();
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
      <Card mode="contained">
        <Card.Content>
          <Text
            style={{
              color: displayMessage.includes("失败") ? theme.colors.danger : theme.colors.text,
            }}
          >
            {displayMessage}
          </Text>
        </Card.Content>
      </Card>
    </Animated.View>
  );
}

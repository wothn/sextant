import { MaterialCommunityIcons } from "@expo/vector-icons";
import { Tabs } from "expo-router";
import type { ComponentProps, ReactNode } from "react";
import { useEffect, useState } from "react";
import { Pressable, Text as ReactNativeText } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import Animated, {
  createAnimatedComponent,
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";

import QuickEntrySheetContainer from "@/src/features/transactions/components/QuickEntrySheetContainer";
import { useUIStore } from "@/src/store/ui.store";
import {
  getSpringConfig,
  getTimingConfig,
  MOTION_DURATION_FAST,
  useReducedMotion,
  useTheme,
} from "@/src/ui";

type IconName = ComponentProps<typeof MaterialCommunityIcons>["name"];

interface AnimatedTabVisualProps {
  color: string;
  focused: boolean;
  label?: string;
  name?: IconName;
  size?: number;
}

interface QuickEntryFabButtonProps {
  children: ReactNode;
  isOpen: boolean;
  onPress: () => void;
}

const AnimatedPressable = createAnimatedComponent(Pressable);

function AnimatedTabIcon({ color, focused, name, size = 24 }: AnimatedTabVisualProps) {
  const reduceMotion = useReducedMotion();
  const progress = useSharedValue(focused ? 1 : 0);

  useEffect(() => {
    progress.value = withTiming(
      focused ? 1 : 0,
      getTimingConfig(MOTION_DURATION_FAST, reduceMotion),
    );
  }, [focused, progress, reduceMotion]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 1], [0.62, 1]),
    transform: [
      {
        translateY: interpolate(progress.value, [0, 1], [2, -2]),
      },
      {
        scale: interpolate(progress.value, [0, 1], [0.92, 1]),
      },
    ],
  }));

  if (!name) {
    return null;
  }

  return (
    <Animated.View style={animatedStyle}>
      <MaterialCommunityIcons name={name} color={color} size={size} />
    </Animated.View>
  );
}

function AnimatedTabLabel({ color, focused, label }: AnimatedTabVisualProps) {
  const reduceMotion = useReducedMotion();
  const progress = useSharedValue(focused ? 1 : 0);

  useEffect(() => {
    progress.value = withTiming(
      focused ? 1 : 0,
      getTimingConfig(MOTION_DURATION_FAST, reduceMotion),
    );
  }, [focused, progress, reduceMotion]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: interpolate(progress.value, [0, 1], [0.54, 1]),
    transform: [
      {
        translateY: interpolate(progress.value, [0, 1], [3, 0]),
      },
    ],
  }));

  if (!label) {
    return null;
  }

  return (
    <Animated.Text
      style={[
        {
          color,
          fontSize: 10,
          fontWeight: "600",
          letterSpacing: 0.2,
        },
        animatedStyle,
      ]}
    >
      {label}
    </Animated.Text>
  );
}

function QuickEntryFabButton({ children, isOpen, onPress }: QuickEntryFabButtonProps) {
  const theme = useTheme();
  const reduceMotion = useReducedMotion();
  const openProgress = useSharedValue(isOpen ? 1 : 0);
  const pressedProgress = useSharedValue(0);
  const [pressed, setPressed] = useState(false);

  useEffect(() => {
    openProgress.value = reduceMotion
      ? isOpen
        ? 1
        : 0
      : withSpring(isOpen ? 1 : 0, getSpringConfig(reduceMotion));
  }, [isOpen, openProgress, reduceMotion]);

  useEffect(() => {
    pressedProgress.value = withTiming(
      pressed ? 1 : 0,
      getTimingConfig(MOTION_DURATION_FAST, reduceMotion),
    );
  }, [pressed, pressedProgress, reduceMotion]);

  const outerStyle = useAnimatedStyle(() => ({
    transform: [
      {
        translateY: interpolate(openProgress.value, [0, 1], [0, -6]),
      },
      {
        scale: interpolate(openProgress.value, [0, 1], [1, 1.03]),
      },
      {
        scale: interpolate(pressedProgress.value, [0, 1], [1, 0.95]),
      },
    ],
  }));

  const innerStyle = useAnimatedStyle(() => ({
    transform: [
      {
        rotate: `${interpolate(openProgress.value, [0, 1], [0, 45])}deg`,
      },
      {
        scale: interpolate(openProgress.value, [0, 1], [0.98, 1]),
      },
    ],
  }));

  return (
    <AnimatedPressable
      accessibilityRole="button"
      accessibilityLabel="记账"
      accessibilityState={{ expanded: isOpen }}
      onPress={onPress}
      onPressIn={() => setPressed(true)}
      onPressOut={() => setPressed(false)}
      style={[{ top: -10 }, outerStyle]}
      testID="quick-entry-tab-button"
    >
      <Animated.View
        style={[
          {
            width: 56,
            height: 56,
            borderRadius: 18,
            backgroundColor: theme.colors.accentStrong,
            alignItems: "center",
            justifyContent: "center",
            shadowColor: theme.colors.shadow,
            shadowOpacity: 0.2,
            shadowRadius: 16,
            shadowOffset: { width: 0, height: 8 },
            elevation: 10,
          },
          innerStyle,
        ]}
      >
        {children}
      </Animated.View>
    </AnimatedPressable>
  );
}

function renderTabLabel(title: string) {
  return ({ color, focused }: { color: string; focused: boolean }) => (
    <AnimatedTabLabel color={color} focused={focused} label={title} />
  );
}

function renderTabIcon(name: IconName) {
  return ({ color, focused, size }: { color: string; focused: boolean; size: number }) => (
    <AnimatedTabIcon color={color} focused={focused} name={name} size={size} />
  );
}

export default function TabsLayout() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const openQuickEntrySheet = useUIStore((state) => state.openQuickEntrySheet);
  const quickEntrySheetVisible = useUIStore((state) => state.quickEntrySheetVisible);
  const tabBarBottomOffset = Math.max(insets.bottom, 16);

  return (
    <>
      <Tabs
        screenOptions={{
          headerShown: false,
          tabBarActiveTintColor: theme.colors.accent,
          tabBarInactiveTintColor: theme.colors.tabInactive,
          tabBarStyle: {
            position: "absolute",
            left: 16,
            right: 16,
            bottom: tabBarBottomOffset,
            height: 64,
            paddingTop: 8,
            paddingBottom: 8,
            borderWidth: 1,
            borderRadius: 20,
            backgroundColor: theme.colors.surface,
            borderColor: theme.colors.border,
            shadowColor: theme.colors.shadow,
            shadowOpacity: 0.05,
            shadowRadius: 10,
            shadowOffset: { width: 0, height: 4 },
            elevation: 6,
          },
          sceneStyle: {
            backgroundColor: theme.colors.background,
          },
        }}
      >
        <Tabs.Screen
          name="index"
          options={{
            title: "首页",
            tabBarIcon: renderTabIcon("home-outline"),
            tabBarLabel: renderTabLabel("首页"),
          }}
        />
        <Tabs.Screen
          name="quick-entry"
          options={{
            title: "记账",
            tabBarIcon: ({ size }) => (
              <MaterialCommunityIcons name="plus" color={theme.colors.onAccent} size={size + 6} />
            ),
            tabBarLabel: () => <ReactNativeText style={{ display: "none" }} />,
            tabBarButton: (props) => (
              <QuickEntryFabButton isOpen={quickEntrySheetVisible} onPress={openQuickEntrySheet}>
                {props.children}
              </QuickEntryFabButton>
            ),
          }}
        />
        <Tabs.Screen
          name="analytics"
          options={{
            title: "分析",
            tabBarIcon: renderTabIcon("chart-donut"),
            tabBarLabel: renderTabLabel("分析"),
          }}
        />
        <Tabs.Screen
          name="settings"
          options={{
            title: "设置",
            tabBarIcon: renderTabIcon("cog-outline"),
            tabBarLabel: renderTabLabel("设置"),
          }}
        />
      </Tabs>

      <QuickEntrySheetContainer />
    </>
  );
}

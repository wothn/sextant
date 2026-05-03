import { MaterialCommunityIcons } from "@expo/vector-icons";
import { Tabs } from "expo-router";
import type { ComponentProps, ReactNode } from "react";
import { useEffect, useState } from "react";
import { Pressable, useWindowDimensions } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Text, useTheme } from "tamagui";

import Animated, {
  createAnimatedComponent,
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";

import QuickEntrySheetContainer from "@/src/features/transactions/components/QuickEntrySheetContainer";
import { getThemeColors } from "@/src/lib/theme";
import { useUIStore } from "@/src/store/ui.store";
import {
  getSpringConfig,
  getTimingConfig,
  MOTION_DURATION_FAST,
  useReducedMotion,
} from "@/src/lib/motion";

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
const TAB_BAR_HORIZONTAL_MARGIN = 16;
const TAB_BAR_MIN_WIDTH = 288;
const TAB_BAR_MAX_WIDTH = 360;
const TAB_BAR_WIDTH_RATIO = 0.82;

export interface FloatingTabBarMetrics {
  marginLeft: number;
  width: number;
}

export function getFloatingTabBarMetrics(windowWidth: number): FloatingTabBarMetrics {
  const availableWidth = Math.max(windowWidth - TAB_BAR_HORIZONTAL_MARGIN * 2, TAB_BAR_MIN_WIDTH);
  const preferredWidth = Math.max(Math.round(windowWidth * TAB_BAR_WIDTH_RATIO), TAB_BAR_MIN_WIDTH);
  const tabBarWidth = Math.min(preferredWidth, availableWidth, TAB_BAR_MAX_WIDTH);

  return {
    marginLeft: -Math.round(tabBarWidth / 2),
    width: tabBarWidth,
  };
}

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
  const colors = getThemeColors(useTheme());
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
            backgroundColor: colors.accentStrong,
            alignItems: "center",
            justifyContent: "center",
            shadowColor: colors.shadow,
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
  const colors = getThemeColors(useTheme());
  const { width: windowWidth } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const openQuickEntrySheet = useUIStore((state) => state.openQuickEntrySheet);
  const quickEntrySheetVisible = useUIStore((state) => state.quickEntrySheetVisible);
  const tabBarBottomOffset = Math.max(insets.bottom, 16);
  const tabBarMetrics = getFloatingTabBarMetrics(windowWidth);

  return (
    <>
      <Tabs
        screenOptions={{
          headerShown: false,
          tabBarActiveTintColor: colors.accent,
          tabBarInactiveTintColor: colors.tabInactive,
          tabBarStyle: {
            position: "absolute",
            start: undefined,
            end: undefined,
            left: "50%",
            marginLeft: tabBarMetrics.marginLeft,
            width: tabBarMetrics.width,
            bottom: tabBarBottomOffset,
            height: 64,
            paddingTop: 8,
            paddingBottom: 8,
            borderWidth: 1,
            borderRadius: 20,
            backgroundColor: colors.surface,
            borderColor: colors.border,
            shadowColor: colors.shadow,
            shadowOpacity: 0.05,
            shadowRadius: 10,
            shadowOffset: { width: 0, height: 4 },
            elevation: 6,
          },
          sceneStyle: {
            backgroundColor: colors.background,
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
          name="budget"
          options={{
            title: "预算",
            tabBarIcon: renderTabIcon("wallet-outline"),
            tabBarLabel: renderTabLabel("预算"),
          }}
        />
        <Tabs.Screen
          name="quick-entry"
          options={{
            title: "记账",
            tabBarIcon: ({ size }) => (
              <MaterialCommunityIcons name="plus" color={colors.onAccent} size={size + 6} />
            ),
            tabBarLabel: () => <Text display="none" />,
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

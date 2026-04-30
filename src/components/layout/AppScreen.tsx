import type { PropsWithChildren } from "react";
import { StyleSheet } from "react-native";
import type { StyleProp, ViewStyle } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { ScrollView, YStack, useTheme } from "tamagui";

import { getThemeColors } from "@/src/lib/theme";

type ScreenSafeAreaEdge = "top" | "right" | "bottom" | "left";

interface AppScreenProps extends PropsWithChildren {
  style?: StyleProp<ViewStyle>;
  contentContainerStyle?: StyleProp<ViewStyle>;
  contentStyle?: StyleProp<ViewStyle>;
  scroll?: boolean;
  safeAreaEdges?: ScreenSafeAreaEdge[];
}

function getPaddingValue(style: ViewStyle | undefined, edge: ScreenSafeAreaEdge): number {
  const edgeKey =
    edge === "top"
      ? "paddingTop"
      : edge === "right"
        ? "paddingRight"
        : edge === "bottom"
          ? "paddingBottom"
          : "paddingLeft";
  const axisKey = edge === "top" || edge === "bottom" ? "paddingVertical" : "paddingHorizontal";
  const specificValue = style?.[edgeKey];

  if (typeof specificValue === "number") {
    return specificValue;
  }

  const axisValue = style?.[axisKey];

  if (typeof axisValue === "number") {
    return axisValue;
  }

  return typeof style?.padding === "number" ? style.padding : 0;
}

function withSafeAreaInsets(
  style: StyleProp<ViewStyle>,
  insets: { top: number; right: number; bottom: number; left: number },
  safeAreaEdges: ScreenSafeAreaEdge[],
): ViewStyle {
  const flattenedStyle = StyleSheet.flatten(style) ?? {};

  if (safeAreaEdges.length === 0) {
    return flattenedStyle;
  }

  const nextStyle: ViewStyle = { ...flattenedStyle };

  if (safeAreaEdges.includes("top")) {
    nextStyle.paddingTop = getPaddingValue(flattenedStyle, "top") + insets.top;
  }

  if (safeAreaEdges.includes("right")) {
    nextStyle.paddingRight = getPaddingValue(flattenedStyle, "right") + insets.right;
  }

  if (safeAreaEdges.includes("bottom")) {
    nextStyle.paddingBottom = getPaddingValue(flattenedStyle, "bottom") + insets.bottom;
  }

  if (safeAreaEdges.includes("left")) {
    nextStyle.paddingLeft = getPaddingValue(flattenedStyle, "left") + insets.left;
  }

  return nextStyle;
}

export function AppScreen({
  children,
  style,
  contentContainerStyle,
  contentStyle,
  scroll = true,
  safeAreaEdges = ["top", "right", "bottom", "left"],
}: AppScreenProps) {
  const colors = getThemeColors(useTheme());
  const insets = useSafeAreaInsets();
  const resolvedContentContainerStyle = withSafeAreaInsets(
    [styles.screenContentContainer, contentContainerStyle],
    insets,
    safeAreaEdges,
  );
  const scrollIndicatorInsets = {
    top: safeAreaEdges.includes("top") ? insets.top : 0,
    right: safeAreaEdges.includes("right") ? insets.right : 0,
    bottom: safeAreaEdges.includes("bottom") ? insets.bottom : 0,
    left: safeAreaEdges.includes("left") ? insets.left : 0,
  };

  return (
    <YStack flex={1} position="relative" backgroundColor={colors.background} style={style}>
      <YStack
        pointerEvents="none"
        position="absolute"
        top={-160}
        right={-120}
        width={280}
        height={280}
        borderRadius={140}
        opacity={0.28}
        backgroundColor={colors.accentSoft}
      />
      <YStack
        pointerEvents="none"
        position="absolute"
        bottom={-200}
        left={-160}
        width={300}
        height={300}
        borderRadius={150}
        opacity={0.12}
        backgroundColor={colors.info}
      />
      {scroll ? (
        <ScrollView
          flex={1}
          automaticallyAdjustContentInsets={false}
          contentContainerStyle={resolvedContentContainerStyle as never}
          contentInsetAdjustmentBehavior="never"
          scrollIndicatorInsets={scrollIndicatorInsets}
          showsVerticalScrollIndicator={false}
        >
          <YStack gap={16} style={contentStyle}>
            {children}
          </YStack>
        </ScrollView>
      ) : (
        <YStack gap={16} style={[resolvedContentContainerStyle, contentStyle]}>
          {children}
        </YStack>
      )}
    </YStack>
  );
}

const styles = StyleSheet.create({
  screenContentContainer: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 120,
  },
});

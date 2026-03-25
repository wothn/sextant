import { useCallback, useEffect, useRef } from "react";
import {
  Pressable,
  ScrollView,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from "react-native";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { Text, useTheme } from "@/src/ui";

const VISIBLE_ROWS = 5;
const ITEM_HEIGHT = 48;
const WHEEL_PADDING = ((VISIBLE_ROWS - 1) / 2) * ITEM_HEIGHT;

interface TimeWheelPickerProps {
  label: string;
  accessibilityPrefix: string;
  options: string[];
  selectedValue: string;
  visible: boolean;
  onSelect: (value: string) => void;
}

function clampIndex(index: number, maxIndex: number): number {
  if (index < 0) {
    return 0;
  }

  if (index > maxIndex) {
    return maxIndex;
  }

  return index;
}

export function TimeWheelPicker({
  label,
  accessibilityPrefix,
  options,
  selectedValue,
  visible,
  onSelect,
}: TimeWheelPickerProps) {
  const theme = useTheme();
  const scrollRef = useRef<ScrollView>(null);

  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option === selectedValue),
  );

  const syncToIndex = useCallback((index: number, animated: boolean): void => {
    scrollRef.current?.scrollTo({
      y: index * ITEM_HEIGHT,
      animated,
    });
  }, []);

  useEffect(() => {
    if (!visible) {
      return;
    }

    const handle = requestAnimationFrame(() => {
      syncToIndex(selectedIndex, false);
    });

    return () => cancelAnimationFrame(handle);
  }, [selectedIndex, syncToIndex, visible]);

  const handleScrollComplete = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>): void => {
      const offsetY = event.nativeEvent.contentOffset.y;
      const nextIndex = clampIndex(Math.round(offsetY / ITEM_HEIGHT), options.length - 1);
      const nextValue = options[nextIndex];

      syncToIndex(nextIndex, true);

      if (nextValue && nextValue !== selectedValue) {
        onSelect(nextValue);
      }
    },
    [onSelect, options, selectedValue, syncToIndex],
  );

  return (
    <View style={styles.timeWheelColumn}>
      <View style={styles.timeWheelHeader}>
        <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
          {label}
        </Text>
      </View>

      <View
        style={[
          styles.timeWheelFrame,
          {
            backgroundColor: theme.colors.backgroundSoft,
            borderColor: theme.colors.border,
          },
        ]}
      >
        <View
          pointerEvents="none"
          style={[
            styles.timeWheelFocusBand,
            {
              backgroundColor: theme.colors.surface,
              borderColor: theme.colors.accentMuted,
            },
          ]}
        />
        <ScrollView
          ref={scrollRef}
          showsVerticalScrollIndicator={false}
          bounces={false}
          snapToInterval={ITEM_HEIGHT}
          decelerationRate="fast"
          contentContainerStyle={styles.timeWheelContent}
          onMomentumScrollEnd={handleScrollComplete}
          onScrollEndDrag={handleScrollComplete}
        >
          <View style={{ height: WHEEL_PADDING }} />
          {options.map((option) => {
            const isSelected = option === selectedValue;

            return (
              <Pressable
                key={option}
                accessibilityRole="button"
                accessibilityLabel={`选择${accessibilityPrefix}${option}`}
                onPress={() => {
                  const nextIndex = options.findIndex((item) => item === option);
                  syncToIndex(nextIndex, true);
                  onSelect(option);
                }}
                style={styles.timeWheelOption}
              >
                <Text
                  variant={isSelected ? "titleMedium" : "bodyLarge"}
                  style={{
                    color: isSelected ? theme.colors.text : theme.colors.textMuted,
                    fontWeight: isSelected ? "700" : "500",
                  }}
                >
                  {option}
                </Text>
              </Pressable>
            );
          })}
          <View style={{ height: WHEEL_PADDING }} />
        </ScrollView>
      </View>
    </View>
  );
}

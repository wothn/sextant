import { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
const LOOP_REPEAT_COUNT = 7;

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

function getLoopedIndex(index: number, length: number): number {
  if (length === 0) {
    return 0;
  }

  return ((index % length) + length) % length;
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
  const isMomentumScrollingRef = useRef<boolean>(false);
  const [previewValue, setPreviewValue] = useState<string>(selectedValue);
  const loopedOptions = useMemo(
    () =>
      Array.from(
        { length: options.length * LOOP_REPEAT_COUNT },
        (_, index) => options[index % options.length],
      ),
    [options],
  );
  const centerLoopStartIndex = Math.floor(LOOP_REPEAT_COUNT / 2) * options.length;

  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option === selectedValue),
  );
  const centeredSelectedIndex = centerLoopStartIndex + selectedIndex;
  const displayedValue = previewValue || selectedValue;

  const syncToIndex = useCallback((index: number, animated: boolean): void => {
    scrollRef.current?.scrollTo({
      y: index * ITEM_HEIGHT,
      animated,
    });
  }, []);

  const finalizeSelection = useCallback(
    (offsetY: number): void => {
      const rawIndex = clampIndex(Math.round(offsetY / ITEM_HEIGHT), loopedOptions.length - 1);
      const nextIndex = getLoopedIndex(rawIndex, options.length);
      const nextValue = options[nextIndex];
      const alignedIndex = centerLoopStartIndex + nextIndex;
      const alignedOffsetY = alignedIndex * ITEM_HEIGHT;

      if (nextValue) {
        setPreviewValue(nextValue);
      }

      if (Math.abs(alignedOffsetY - offsetY) > 1) {
        syncToIndex(alignedIndex, false);
      }

      if (nextValue && nextValue !== selectedValue) {
        onSelect(nextValue);
      }
    },
    [centerLoopStartIndex, loopedOptions.length, onSelect, options, selectedValue, syncToIndex],
  );

  useEffect(() => {
    if (!visible) {
      return;
    }

    const handle = requestAnimationFrame(() => {
      syncToIndex(centeredSelectedIndex, false);
    });

    return () => cancelAnimationFrame(handle);
  }, [centeredSelectedIndex, syncToIndex, visible]);

  useEffect(() => {
    setPreviewValue(selectedValue);
  }, [selectedValue]);

  const handleMomentumScrollBegin = useCallback((): void => {
    isMomentumScrollingRef.current = true;
  }, []);

  const handleScrollEndDrag = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>): void => {
      const velocityY = Math.abs(event.nativeEvent.velocity?.y ?? 0);

      if (velocityY > 0.05 || isMomentumScrollingRef.current) {
        return;
      }

      finalizeSelection(event.nativeEvent.contentOffset.y);
    },
    [finalizeSelection],
  );

  const handleMomentumScrollEnd = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>): void => {
      isMomentumScrollingRef.current = false;
      finalizeSelection(event.nativeEvent.contentOffset.y);
    },
    [finalizeSelection],
  );

  const handleScroll = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>): void => {
      const rawIndex = clampIndex(
        Math.round(event.nativeEvent.contentOffset.y / ITEM_HEIGHT),
        loopedOptions.length - 1,
      );
      const nextValue = options[getLoopedIndex(rawIndex, options.length)];

      if (nextValue && nextValue !== previewValue) {
        setPreviewValue(nextValue);
      }
    },
    [loopedOptions.length, options, previewValue],
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
          testID={`${accessibilityPrefix}-focus-band`}
          pointerEvents="none"
          style={[
            styles.timeWheelFocusBand,
            {
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
          scrollEventThrottle={16}
          onScroll={handleScroll}
          onMomentumScrollBegin={handleMomentumScrollBegin}
          onMomentumScrollEnd={handleMomentumScrollEnd}
          onScrollEndDrag={handleScrollEndDrag}
        >
          <View style={{ height: WHEEL_PADDING }} />
          {loopedOptions.map((option, index) => {
            const isCenterCopy =
              index >= centerLoopStartIndex && index < centerLoopStartIndex + options.length;
            const isSelected = option === displayedValue;

            return (
              <Pressable
                key={`${option}-${index}`}
                accessibilityRole={isCenterCopy ? "button" : undefined}
                accessibilityLabel={
                  isCenterCopy ? `选择${accessibilityPrefix}${option}` : undefined
                }
                accessibilityElementsHidden={!isCenterCopy}
                importantForAccessibility={isCenterCopy ? "auto" : "no-hide-descendants"}
                onPress={() => {
                  const nextIndex = options.findIndex((item) => item === option);
                  setPreviewValue(option);
                  syncToIndex(centerLoopStartIndex + nextIndex, true);
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

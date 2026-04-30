import { Button, Dialog, Text, XStack, YStack, useTheme } from "tamagui";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  WEEKDAY_LABELS,
  isSameDay,
} from "@/src/features/transactions/components/quick-entry/utils";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface DatePickerDialogProps {
  visible: boolean;
  calendarMonth: Date;
  selectedDate: Date;
  monthMatrix: Date[][];
  onDismiss: () => void;
  onChangeCalendarMonth: (date: Date) => void;
  onSelectDate: (date: Date) => void;
}

export function DatePickerDialog({
  visible,
  calendarMonth,
  selectedDate,
  monthMatrix,
  onDismiss,
  onChangeCalendarMonth,
  onSelectDate,
}: DatePickerDialogProps) {
  const colors = getThemeColors(useTheme());
  const today = new Date();

  return (
    <Dialog
      open={visible}
      onOpenChange={(open) => {
        if (!open) {
          onDismiss();
        }
      }}
      modal
    >
      <Dialog.Portal>
        <Dialog.Overlay backgroundColor={colors.overlay} />
        <Dialog.Content
          width="100%"
          maxWidth={420}
          borderRadius={20}
          borderWidth={1}
          borderColor={colors.border}
          backgroundColor={colors.surface}
          paddingHorizontal={20}
          paddingVertical={18}
          gap={12}
        >
        <YStack
          style={[
            styles.calendarPanel,
            {
              backgroundColor: colors.backgroundSoft,
              borderColor: colors.border,
            },
          ]}
        >
          <XStack style={styles.calendarHeader}>
            <Button
              unstyled
              accessibilityRole="button"
              accessibilityLabel="查看上月"
              onPress={() =>
                onChangeCalendarMonth(
                  new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() - 1, 1),
                )
              }
              style={styles.calendarNavButton}
              backgroundColor={colors.surface}
              borderColor={colors.border}
              pressStyle={{ opacity: 0.92 }}
            >
              <MaterialCommunityIcons name="chevron-left" size={18} color={colors.text} />
            </Button>
            <Text style={[TEXT_VARIANTS.titleMedium, { fontWeight: "700" }]}>
              {calendarMonth.getFullYear()}年{calendarMonth.getMonth() + 1}月
            </Text>
            <Button
              unstyled
              accessibilityRole="button"
              accessibilityLabel="查看下月"
              onPress={() =>
                onChangeCalendarMonth(
                  new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + 1, 1),
                )
              }
              style={styles.calendarNavButton}
              backgroundColor={colors.surface}
              borderColor={colors.border}
              pressStyle={{ opacity: 0.92 }}
            >
              <MaterialCommunityIcons name="chevron-right" size={18} color={colors.text} />
            </Button>
          </XStack>

          <XStack style={styles.calendarMetaRow}>
            <Text style={[TEXT_VARIANTS.bodySmall, { color: colors.textMuted }]}>
              直接点选日期，保持当前时间不变
            </Text>
            <Button
              unstyled
              accessibilityRole="button"
              accessibilityLabel="回到今天"
              onPress={() => {
                const todayDate = new Date();
                onChangeCalendarMonth(new Date(todayDate.getFullYear(), todayDate.getMonth(), 1));
                onSelectDate(todayDate);
              }}
              style={styles.calendarTodayButton}
              backgroundColor={colors.surface}
              borderColor={colors.borderStrong}
              pressStyle={{ opacity: 0.92 }}
            >
              <Text style={[TEXT_VARIANTS.labelLarge, { color: colors.text }]}>
                今天
              </Text>
            </Button>
          </XStack>

          <XStack style={styles.weekRow}>
            {WEEKDAY_LABELS.map((label) => (
              <Text
                key={label}
                style={[TEXT_VARIANTS.labelMedium, styles.weekCell, { color: colors.textMuted }]}
              >
                {label.replace("周", "")}
              </Text>
            ))}
          </XStack>

          {monthMatrix.map((week, index) => (
            <XStack key={`week-${index}`} style={styles.weekRow}>
              {week.map((day) => {
                const isCurrentMonth = day.getMonth() === calendarMonth.getMonth();
                const isSelected = isSameDay(day, selectedDate);
                const isToday = isSameDay(day, today);

                return (
                  <Button
                    unstyled
                    key={day.toISOString()}
                    accessibilityRole="button"
                    accessibilityLabel={`选择${day.getFullYear()}年${day.getMonth() + 1}月${day.getDate()}日`}
                    onPress={() => onSelectDate(day)}
                    style={styles.dayCell}
                    backgroundColor={
                      isSelected ? colors.accent : isToday ? colors.accentSoft : "transparent"
                    }
                    borderColor={
                      isSelected ? colors.accent : isToday ? colors.accentMuted : "transparent"
                    }
                    pressStyle={{ opacity: 0.9 }}
                  >
                    <Text
                      style={[
                        TEXT_VARIANTS.labelLarge,
                        {
                          color: isSelected
                            ? colors.onAccent
                            : isCurrentMonth
                              ? colors.text
                              : colors.textSubtle,
                          fontWeight: isSelected || isToday ? "700" : "600",
                        },
                      ]}
                    >
                      {day.getDate()}
                    </Text>
                    {isToday && !isSelected ? (
                      <YStack
                        style={[styles.todayMarker, { backgroundColor: colors.accentStrong }]}
                      />
                    ) : null}
                  </Button>
                );
              })}
            </XStack>
          ))}
        </YStack>
        <XStack justifyContent="flex-end" gap={8} marginTop={4}>
        <Button
          unstyled
          minHeight={44}
          borderRadius={12}
          borderWidth={1.25}
          borderColor={colors.borderStrong}
          backgroundColor={colors.surface}
          paddingHorizontal={16}
          paddingVertical={10}
          onPress={onDismiss}
        >
          <Text style={[TEXT_VARIANTS.labelLarge, { color: colors.text }]}>关闭</Text>
        </Button>
        </XStack>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog>
  );
}

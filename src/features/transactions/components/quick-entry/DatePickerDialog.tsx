import { Pressable, View } from "react-native";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  WEEKDAY_LABELS,
  getDateLabel,
  isSameDay,
} from "@/src/features/transactions/components/quick-entry/utils";
import { Button, Dialog, Text, useTheme } from "@/src/ui";

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
  const theme = useTheme();
  const today = new Date();

  return (
    <Dialog
      visible={visible}
      onDismiss={onDismiss}
      style={{ backgroundColor: theme.colors.surface }}
    >
      <Dialog.Title accessibilityLabel="日期弹窗标题">记账日期</Dialog.Title>
      <Dialog.Content>
        <View
          style={[
            styles.dialogHeroCard,
            {
              backgroundColor: theme.colors.backgroundSoft,
              borderColor: theme.colors.border,
            },
          ]}
        >
          <View
            style={[
              styles.dialogHeroIcon,
              { backgroundColor: theme.colors.accentSoft, borderColor: theme.colors.accentMuted },
            ]}
          >
            <MaterialCommunityIcons
              name="calendar-check-outline"
              size={20}
              color={theme.colors.accentStrong}
            />
          </View>
          <View style={styles.dialogHeroCopy}>
            <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
              当前记账日期
            </Text>
            <Text variant="headlineSmall">{`${selectedDate.getMonth() + 1}月${selectedDate.getDate()}日`}</Text>
            <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
              {getDateLabel(selectedDate.getTime())}
            </Text>
          </View>
        </View>

        <View
          style={[
            styles.calendarPanel,
            {
              backgroundColor: theme.colors.backgroundSoft,
              borderColor: theme.colors.border,
            },
          ]}
        >
          <View style={styles.calendarHeader}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="查看上月"
              onPress={() =>
                onChangeCalendarMonth(
                  new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() - 1, 1),
                )
              }
              style={({ pressed }) => [
                styles.calendarNavButton,
                {
                  backgroundColor: theme.colors.surface,
                  borderColor: theme.colors.border,
                  opacity: pressed ? 0.92 : 1,
                },
              ]}
            >
              <MaterialCommunityIcons name="chevron-left" size={18} color={theme.colors.text} />
            </Pressable>
            <Text variant="titleMedium" style={{ fontWeight: "700" }}>
              {calendarMonth.getFullYear()}年{calendarMonth.getMonth() + 1}月
            </Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="查看下月"
              onPress={() =>
                onChangeCalendarMonth(
                  new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + 1, 1),
                )
              }
              style={({ pressed }) => [
                styles.calendarNavButton,
                {
                  backgroundColor: theme.colors.surface,
                  borderColor: theme.colors.border,
                  opacity: pressed ? 0.92 : 1,
                },
              ]}
            >
              <MaterialCommunityIcons name="chevron-right" size={18} color={theme.colors.text} />
            </Pressable>
          </View>

          <View style={styles.calendarMetaRow}>
            <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
              直接点选日期，保持当前时间不变
            </Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="回到今天"
              onPress={() => {
                const todayDate = new Date();
                onChangeCalendarMonth(new Date(todayDate.getFullYear(), todayDate.getMonth(), 1));
                onSelectDate(todayDate);
              }}
              style={({ pressed }) => [
                styles.calendarTodayButton,
                {
                  backgroundColor: theme.colors.surface,
                  borderColor: theme.colors.borderStrong,
                  opacity: pressed ? 0.92 : 1,
                },
              ]}
            >
              <Text variant="labelLarge" style={{ color: theme.colors.text }}>
                今天
              </Text>
            </Pressable>
          </View>

          <View style={styles.weekRow}>
            {WEEKDAY_LABELS.map((label) => (
              <Text
                key={label}
                variant="labelMedium"
                style={[styles.weekCell, { color: theme.colors.textMuted }]}
              >
                {label.replace("周", "")}
              </Text>
            ))}
          </View>

          {monthMatrix.map((week, index) => (
            <View key={`week-${index}`} style={styles.weekRow}>
              {week.map((day) => {
                const isCurrentMonth = day.getMonth() === calendarMonth.getMonth();
                const isSelected = isSameDay(day, selectedDate);
                const isToday = isSameDay(day, today);

                return (
                  <Pressable
                    key={day.toISOString()}
                    accessibilityRole="button"
                    accessibilityLabel={`选择${day.getFullYear()}年${day.getMonth() + 1}月${day.getDate()}日`}
                    onPress={() => onSelectDate(day)}
                    style={({ pressed }) => [
                      styles.dayCell,
                      {
                        backgroundColor: isSelected
                          ? theme.colors.accent
                          : isToday
                            ? theme.colors.accentSoft
                            : "transparent",
                        borderColor: isSelected
                          ? theme.colors.accent
                          : isToday
                            ? theme.colors.accentMuted
                            : "transparent",
                        opacity: pressed ? 0.9 : 1,
                      },
                    ]}
                  >
                    <Text
                      variant="labelLarge"
                      style={{
                        color: isSelected
                          ? theme.colors.onAccent
                          : isCurrentMonth
                            ? theme.colors.text
                            : theme.colors.textSubtle,
                        fontWeight: isSelected || isToday ? "700" : "600",
                      }}
                    >
                      {day.getDate()}
                    </Text>
                    {isToday && !isSelected ? (
                      <View
                        style={[styles.todayMarker, { backgroundColor: theme.colors.accentStrong }]}
                      />
                    ) : null}
                  </Pressable>
                );
              })}
            </View>
          ))}
        </View>
      </Dialog.Content>
      <Dialog.Actions>
        <Button mode="outlined" onPress={onDismiss}>
          关闭
        </Button>
      </Dialog.Actions>
    </Dialog>
  );
}

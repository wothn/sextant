import { useEffect, useMemo, useState } from "react";
import { Pressable, ScrollView, StyleSheet, View } from "react-native";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import type { Category } from "@/src/types/domain";
import { Button, Chip, Dialog, Divider, Modal, Portal, Surface, Text, useTheme } from "@/src/ui";

type EntryType = "expense" | "income";
type PickerMode = "date" | "time" | null;

export interface QuickEntryFormValue {
  type: EntryType;
  amountText: string;
  categoryId: string | null;
  transactionDate: number;
}

interface QuickEntrySheetFormProps {
  visible: boolean;
  categories: Category[];
  value: QuickEntryFormValue;
  saving?: boolean;
  message?: string;
  onChange: (partial: Partial<QuickEntryFormValue>) => void;
  onDismiss: () => void;
  onSubmit: () => void;
}

const WEEKDAY_LABELS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"] as const;

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

function getDateLabel(timestamp: number): string {
  const date = new Date(timestamp);
  const now = new Date();
  const isToday =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();

  const base = `${date.getMonth() + 1}月${date.getDate()}日 ${WEEKDAY_LABELS[date.getDay()]}`;
  return isToday ? `今天 · ${base}` : `${date.getFullYear()}年${base}`;
}

function getTimeLabel(timestamp: number): string {
  const date = new Date(timestamp);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function setDatePart(baseTimestamp: number, sourceTimestamp: number): number {
  const base = new Date(baseTimestamp);
  const source = new Date(sourceTimestamp);

  return new Date(
    source.getFullYear(),
    source.getMonth(),
    source.getDate(),
    base.getHours(),
    base.getMinutes(),
    0,
    0,
  ).getTime();
}

function setTimePart(baseTimestamp: number, hours: number, minutes: number): number {
  const base = new Date(baseTimestamp);

  return new Date(
    base.getFullYear(),
    base.getMonth(),
    base.getDate(),
    hours,
    minutes,
    0,
    0,
  ).getTime();
}

function getMonthMatrix(displayDate: Date): Date[][] {
  const year = displayDate.getFullYear();
  const month = displayDate.getMonth();
  const firstDay = new Date(year, month, 1);
  const firstWeekday = firstDay.getDay();
  const startDate = new Date(year, month, 1 - firstWeekday);
  const weeks: Date[][] = [];

  for (let weekIndex = 0; weekIndex < 6; weekIndex += 1) {
    const week: Date[] = [];
    for (let dayIndex = 0; dayIndex < 7; dayIndex += 1) {
      const current = new Date(startDate);
      current.setDate(startDate.getDate() + weekIndex * 7 + dayIndex);
      week.push(current);
    }
    weeks.push(week);
  }

  return weeks;
}

function buildHourOptions(): string[] {
  return Array.from({ length: 24 }, (_, index) => pad(index));
}

function buildMinuteOptions(): string[] {
  return Array.from({ length: 12 }, (_, index) => pad(index * 5));
}

export default function QuickEntrySheetForm({
  visible,
  categories,
  value,
  saving = false,
  message = "",
  onChange,
  onDismiss,
  onSubmit,
}: QuickEntrySheetFormProps) {
  const theme = useTheme();
  const [pickerMode, setPickerMode] = useState<PickerMode>(null);
  const [calendarMonth, setCalendarMonth] = useState<Date>(new Date(value.transactionDate));

  useEffect(() => {
    if (visible) {
      setCalendarMonth(new Date(value.transactionDate));
      return;
    }

    setPickerMode(null);
  }, [value.transactionDate, visible]);

  const amountDisplay = value.amountText || "0";
  const selectedCategory = useMemo(
    () => categories.find((item) => item.id === value.categoryId) ?? null,
    [categories, value.categoryId],
  );
  const monthMatrix = useMemo(() => getMonthMatrix(calendarMonth), [calendarMonth]);
  const hourOptions = useMemo(() => buildHourOptions(), []);
  const minuteOptions = useMemo(() => buildMinuteOptions(), []);

  const selectedDate = new Date(value.transactionDate);
  const selectedHour = pad(selectedDate.getHours());
  const selectedMinute = pad(selectedDate.getMinutes());
  const expenseSelected = value.type === "expense";
  const incomeSelected = value.type === "income";

  const handleTypeChange = (nextType: EntryType): void => {
    if (nextType === value.type) {
      return;
    }

    onChange({
      type: nextType,
      categoryId: null,
    });
  };

  const handleCategorySelect = (categoryId: string): void => {
    onChange({ categoryId });
  };

  const handleDigitPress = (digit: string): void => {
    if (digit === "." && value.amountText.includes(".")) {
      return;
    }

    if (digit === ".") {
      onChange({
        amountText: value.amountText ? `${value.amountText}.` : "0.",
      });
      return;
    }

    if (value.amountText === "0") {
      onChange({ amountText: digit });
      return;
    }

    const nextText = `${value.amountText}${digit}`;
    const [integerPart, decimalPart] = nextText.split(".");

    if (decimalPart && decimalPart.length > 2) {
      return;
    }

    if (integerPart.length > 9) {
      return;
    }

    onChange({ amountText: nextText });
  };

  const handleDelete = (): void => {
    if (!value.amountText) {
      return;
    }

    onChange({ amountText: value.amountText.slice(0, -1) });
  };

  const handleClear = (): void => {
    onChange({ amountText: "" });
  };

  const handleSelectDate = (nextDate: Date): void => {
    onChange({
      transactionDate: setDatePart(value.transactionDate, nextDate.getTime()),
    });
    setPickerMode(null);
  };

  const handleSelectHour = (hour: string): void => {
    onChange({
      transactionDate: setTimePart(value.transactionDate, Number(hour), Number(selectedMinute)),
    });
  };

  const handleSelectMinute = (minute: string): void => {
    onChange({
      transactionDate: setTimePart(value.transactionDate, Number(selectedHour), Number(minute)),
    });
  };

  const keypadRows: string[][] = [
    ["1", "2", "3"],
    ["4", "5", "6"],
    ["7", "8", "9"],
    [".", "0", "backspace"],
  ];

  return (
    <Portal>
      <Modal visible={visible} onDismiss={onDismiss} contentContainerStyle={styles.modalContainer}>
        <Surface
          style={[
            styles.sheet,
            {
              backgroundColor: theme.colors.surface,
              borderColor: theme.colors.border,
            },
          ]}
          elevation={2}
        >
          <View style={styles.dragHandleWrap}>
            <View
              style={[
                styles.dragHandle,
                {
                  backgroundColor: theme.colors.borderStrong,
                },
              ]}
            />
          </View>

          <View style={styles.topBar}>
            <View
              style={[
                styles.typeSwitch,
                {
                  backgroundColor: theme.colors.surfaceAlt,
                  borderColor: theme.colors.border,
                },
              ]}
            >
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="选择支出"
                onPress={() => handleTypeChange("expense")}
                style={[
                  styles.typeButton,
                  expenseSelected ? { backgroundColor: theme.colors.danger } : null,
                ]}
              >
                <Text
                  variant="labelLarge"
                  style={{
                    color: expenseSelected ? theme.colors.onAccent : theme.colors.text,
                    fontWeight: "700",
                  }}
                >
                  支出
                </Text>
              </Pressable>

              <Pressable
                accessibilityRole="button"
                accessibilityLabel="选择收入"
                onPress={() => handleTypeChange("income")}
                style={[
                  styles.typeButton,
                  incomeSelected ? { backgroundColor: theme.colors.success } : null,
                ]}
              >
                <Text
                  variant="labelLarge"
                  style={{
                    color: incomeSelected ? theme.colors.onAccent : theme.colors.text,
                    fontWeight: "700",
                  }}
                >
                  收入
                </Text>
              </Pressable>
            </View>

            <Button
              compact
              mode="text"
              onPress={onDismiss}
              disabled={saving}
              accessibilityLabel="关闭记账弹窗"
            >
              关闭
            </Button>
          </View>

          <View
            style={[
              styles.amountSection,
              {
                backgroundColor: theme.colors.backgroundSoft,
                borderColor: theme.colors.border,
              },
            ]}
          >
            <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
              金额
            </Text>
            <Text
              variant="displaySmall"
              accessibilityLabel="金额"
              numberOfLines={1}
              adjustsFontSizeToFit
              tabularNums
              style={{
                fontWeight: "800",
                color: theme.colors.text,
              }}
            >
              ¥{amountDisplay}
            </Text>
            {message ? (
              <Text variant="bodyMedium" style={{ color: theme.colors.danger }}>
                {message}
              </Text>
            ) : null}
          </View>

          <View style={styles.metaRow}>
            <View style={styles.metaField}>
              <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
                日期
              </Text>
              <Button
                mode="outlined"
                compact
                accessibilityLabel="日期"
                onPress={() => setPickerMode("date")}
                style={styles.metaButton}
                contentStyle={styles.metaButtonContent}
                labelStyle={styles.metaButtonLabel}
                icon="calendar-month-outline"
              >
                {getDateLabel(value.transactionDate)}
              </Button>
            </View>

            <View style={styles.metaField}>
              <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
                时间
              </Text>
              <Button
                mode="outlined"
                compact
                accessibilityLabel="时间"
                onPress={() => setPickerMode("time")}
                style={styles.metaButton}
                contentStyle={styles.metaButtonContent}
                labelStyle={styles.metaButtonLabel}
                icon="clock-time-four-outline"
              >
                {getTimeLabel(value.transactionDate)}
              </Button>
            </View>
          </View>

          <Divider />

          <View style={styles.categorySection}>
            <View style={styles.categoryHeader}>
              <Text variant="titleMedium" style={{ fontWeight: "700" }}>
                分类
              </Text>
              <Text
                variant="bodyMedium"
                numberOfLines={1}
                style={[styles.categoryHint, { color: theme.colors.textMuted }]}
              >
                {selectedCategory ? `已选分类：${selectedCategory.name}` : "请选择分类"}
              </Text>
            </View>

            <View style={styles.categoryList}>
              {categories.map((item) => (
                <Chip
                  key={item.id}
                  selected={value.categoryId === item.id}
                  showSelectedOverlay
                  onPress={() => handleCategorySelect(item.id)}
                  style={styles.categoryChip}
                >
                  {item.name}
                </Chip>
              ))}
            </View>
          </View>

          <View style={styles.keypadSection}>
            {keypadRows.map((row, rowIndex) => (
              <View key={`row-${rowIndex}`} style={styles.keypadRow}>
                {row.map((key) => {
                  if (key === "backspace") {
                    return (
                      <Pressable
                        key={key}
                        accessibilityRole="button"
                        accessibilityLabel="删除金额"
                        onPress={handleDelete}
                        style={({ pressed }) => [
                          styles.keypadButton,
                          styles.deleteKeypadButton,
                          {
                            backgroundColor: theme.colors.surfaceAlt,
                            borderWidth: 1,
                            borderColor: theme.colors.borderStrong,
                          },
                          pressed ? { transform: [{ scale: 0.98 }] } : null,
                        ]}
                      >
                        <MaterialCommunityIcons
                          name="backspace-outline"
                          size={24}
                          color={theme.colors.textMuted}
                        />
                      </Pressable>
                    );
                  }

                  return (
                    <Pressable
                      key={key}
                      accessibilityRole="button"
                      accessibilityLabel={key}
                      onPress={() => handleDigitPress(key)}
                      style={({ pressed }) => [
                        styles.keypadButton,
                        {
                          backgroundColor: theme.colors.surface,
                          borderWidth: 1,
                          borderColor: theme.colors.borderStrong,
                        },
                        pressed
                          ? {
                              transform: [{ scale: 0.98 }],
                              backgroundColor: theme.colors.surfaceAlt,
                            }
                          : null,
                      ]}
                    >
                      <Text
                        variant="headlineSmall"
                        style={{
                          color: theme.colors.text,
                          fontWeight: "700",
                        }}
                      >
                        {key}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
            ))}

            <View style={styles.actionRow}>
              <Button
                mode="text"
                onPress={handleClear}
                disabled={saving}
                accessibilityLabel="清空金额"
              >
                清空
              </Button>
              <Button
                mode="contained"
                onPress={onSubmit}
                loading={saving}
                disabled={saving}
                accessibilityLabel="保存本次记账"
              >
                保存本次记账
              </Button>
            </View>
          </View>
        </Surface>
      </Modal>

      <Dialog
        visible={visible && pickerMode === "date"}
        onDismiss={() => setPickerMode(null)}
        style={{ backgroundColor: theme.colors.surface }}
      >
        <Dialog.Title accessibilityLabel="日期弹窗标题">选择日期</Dialog.Title>
        <Dialog.Content>
          <View style={styles.calendarHeader}>
            <Button
              compact
              onPress={() =>
                setCalendarMonth(
                  new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() - 1, 1),
                )
              }
            >
              上月
            </Button>
            <Text variant="titleMedium" style={{ fontWeight: "700" }}>
              {calendarMonth.getFullYear()}年{calendarMonth.getMonth() + 1}月
            </Text>
            <Button
              compact
              onPress={() =>
                setCalendarMonth(
                  new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + 1, 1),
                )
              }
            >
              下月
            </Button>
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
                const isSelected =
                  day.getFullYear() === selectedDate.getFullYear() &&
                  day.getMonth() === selectedDate.getMonth() &&
                  day.getDate() === selectedDate.getDate();

                return (
                  <Pressable
                    key={day.toISOString()}
                    accessibilityRole="button"
                    accessibilityLabel={`选择${day.getMonth() + 1}月${day.getDate()}日`}
                    onPress={() => handleSelectDate(day)}
                    style={[
                      styles.dayCell,
                      isSelected ? { backgroundColor: theme.colors.accent } : null,
                    ]}
                  >
                    <Text
                      style={{
                        color: isSelected
                          ? theme.colors.onAccent
                          : isCurrentMonth
                            ? theme.colors.text
                            : theme.colors.textSubtle,
                        fontWeight: isSelected ? "700" : "500",
                      }}
                    >
                      {day.getDate()}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          ))}
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={() => setPickerMode(null)}>关闭</Button>
        </Dialog.Actions>
      </Dialog>

      <Dialog
        visible={visible && pickerMode === "time"}
        onDismiss={() => setPickerMode(null)}
        style={{ backgroundColor: theme.colors.surface }}
      >
        <Dialog.Title accessibilityLabel="时间弹窗标题">选择时间</Dialog.Title>
        <Dialog.Content>
          <View style={styles.timePreviewRow}>
            <MaterialCommunityIcons name="clock-outline" size={18} color={theme.colors.accent} />
            <Text variant="titleMedium" style={{ fontWeight: "700" }}>
              {getTimeLabel(value.transactionDate)}
            </Text>
          </View>

          <View style={styles.timePickerContent}>
            <View style={styles.timeColumn}>
              <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                小时
              </Text>
              <ScrollView style={styles.timeScrollArea}>
                <View style={styles.timeOptions}>
                  {hourOptions.map((hour) => (
                    <Chip
                      key={hour}
                      selected={selectedHour === hour}
                      showSelectedOverlay
                      onPress={() => handleSelectHour(hour)}
                      style={styles.timeChip}
                    >
                      {hour}
                    </Chip>
                  ))}
                </View>
              </ScrollView>
            </View>

            <View style={styles.timeColumn}>
              <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                分钟
              </Text>
              <ScrollView style={styles.timeScrollArea}>
                <View style={styles.timeOptions}>
                  {minuteOptions.map((minute) => (
                    <Chip
                      key={minute}
                      selected={selectedMinute === minute}
                      showSelectedOverlay
                      onPress={() => handleSelectMinute(minute)}
                      style={styles.timeChip}
                    >
                      {minute}
                    </Chip>
                  ))}
                </View>
              </ScrollView>
            </View>
          </View>
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={() => setPickerMode(null)}>完成</Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({
  modalContainer: {
    flex: 1,
    justifyContent: "flex-end",
  },
  sheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    paddingTop: 8,
    paddingHorizontal: 16,
    paddingBottom: 20,
    gap: 14,
    minHeight: "68%",
  },
  dragHandleWrap: {
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: 4,
  },
  dragHandle: {
    width: 44,
    height: 5,
    borderRadius: 999,
  },
  topBar: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 12,
  },
  typeSwitch: {
    flexDirection: "row",
    borderRadius: 14,
    padding: 4,
    gap: 4,
    borderWidth: 1,
  },
  typeButton: {
    minWidth: 72,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  amountSection: {
    gap: 6,
    borderRadius: 18,
    padding: 16,
    borderWidth: 1,
  },
  metaRow: {
    flexDirection: "row",
    gap: 12,
  },
  metaField: {
    flex: 1,
    gap: 6,
    minWidth: 0,
  },
  metaButton: {
    width: "100%",
    minWidth: 0,
  },
  metaButtonContent: {
    height: 44,
    justifyContent: "flex-start",
  },
  metaButtonLabel: {
    fontSize: 13,
    marginHorizontal: 2,
    flexShrink: 1,
  },
  categorySection: {
    gap: 8,
  },
  categoryHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
  categoryHint: {
    flex: 1,
    textAlign: "right",
  },
  categoryList: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  categoryChip: {
    marginRight: 0,
  },
  keypadSection: {
    gap: 8,
    marginTop: "auto",
  },
  keypadRow: {
    flexDirection: "row",
    gap: 8,
  },
  keypadButton: {
    flex: 1,
    height: 56,
    borderRadius: 16,
    alignItems: "center",
    justifyContent: "center",
  },
  deleteKeypadButton: {
    flex: 1,
  },
  actionRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: 4,
  },
  calendarHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 12,
  },
  weekRow: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  weekCell: {
    width: `${100 / 7}%`,
    textAlign: "center",
    marginBottom: 8,
  },
  dayCell: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: "center",
    justifyContent: "center",
    marginVertical: 2,
  },
  timePreviewRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 12,
  },
  timePickerContent: {
    flexDirection: "row",
    gap: 12,
    maxHeight: 320,
  },
  timeColumn: {
    flex: 1,
    gap: 8,
  },
  timeScrollArea: {
    maxHeight: 220,
  },
  timeOptions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  timeChip: {
    marginRight: 0,
  },
});

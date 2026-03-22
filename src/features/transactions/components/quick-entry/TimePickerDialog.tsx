import { Pressable, View } from "react-native";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  type TimePresetOption,
  getDateLabel,
  getTimeLabel,
} from "@/src/features/transactions/components/quick-entry/utils";
import { Button, Dialog, Text, useTheme } from "@/src/ui";

interface TimePickerDialogProps {
  visible: boolean;
  transactionDate: number;
  selectedHour: string;
  selectedMinute: string;
  hourOptions: string[];
  minuteOptions: string[];
  presetOptions: TimePresetOption[];
  onDismiss: () => void;
  onSelectHour: (hour: string) => void;
  onSelectMinute: (minute: string) => void;
  onSelectTime: (hours: number, minutes: number) => void;
}

export function TimePickerDialog({
  visible,
  transactionDate,
  selectedHour,
  selectedMinute,
  hourOptions,
  minuteOptions,
  presetOptions,
  onDismiss,
  onSelectHour,
  onSelectMinute,
  onSelectTime,
}: TimePickerDialogProps) {
  const theme = useTheme();
  const currentTimeLabel = getTimeLabel(transactionDate);

  return (
    <Dialog
      visible={visible}
      onDismiss={onDismiss}
      style={{ backgroundColor: theme.colors.surface }}
    >
      <Dialog.Title accessibilityLabel="时间弹窗标题">记账时间</Dialog.Title>
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
              name="clock-time-four-outline"
              size={20}
              color={theme.colors.accentStrong}
            />
          </View>
          <View style={styles.dialogHeroCopy}>
            <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
              当前记账时间
            </Text>
            <Text variant="headlineSmall">{currentTimeLabel}</Text>
            <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
              {getDateLabel(transactionDate)}
            </Text>
          </View>
        </View>

        <View style={styles.dialogSection}>
          <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
            常用时间
          </Text>
          <View style={styles.timePresetRow}>
            {presetOptions.map((option) => {
              const isSelected = currentTimeLabel === option.hint;

              return (
                <Pressable
                  key={option.key}
                  accessibilityRole="button"
                  accessibilityLabel={`选择常用时间${option.label}`}
                  onPress={() => onSelectTime(option.hours, option.minutes)}
                  style={({ pressed }) => [
                    styles.timePresetCard,
                    {
                      backgroundColor: isSelected
                        ? theme.colors.accentSoft
                        : theme.colors.surfaceAlt,
                      borderColor: isSelected
                        ? theme.colors.accentMuted
                        : theme.colors.borderStrong,
                      opacity: pressed ? 0.92 : 1,
                    },
                  ]}
                >
                  <MaterialCommunityIcons
                    name={option.icon}
                    size={16}
                    color={isSelected ? theme.colors.accentStrong : theme.colors.textMuted}
                  />
                  <Text
                    variant="labelLarge"
                    style={{ color: isSelected ? theme.colors.accentStrong : theme.colors.text }}
                  >
                    {option.label}
                  </Text>
                  <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
                    {option.hint}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>

        <View style={styles.timePickerContent}>
          <View
            style={[
              styles.timeColumn,
              {
                backgroundColor: theme.colors.backgroundSoft,
                borderColor: theme.colors.border,
              },
            ]}
          >
            <View style={styles.timeSectionHeader}>
              <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                小时
              </Text>
              <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                24 小时制
              </Text>
            </View>
            <View style={styles.timeOptionsGrid}>
              {hourOptions.map((hour) => (
                <Pressable
                  key={hour}
                  accessibilityRole="button"
                  accessibilityLabel={`选择小时${hour}`}
                  onPress={() => onSelectHour(hour)}
                  style={({ pressed }) => [
                    styles.timeGridChip,
                    {
                      backgroundColor:
                        selectedHour === hour ? theme.colors.accent : theme.colors.surface,
                      borderColor:
                        selectedHour === hour ? theme.colors.accent : theme.colors.borderStrong,
                      opacity: pressed ? 0.9 : 1,
                    },
                  ]}
                >
                  <Text
                    variant="labelLarge"
                    style={{
                      color:
                        selectedHour === hour ? theme.colors.onAccent : theme.colors.text,
                      fontWeight: "700",
                    }}
                  >
                    {hour}
                  </Text>
                </Pressable>
              ))}
            </View>
          </View>

          <View
            style={[
              styles.timeColumn,
              {
                backgroundColor: theme.colors.backgroundSoft,
                borderColor: theme.colors.border,
              },
            ]}
          >
            <View style={styles.timeSectionHeader}>
              <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                分钟
              </Text>
              <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                5 分钟步进
              </Text>
            </View>
            <View style={styles.timeOptionsGrid}>
              {minuteOptions.map((minute) => (
                <Pressable
                  key={minute}
                  accessibilityRole="button"
                  accessibilityLabel={`选择分钟${minute}`}
                  onPress={() => onSelectMinute(minute)}
                  style={({ pressed }) => [
                    styles.timeGridChip,
                    {
                      backgroundColor:
                        selectedMinute === minute ? theme.colors.accent : theme.colors.surface,
                      borderColor:
                        selectedMinute === minute
                          ? theme.colors.accent
                          : theme.colors.borderStrong,
                      opacity: pressed ? 0.9 : 1,
                    },
                  ]}
                >
                  <Text
                    variant="labelLarge"
                    style={{
                      color:
                        selectedMinute === minute ? theme.colors.onAccent : theme.colors.text,
                      fontWeight: "700",
                    }}
                  >
                    {minute}
                  </Text>
                </Pressable>
              ))}
            </View>
          </View>
        </View>
      </Dialog.Content>
      <Dialog.Actions>
        <Button mode="outlined" onPress={onDismiss}>
          完成
        </Button>
      </Dialog.Actions>
    </Dialog>
  );
}

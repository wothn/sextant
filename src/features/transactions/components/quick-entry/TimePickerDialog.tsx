import { View } from "react-native";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { TimeWheelPicker } from "@/src/features/transactions/components/quick-entry/TimeWheelPicker";
import {
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
  onDismiss: () => void;
  onSelectHour: (hour: string) => void;
  onSelectMinute: (minute: string) => void;
}

export function TimePickerDialog({
  visible,
  transactionDate,
  selectedHour,
  selectedMinute,
  hourOptions,
  minuteOptions,
  onDismiss,
  onSelectHour,
  onSelectMinute,
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

        <View style={styles.timePickerContent}>
          <TimeWheelPicker
            label="小时"
            accessibilityPrefix="小时"
            options={hourOptions}
            selectedValue={selectedHour}
            visible={visible}
            onSelect={onSelectHour}
          />

          <TimeWheelPicker
            label="分钟"
            accessibilityPrefix="分钟"
            options={minuteOptions}
            selectedValue={selectedMinute}
            visible={visible}
            onSelect={onSelectMinute}
          />
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

import { View } from "react-native";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { TimeWheelPicker } from "@/src/features/transactions/components/quick-entry/TimeWheelPicker";
import { Button, Dialog, Text, useTheme } from "@/src/ui";

interface TimePickerDialogProps {
  visible: boolean;
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
  selectedHour,
  selectedMinute,
  hourOptions,
  minuteOptions,
  onDismiss,
  onSelectHour,
  onSelectMinute,
}: TimePickerDialogProps) {
  const theme = useTheme();

  return (
    <Dialog
      visible={visible}
      onDismiss={onDismiss}
      style={{ backgroundColor: theme.colors.surface }}
    >
      <Dialog.Content>
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

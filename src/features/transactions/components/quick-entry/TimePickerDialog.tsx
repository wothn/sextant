import { Button, Dialog, XStack, useTheme } from "tamagui";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { TimeWheelPicker } from "@/src/features/transactions/components/quick-entry/TimeWheelPicker";
import { getThemeColors } from "@/src/lib/theme";

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
  const colors = getThemeColors(useTheme());

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
        <XStack style={styles.timePickerContent}>
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
        </XStack>
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
          完成
        </Button>
        </XStack>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog>
  );
}

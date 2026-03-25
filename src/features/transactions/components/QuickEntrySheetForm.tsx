import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { View, useWindowDimensions } from "react-native";
import type { TextInput as ReactNativeTextInput } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { AmountKeypad } from "@/src/features/transactions/components/quick-entry/AmountKeypad";
import { CategorySelectorStrip } from "@/src/features/transactions/components/quick-entry/CategorySelectorStrip";
import { DatePickerDialog } from "@/src/features/transactions/components/quick-entry/DatePickerDialog";
import { EntryAmountPanel } from "@/src/features/transactions/components/quick-entry/EntryAmountPanel";
import { EntryMetaBar } from "@/src/features/transactions/components/quick-entry/EntryMetaBar";
import { EntryTypeSwitch } from "@/src/features/transactions/components/quick-entry/EntryTypeSwitch";
import { NoteEditorSheet } from "@/src/features/transactions/components/quick-entry/NoteEditorSheet";
import { NoteTrigger } from "@/src/features/transactions/components/quick-entry/NoteTrigger";
import { PaymentMethodSelectorStrip } from "@/src/features/transactions/components/quick-entry/PaymentMethodSelectorStrip";
import { QuickEntrySheetLayout } from "@/src/features/transactions/components/quick-entry/QuickEntrySheetLayout";
import { TimePickerDialog } from "@/src/features/transactions/components/quick-entry/TimePickerDialog";
import type {
  EntryType,
  PickerMode,
  QuickEntryFormValue,
} from "@/src/features/transactions/components/quick-entry/types";
import {
  buildHourOptions,
  buildMinuteOptions,
  getKeypadRows,
  getMonthMatrix,
  pad,
  setDatePart,
  setTimePart,
} from "@/src/features/transactions/components/quick-entry/utils";
import type { Category, PaymentMethod } from "@/src/types/domain";
import { Portal } from "@/src/ui";
import type { TextVariant } from "@/src/ui/typography";

export type { QuickEntryFormValue } from "@/src/features/transactions/components/quick-entry/types";

interface QuickEntrySheetFormProps {
  visible: boolean;
  categories: Category[];
  paymentMethods: PaymentMethod[];
  value: QuickEntryFormValue;
  saving?: boolean;
  message?: string;
  messageTone?: "error" | "success";
  onChange: (partial: Partial<QuickEntryFormValue>) => void;
  onDismiss: () => void;
  onAfterDismiss?: () => void;
  onSubmit: () => void;
}

export default function QuickEntrySheetForm({
  visible,
  categories,
  paymentMethods,
  value,
  saving = false,
  message = "",
  messageTone = "error",
  onChange,
  onDismiss,
  onAfterDismiss,
  onSubmit,
}: QuickEntrySheetFormProps) {
  const { height: windowHeight, width: windowWidth } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const [pickerMode, setPickerMode] = useState<PickerMode>(null);
  const [calendarMonth, setCalendarMonth] = useState<Date>(new Date(value.transactionDate));
  const [noteDialogVisible, setNoteDialogVisible] = useState(false);
  const [tempNote, setTempNote] = useState(value.description);
  const noteInputRef = useRef<ReactNativeTextInput>(null);
  const noteFocusFrameRef = useRef<number | null>(null);
  const noteSheetVisible = visible && noteDialogVisible;

  useEffect(() => {
    if (visible) {
      setCalendarMonth(new Date(value.transactionDate));
      return;
    }

    setPickerMode(null);
  }, [value.transactionDate, visible]);

  const clearNoteFocusSchedule = useCallback((): void => {
    if (noteFocusFrameRef.current === null) {
      return;
    }

    cancelAnimationFrame(noteFocusFrameRef.current);
    noteFocusFrameRef.current = null;
  }, []);

  const focusNoteInput = useCallback((): void => {
    clearNoteFocusSchedule();
    noteFocusFrameRef.current = requestAnimationFrame(() => {
      noteFocusFrameRef.current = null;
      noteInputRef.current?.focus();
    });
  }, [clearNoteFocusSchedule]);

  useEffect(() => {
    if (!visible) {
      setNoteDialogVisible(false);
    }
  }, [visible]);

  useEffect(() => {
    if (!noteSheetVisible) {
      clearNoteFocusSchedule();
      return;
    }

    focusNoteInput();

    return clearNoteFocusSchedule;
  }, [clearNoteFocusSchedule, focusNoteInput, noteSheetVisible]);

  const handleNoteInputLayout = useCallback((): void => {
    if (noteSheetVisible) {
      focusNoteInput();
    }
  }, [focusNoteInput, noteSheetVisible]);

  const amountDisplay = value.amountText || "0";
  const monthMatrix = useMemo(() => getMonthMatrix(calendarMonth), [calendarMonth]);
  const hourOptions = useMemo(() => buildHourOptions(), []);
  const minuteOptions = useMemo(() => buildMinuteOptions(), []);
  const keypadRows = useMemo(() => getKeypadRows(), []);

  const selectedDate = new Date(value.transactionDate);
  const selectedHour = pad(selectedDate.getHours());
  const selectedMinute = pad(selectedDate.getMinutes());
  const amountVariant: TextVariant = windowWidth >= 390 ? "displaySmall" : "headlineMedium";
  const sheetHeight =
    windowHeight > 0
      ? Math.min(Math.max(windowHeight * 0.82, 480), windowHeight - Math.max(insets.top, 8) - 8)
      : 560;

  const handleTypeChange = (nextType: EntryType): void => {
    if (nextType !== value.type) {
      onChange({ type: nextType, categoryId: null });
    }
  };

  const handleDigitPress = (digit: string): void => {
    if (digit === "." && value.amountText.includes(".")) {
      return;
    }

    if (digit === ".") {
      onChange({ amountText: value.amountText ? `${value.amountText}.` : "0." });
      return;
    }

    if (value.amountText === "0") {
      onChange({ amountText: digit });
      return;
    }

    const nextText = `${value.amountText}${digit}`;
    const [integerPart, decimalPart] = nextText.split(".");

    if ((decimalPart && decimalPart.length > 2) || integerPart.length > 9) {
      return;
    }

    onChange({ amountText: nextText });
  };

  return (
    <Portal>
      <QuickEntrySheetLayout
        visible={visible}
        saving={saving}
        onDismiss={onDismiss}
        onExited={onAfterDismiss}
        sheetHeight={sheetHeight}
      >
        <View>
          <View style={styles.controlRow}>
            <EntryTypeSwitch value={value.type} onChange={handleTypeChange} />
            <EntryMetaBar
              transactionDate={value.transactionDate}
              onOpenDate={() => setPickerMode("date")}
              onOpenTime={() => setPickerMode("time")}
            />
          </View>
        </View>

        <View>
          <EntryAmountPanel
            amountDisplay={amountDisplay}
            amountVariant={amountVariant}
            message={message}
            messageTone={messageTone}
          />
        </View>

        <View>
          <CategorySelectorStrip
            categories={categories}
            selectedCategoryId={value.categoryId}
            onSelectCategory={(categoryId) => onChange({ categoryId })}
          />
        </View>

        <View>
          <View style={styles.footerBlock}>
            <PaymentMethodSelectorStrip
              paymentMethods={paymentMethods}
              selectedPaymentMethodId={value.paymentMethodId}
              onSelectPaymentMethod={(paymentMethodId) => onChange({ paymentMethodId })}
            />
            <NoteTrigger
              description={value.description}
              onPress={() => {
                setTempNote(value.description);
                setNoteDialogVisible(true);
              }}
            />
            <AmountKeypad
              keypadRows={keypadRows}
              saving={saving}
              onPressDigit={handleDigitPress}
              onDelete={() => onChange({ amountText: value.amountText.slice(0, -1) })}
              onClear={() => onChange({ amountText: "" })}
              onSubmit={onSubmit}
            />
          </View>
        </View>
      </QuickEntrySheetLayout>

      <NoteEditorSheet
        visible={noteSheetVisible}
        note={tempNote}
        inputRef={noteInputRef}
        onChangeNote={setTempNote}
        onClose={() => setNoteDialogVisible(false)}
        onConfirm={() => {
          onChange({ description: tempNote.trim() });
          setNoteDialogVisible(false);
        }}
        onInputLayout={handleNoteInputLayout}
      />

      <DatePickerDialog
        visible={visible && pickerMode === "date"}
        calendarMonth={calendarMonth}
        selectedDate={selectedDate}
        monthMatrix={monthMatrix}
        onDismiss={() => setPickerMode(null)}
        onChangeCalendarMonth={setCalendarMonth}
        onSelectDate={(nextDate) => {
          onChange({ transactionDate: setDatePart(value.transactionDate, nextDate.getTime()) });
          setPickerMode(null);
        }}
      />

      <TimePickerDialog
        visible={visible && pickerMode === "time"}
        selectedHour={selectedHour}
        selectedMinute={selectedMinute}
        hourOptions={hourOptions}
        minuteOptions={minuteOptions}
        onDismiss={() => setPickerMode(null)}
        onSelectHour={(hour) =>
          onChange({
            transactionDate: setTimePart(
              value.transactionDate,
              Number(hour),
              Number(selectedMinute),
            ),
          })
        }
        onSelectMinute={(minute) =>
          onChange({
            transactionDate: setTimePart(
              value.transactionDate,
              Number(selectedHour),
              Number(minute),
            ),
          })
        }
      />
    </Portal>
  );
}

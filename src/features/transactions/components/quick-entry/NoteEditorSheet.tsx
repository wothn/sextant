import { useCallback, useEffect, useRef, useState } from "react";
import { Keyboard, Platform, Pressable, View } from "react-native";
import type { RefObject } from "react";
import type {
  KeyboardEvent as ReactNativeKeyboardEvent,
  TextInput as ReactNativeTextInput,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { BottomSheetModal, Button, Surface, Text, TextInput, useTheme } from "@/src/ui";

interface NoteEditorSheetProps {
  visible: boolean;
  note: string;
  inputRef: RefObject<ReactNativeTextInput | null>;
  onChangeNote: (value: string) => void;
  onClose: () => void;
  onConfirm: () => void;
  onInputLayout?: () => void;
}

export function NoteEditorSheet({
  visible,
  note,
  inputRef,
  onChangeNote,
  onClose,
  onConfirm,
  onInputLayout,
}: NoteEditorSheetProps) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const [keyboardOffset, setKeyboardOffset] = useState(0);
  const focusFrameRef = useRef<number | null>(null);
  const focusTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inputLaidOutRef = useRef(false);
  const modalShownRef = useRef(false);
  const sheetEnteredRef = useRef(false);
  const inputFocusedRef = useRef(false);
  const visibleRef = useRef(visible);

  const clearFocusSchedule = useCallback((): void => {
    if (focusFrameRef.current !== null) {
      cancelAnimationFrame(focusFrameRef.current);
      focusFrameRef.current = null;
    }

    if (focusTimerRef.current === null) {
      return;
    }

    clearTimeout(focusTimerRef.current);
    focusTimerRef.current = null;
  }, []);

  const scheduleFocusInput = useCallback((): void => {
    if (
      !visibleRef.current ||
      !modalShownRef.current ||
      !inputLaidOutRef.current ||
      inputFocusedRef.current
    ) {
      return;
    }

    clearFocusSchedule();

    focusFrameRef.current = requestAnimationFrame(() => {
      focusFrameRef.current = null;

      focusTimerRef.current = setTimeout(() => {
        focusTimerRef.current = null;

        if (!visibleRef.current || inputFocusedRef.current) {
          return;
        }

        inputRef.current?.focus();

        if (!sheetEnteredRef.current) {
          return;
        }

        focusTimerRef.current = setTimeout(() => {
          focusTimerRef.current = null;
          if (!visibleRef.current || inputFocusedRef.current) {
            return;
          }

          inputRef.current?.focus();
        }, 80);
      }, 40);
    });
  }, [clearFocusSchedule, inputRef]);

  const handleModalShow = useCallback((): void => {
    modalShownRef.current = true;
    scheduleFocusInput();
  }, [scheduleFocusInput]);

  const handleSheetEntered = useCallback((): void => {
    sheetEnteredRef.current = true;
    scheduleFocusInput();
  }, [scheduleFocusInput]);

  const handleInputLayout = useCallback((): void => {
    inputLaidOutRef.current = true;
    onInputLayout?.();
    scheduleFocusInput();
  }, [onInputLayout, scheduleFocusInput]);

  const handleInputFocus = useCallback((): void => {
    inputFocusedRef.current = true;
    clearFocusSchedule();
  }, [clearFocusSchedule]);

  const handleInputBlur = useCallback((): void => {
    inputFocusedRef.current = false;
  }, []);

  useEffect(() => {
    const showEventName = Platform.OS === "ios" ? "keyboardWillShow" : "keyboardDidShow";
    const hideEventName = Platform.OS === "ios" ? "keyboardWillHide" : "keyboardDidHide";

    const handleKeyboardShow = (event: ReactNativeKeyboardEvent): void => {
      Keyboard.scheduleLayoutAnimation(event);
      setKeyboardOffset(Math.max(event.endCoordinates.height - insets.bottom, 0));
    };

    const handleKeyboardHide = (event: ReactNativeKeyboardEvent): void => {
      Keyboard.scheduleLayoutAnimation(event);
      setKeyboardOffset(0);
    };

    const showSubscription = Keyboard.addListener(showEventName, handleKeyboardShow);
    const hideSubscription = Keyboard.addListener(hideEventName, handleKeyboardHide);

    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, [insets.bottom]);

  useEffect(() => {
    visibleRef.current = visible;

    if (visible) {
      inputLaidOutRef.current = false;
      modalShownRef.current = false;
      sheetEnteredRef.current = false;
      inputFocusedRef.current = false;
      setKeyboardOffset(0);
      return;
    }

    clearFocusSchedule();
  }, [clearFocusSchedule, visible]);

  if (!visible) {
    return null;
  }

  return (
    <BottomSheetModal
      visible={visible}
      onDismiss={onClose}
      contentContainerStyle={[
        styles.modalContainer,
        {
          paddingTop: Math.max(insets.top, 16),
          paddingHorizontal: Math.max(insets.left, insets.right),
          paddingBottom: keyboardOffset,
        },
      ]}
      onShow={handleModalShow}
      onEntered={handleSheetEntered}
    >
      <View style={styles.noteSheetContainer}>
        <Surface
          style={[
            styles.sheet,
            {
              backgroundColor: theme.colors.surface,
              borderColor: theme.colors.border,
              paddingBottom: 24 + insets.bottom,
            },
          ]}
        >
          <View style={styles.dragHandleWrap}>
            <View style={[styles.dragHandle, { backgroundColor: theme.colors.borderStrong }]} />
          </View>

          <View style={styles.headerRow}>
            <Text variant="titleMedium" style={{ fontWeight: "700", color: theme.colors.text }}>
              添加备注
            </Text>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="关闭备注弹窗"
              onPress={onClose}
              style={({ pressed }) => [
                styles.iconButton,
                {
                  backgroundColor: theme.colors.surfaceAlt,
                  borderColor: theme.colors.border,
                  opacity: pressed ? 0.8 : 1,
                },
              ]}
            >
              <MaterialCommunityIcons name="close" size={22} color={theme.colors.text} />
            </Pressable>
          </View>

          <TextInput
            ref={inputRef}
            value={note}
            onChangeText={onChangeNote}
            onFocus={handleInputFocus}
            onBlur={handleInputBlur}
            onLayout={handleInputLayout}
            placeholder="在此输入备注..."
            maxLength={100}
            multiline
            showSoftInputOnFocus
            inputStyle={styles.noteSheetInput}
          />

          <Button mode="contained" onPress={onConfirm} style={{ marginTop: 8 }}>
            确定
          </Button>
        </Surface>
      </View>
    </BottomSheetModal>
  );
}

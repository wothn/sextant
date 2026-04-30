import { useCallback, useEffect, useRef, useState } from "react";
import {
  Keyboard,
  Platform,
} from "react-native";
import type { ComponentRef, RefObject } from "react";
import type { KeyboardEvent as ReactNativeKeyboardEvent } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Button, Input, Sheet, Text, XStack, YStack, useTheme } from "tamagui";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface NoteEditorSheetProps {
  visible: boolean;
  note: string;
  inputRef: RefObject<FocusableInputRef | null>;
  onChangeNote: (value: string) => void;
  onClose: () => void;
  onConfirm: () => void;
  onInputLayout?: () => void;
}

export type FocusableInputRef = ComponentRef<typeof Input>;

export function NoteEditorSheet({
  visible,
  note,
  inputRef,
  onChangeNote,
  onClose,
  onConfirm,
  onInputLayout,
}: NoteEditorSheetProps) {
  const colors = getThemeColors(useTheme());
  const insets = useSafeAreaInsets();
  const [keyboardOffset, setKeyboardOffset] = useState(0);
  const focusFrameRef = useRef<number | null>(null);
  const modalShownRef = useRef(false);
  const inputFocusedRef = useRef(false);
  const visibleRef = useRef(visible);

  const clearFocusSchedule = useCallback((): void => {
    if (focusFrameRef.current !== null) {
      cancelAnimationFrame(focusFrameRef.current);
      focusFrameRef.current = null;
    }
  }, []);

  const scheduleFocusInput = useCallback((): void => {
    if (!visibleRef.current || !modalShownRef.current || inputFocusedRef.current) {
      return;
    }

    clearFocusSchedule();

    inputRef.current?.focus();

    focusFrameRef.current = requestAnimationFrame(() => {
      focusFrameRef.current = null;

      if (!visibleRef.current || inputFocusedRef.current) {
        return;
      }

      inputRef.current?.focus();
    });
  }, [clearFocusSchedule, inputRef]);

  const handleInputLayout = useCallback((): void => {
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
      setKeyboardOffset(Math.max(event.endCoordinates.height, 0));
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
  }, []);

  useEffect(() => {
    visibleRef.current = visible;

    if (visible) {
      modalShownRef.current = true;
      inputFocusedRef.current = false;
      scheduleFocusInput();
      return;
    }

    Keyboard.dismiss();
    setKeyboardOffset(0);
    clearFocusSchedule();
  }, [clearFocusSchedule, scheduleFocusInput, visible]);

  return (
    <Sheet
      open={visible}
      onOpenChange={(open: boolean) => {
        if (!open) {
          onClose();
        }
      }}
      modal
      dismissOnOverlayPress
      snapPoints={[360]}
      snapPointsMode="constant"
      disableDrag
      unmountChildrenWhenHidden
    >
      <Sheet.Overlay backgroundColor={colors.overlay} />
      <Sheet.Frame
        style={[
          styles.sheet,
          {
            backgroundColor: colors.surface,
            borderColor: colors.border,
            paddingBottom: 24 + insets.bottom + keyboardOffset,
          },
        ]}
        marginHorizontal={Math.max(insets.left, insets.right)}
        alignSelf="center"
      >
        <YStack style={styles.dragHandleWrap}>
          <YStack style={[styles.dragHandle, { backgroundColor: colors.borderStrong }]} />
        </YStack>

              <XStack style={styles.headerRow}>
                <Text
                  style={[TEXT_VARIANTS.titleMedium, { fontWeight: "700", color: colors.text }]}
                >
                  添加备注
                </Text>
                <Button
                  unstyled
                  accessibilityRole="button"
                  accessibilityLabel="关闭备注弹窗"
                  onPress={onClose}
                  style={styles.iconButton}
                  backgroundColor={colors.surfaceAlt}
                  borderColor={colors.border}
                  pressStyle={{ opacity: 0.8 }}
                >
                  <MaterialCommunityIcons name="close" size={22} color={colors.text} />
                </Button>
              </XStack>

              <Input
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
                minHeight={60}
                textAlignVertical="top"
                paddingTop={12}
                borderWidth={1}
                borderRadius={12}
                borderColor={colors.borderStrong}
                backgroundColor={colors.surface}
                color={colors.text}
                placeholderTextColor="$onSurfaceMuted"
                fontFamily="$body"
                fontSize={16}
                paddingHorizontal={12}
                paddingVertical={10}
              />

              <Button
                unstyled
                alignSelf="flex-start"
                minHeight={44}
                borderRadius={12}
                backgroundColor={colors.accent}
                paddingHorizontal={16}
                paddingVertical={10}
                onPress={onConfirm}
                marginTop={8}
              >
                <Text style={[TEXT_VARIANTS.labelLarge, { color: colors.onAccent }]}>确定</Text>
              </Button>
      </Sheet.Frame>
    </Sheet>
  );
}

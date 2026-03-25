import { useCallback, useEffect, useState } from "react";
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
  onInputLayout: () => void;
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

  const focusInput = useCallback((): void => {
    requestAnimationFrame(() => {
      inputRef.current?.focus();
    });
  }, [inputRef]);

  useEffect(() => {
    const showEventName = Platform.OS === "ios" ? "keyboardWillShow" : "keyboardDidShow";
    const hideEventName = Platform.OS === "ios" ? "keyboardWillHide" : "keyboardDidHide";

    const handleKeyboardShow = (event: ReactNativeKeyboardEvent): void => {
      setKeyboardOffset(Math.max(event.endCoordinates.height - insets.bottom, 0));
    };

    const handleKeyboardHide = (): void => {
      setKeyboardOffset(0);
    };

    const showSubscription = Keyboard.addListener(showEventName, handleKeyboardShow);
    const hideSubscription = Keyboard.addListener(hideEventName, handleKeyboardHide);

    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, [insets.bottom]);

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
      onShow={focusInput}
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
            onLayout={onInputLayout}
            placeholder="在此输入备注..."
            maxLength={100}
            multiline
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

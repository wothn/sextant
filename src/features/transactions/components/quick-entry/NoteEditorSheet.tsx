import type { PropsWithChildren, ReactElement } from "react";
import { useCallback, useEffect, useRef } from "react";
import { Keyboard, TextInput } from "react-native";
import { KeyboardAvoidingView } from "react-native-keyboard-controller";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Button, Sheet, Text, XStack, YStack, useTheme } from "tamagui";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface NoteEditorSheetProps {
  visible: boolean;
  value: string;
  onChange: (value: string) => void;
  onDismiss: () => void;
}

function NoteKeyboardContainer({ children }: PropsWithChildren): ReactElement {
  return (
    <KeyboardAvoidingView
      behavior="translate-with-padding"
      pointerEvents="box-none"
      style={styles.noteKeyboardWrap}
      testID="note-keyboard-avoiding-view"
    >
      {children}
    </KeyboardAvoidingView>
  );
}

export function NoteEditorSheet({ visible, value, onChange, onDismiss }: NoteEditorSheetProps) {
  const colors = getThemeColors(useTheme());
  const insets = useSafeAreaInsets();
  const inputRef = useRef<TextInput | null>(null);
  const didRequestFocusRef = useRef(false);
  const bottomPadding = 12 + Math.min(insets.bottom, 8);

  const requestInputFocus = useCallback((): void => {
    if (!visible || didRequestFocusRef.current) {
      return;
    }

    didRequestFocusRef.current = true;
    inputRef.current?.focus();
  }, [visible]);

  useEffect(() => {
    if (visible) {
      didRequestFocusRef.current = false;
      requestInputFocus();
      return;
    }

    didRequestFocusRef.current = false;
    Keyboard.dismiss();
  }, [requestInputFocus, visible]);

  return (
    <Sheet
      open={visible}
      onOpenChange={(open: boolean) => {
        if (!open) {
          onDismiss();
        }
      }}
      modal
      dismissOnOverlayPress
      snapPoints={[218]}
      snapPointsMode="constant"
      disableDrag
      containerComponent={NoteKeyboardContainer}
      unmountChildrenWhenHidden
    >
      <Sheet.Overlay backgroundColor={colors.overlay} />
      <Sheet.Frame
        style={[
          styles.noteSheet,
          {
            backgroundColor: colors.surface,
            borderColor: colors.border,
            paddingBottom: bottomPadding,
          },
        ]}
        marginHorizontal={Math.max(insets.left, insets.right)}
        alignSelf="center"
        elevation={3}
        testID="note-sheet-frame"
      >
        <XStack style={styles.noteSheetHeader}>
          <YStack gap={2} flex={1}>
            <Text style={[TEXT_VARIANTS.titleSmall, { color: colors.text, fontWeight: "700" }]}>
              添加备注
            </Text>
            <Text style={[TEXT_VARIANTS.bodySmall, { color: colors.textMuted }]}>
              给这笔记账补充一点上下文
            </Text>
          </YStack>
          <Button
            unstyled
            accessibilityRole="button"
            accessibilityLabel="完成备注"
            onPress={onDismiss}
            style={styles.noteDoneButton}
            backgroundColor={colors.accent}
            pressStyle={{ opacity: 0.92 }}
          >
            <Text style={[TEXT_VARIANTS.labelLarge, { color: colors.onAccent }]}>完成</Text>
          </Button>
        </XStack>

        <XStack
          style={[
            styles.noteInputFrame,
            {
              backgroundColor: colors.backgroundSoft,
              borderColor: colors.borderStrong,
            },
          ]}
        >
          <MaterialCommunityIcons name="text-box-edit-outline" size={20} color={colors.textMuted} />
          <TextInput
            ref={inputRef}
            accessibilityLabel="备注输入框"
            autoFocus={visible}
            value={value}
            onChangeText={onChange}
            onLayout={requestInputFocus}
            placeholder="例如：和朋友晚餐、项目报销..."
            placeholderTextColor={colors.textSubtle}
            showSoftInputOnFocus
            multiline
            maxLength={120}
            returnKeyType="done"
            style={[styles.noteTextInput, { color: colors.text }]}
            testID="note-text-input"
            textAlignVertical="top"
          />
        </XStack>
      </Sheet.Frame>
    </Sheet>
  );
}

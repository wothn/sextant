import type { PropsWithChildren } from "react";
import { Modal as ReactNativeModal, Pressable, StyleSheet, View } from "react-native";
import type { StyleProp, ViewStyle } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { Text } from "@/src/ui/Text";
import { useTheme } from "@/src/ui/theme";

interface DialogProps extends PropsWithChildren {
  visible: boolean;
  onDismiss?: () => void;
  style?: StyleProp<ViewStyle>;
}

function DialogBase({ children, visible, onDismiss, style }: DialogProps) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <ReactNativeModal transparent visible={visible} animationType="fade" onRequestClose={onDismiss}>
      <View
        style={[
          styles.dialogOverlay,
          {
            paddingTop: insets.top + 20,
            paddingRight: insets.right + 20,
            paddingBottom: insets.bottom + 20,
            paddingLeft: insets.left + 20,
          },
        ]}
      >
        <Pressable style={StyleSheet.absoluteFill} onPress={onDismiss} />
        <View
          style={[
            styles.dialogCard,
            {
              backgroundColor: colors.surface,
              borderColor: colors.border,
              shadowColor: colors.shadow,
            },
            style,
          ]}
        >
          {children}
        </View>
      </View>
    </ReactNativeModal>
  );
}

function DialogTitle({
  children,
  accessibilityLabel,
}: PropsWithChildren<{ accessibilityLabel?: string }>) {
  return (
    <View style={styles.dialogTitleWrap}>
      <Text variant="titleLarge" accessibilityLabel={accessibilityLabel}>
        {children}
      </Text>
    </View>
  );
}

function DialogContent({ children }: PropsWithChildren) {
  return <View style={styles.dialogContent}>{children}</View>;
}

function DialogActions({ children }: PropsWithChildren) {
  return <View style={styles.dialogActions}>{children}</View>;
}

export const Dialog = Object.assign(DialogBase, {
  Title: DialogTitle,
  Content: DialogContent,
  Actions: DialogActions,
});

const styles = StyleSheet.create({
  dialogOverlay: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  dialogCard: {
    width: "100%",
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 20,
    paddingVertical: 18,
    gap: 12,
    maxWidth: 420,
    shadowOpacity: 0.16,
    shadowRadius: 22,
    shadowOffset: { width: 0, height: 12 },
    elevation: 7,
  },
  dialogTitleWrap: {
    marginBottom: 4,
  },
  dialogContent: {
    gap: 12,
  },
  dialogActions: {
    flexDirection: "row",
    justifyContent: "flex-end",
    gap: 8,
    marginTop: 4,
  },
});

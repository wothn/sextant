import type { PropsWithChildren } from "react";
import { Modal as ReactNativeModal, Pressable, StyleSheet, View } from "react-native";
import type { StyleProp, ViewStyle } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useTheme } from "@/src/ui/theme";

interface ModalProps extends PropsWithChildren {
  visible: boolean;
  onDismiss?: () => void;
  onShow?: () => void;
  contentContainerStyle?: StyleProp<ViewStyle>;
}

export function Modal({ children, visible, onDismiss, onShow, contentContainerStyle }: ModalProps) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <ReactNativeModal
      transparent
      visible={visible}
      animationType="fade"
      onRequestClose={onDismiss}
      onShow={onShow}
    >
      <View style={StyleSheet.absoluteFill}>
        <Pressable
          style={[styles.backdrop, { backgroundColor: colors.overlay }]}
          onPress={onDismiss}
        />
        <View
          pointerEvents="box-none"
          style={[
            StyleSheet.absoluteFill,
            {
              paddingTop: insets.top,
              paddingRight: insets.right,
              paddingBottom: insets.bottom,
              paddingLeft: insets.left,
            },
            contentContainerStyle,
          ]}
        >
          {children}
        </View>
      </View>
    </ReactNativeModal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    position: "absolute",
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
  },
});

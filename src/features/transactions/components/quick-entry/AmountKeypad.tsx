import { ActivityIndicator, Pressable, View } from "react-native";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { Text, useTheme } from "@/src/ui";

interface AmountKeypadProps {
  keypadRows: string[][];
  saving: boolean;
  onPressDigit: (digit: string) => void;
  onDelete: () => void;
  onClear: () => void;
  onSubmit: () => void;
}

export function AmountKeypad({
  keypadRows,
  saving,
  onPressDigit,
  onDelete,
  onClear,
  onSubmit,
}: AmountKeypadProps) {
  const theme = useTheme();

  return (
    <View style={styles.keypadLayout}>
      <View style={styles.keypadGrid}>
        {keypadRows.map((row, rowIndex) => (
          <View key={`row-${rowIndex}`} style={styles.keypadRow}>
            {row.map((key) => {
              if (key === "spacer") {
                return <View key={key} style={styles.keypadSpacer} />;
              }

              return (
                <Pressable
                  key={key}
                  accessibilityRole="button"
                  accessibilityLabel={key}
                  onPress={() => onPressDigit(key)}
                  style={({ pressed }) => [
                    styles.keypadButton,
                    {
                      backgroundColor: theme.colors.surface,
                      borderColor: theme.colors.borderStrong,
                      opacity: pressed ? 0.9 : 1,
                    },
                  ]}
                >
                  <Text
                    variant="headlineSmall"
                    style={{ color: theme.colors.text, fontWeight: "700" }}
                  >
                    {key}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        ))}
      </View>

      <View style={styles.keypadRail}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="删除金额"
          onPress={onDelete}
          style={({ pressed }) => [
            styles.railButton,
            {
              backgroundColor: theme.colors.surfaceAlt,
              borderColor: theme.colors.borderStrong,
              opacity: pressed ? 0.9 : 1,
            },
          ]}
        >
          <MaterialCommunityIcons
            name="backspace-outline"
            size={24}
            color={theme.colors.textMuted}
          />
        </Pressable>

        <Pressable
          accessibilityRole="button"
          accessibilityLabel="清空金额"
          onPress={onClear}
          style={({ pressed }) => [
            styles.railButton,
            {
              backgroundColor: theme.colors.surfaceAlt,
              borderColor: theme.colors.borderStrong,
              opacity: pressed ? 0.9 : 1,
            },
          ]}
        >
          <Text variant="titleSmall" style={{ fontWeight: "700", color: theme.colors.text }}>
            清空
          </Text>
        </Pressable>

        <Pressable
          accessibilityRole="button"
          accessibilityLabel="保存本次记账"
          disabled={saving}
          onPress={onSubmit}
          style={({ pressed }) => [
            styles.saveButton,
            {
              backgroundColor: theme.colors.accent,
              borderColor: theme.colors.accent,
              opacity: saving ? 0.7 : pressed ? 0.9 : 1,
            },
          ]}
        >
          {saving ? (
            <ActivityIndicator size="small" color={theme.colors.onAccent} />
          ) : (
            <Text
              variant="headlineSmall"
              style={{ color: theme.colors.onAccent, fontWeight: "800" }}
            >
              保存
            </Text>
          )}
        </Pressable>
      </View>
    </View>
  );
}

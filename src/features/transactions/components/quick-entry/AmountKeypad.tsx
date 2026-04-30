import { Button, Spinner, Text, XStack, YStack, useTheme } from "tamagui";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

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
  const colors = getThemeColors(useTheme());

  return (
    <XStack style={styles.keypadLayout}>
      <YStack style={styles.keypadGrid}>
        {keypadRows.map((row, rowIndex) => (
          <XStack key={`row-${rowIndex}`} style={styles.keypadRow}>
            {row.map((key) => {
              if (key === "spacer") {
                return <YStack key={key} style={styles.keypadSpacer} />;
              }

              return (
                <Button
                  unstyled
                  key={key}
                  accessibilityRole="button"
                  accessibilityLabel={key}
                  onPress={() => onPressDigit(key)}
                  style={styles.keypadButton}
                  backgroundColor={colors.surface}
                  borderColor={colors.borderStrong}
                  pressStyle={{ opacity: 0.9 }}
                >
                  <Text
                    style={[TEXT_VARIANTS.headlineSmall, { color: colors.text, fontWeight: "700" }]}
                  >
                    {key}
                  </Text>
                </Button>
              );
            })}
          </XStack>
        ))}
      </YStack>

      <YStack style={styles.keypadRail}>
        <Button
          unstyled
          accessibilityRole="button"
          accessibilityLabel="删除金额"
          onPress={onDelete}
          style={styles.railButton}
          backgroundColor={colors.surfaceAlt}
          borderColor={colors.borderStrong}
          pressStyle={{ opacity: 0.9 }}
        >
          <MaterialCommunityIcons
            name="backspace-outline"
            size={24}
            color={colors.textMuted}
          />
        </Button>

        <Button
          unstyled
          accessibilityRole="button"
          accessibilityLabel="清空金额"
          onPress={onClear}
          style={styles.railButton}
          backgroundColor={colors.surfaceAlt}
          borderColor={colors.borderStrong}
          pressStyle={{ opacity: 0.9 }}
        >
          <Text style={[TEXT_VARIANTS.titleSmall, { fontWeight: "700", color: colors.text }]}>
            清空
          </Text>
        </Button>

        <Button
          unstyled
          accessibilityRole="button"
          accessibilityLabel="保存本次记账"
          disabled={saving}
          onPress={onSubmit}
          style={styles.saveButton}
          backgroundColor={colors.accent}
          borderColor={colors.accent}
          opacity={saving ? 0.7 : 1}
          pressStyle={{ opacity: 0.9 }}
        >
          {saving ? (
            <Spinner size="small" color={colors.onAccent} />
          ) : (
            <Text
              style={[
                TEXT_VARIANTS.headlineSmall,
                { color: colors.onAccent, fontWeight: "800" },
              ]}
            >
              保存
            </Text>
          )}
        </Button>
      </YStack>
    </XStack>
  );
}

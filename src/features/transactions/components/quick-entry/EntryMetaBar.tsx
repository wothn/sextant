import { Button, Text, XStack, useTheme } from "tamagui";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  getDateChipLabel,
  getTimeLabel,
} from "@/src/features/transactions/components/quick-entry/utils";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface EntryMetaBarProps {
  transactionDate: number;
  onOpenDate: () => void;
  onOpenTime: () => void;
}

export function EntryMetaBar({ transactionDate, onOpenDate, onOpenTime }: EntryMetaBarProps) {
  const colors = getThemeColors(useTheme());

  return (
    <XStack style={styles.metaControlRow}>
      <Button
        unstyled
        accessibilityRole="button"
        accessibilityLabel="日期"
        onPress={onOpenDate}
        style={styles.metaChip}
        backgroundColor={colors.surfaceAlt}
        borderColor={colors.border}
      >
        <Text style={[TEXT_VARIANTS.labelLarge, { fontWeight: "700" }]}>
          {getDateChipLabel(transactionDate)}
        </Text>
        <MaterialCommunityIcons name="chevron-down" size={16} color={colors.textMuted} />
      </Button>

      <Button
        unstyled
        accessibilityRole="button"
        accessibilityLabel="时间"
        onPress={onOpenTime}
        style={styles.metaChip}
        backgroundColor={colors.surfaceAlt}
        borderColor={colors.border}
      >
        <Text style={[TEXT_VARIANTS.labelLarge, { fontWeight: "700" }]}>
          {getTimeLabel(transactionDate)}
        </Text>
        <MaterialCommunityIcons name="chevron-down" size={16} color={colors.textMuted} />
      </Button>
    </XStack>
  );
}

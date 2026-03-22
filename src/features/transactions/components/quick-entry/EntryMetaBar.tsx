import { Pressable, View } from "react-native";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  getDateChipLabel,
  getTimeLabel,
} from "@/src/features/transactions/components/quick-entry/utils";
import { Text, useTheme } from "@/src/ui";

interface EntryMetaBarProps {
  transactionDate: number;
  onOpenDate: () => void;
  onOpenTime: () => void;
}

export function EntryMetaBar({ transactionDate, onOpenDate, onOpenTime }: EntryMetaBarProps) {
  const theme = useTheme();

  return (
    <View style={styles.metaControlRow}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="日期"
        onPress={onOpenDate}
        style={[
          styles.metaChip,
          {
            backgroundColor: theme.colors.surfaceAlt,
            borderColor: theme.colors.border,
          },
        ]}
      >
        <Text variant="labelLarge" style={{ fontWeight: "700" }}>
          {getDateChipLabel(transactionDate)}
        </Text>
        <MaterialCommunityIcons name="chevron-down" size={16} color={theme.colors.textMuted} />
      </Pressable>

      <Pressable
        accessibilityRole="button"
        accessibilityLabel="时间"
        onPress={onOpenTime}
        style={[
          styles.metaChip,
          {
            backgroundColor: theme.colors.surfaceAlt,
            borderColor: theme.colors.border,
          },
        ]}
      >
        <Text variant="labelLarge" style={{ fontWeight: "700" }}>
          {getTimeLabel(transactionDate)}
        </Text>
        <MaterialCommunityIcons name="chevron-down" size={16} color={theme.colors.textMuted} />
      </Pressable>
    </View>
  );
}

import { View } from "react-native";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { Text, useTheme } from "@/src/ui";
import type { TextVariant } from "@/src/ui/typography";

interface EntryAmountPanelProps {
  amountDisplay: string;
  amountVariant: TextVariant;
  message: string;
  messageTone?: "error" | "success";
}

export function EntryAmountPanel({
  amountDisplay,
  amountVariant,
  message,
  messageTone = "error",
}: EntryAmountPanelProps) {
  const theme = useTheme();
  const isError = messageTone === "error";
  const bannerColor = isError ? theme.colors.danger : theme.colors.success;
  const iconName = isError ? "alert-circle-outline" : "check-circle-outline";

  return (
    <View
      style={[
        styles.amountPanel,
        {
          backgroundColor: theme.colors.backgroundSoft,
          borderColor: theme.colors.border,
        },
      ]}
    >
      <View style={styles.amountRow}>
        <Text
          variant="titleLarge"
          style={[styles.amountCurrency, { color: theme.colors.textMuted }]}
        >
          ¥
        </Text>
        <Text
          variant={amountVariant}
          accessibilityLabel="金额"
          numberOfLines={1}
          adjustsFontSizeToFit
          tabularNums
          style={[styles.amountValue, { fontWeight: "800", color: theme.colors.text }]}
        >
          {amountDisplay}
        </Text>
      </View>

      {message ? (
        <View
          style={[
            styles.messageBanner,
            {
              backgroundColor: theme.colors.surface,
              borderColor: bannerColor,
            },
          ]}
        >
          <MaterialCommunityIcons name={iconName} size={16} color={bannerColor} />
          <Text variant="bodyMedium" style={{ color: bannerColor, flex: 1 }}>
            {message}
          </Text>
        </View>
      ) : null}
    </View>
  );
}

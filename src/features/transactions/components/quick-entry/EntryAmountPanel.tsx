import { Text, XStack, YStack, useTheme } from "tamagui";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS, type TextVariant } from "@/src/lib/typography";

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
  const colors = getThemeColors(useTheme());
  const isError = messageTone === "error";
  const bannerColor = isError ? colors.danger : colors.success;
  const iconName = isError ? "alert-circle-outline" : "check-circle-outline";

  return (
    <YStack
      style={[
        styles.amountPanel,
        {
          backgroundColor: colors.backgroundSoft,
          borderColor: colors.border,
        },
      ]}
    >
      <XStack style={styles.amountRow}>
        <Text
          style={[TEXT_VARIANTS.titleLarge, styles.amountCurrency, { color: colors.textMuted }]}
        >
          ¥
        </Text>
        <Text
          accessibilityLabel="金额"
          numberOfLines={1}
          adjustsFontSizeToFit
          style={[
            TEXT_VARIANTS[amountVariant],
            styles.amountValue,
            { fontWeight: "800", color: colors.text, fontVariant: ["tabular-nums"] },
          ]}
        >
          {amountDisplay}
        </Text>
      </XStack>

      {message ? (
        <XStack
          style={[
            styles.messageBanner,
            {
              backgroundColor: colors.surface,
              borderColor: bannerColor,
            },
          ]}
        >
          <MaterialCommunityIcons name={iconName} size={16} color={bannerColor} />
          <Text style={[TEXT_VARIANTS.bodyMedium, { color: bannerColor, flex: 1 }]}>
            {message}
          </Text>
        </XStack>
      ) : null}
    </YStack>
  );
}

import { Button, Sheet, Text, XStack, YStack, useTheme } from "tamagui";

import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface TransactionDetailSummary {
  categoryName: string;
  amount: string;
  time: string;
  typeLabel: string;
  spendingLabel: string;
  paymentMethodName: string;
  description: string;
}

interface TransactionDetailSheetProps {
  visible: boolean;
  amountTone: string;
  summary: TransactionDetailSummary | null;
  onDismiss: () => void;
}

export function TransactionDetailSheet({
  visible,
  amountTone,
  summary,
  onDismiss,
}: TransactionDetailSheetProps) {
  const colors = getThemeColors(useTheme());

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
      snapPoints={[55]}
    >
      <Sheet.Overlay backgroundColor={colors.overlay} />
      <Sheet.Frame
        backgroundColor={colors.surface}
        borderTopLeftRadius={24}
        borderTopRightRadius={24}
        borderWidth={1}
        borderColor={colors.border}
        padding={20}
        gap={16}
      >
        {summary ? (
          <>
            <XStack justifyContent="space-between" alignItems="center">
              <Text style={TEXT_VARIANTS.titleLarge}>交易详情</Text>
              <Button
                unstyled
                minHeight={36}
                borderRadius={12}
                paddingHorizontal={12}
                paddingVertical={8}
                onPress={onDismiss}
              >
                <Text style={[TEXT_VARIANTS.labelLarge, { color: colors.accent }]}>关闭</Text>
              </Button>
            </XStack>

            <YStack gap={4}>
              <Text style={TEXT_VARIANTS.headlineSmall}>{summary.categoryName}</Text>
              <Text
                style={[
                  TEXT_VARIANTS.titleLarge,
                  { color: amountTone, fontWeight: "700", fontVariant: ["tabular-nums"] },
                ]}
              >
                {summary.amount}
              </Text>
            </YStack>

            <YStack gap={12}>
              {[
                { label: "类型", value: summary.typeLabel },
                { label: "支付方式", value: summary.paymentMethodName },
                { label: "统计口径", value: summary.spendingLabel },
                { label: "时间", value: summary.time },
                { label: "备注", value: summary.description },
              ].map((item) => (
                <YStack key={item.label} gap={4}>
                  <Text style={[TEXT_VARIANTS.labelMedium, { color: colors.textMuted }]}>
                    {item.label}
                  </Text>
                  <Text style={TEXT_VARIANTS.bodyLarge}>{item.value}</Text>
                </YStack>
              ))}
            </YStack>
          </>
        ) : null}
      </Sheet.Frame>
    </Sheet>
  );
}

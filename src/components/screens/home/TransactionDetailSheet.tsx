import { View } from "react-native";

import { BottomSheetModal, Button, Portal, Text, useTheme } from "@/src/ui";

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
  const theme = useTheme();

  return (
    <Portal>
      <BottomSheetModal
        visible={visible}
        onDismiss={onDismiss}
        contentContainerStyle={{ flex: 1, justifyContent: "flex-end" }}
      >
        {summary ? (
          <View
            style={{
              backgroundColor: theme.colors.surface,
              borderTopLeftRadius: 24,
              borderTopRightRadius: 24,
              borderWidth: 1,
              borderColor: theme.colors.border,
              padding: 20,
              gap: 16,
            }}
          >
            <View
              style={{
                flexDirection: "row",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <Text variant="titleLarge">交易详情</Text>
              <Button compact onPress={onDismiss}>
                关闭
              </Button>
            </View>

            <View style={{ gap: 4 }}>
              <Text variant="headlineSmall">{summary.categoryName}</Text>
              <Text
                variant="titleLarge"
                style={{ color: amountTone, fontWeight: "700" }}
                tabularNums
              >
                {summary.amount}
              </Text>
            </View>

            <View style={{ gap: 12 }}>
              {[
                { label: "类型", value: summary.typeLabel },
                { label: "支付方式", value: summary.paymentMethodName },
                { label: "统计口径", value: summary.spendingLabel },
                { label: "时间", value: summary.time },
                { label: "备注", value: summary.description },
              ].map((item) => (
                <View key={item.label} style={{ gap: 4 }}>
                  <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
                    {item.label}
                  </Text>
                  <Text variant="bodyLarge">{item.value}</Text>
                </View>
              ))}
            </View>
          </View>
        ) : null}
      </BottomSheetModal>
    </Portal>
  );
}

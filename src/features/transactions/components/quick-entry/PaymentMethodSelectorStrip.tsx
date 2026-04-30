import { Button, ScrollView, Text, XStack, YStack, useTheme } from "tamagui";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  getInitialLabel,
  getOptionIconName,
} from "@/src/features/transactions/components/quick-entry/utils";
import type { PaymentMethod } from "@/src/types/domain";
import { getThemeColors } from "@/src/lib/theme";
import { TEXT_VARIANTS } from "@/src/lib/typography";

interface PaymentMethodSelectorStripProps {
  paymentMethods: PaymentMethod[];
  selectedPaymentMethodId: string | null;
  onSelectPaymentMethod: (paymentMethodId: string | null) => void;
}

export function PaymentMethodSelectorStrip({
  paymentMethods,
  selectedPaymentMethodId,
  onSelectPaymentMethod,
}: PaymentMethodSelectorStripProps) {
  const colors = getThemeColors(useTheme());

  return (
    <>
      <XStack style={styles.sectionHeader}>
        <Text style={[TEXT_VARIANTS.titleSmall, { fontWeight: "700" }]}>
          支付方式
        </Text>
      </XStack>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.paymentMethodRow}
      >
        <Button
          unstyled
          accessibilityRole="button"
          accessibilityLabel="选择支付方式不设置"
          onPress={() => onSelectPaymentMethod(null)}
          style={styles.paymentMethodChip}
          backgroundColor={selectedPaymentMethodId === null ? colors.accentSoft : colors.surfaceAlt}
          borderColor={selectedPaymentMethodId === null ? colors.accentMuted : colors.borderStrong}
          pressStyle={{ opacity: 0.92 }}
        >
          <MaterialCommunityIcons
            name="minus-circle-outline"
            size={18}
            color={selectedPaymentMethodId === null ? colors.accentStrong : colors.textMuted}
          />
          <Text
            style={[
              TEXT_VARIANTS.labelLarge,
              {
                color: selectedPaymentMethodId === null ? colors.accentStrong : colors.text,
                fontWeight: "700",
              },
            ]}
          >
            不设置
          </Text>
        </Button>

        {paymentMethods.map((item) => {
          const selected = selectedPaymentMethodId === item.id;
          const iconName = getOptionIconName(item.icon);

          return (
            <Button
              unstyled
              key={item.id}
              accessibilityRole="button"
              accessibilityLabel={`选择支付方式${item.name}`}
              onPress={() => onSelectPaymentMethod(item.id)}
              style={styles.paymentMethodChip}
              backgroundColor={selected ? colors.accentSoft : colors.surfaceAlt}
              borderColor={selected ? colors.accentMuted : colors.borderStrong}
              pressStyle={{ opacity: 0.92 }}
            >
              <YStack
                style={[
                  styles.paymentMethodBadge,
                  {
                    backgroundColor: selected ? colors.surface : colors.backgroundSoft,
                  },
                ]}
              >
                {iconName ? (
                  <MaterialCommunityIcons
                    name={iconName}
                    size={14}
                    color={selected ? colors.accentStrong : item.color}
                  />
                ) : (
                  <Text
                    style={[
                      TEXT_VARIANTS.labelMedium,
                      { color: selected ? colors.accentStrong : item.color, fontWeight: "800" },
                    ]}
                  >
                    {getInitialLabel(item.name)}
                  </Text>
                )}
              </YStack>
              <Text
                style={[
                  TEXT_VARIANTS.labelLarge,
                  { color: selected ? colors.accentStrong : colors.text, fontWeight: "700" },
                ]}
              >
                {item.name}
              </Text>
            </Button>
          );
        })}
      </ScrollView>
    </>
  );
}

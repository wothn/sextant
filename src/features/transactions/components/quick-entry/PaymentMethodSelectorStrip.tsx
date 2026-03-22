import { Pressable, ScrollView, View } from "react-native";

import { MaterialCommunityIcons } from "@expo/vector-icons";

import { styles } from "@/src/features/transactions/components/quick-entry/styles";
import {
  getInitialLabel,
  getOptionIconName,
} from "@/src/features/transactions/components/quick-entry/utils";
import type { PaymentMethod } from "@/src/types/domain";
import { Text, useTheme } from "@/src/ui";

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
  const theme = useTheme();

  return (
    <>
      <View style={styles.sectionHeader}>
        <Text variant="titleSmall" style={{ fontWeight: "700" }}>
          支付方式
        </Text>
      </View>

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.paymentMethodRow}
      >
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="选择支付方式不设置"
          onPress={() => onSelectPaymentMethod(null)}
          style={({ pressed }) => [
            styles.paymentMethodChip,
            {
              backgroundColor:
                selectedPaymentMethodId === null
                  ? theme.colors.accentSoft
                  : theme.colors.surfaceAlt,
              borderColor:
                selectedPaymentMethodId === null
                  ? theme.colors.accentMuted
                  : theme.colors.borderStrong,
              opacity: pressed ? 0.92 : 1,
            },
          ]}
        >
          <MaterialCommunityIcons
            name="minus-circle-outline"
            size={18}
            color={
              selectedPaymentMethodId === null ? theme.colors.accentStrong : theme.colors.textMuted
            }
          />
          <Text
            variant="labelLarge"
            style={{
              color:
                selectedPaymentMethodId === null ? theme.colors.accentStrong : theme.colors.text,
              fontWeight: "700",
            }}
          >
            不设置
          </Text>
        </Pressable>

        {paymentMethods.map((item) => {
          const selected = selectedPaymentMethodId === item.id;
          const iconName = getOptionIconName(item.icon);

          return (
            <Pressable
              key={item.id}
              accessibilityRole="button"
              accessibilityLabel={`选择支付方式${item.name}`}
              onPress={() => onSelectPaymentMethod(item.id)}
              style={({ pressed }) => [
                styles.paymentMethodChip,
                {
                  backgroundColor: selected ? theme.colors.accentSoft : theme.colors.surfaceAlt,
                  borderColor: selected ? theme.colors.accentMuted : theme.colors.borderStrong,
                  opacity: pressed ? 0.92 : 1,
                },
              ]}
            >
              <View
                style={[
                  styles.paymentMethodBadge,
                  {
                    backgroundColor: selected ? theme.colors.surface : theme.colors.backgroundSoft,
                  },
                ]}
              >
                {iconName ? (
                  <MaterialCommunityIcons
                    name={iconName}
                    size={14}
                    color={selected ? theme.colors.accentStrong : item.color}
                  />
                ) : (
                  <Text
                    variant="labelMedium"
                    style={{
                      color: selected ? theme.colors.accentStrong : item.color,
                      fontWeight: "800",
                    }}
                  >
                    {getInitialLabel(item.name)}
                  </Text>
                )}
              </View>
              <Text
                variant="labelLarge"
                style={{
                  color: selected ? theme.colors.accentStrong : theme.colors.text,
                  fontWeight: "700",
                }}
              >
                {item.name}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </>
  );
}

import type { ComponentProps } from "react";

import { MaterialCommunityIcons } from "@expo/vector-icons";

export type EntryType = "expense" | "income";
export type PickerMode = "date" | "time" | null;
export type MaterialIconName = ComponentProps<typeof MaterialCommunityIcons>["name"];

export interface QuickEntryFormValue {
  type: EntryType;
  amountText: string;
  description: string;
  categoryId: string | null;
  paymentMethodId: string | null;
  transactionDate: number;
}

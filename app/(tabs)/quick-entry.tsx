import { router } from "expo-router";

import QuickEntrySheetContainer from "@/src/features/transactions/components/QuickEntrySheetContainer";

export default function QuickEntryScreen() {
  return <QuickEntrySheetContainer visible onDismiss={() => router.replace("/(tabs)")} />;
}

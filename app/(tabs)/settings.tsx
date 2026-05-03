import * as Sharing from "expo-sharing";
import { useState } from "react";

import { SettingsScreenContent } from "@/src/components/screens/settings/SettingsScreenContent";
import { exportTransactionsCsv } from "@/src/lib/backup/backup.service";
import { AppScreen } from "@/src/components/layout/AppScreen";

export default function SettingsScreen() {
  const [message, setMessage] = useState("");

  const handleExportCsv = async (): Promise<void> => {
    try {
      const path = await exportTransactionsCsv();
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(path);
      }
      setMessage(`导出成功：${path}`);
    } catch (error) {
      setMessage(`导出失败：${String(error)}`);
    }
  };

  return (
    <AppScreen contentContainerStyle={{ paddingBottom: 132 }}>
      <SettingsScreenContent message={message} onExportCsv={handleExportCsv} />
    </AppScreen>
  );
}

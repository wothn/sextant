import { DataManagementCard } from "@/src/components/screens/settings/DataManagementCard";
import { FeedbackMessageCard } from "@/src/components/screens/settings/FeedbackMessageCard";

interface SettingsScreenContentProps {
  message: string;
  onExportCsv: () => void;
}

export function SettingsScreenContent({ message, onExportCsv }: SettingsScreenContentProps) {
  return (
    <>
      <DataManagementCard onExportCsv={onExportCsv} />
      <FeedbackMessageCard message={message} />
    </>
  );
}

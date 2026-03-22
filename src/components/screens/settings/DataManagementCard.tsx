import { Button, Card, Text } from "@/src/ui";

interface DataManagementCardProps {
  onExportCsv: () => void;
}

export function DataManagementCard({ onExportCsv }: DataManagementCardProps) {
  return (
    <Card>
      <Card.Content style={{ gap: 10 }}>
        <Text variant="titleMedium">数据管理</Text>
        <Button mode="outlined" onPress={onExportCsv}>
          导出交易 CSV
        </Button>
      </Card.Content>
    </Card>
  );
}

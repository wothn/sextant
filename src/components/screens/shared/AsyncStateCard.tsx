import { Button, Card, Text, useTheme } from "@/src/ui";

interface AsyncStateCardProps {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}

export function AsyncStateCard({ title, description, actionLabel, onAction }: AsyncStateCardProps) {
  const theme = useTheme();

  return (
    <Card style={{ borderRadius: 18 }}>
      <Card.Content style={{ gap: 10 }}>
        <Text variant="titleMedium">{title}</Text>
        <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
          {description}
        </Text>
        {actionLabel && onAction ? (
          <Button mode="outlined" onPress={onAction}>
            {actionLabel}
          </Button>
        ) : null}
      </Card.Content>
    </Card>
  );
}

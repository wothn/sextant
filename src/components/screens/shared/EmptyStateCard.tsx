import { Card, Text, useTheme } from "@/src/ui";

interface EmptyStateCardProps {
  title: string;
  description: string;
}

export function EmptyStateCard({ title, description }: EmptyStateCardProps) {
  const theme = useTheme();

  return (
    <Card style={{ borderRadius: 18 }}>
      <Card.Content style={{ gap: 12 }}>
        <Text variant="titleMedium">{title}</Text>
        <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
          {description}
        </Text>
      </Card.Content>
    </Card>
  );
}

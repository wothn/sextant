import { Text, useTheme } from "@/src/ui";

const DAILY_LINES = [
  "把今天折成一页",
  "让心事慢慢落地",
  "给生活一点微光",
  "把日子写轻一点",
  "把勇气放进清晨",
  "把温柔留给自己",
  "把风交给方向",
  "把念头归于安静",
  "把时间交给当下",
  "让步子从容些",
  "把忙碌变得可爱",
  "把期待留给晴天",
  "把浪漫装进口袋",
  "把心跳交给节奏",
] as const;

function getDayOfYear(date: Date): number {
  const start = new Date(date.getFullYear(), 0, 0);
  const diff = date.getTime() - start.getTime();
  return Math.floor(diff / 86_400_000);
}

function getGreeting(): { greeting: string; mood: string } {
  const now = new Date();
  const hour = now.getHours();
  const dayOfYear = getDayOfYear(now);
  const greeting = DAILY_LINES[dayOfYear % DAILY_LINES.length] ?? "把今天折成一页";

  if (hour >= 0 && hour < 5) {
    return { greeting, mood: "夜色未央" };
  }

  if (hour >= 5 && hour < 9) {
    return { greeting, mood: "晨光微澜" };
  }

  if (hour >= 9 && hour < 11) {
    return { greeting, mood: "风从窗边过" };
  }

  if (hour >= 11 && hour < 14) {
    return { greeting, mood: "日光正好" };
  }

  if (hour >= 14 && hour < 17) {
    return { greeting, mood: "云影缓行" };
  }

  if (hour >= 17 && hour < 19) {
    return { greeting, mood: "暮色初至" };
  }

  return { greeting, mood: "灯火将眠" };
}

function formatDate(): string {
  const now = new Date();
  const weekdays = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"] as const;
  return `${now.getMonth() + 1}月${now.getDate()}日 ${weekdays[now.getDay()]}`;
}

export function HomeGreetingSection() {
  const theme = useTheme();
  const { greeting, mood } = getGreeting();

  return (
    <>
      <Text variant="headlineMedium" style={{ fontWeight: "700", letterSpacing: -0.4 }}>
        {greeting}
      </Text>
      <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
        {formatDate()} · {mood}
      </Text>
    </>
  );
}

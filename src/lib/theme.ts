import type { useTheme } from "tamagui";

type TamaguiTheme = ReturnType<typeof useTheme>;
type ThemeToken = { val?: unknown } | undefined;

export interface ThemeColors {
  accent: string;
  accentStrong: string;
  accentSoft: string;
  accentMuted: string;
  onAccent: string;
  surface: string;
  surfaceAlt: string;
  surfaceStrong: string;
  background: string;
  backgroundSoft: string;
  text: string;
  textMuted: string;
  textSubtle: string;
  border: string;
  borderStrong: string;
  success: string;
  danger: string;
  warning: string;
  info: string;
  shadow: string;
  overlay: string;
  tabInactive: string;
}

function tokenValue(token: ThemeToken, fallback: string): string {
  const value = token?.val;

  if (typeof value === "string" || typeof value === "number") {
    return String(value);
  }

  return fallback;
}

export function getThemeColors(theme: TamaguiTheme): ThemeColors {
  return {
    accent: tokenValue(theme.accent, "#3B82F6"),
    accentStrong: tokenValue(theme.accentStrong, "#1D4ED8"),
    accentSoft: tokenValue(theme.accentSoft, "#DBEAFE"),
    accentMuted: tokenValue(theme.accentMuted, "#BFDBFE"),
    onAccent: tokenValue(theme.onAccent, "#FFFFFF"),
    surface: tokenValue(theme.surface, "#FFFFFF"),
    surfaceAlt: tokenValue(theme.surfaceAlt, "#F1F5F9"),
    surfaceStrong: tokenValue(theme.surfaceStrong, "#E2E8F0"),
    background: tokenValue(theme.background, "#F8FAFC"),
    backgroundSoft: tokenValue(theme.backgroundSoft, "#EEF2F7"),
    text: tokenValue(theme.onSurface ?? theme.color, "#0F172A"),
    textMuted: tokenValue(theme.onSurfaceVariant, "#475569"),
    textSubtle: tokenValue(theme.onSurfaceMuted, "#64748B"),
    border: tokenValue(theme.border, "#E2E8F0"),
    borderStrong: tokenValue(theme.borderStrong, "#CBD5E1"),
    success: tokenValue(theme.success, "#22C55E"),
    danger: tokenValue(theme.danger, "#EF4444"),
    warning: tokenValue(theme.warning, "#F59E0B"),
    info: tokenValue(theme.info, "#0EA5E9"),
    shadow: tokenValue(theme.shadowColor, "#0B1220"),
    overlay: tokenValue(theme.overlay, "rgba(15, 23, 42, 0.4)"),
    tabInactive: tokenValue(theme.tabInactive, "#94A3B8"),
  };
}

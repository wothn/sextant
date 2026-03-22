import { useTheme as useTamaguiTheme } from "@tamagui/core";

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

export function useTheme(): { colors: ThemeColors } {
  const theme = useTamaguiTheme();

  return {
    colors: {
      accent: String(theme.accent?.val ?? "#3B82F6"),
      accentStrong: String(theme.accentStrong?.val ?? "#1D4ED8"),
      accentSoft: String(theme.accentSoft?.val ?? "#DBEAFE"),
      accentMuted: String(theme.accentMuted?.val ?? "#BFDBFE"),
      onAccent: String(theme.onAccent?.val ?? "#FFFFFF"),
      surface: String(theme.surface?.val ?? "#FFFFFF"),
      surfaceAlt: String(theme.surfaceAlt?.val ?? "#F1F5F9"),
      surfaceStrong: String(theme.surfaceStrong?.val ?? "#E2E8F0"),
      background: String(theme.background?.val ?? "#F8FAFC"),
      backgroundSoft: String(theme.backgroundSoft?.val ?? "#EEF2F7"),
      text: String(theme.onSurface?.val ?? theme.color?.val ?? "#0F172A"),
      textMuted: String(theme.onSurfaceVariant?.val ?? "#475569"),
      textSubtle: String(theme.onSurfaceMuted?.val ?? "#64748B"),
      border: String(theme.border?.val ?? "#E2E8F0"),
      borderStrong: String(theme.borderStrong?.val ?? "#CBD5E1"),
      success: String(theme.success?.val ?? "#22C55E"),
      danger: String(theme.danger?.val ?? "#EF4444"),
      warning: String(theme.warning?.val ?? "#F59E0B"),
      info: String(theme.info?.val ?? "#0EA5E9"),
      shadow: String(theme.shadowColor?.val ?? "#0B1220"),
      overlay: String(theme.overlay?.val ?? "rgba(15, 23, 42, 0.4)"),
      tabInactive: String(theme.tabInactive?.val ?? "#94A3B8"),
    },
  };
}

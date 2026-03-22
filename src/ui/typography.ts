export const TEXT_VARIANTS = {
  displaySmall: {
    fontSize: 40,
    lineHeight: 46,
    fontWeight: "700" as const,
    letterSpacing: -0.6,
  },
  headlineMedium: {
    fontSize: 28,
    lineHeight: 34,
    fontWeight: "700" as const,
    letterSpacing: -0.3,
  },
  headlineSmall: {
    fontSize: 24,
    lineHeight: 30,
    fontWeight: "700" as const,
    letterSpacing: -0.2,
  },
  titleLarge: {
    fontSize: 20,
    lineHeight: 26,
    fontWeight: "600" as const,
    letterSpacing: -0.1,
  },
  titleMedium: { fontSize: 16, lineHeight: 22, fontWeight: "600" as const },
  titleSmall: { fontSize: 14, lineHeight: 20, fontWeight: "600" as const },
  bodyLarge: { fontSize: 16, lineHeight: 24, fontWeight: "400" as const },
  bodyMedium: { fontSize: 14, lineHeight: 22, fontWeight: "400" as const },
  bodySmall: { fontSize: 12, lineHeight: 18, fontWeight: "400" as const },
  labelLarge: { fontSize: 13, lineHeight: 18, fontWeight: "600" as const },
  labelMedium: { fontSize: 12, lineHeight: 16, fontWeight: "600" as const },
  labelSmall: { fontSize: 11, lineHeight: 14, fontWeight: "600" as const },
} as const;

export const FONT_FAMILY = "Avenir Next";

export type TextVariant = keyof typeof TEXT_VARIANTS;

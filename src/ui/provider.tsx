import type { PropsWithChildren } from "react";
import { useMemo } from "react";
import { useColorScheme } from "react-native";
import { TamaguiProvider, Theme } from "@tamagui/core";
import { SafeAreaProvider } from "react-native-safe-area-context";

import tamaguiConfig from "@/src/tamagui.config";

type ThemeScheme = "light" | "dark";

interface AppProvidersProps extends PropsWithChildren {
  scheme?: ThemeScheme | null;
}

export function AppProviders({ children, scheme }: AppProvidersProps) {
  const colorScheme = useColorScheme();
  const resolvedScheme = useMemo<ThemeScheme>(
    () => ((scheme ?? colorScheme) === "dark" ? "dark" : "light"),
    [colorScheme, scheme],
  );

  return (
    <SafeAreaProvider>
      <TamaguiProvider config={tamaguiConfig} defaultTheme={resolvedScheme}>
        <Theme name={resolvedScheme}>{children}</Theme>
      </TamaguiProvider>
    </SafeAreaProvider>
  );
}

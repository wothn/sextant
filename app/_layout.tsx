import { Stack } from "expo-router";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import { useEffect, useState } from "react";
import { Button, Spinner, Text, YStack } from "tamagui";

import { getDb } from "@/src/db/client";
import { TEXT_VARIANTS } from "@/src/lib/typography";
import { AppProviders } from "@/src/providers/AppProviders";

export default function RootLayout() {
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let mounted = true;
    setReady(false);
    setError(null);

    Promise.all([getDb(), MaterialCommunityIcons.loadFont()])
      .then(() => {
        if (mounted) {
          setReady(true);
        }
      })
      .catch((initError) => {
        console.error("App init failed", initError);
        if (mounted) {
          setError(initError instanceof Error ? initError : new Error(String(initError)));
        }
      });

    return () => {
      mounted = false;
    };
  }, [retryKey]);

  if (!ready) {
    return (
      <AppProviders>
        <YStack flex={1} alignItems="center" justifyContent="center">
          {error ? (
            <YStack alignItems="center" gap={12} paddingHorizontal={24}>
              <Text style={TEXT_VARIANTS.titleMedium}>应用初始化失败</Text>
              <Text style={[TEXT_VARIANTS.bodyMedium, { textAlign: "center" }]}>
                {error.message || "请稍后重试"}
              </Text>
              <Button
                unstyled
                backgroundColor="$accent"
                borderRadius={12}
                minHeight={44}
                paddingHorizontal={16}
                paddingVertical={10}
                onPress={() => setRetryKey((key) => key + 1)}
              >
                重试
              </Button>
            </YStack>
          ) : (
            <Spinner />
          )}
        </YStack>
      </AppProviders>
    );
  }

  return (
    <AppProviders>
      <Stack screenOptions={{ headerShown: false }} />
    </AppProviders>
  );
}

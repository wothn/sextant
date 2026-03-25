import { Stack } from "expo-router";
import { MaterialCommunityIcons } from "@expo/vector-icons";
import { useEffect, useState } from "react";
import { ActivityIndicator, View } from "react-native";

import { getDb } from "@/src/db/client";
import { Button, Text } from "@/src/ui";
import { AppProviders } from "@/src/ui/provider";

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
        <View style={{ flex: 1, alignItems: "center", justifyContent: "center" }}>
          {error ? (
            <View style={{ alignItems: "center", gap: 12, paddingHorizontal: 24 }}>
              <Text variant="titleMedium">应用初始化失败</Text>
              <Text variant="bodyMedium" style={{ textAlign: "center" }}>
                {error.message || "请稍后重试"}
              </Text>
              <Button mode="contained" onPress={() => setRetryKey((key) => key + 1)}>
                重试
              </Button>
            </View>
          ) : (
            <ActivityIndicator />
          )}
        </View>
      </AppProviders>
    );
  }

  return (
    <AppProviders>
      <Stack screenOptions={{ headerShown: false }} />
    </AppProviders>
  );
}

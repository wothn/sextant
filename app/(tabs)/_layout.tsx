import { MaterialCommunityIcons } from "@expo/vector-icons";
import { Tabs } from "expo-router";
import { TouchableOpacity, View } from "react-native";

import QuickEntrySheetContainer from "@/src/features/transactions/components/QuickEntrySheetContainer";
import { useUIStore } from "@/src/store/ui.store";
import { useTheme } from "@/src/ui";

export default function TabsLayout() {
  const theme = useTheme();
  const openQuickEntrySheet = useUIStore((state) => state.openQuickEntrySheet);

  return (
    <>
      <Tabs
        screenOptions={{
          headerShown: false,
          tabBarActiveTintColor: theme.colors.accent,
          tabBarInactiveTintColor: theme.colors.tabInactive,
          tabBarLabelStyle: {
            fontSize: 10,
            fontWeight: "600",
            letterSpacing: 0.2,
          },
          tabBarStyle: {
            position: "absolute",
            left: 16,
            right: 16,
            bottom: 16,
            height: 64,
            paddingTop: 8,
            paddingBottom: 8,
            borderWidth: 1,
            borderRadius: 20,
            backgroundColor: theme.colors.surface,
            borderColor: theme.colors.border,
            shadowColor: theme.colors.shadow,
            shadowOpacity: 0.05,
            shadowRadius: 10,
            shadowOffset: { width: 0, height: 4 },
            elevation: 6,
          },
          sceneStyle: {
            backgroundColor: theme.colors.background,
          },
        }}
      >
        <Tabs.Screen
          name="index"
          options={{
            title: "首页",
            tabBarIcon: ({ color, size }) => (
              <MaterialCommunityIcons name="home-outline" color={color} size={size} />
            ),
          }}
        />
        <Tabs.Screen
          name="accounts"
          options={{
            title: "账户",
            tabBarIcon: ({ color, size }) => (
              <MaterialCommunityIcons name="wallet-outline" color={color} size={size} />
            ),
          }}
        />
        <Tabs.Screen
          name="quick-entry"
          options={{
            title: "记账",
            tabBarIcon: ({ size }) => (
              <MaterialCommunityIcons name="plus" color={theme.colors.onAccent} size={size + 6} />
            ),
            tabBarButton: (props) => (
              <TouchableOpacity
                accessibilityRole="button"
                accessibilityLabel="记账"
                onPress={openQuickEntrySheet}
                style={{ top: -10 }}
              >
                <View
                  style={{
                    width: 56,
                    height: 56,
                    borderRadius: 18,
                    backgroundColor: theme.colors.accentStrong,
                    borderWidth: 2,
                    borderColor: theme.colors.surface,
                    alignItems: "center",
                    justifyContent: "center",
                    shadowColor: theme.colors.shadow,
                    shadowOpacity: 0.2,
                    shadowRadius: 16,
                    shadowOffset: { width: 0, height: 8 },
                    elevation: 10,
                  }}
                >
                  {props.children}
                </View>
              </TouchableOpacity>
            ),
          }}
        />
        <Tabs.Screen
          name="analytics"
          options={{
            title: "分析",
            tabBarIcon: ({ color, size }) => (
              <MaterialCommunityIcons name="chart-donut" color={color} size={size} />
            ),
          }}
        />
        <Tabs.Screen
          name="settings"
          options={{
            title: "设置",
            tabBarIcon: ({ color, size }) => (
              <MaterialCommunityIcons name="cog-outline" color={color} size={size} />
            ),
          }}
        />
      </Tabs>

      <QuickEntrySheetContainer />
    </>
  );
}

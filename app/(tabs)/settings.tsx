import * as Sharing from "expo-sharing";
import { useEffect, useMemo, useState } from "react";
import { View } from "react-native";

import { upsertMonthlyBudget } from "@/src/features/budgets/budget.service";
import { listCategories } from "@/src/features/transactions/transaction.service";
import { exportTransactionsCsv } from "@/src/lib/backup/backup.service";
import type { Category } from "@/src/types/domain";
import { Button, Card, Chip, Screen, Text, TextInput, useTheme } from "@/src/ui";

export default function SettingsScreen() {
  const theme = useTheme();
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [budgetText, setBudgetText] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    let mounted = true;

    const loadCategories = async (): Promise<void> => {
      const rows = await listCategories("expense");
      if (!mounted) {
        return;
      }
      setCategories(rows);
      setSelectedCategory((current) => current ?? rows[0]?.id ?? null);
    };

    void loadCategories();

    return () => {
      mounted = false;
    };
  }, []);

  const selectedName = useMemo(
    () => categories.find((c) => c.id === selectedCategory)?.name ?? "未选择",
    [categories, selectedCategory],
  );

  const handleBudgetSave = async () => {
    const amount = Number(budgetText);
    if (!selectedCategory || !amount || amount <= 0) {
      setMessage("请输入有效预算金额");
      return;
    }
    await upsertMonthlyBudget(selectedCategory, amount, 0.8);
    setMessage(`预算已保存：${selectedName} ¥${amount.toFixed(2)}`);
    setBudgetText("");
  };

  const handleExportCsv = async () => {
    try {
      const path = await exportTransactionsCsv();
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(path);
      }
      setMessage(`导出成功：${path}`);
    } catch (error) {
      setMessage(`导出失败：${String(error)}`);
    }
  };

  return (
    <Screen contentContainerStyle={{ paddingBottom: 132 }}>
      <Card>
        <Card.Content style={{ gap: 10 }}>
          <Text variant="titleMedium">本月预算设置</Text>
          <Text variant="bodyMedium">当前分类：{selectedName}</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
            {categories.map((item) => (
              <Chip
                key={item.id}
                selected={selectedCategory === item.id}
                onPress={() => setSelectedCategory(item.id)}
              >
                {item.name}
              </Chip>
            ))}
          </View>
          <TextInput
            mode="outlined"
            label="预算金额"
            keyboardType="numeric"
            value={budgetText}
            onChangeText={setBudgetText}
          />
          <Button mode="contained" onPress={handleBudgetSave}>
            保存预算
          </Button>
        </Card.Content>
      </Card>

      <Card>
        <Card.Content style={{ gap: 10 }}>
          <Text variant="titleMedium">数据管理</Text>
          <Button mode="outlined" onPress={handleExportCsv}>
            导出交易 CSV
          </Button>
        </Card.Content>
      </Card>

      {message ? (
        <Card mode="contained">
          <Card.Content>
            <Text style={{ color: message.includes("失败") ? theme.colors.danger : theme.colors.text }}>
              {message}
            </Text>
          </Card.Content>
        </Card>
      ) : null}
    </Screen>
  );
}

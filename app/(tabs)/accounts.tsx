import { useEffect, useState } from "react";
import { View } from "react-native";

import { createAccount, listAccounts } from "@/src/features/transactions/account.service";
import { ACCOUNT_TYPE_OPTIONS, getAccountTypeLabel } from "@/src/types/domain";
import type { Account } from "@/src/types/domain";
import { Button, Card, Chip, Screen, Text, TextInput, useTheme } from "@/src/ui";

const ACCOUNT_TYPES: Account["type"][] = ACCOUNT_TYPE_OPTIONS;

export default function AccountsScreen() {
  const theme = useTheme();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [name, setName] = useState("");
  const [type, setType] = useState<Account["type"]>("cash");

  const refresh = async () => {
    setAccounts(await listAccounts());
  };

  useEffect(() => {
    refresh();
  }, []);

  const handleCreate = async () => {
    if (!name.trim()) {
      return;
    }
    await createAccount(name, type);
    setName("");
    await refresh();
  };

  return (
    <Screen contentContainerStyle={{ paddingBottom: 132 }}>
      <Card>
        <Card.Content style={{ gap: 10 }}>
          <Text variant="titleMedium">创建账户</Text>
          <TextInput label="账户名称" value={name} onChangeText={setName} mode="outlined" />

          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
            {ACCOUNT_TYPES.map((accountType) => (
              <Chip
                key={accountType}
                selected={type === accountType}
                onPress={() => setType(accountType)}
              >
                {getAccountTypeLabel(accountType)}
              </Chip>
            ))}
          </View>

          <Text variant="bodyMedium">当前类型：{getAccountTypeLabel(type)}</Text>

          <Button mode="contained" onPress={handleCreate}>
            新增账户
          </Button>
        </Card.Content>
      </Card>

      <View style={{ gap: 10 }}>
        <Text variant="titleMedium">已有账户</Text>
        {accounts.map((account) => (
          <Card key={account.id}>
            <Card.Content style={{ gap: 14 }}>
              <View
                style={{
                  flexDirection: "row",
                  justifyContent: "space-between",
                  alignItems: "flex-start",
                  gap: 16,
                }}
              >
                <View style={{ flex: 1, gap: 4 }}>
                  <Text variant="titleLarge">{account.name}</Text>
                  <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }} tabularNums>
                    {`${getAccountTypeLabel(account.type)} · 余额 ¥${account.balance.toFixed(2)}`}
                  </Text>
                </View>
                <View
                  style={{
                    borderRadius: 999,
                    backgroundColor: theme.colors.surfaceAlt,
                    borderWidth: 1,
                    borderColor: theme.colors.border,
                    paddingHorizontal: 12,
                    paddingVertical: 8,
                  }}
                >
                  <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
                    {getAccountTypeLabel(account.type)}
                  </Text>
                </View>
              </View>
            </Card.Content>
          </Card>
        ))}
      </View>
    </Screen>
  );
}

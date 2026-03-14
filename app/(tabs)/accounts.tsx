import { MaterialCommunityIcons } from "@expo/vector-icons";
import type { ComponentProps } from "react";
import { useMemo, useState } from "react";
import { View } from "react-native";

import {
  createAccount,
  listAccounts,
  setAccountActive,
  updateAccount,
} from "@/src/features/transactions/account.service";
import {
  getCurrentMonthAccountSummaries,
  type AccountMonthlySummary,
} from "@/src/features/transactions/transaction.service";
import { useAsyncData } from "@/src/hooks/use-async-data";
import { formatCurrency, formatSignedCurrency } from "@/src/lib/format";
import { useUIStore } from "@/src/store/ui.store";
import {
  ACCOUNT_TYPE_OPTIONS,
  getAccountTypeLabel,
  type Account,
  type AccountType,
} from "@/src/types/domain";
import { Button, Card, Chip, Divider, Screen, Text, TextInput, useTheme } from "@/src/ui";

const ACCOUNT_TYPES: AccountType[] = ACCOUNT_TYPE_OPTIONS;
const LIQUID_ACCOUNT_TYPES: AccountType[] = ["cash", "debit", "ewallet"];
const STATUS_FILTER_OPTIONS = [
  { key: "active", label: "启用中" },
  { key: "inactive", label: "已停用" },
  { key: "all", label: "全部" },
] as const;
const TYPE_FILTER_ALL = "all";

type IconName = ComponentProps<typeof MaterialCommunityIcons>["name"];
type StatusFilter = (typeof STATUS_FILTER_OPTIONS)[number]["key"];
type TypeFilter = AccountType | typeof TYPE_FILTER_ALL;

interface AccountTypeMeta {
  icon: IconName;
  accent: string;
  soft: string;
}

interface AccountFormState {
  name: string;
  type: AccountType;
  initialBalance: string;
}

interface AccountsScreenData {
  accounts: Account[];
  monthlySummaries: AccountMonthlySummary[];
}

interface FeedbackState {
  tone: "success" | "danger";
  text: string;
}

interface AccountGroup {
  type: AccountType;
  accounts: Account[];
  totalBalance: number;
}

const ACCOUNT_TYPE_META: Record<AccountType, AccountTypeMeta> = {
  cash: {
    icon: "cash-multiple",
    accent: "#1F9D66",
    soft: "#DDF6EA",
  },
  debit: {
    icon: "bank-outline",
    accent: "#2563EB",
    soft: "#E3EEFF",
  },
  credit: {
    icon: "credit-card-outline",
    accent: "#D97706",
    soft: "#FFF1D8",
  },
  ewallet: {
    icon: "wallet-outline",
    accent: "#0F9F9A",
    soft: "#D9F5F3",
  },
  investment: {
    icon: "chart-line",
    accent: "#4F46E5",
    soft: "#E6E6FF",
  },
  loan: {
    icon: "hand-coin-outline",
    accent: "#CC5B3E",
    soft: "#FBE5DF",
  },
};

const INITIAL_FORM_STATE: AccountFormState = {
  name: "",
  type: "cash",
  initialBalance: "",
};

const INITIAL_SCREEN_DATA: AccountsScreenData = {
  accounts: [],
  monthlySummaries: [],
};

async function loadAccountsScreenData(): Promise<AccountsScreenData> {
  const [accounts, monthlySummaries] = await Promise.all([
    listAccounts({ includeInactive: true }),
    getCurrentMonthAccountSummaries(),
  ]);

  return {
    accounts,
    monthlySummaries,
  };
}

function sortAccounts(accounts: Account[]): Account[] {
  return [...accounts].sort((left, right) => {
    if (left.isActive !== right.isActive) {
      return right.isActive - left.isActive;
    }

    const balanceDelta = Math.abs(right.balance) - Math.abs(left.balance);
    if (balanceDelta !== 0) {
      return balanceDelta;
    }

    return left.createdAt - right.createdAt;
  });
}

function parseInitialBalance(text: string): number {
  const trimmed = text.trim();

  if (!trimmed) {
    return 0;
  }

  const value = Number(trimmed);

  if (!Number.isFinite(value)) {
    throw new Error("初始余额格式不正确");
  }

  return value;
}

function normalizeErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "账户操作失败，请稍后再试";
}

function getFeedbackColor(
  tone: FeedbackState["tone"],
  colors: ReturnType<typeof useTheme>["colors"],
): string {
  return tone === "danger" ? colors.danger : colors.success;
}

function AccountMetric({ label, value, tone }: { label: string; value: string; tone: string }) {
  const theme = useTheme();

  return (
    <View
      style={{
        flex: 1,
        minWidth: 92,
        borderRadius: 18,
        borderWidth: 1,
        borderColor: theme.colors.border,
        backgroundColor: theme.colors.surface,
        paddingHorizontal: 14,
        paddingVertical: 12,
        gap: 4,
      }}
    >
      <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
        {label}
      </Text>
      <Text variant="titleMedium" style={{ color: tone }} tabularNums numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

function AccountCard({
  account,
  summary,
  onEdit,
  onToggle,
  disabled,
}: {
  account: Account;
  summary: AccountMonthlySummary | null;
  onEdit: (account: Account) => void;
  onToggle: (account: Account) => void;
  disabled: boolean;
}) {
  const theme = useTheme();
  const meta = ACCOUNT_TYPE_META[account.type];
  const statusLabel = account.isActive ? "启用中" : "已停用";
  const statusTone = account.isActive ? theme.colors.success : theme.colors.textMuted;

  return (
    <View
      style={{
        borderRadius: 20,
        borderWidth: 1,
        borderColor: theme.colors.border,
        backgroundColor: theme.colors.surfaceAlt,
        padding: 14,
        gap: 12,
      }}
    >
      <View
        style={{
          flexDirection: "row",
          justifyContent: "space-between",
          alignItems: "flex-start",
          gap: 12,
        }}
      >
        <View style={{ flex: 1, flexDirection: "row", gap: 12 }}>
          <View
            style={{
              width: 44,
              height: 44,
              borderRadius: 16,
              alignItems: "center",
              justifyContent: "center",
              backgroundColor: meta.soft,
            }}
          >
            <MaterialCommunityIcons color={meta.accent} name={meta.icon} size={22} />
          </View>

          <View style={{ flex: 1, gap: 4 }}>
            <Text variant="titleMedium">{account.name}</Text>
            <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
              {getAccountTypeLabel(account.type)} · {statusLabel}
            </Text>
          </View>
        </View>

        <View style={{ alignItems: "flex-end", gap: 4 }}>
          <Text variant="labelSmall" style={{ color: statusTone }}>
            {statusLabel}
          </Text>
          <Text variant="titleLarge" tabularNums>
            {formatCurrency(account.balance)}
          </Text>
        </View>
      </View>

      <View style={{ flexDirection: "row", gap: 10 }}>
        <AccountMetric
          label="本月净额"
          tone={(summary?.net ?? 0) >= 0 ? theme.colors.success : theme.colors.danger}
          value={formatSignedCurrency(summary?.net ?? 0)}
        />
        <AccountMetric
          label="本月流水"
          tone={theme.colors.accent}
          value={`${summary?.transactionCount ?? 0} 笔`}
        />
      </View>

      <View style={{ flexDirection: "row", gap: 12 }}>
        <Text variant="bodySmall" style={{ color: theme.colors.success }} tabularNums>
          收入 {formatCurrency(summary?.income ?? 0)}
        </Text>
        <Text variant="bodySmall" style={{ color: theme.colors.danger }} tabularNums>
          支出 {formatCurrency(summary?.expense ?? 0)}
        </Text>
      </View>

      {!account.isActive ? (
        <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
          已停用账户不会出现在日常选择列表里，但数据会保留。
        </Text>
      ) : null}

      <View style={{ flexDirection: "row", gap: 10 }}>
        <Button
          compact
          mode="outlined"
          disabled={disabled}
          accessibilityLabel={`编辑${account.name}`}
          onPress={() => onEdit(account)}
        >
          编辑
        </Button>
        <Button
          compact
          mode="text"
          disabled={disabled}
          accessibilityLabel={`${account.isActive ? "停用" : "恢复"}${account.name}`}
          onPress={() => onToggle(account)}
        >
          {account.isActive ? "停用" : "恢复"}
        </Button>
      </View>
    </View>
  );
}

export default function AccountsScreen() {
  const theme = useTheme();
  const refreshKey = useUIStore((state) => state.refreshKey);
  const bumpRefreshKey = useUIStore((state) => state.bumpRefreshKey);
  const [form, setForm] = useState<AccountFormState>(INITIAL_FORM_STATE);
  const [editingAccountId, setEditingAccountId] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("active");
  const [typeFilter, setTypeFilter] = useState<TypeFilter>(TYPE_FILTER_ALL);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [saving, setSaving] = useState(false);
  const { data, loading, error, reload } = useAsyncData<AccountsScreenData>({
    initialData: INITIAL_SCREEN_DATA,
    fetcher: loadAccountsScreenData,
    deps: [refreshKey],
  });

  const monthlySummaryMap = useMemo(() => {
    return new Map<string, AccountMonthlySummary>(
      data.monthlySummaries.map((item) => [item.accountId, item]),
    );
  }, [data.monthlySummaries]);

  const accounts = useMemo(() => sortAccounts(data.accounts), [data.accounts]);
  const activeAccounts = useMemo(() => {
    return accounts.filter((account) => account.isActive === 1);
  }, [accounts]);
  const inactiveAccounts = useMemo(() => {
    return accounts.filter((account) => account.isActive === 0);
  }, [accounts]);

  const summaryNumbers = useMemo(() => {
    const totalBalance = activeAccounts.reduce((sum, account) => sum + account.balance, 0);
    const liquidBalance = activeAccounts
      .filter((account) => LIQUID_ACCOUNT_TYPES.includes(account.type))
      .reduce((sum, account) => sum + account.balance, 0);
    const debtExposure = activeAccounts
      .filter((account) => account.type === "loan")
      .reduce((sum, account) => sum + account.balance, 0);
    const monthIncome = data.monthlySummaries.reduce((sum, item) => sum + item.income, 0);
    const monthExpense = data.monthlySummaries.reduce((sum, item) => sum + item.expense, 0);

    return {
      totalBalance,
      liquidBalance,
      debtExposure,
      monthIncome,
      monthExpense,
      monthNet: monthIncome - monthExpense,
    };
  }, [activeAccounts, data.monthlySummaries]);

  const topAccount = useMemo(() => {
    return [...activeAccounts].sort((left, right) => right.balance - left.balance)[0] ?? null;
  }, [activeAccounts]);

  const filteredAccounts = useMemo(() => {
    return accounts.filter((account) => {
      const matchesStatus =
        statusFilter === "all"
          ? true
          : statusFilter === "active"
            ? account.isActive === 1
            : account.isActive === 0;
      const matchesType = typeFilter === TYPE_FILTER_ALL ? true : account.type === typeFilter;

      return matchesStatus && matchesType;
    });
  }, [accounts, statusFilter, typeFilter]);

  const spotlightAccounts = useMemo(() => {
    return [...filteredAccounts]
      .filter((account) => account.isActive === 1)
      .sort((left, right) => Math.abs(right.balance) - Math.abs(left.balance))
      .slice(0, 2);
  }, [filteredAccounts]);

  const groupedAccounts = useMemo<AccountGroup[]>(() => {
    return ACCOUNT_TYPES.map((type) => {
      const matchingAccounts = filteredAccounts.filter((account) => account.type === type);
      return {
        type,
        accounts: matchingAccounts,
        totalBalance: matchingAccounts.reduce((sum, account) => sum + account.balance, 0),
      };
    }).filter((group) => group.accounts.length > 0);
  }, [filteredAccounts]);

  const editingAccount = useMemo(() => {
    if (!editingAccountId) {
      return null;
    }

    return accounts.find((account) => account.id === editingAccountId) ?? null;
  }, [accounts, editingAccountId]);

  const feedbackColor = feedback ? getFeedbackColor(feedback.tone, theme.colors) : null;

  const setFormField = <K extends keyof AccountFormState>(
    key: K,
    value: AccountFormState[K],
  ): void => {
    setForm((current) => ({
      ...current,
      [key]: value,
    }));
  };

  const resetForm = (): void => {
    setForm(INITIAL_FORM_STATE);
    setEditingAccountId(null);
  };

  const handleEdit = (account: Account): void => {
    setEditingAccountId(account.id);
    setForm({
      name: account.name,
      type: account.type,
      initialBalance: "",
    });
    setFeedback(null);
  };

  const handleSubmit = async (): Promise<void> => {
    setSaving(true);
    setFeedback(null);

    try {
      if (editingAccountId) {
        await updateAccount(editingAccountId, {
          name: form.name,
          type: form.type,
        });
        setFeedback({
          tone: "success",
          text: "账户信息已更新",
        });
      } else {
        await createAccount({
          name: form.name,
          type: form.type,
          initialBalance: parseInitialBalance(form.initialBalance),
        });
        setFeedback({
          tone: "success",
          text: "账户已创建",
        });
      }

      resetForm();
      bumpRefreshKey();
    } catch (submitError) {
      setFeedback({
        tone: "danger",
        text: normalizeErrorMessage(submitError),
      });
    } finally {
      setSaving(false);
    }
  };

  const handleToggleAccount = async (account: Account): Promise<void> => {
    setSaving(true);
    setFeedback(null);

    try {
      await setAccountActive(account.id, account.isActive === 0);
      if (editingAccountId === account.id) {
        resetForm();
      }
      setFeedback({
        tone: "success",
        text: account.isActive ? "账户已停用" : "账户已恢复",
      });
      bumpRefreshKey();
    } catch (toggleError) {
      setFeedback({
        tone: "danger",
        text: normalizeErrorMessage(toggleError),
      });
    } finally {
      setSaving(false);
    }
  };

  const formTitle = editingAccount ? `编辑账户 · ${editingAccount.name}` : "创建新账户";
  const formNote = editingAccount
    ? "账户余额由流水累计，如需修正余额，建议补记一笔收入、支出或转账。"
    : "新账户支持直接录入初始余额，适合现金、银行卡和电子钱包迁移。";

  return (
    <Screen contentContainerStyle={{ paddingBottom: 132 }}>
      <View style={{ gap: 4 }}>
        <Text variant="headlineMedium" style={{ fontWeight: "700", letterSpacing: -0.4 }}>
          账户总览
        </Text>
        <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
          把现金、卡片、钱包和负债放进同一块版图里。
        </Text>
      </View>

      <Card style={{ borderRadius: 26, overflow: "hidden" }}>
        <Card.Content style={{ gap: 18 }}>
          <View
            pointerEvents="none"
            style={{
              position: "absolute",
              top: -50,
              right: -26,
              width: 156,
              height: 156,
              borderRadius: 78,
              backgroundColor: theme.colors.accentSoft,
              opacity: 0.85,
            }}
          />

          <View
            pointerEvents="none"
            style={{
              position: "absolute",
              bottom: -62,
              left: -14,
              width: 132,
              height: 132,
              borderRadius: 66,
              backgroundColor: theme.colors.info,
              opacity: 0.12,
            }}
          />

          <View
            style={{
              flexDirection: "row",
              justifyContent: "space-between",
              alignItems: "flex-start",
              gap: 12,
            }}
          >
            <View style={{ flex: 1, gap: 6 }}>
              <Text variant="labelLarge" style={{ color: theme.colors.textMuted }}>
                账户净资产
              </Text>
              <Text variant="displaySmall" tabularNums style={{ fontWeight: "800" }}>
                {formatCurrency(summaryNumbers.totalBalance)}
              </Text>
              <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
                活跃 {activeAccounts.length} 个 · 停用 {inactiveAccounts.length} 个
              </Text>
            </View>

            <View
              style={{
                borderRadius: 999,
                paddingHorizontal: 12,
                paddingVertical: 8,
                backgroundColor: theme.colors.surface,
                borderWidth: 1,
                borderColor: theme.colors.border,
              }}
            >
              <Text variant="labelLarge" style={{ color: theme.colors.accentStrong }}>
                本月 {formatSignedCurrency(summaryNumbers.monthNet)}
              </Text>
            </View>
          </View>

          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
            <AccountMetric
              label="流动资金"
              tone={theme.colors.accent}
              value={formatCurrency(summaryNumbers.liquidBalance)}
            />
            <AccountMetric
              label="本月收入"
              tone={theme.colors.success}
              value={formatCurrency(summaryNumbers.monthIncome)}
            />
            <AccountMetric
              label="本月支出"
              tone={theme.colors.danger}
              value={formatCurrency(summaryNumbers.monthExpense)}
            />
          </View>

          <Divider />

          <View
            style={{
              flexDirection: "row",
              justifyContent: "space-between",
              alignItems: "center",
              gap: 12,
            }}
          >
            <View style={{ flex: 1, gap: 4 }}>
              <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
                当前最大账户
              </Text>
              <Text variant="titleMedium">
                {topAccount
                  ? `${topAccount.name} · ${formatCurrency(topAccount.balance)}`
                  : "还没有启用账户"}
              </Text>
            </View>
            <View style={{ alignItems: "flex-end", gap: 4 }}>
              <Text variant="labelSmall" style={{ color: theme.colors.textMuted }}>
                负债敞口
              </Text>
              <Text variant="titleMedium" tabularNums style={{ color: theme.colors.warning }}>
                {formatCurrency(summaryNumbers.debtExposure)}
              </Text>
            </View>
          </View>
        </Card.Content>
      </Card>

      <Card style={{ borderRadius: 22 }}>
        <Card.Content style={{ gap: 12 }}>
          <View style={{ gap: 4 }}>
            <Text variant="titleLarge">{formTitle}</Text>
            <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
              {formNote}
            </Text>
          </View>

          <TextInput
            accessibilityLabel="账户名称输入"
            label="账户名称"
            placeholder="例如：招商储蓄卡 / 备用金"
            value={form.name}
            onChangeText={(text) => setFormField("name", text)}
          />

          {!editingAccount ? (
            <TextInput
              accessibilityLabel="账户初始余额输入"
              keyboardType="numbers-and-punctuation"
              label="初始余额"
              placeholder="默认 0，可输入负数"
              value={form.initialBalance}
              onChangeText={(text) => setFormField("initialBalance", text)}
            />
          ) : null}

          <View style={{ gap: 8 }}>
            <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
              账户类型
            </Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
              {ACCOUNT_TYPES.map((accountType) => (
                <Chip
                  key={accountType}
                  selected={form.type === accountType}
                  onPress={() => setFormField("type", accountType)}
                >
                  {getAccountTypeLabel(accountType)}
                </Chip>
              ))}
            </View>
          </View>

          {feedback && feedbackColor ? (
            <Text variant="bodySmall" style={{ color: feedbackColor }}>
              {feedback.text}
            </Text>
          ) : null}

          <View style={{ flexDirection: "row", gap: 10 }}>
            <Button
              accessibilityLabel={editingAccount ? "保存账户修改" : "创建账户"}
              loading={saving}
              mode="contained"
              onPress={handleSubmit}
            >
              {editingAccount ? "保存修改" : "创建账户"}
            </Button>

            {editingAccount ? (
              <Button
                compact
                disabled={saving}
                mode="outlined"
                onPress={resetForm}
                accessibilityLabel="取消编辑账户"
              >
                取消
              </Button>
            ) : null}
          </View>
        </Card.Content>
      </Card>

      <Card mode="contained" style={{ borderRadius: 20 }}>
        <Card.Content style={{ gap: 12 }}>
          <View style={{ gap: 8 }}>
            <Text variant="titleMedium">筛选视图</Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
              {STATUS_FILTER_OPTIONS.map((option) => (
                <Chip
                  key={option.key}
                  selected={statusFilter === option.key}
                  onPress={() => setStatusFilter(option.key)}
                >
                  {option.label}
                </Chip>
              ))}
            </View>
          </View>

          <View style={{ gap: 8 }}>
            <Text variant="labelMedium" style={{ color: theme.colors.textMuted }}>
              账户类型
            </Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8 }}>
              <Chip
                selected={typeFilter === TYPE_FILTER_ALL}
                onPress={() => setTypeFilter(TYPE_FILTER_ALL)}
              >
                全部类型
              </Chip>
              {ACCOUNT_TYPES.map((accountType) => (
                <Chip
                  key={accountType}
                  selected={typeFilter === accountType}
                  onPress={() => setTypeFilter(accountType)}
                >
                  {getAccountTypeLabel(accountType)}
                </Chip>
              ))}
            </View>
          </View>

          <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
            当前显示 {filteredAccounts.length} 个账户。
          </Text>
        </Card.Content>
      </Card>

      {loading ? (
        <Card mode="contained">
          <Card.Content>
            <Text style={{ color: theme.colors.textMuted }}>账户数据加载中...</Text>
          </Card.Content>
        </Card>
      ) : null}

      {error ? (
        <Card>
          <Card.Content style={{ gap: 10 }}>
            <Text variant="titleMedium">账户数据加载失败</Text>
            <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
              {error.message}
            </Text>
            <Button mode="outlined" onPress={() => void reload()}>
              重试
            </Button>
          </Card.Content>
        </Card>
      ) : null}

      {!loading && !error && spotlightAccounts.length > 0 ? (
        <View style={{ gap: 10 }}>
          <Text variant="titleMedium">重点账户</Text>
          {spotlightAccounts.map((account) => {
            const meta = ACCOUNT_TYPE_META[account.type];
            const summary = monthlySummaryMap.get(account.id) ?? null;

            return (
              <Card
                key={account.id}
                style={{
                  borderRadius: 22,
                  borderColor: meta.soft,
                  backgroundColor: theme.colors.surface,
                }}
              >
                <Card.Content style={{ gap: 12 }}>
                  <View
                    style={{
                      flexDirection: "row",
                      justifyContent: "space-between",
                      alignItems: "flex-start",
                      gap: 12,
                    }}
                  >
                    <View style={{ flexDirection: "row", flex: 1, gap: 12 }}>
                      <View
                        style={{
                          width: 48,
                          height: 48,
                          borderRadius: 18,
                          alignItems: "center",
                          justifyContent: "center",
                          backgroundColor: meta.soft,
                        }}
                      >
                        <MaterialCommunityIcons color={meta.accent} name={meta.icon} size={24} />
                      </View>
                      <View style={{ flex: 1, gap: 4 }}>
                        <Text variant="titleLarge">{account.name}</Text>
                        <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
                          {getAccountTypeLabel(account.type)} · 余额结构里的重点账户
                        </Text>
                      </View>
                    </View>

                    <Text variant="headlineSmall" tabularNums>
                      {formatCurrency(account.balance)}
                    </Text>
                  </View>

                  <View style={{ flexDirection: "row", gap: 10 }}>
                    <AccountMetric
                      label="本月净额"
                      tone={(summary?.net ?? 0) >= 0 ? theme.colors.success : theme.colors.danger}
                      value={formatSignedCurrency(summary?.net ?? 0)}
                    />
                    <AccountMetric
                      label="本月流水"
                      tone={theme.colors.accent}
                      value={`${summary?.transactionCount ?? 0} 笔`}
                    />
                  </View>
                </Card.Content>
              </Card>
            );
          })}
        </View>
      ) : null}

      {!loading && !error && filteredAccounts.length === 0 ? (
        <Card mode="contained" style={{ borderRadius: 20 }}>
          <Card.Content style={{ gap: 8 }}>
            <Text variant="titleMedium">没有匹配的账户</Text>
            <Text variant="bodyMedium" style={{ color: theme.colors.textMuted }}>
              可以切换筛选条件，或者先创建一个新的账户入口。
            </Text>
          </Card.Content>
        </Card>
      ) : null}

      {!loading && !error && groupedAccounts.length > 0 ? (
        <View style={{ gap: 12 }}>
          <Text variant="titleMedium">账户分组</Text>
          {groupedAccounts.map((group) => {
            const meta = ACCOUNT_TYPE_META[group.type];

            return (
              <Card key={group.type} style={{ borderRadius: 22 }}>
                <Card.Content style={{ gap: 14 }}>
                  <View
                    style={{
                      flexDirection: "row",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: 12,
                    }}
                  >
                    <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
                      <View
                        style={{
                          width: 40,
                          height: 40,
                          borderRadius: 14,
                          alignItems: "center",
                          justifyContent: "center",
                          backgroundColor: meta.soft,
                        }}
                      >
                        <MaterialCommunityIcons color={meta.accent} name={meta.icon} size={20} />
                      </View>
                      <View style={{ gap: 2 }}>
                        <Text variant="titleMedium">{getAccountTypeLabel(group.type)}</Text>
                        <Text variant="bodySmall" style={{ color: theme.colors.textMuted }}>
                          {group.accounts.length} 个账户
                        </Text>
                      </View>
                    </View>

                    <Text variant="titleMedium" tabularNums>
                      {formatCurrency(group.totalBalance)}
                    </Text>
                  </View>

                  <Divider />

                  <View style={{ gap: 12 }}>
                    {group.accounts.map((account) => (
                      <AccountCard
                        key={account.id}
                        account={account}
                        disabled={saving}
                        summary={monthlySummaryMap.get(account.id) ?? null}
                        onEdit={handleEdit}
                        onToggle={(targetAccount) => void handleToggleAccount(targetAccount)}
                      />
                    ))}
                  </View>
                </Card.Content>
              </Card>
            );
          })}
        </View>
      ) : null}
    </Screen>
  );
}

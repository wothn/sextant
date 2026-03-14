import { fireEvent, screen, waitFor } from "@testing-library/react-native";

import AccountsScreen from "@/app/(tabs)/accounts";
import { renderWithProviders } from "@/src/test/render";
import { useUIStore } from "@/src/store/ui.store";

const mockListAccounts = jest.fn();
const mockCreateAccount = jest.fn();
const mockUpdateAccount = jest.fn();
const mockSetAccountActive = jest.fn();
const mockGetCurrentMonthAccountSummaries = jest.fn();

jest.mock("@/src/features/transactions/account.service", () => ({
  createAccount: (...args: unknown[]) => mockCreateAccount(...args),
  listAccounts: (...args: unknown[]) => mockListAccounts(...args),
  setAccountActive: (...args: unknown[]) => mockSetAccountActive(...args),
  updateAccount: (...args: unknown[]) => mockUpdateAccount(...args),
}));

jest.mock("@/src/features/transactions/transaction.service", () => ({
  getCurrentMonthAccountSummaries: () => mockGetCurrentMonthAccountSummaries(),
}));

describe("AccountsScreen", () => {
  beforeEach(() => {
    useUIStore.setState({
      refreshKey: 0,
      quickEntrySheetVisible: false,
    });

    mockListAccounts.mockReset();
    mockCreateAccount.mockReset();
    mockUpdateAccount.mockReset();
    mockSetAccountActive.mockReset();
    mockGetCurrentMonthAccountSummaries.mockReset();

    mockListAccounts.mockResolvedValue([
      {
        id: "acc-1",
        name: "支付宝",
        type: "ewallet",
        balance: 2500,
        currency: "CNY",
        isActive: 1,
        createdAt: 1,
        updatedAt: 1,
      },
      {
        id: "acc-2",
        name: "现金储备",
        type: "cash",
        balance: 100,
        currency: "CNY",
        isActive: 1,
        createdAt: 2,
        updatedAt: 2,
      },
      {
        id: "acc-3",
        name: "旧信用卡",
        type: "credit",
        balance: 800,
        currency: "CNY",
        isActive: 0,
        createdAt: 3,
        updatedAt: 3,
      },
    ]);
    mockGetCurrentMonthAccountSummaries.mockResolvedValue([
      {
        accountId: "acc-1",
        accountName: "支付宝",
        accountType: "ewallet",
        balance: 2500,
        income: 1200,
        expense: 300,
        net: 900,
        transactionCount: 5,
      },
      {
        accountId: "acc-2",
        accountName: "现金储备",
        accountType: "cash",
        balance: 100,
        income: 0,
        expense: 40,
        net: -40,
        transactionCount: 2,
      },
    ]);
    mockCreateAccount.mockResolvedValue("acc-4");
    mockUpdateAccount.mockResolvedValue(undefined);
    mockSetAccountActive.mockResolvedValue(undefined);
  });

  it("renders account overview, spotlight cards, and grouped accounts", async () => {
    renderWithProviders(<AccountsScreen />);

    await waitFor(() => {
      expect(screen.getByText("账户总览")).toBeTruthy();
      expect(screen.getByText("账户净资产")).toBeTruthy();
      expect(screen.getAllByText("¥2600.00").length).toBeGreaterThan(0);
      expect(screen.getAllByText("支付宝").length).toBeGreaterThan(0);
      expect(screen.getAllByText("现金储备").length).toBeGreaterThan(0);
      expect(screen.queryByText("旧信用卡")).toBeNull();
    });

    expect(mockListAccounts).toHaveBeenCalledWith({ includeInactive: true });

    fireEvent.press(screen.getByText("已停用"));

    await waitFor(() => {
      expect(screen.getByText("旧信用卡")).toBeTruthy();
      expect(screen.getAllByText("已停用").length).toBeGreaterThan(0);
    });
  });

  it("creates a new account with initial balance", async () => {
    renderWithProviders(<AccountsScreen />);

    await waitFor(() => {
      expect(screen.getByLabelText("账户名称输入")).toBeTruthy();
    });

    fireEvent.changeText(screen.getByLabelText("账户名称输入"), "备用金");
    fireEvent.changeText(screen.getByLabelText("账户初始余额输入"), "520.5");
    fireEvent.press(screen.getByLabelText("创建账户"));

    await waitFor(() => {
      expect(mockCreateAccount).toHaveBeenCalledWith({
        name: "备用金",
        type: "cash",
        initialBalance: 520.5,
      });
      expect(screen.getByText("账户已创建")).toBeTruthy();
    });
  });

  it("supports editing and archiving an account", async () => {
    renderWithProviders(<AccountsScreen />);

    await waitFor(() => {
      expect(screen.getByLabelText("编辑支付宝")).toBeTruthy();
    });

    fireEvent.press(screen.getByLabelText("编辑支付宝"));

    await waitFor(() => {
      expect(screen.getByText("编辑账户 · 支付宝")).toBeTruthy();
    });

    fireEvent.changeText(screen.getByLabelText("账户名称输入"), "主支付钱包");
    fireEvent.press(screen.getByLabelText("保存账户修改"));

    await waitFor(() => {
      expect(mockUpdateAccount).toHaveBeenCalledWith("acc-1", {
        name: "主支付钱包",
        type: "ewallet",
      });
      expect(screen.getByText("账户信息已更新")).toBeTruthy();
    });

    fireEvent.press(screen.getByLabelText("停用支付宝"));

    await waitFor(() => {
      expect(mockSetAccountActive).toHaveBeenCalledWith("acc-1", false);
      expect(screen.getByText("账户已停用")).toBeTruthy();
    });
  });
});

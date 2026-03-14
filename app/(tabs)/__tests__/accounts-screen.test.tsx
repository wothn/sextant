import { screen, waitFor } from "@testing-library/react-native";

import AccountsScreen from "@/app/(tabs)/accounts";
import { renderWithProviders } from "@/src/test/render";

const mockListAccounts = jest.fn();
const mockCreateAccount = jest.fn();

jest.mock("@/src/features/transactions/account.service", () => ({
  createAccount: (...args: unknown[]) => mockCreateAccount(...args),
  listAccounts: () => mockListAccounts(),
}));

describe("AccountsScreen", () => {
  beforeEach(() => {
    mockListAccounts.mockResolvedValue([
      {
        id: "acc-1",
        name: "现金",
        type: "cash",
        balance: 100,
        currency: "CNY",
        isActive: 1,
        createdAt: 1,
        updatedAt: 1,
      },
    ]);
    mockCreateAccount.mockResolvedValue("acc-2");
  });

  it("renders the current account list", async () => {
    renderWithProviders(<AccountsScreen />);

    await waitFor(() => {
      expect(screen.getByText("新增账户")).toBeTruthy();
      expect(screen.getByText("现金")).toBeTruthy();
      expect(screen.getByText("cash · 余额 ¥100.00")).toBeTruthy();
    });
  });
});

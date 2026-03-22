import { fireEvent, screen } from "@testing-library/react-native";

import { TransactionDetailSheet } from "@/src/components/screens/home/TransactionDetailSheet";
import { renderWithProviders } from "@/src/test/render";

describe("TransactionDetailSheet", () => {
  it("renders the summary when visible and dismisses from the close button", () => {
    const handleDismiss = jest.fn();

    renderWithProviders(
      <TransactionDetailSheet
        visible
        amountTone="#16A34A"
        summary={{
          categoryName: "工资",
          amount: "+¥1200.00",
          time: "3月8日 09:30",
          typeLabel: "收入",
          spendingLabel: "不适用",
          paymentMethodName: "现金",
          description: "工资到账",
        }}
        onDismiss={handleDismiss}
      />,
    );

    expect(screen.getByText("交易详情")).toBeTruthy();
    expect(screen.getByText("工资")).toBeTruthy();
    fireEvent.press(screen.getByText("关闭"));

    expect(handleDismiss).toHaveBeenCalledTimes(1);
  });
});

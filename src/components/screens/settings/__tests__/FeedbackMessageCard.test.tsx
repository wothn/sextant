import { screen } from "@testing-library/react-native";

import { FeedbackMessageCard } from "@/src/components/screens/settings/FeedbackMessageCard";
import { renderWithProviders } from "@/src/test/render";

describe("FeedbackMessageCard", () => {
  it("does not render when the message is empty", () => {
    renderWithProviders(<FeedbackMessageCard message="" />);

    expect(screen.queryByText("导出成功")).toBeNull();
  });

  it("renders success and failure messages", () => {
    const view = renderWithProviders(<FeedbackMessageCard message="导出成功：/tmp/test.csv" />);

    expect(screen.getByText("导出成功：/tmp/test.csv")).toBeTruthy();

    view.rerender(<FeedbackMessageCard message="导出失败：磁盘不可写" />);

    expect(screen.getByText("导出失败：磁盘不可写")).toBeTruthy();
  });
});

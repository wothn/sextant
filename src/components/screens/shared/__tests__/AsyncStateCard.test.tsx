import { fireEvent, screen } from "@testing-library/react-native";

import { AsyncStateCard } from "@/src/components/screens/shared/AsyncStateCard";
import { renderWithProviders } from "@/src/test/render";

describe("AsyncStateCard", () => {
  it("renders retry content and forwards the action callback", () => {
    const handleRetry = jest.fn();

    renderWithProviders(
      <AsyncStateCard
        title="数据加载失败"
        description="请稍后重试"
        actionLabel="重试"
        onAction={handleRetry}
      />,
    );

    expect(screen.getByText("数据加载失败")).toBeTruthy();
    expect(screen.getByText("请稍后重试")).toBeTruthy();

    fireEvent.press(screen.getByText("重试"));

    expect(handleRetry).toHaveBeenCalledTimes(1);
  });
});

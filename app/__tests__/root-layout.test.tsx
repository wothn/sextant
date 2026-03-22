import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import RootLayout from "@/app/_layout";

const mockGetDb = jest.fn();

jest.mock("expo-router", () => {
  const React = require("react");
  const { Text } = require("react-native");

  return {
    Stack: () => React.createElement(Text, null, "router-stack"),
  };
});

jest.mock("@/src/db/client", () => ({
  getDb: () => mockGetDb(),
}));

describe("RootLayout", () => {
  let consoleErrorSpy: jest.SpiedFunction<typeof console.error>;

  beforeEach(() => {
    mockGetDb.mockReset();
    consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => undefined);
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it("shows the router only after database initialization succeeds", async () => {
    let resolvePromise: () => void = () => undefined;
    mockGetDb.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolvePromise = resolve;
        }),
    );

    render(<RootLayout />);

    expect(screen.queryByText("router-stack")).toBeNull();

    resolvePromise();

    await waitFor(() => {
      expect(screen.getByText("router-stack")).toBeTruthy();
    });
  });

  it("shows an error state and retries database initialization", async () => {
    mockGetDb.mockRejectedValueOnce(new Error("boom")).mockResolvedValueOnce(undefined);

    render(<RootLayout />);

    await waitFor(() => {
      expect(screen.getByText("数据库初始化失败")).toBeTruthy();
      expect(screen.getByText("boom")).toBeTruthy();
    });

    expect(consoleErrorSpy).toHaveBeenCalledWith("Database init failed", expect.any(Error));

    fireEvent.press(screen.getByText("重试"));

    await waitFor(() => {
      expect(mockGetDb).toHaveBeenCalledTimes(2);
      expect(screen.getByText("router-stack")).toBeTruthy();
    });
  });
});

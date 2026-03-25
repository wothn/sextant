import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";

import RootLayout from "@/app/_layout";

const mockGetDb = jest.fn();
const mockLoadIconFont = jest.fn();

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

jest.mock("@expo/vector-icons", () => {
  const MaterialCommunityIcons = () => null;
  MaterialCommunityIcons.loadFont = () => mockLoadIconFont();

  return {
    MaterialCommunityIcons,
  };
});

describe("RootLayout", () => {
  let consoleErrorSpy: jest.SpiedFunction<typeof console.error>;

  beforeEach(() => {
    mockGetDb.mockReset();
    mockLoadIconFont.mockReset();
    mockLoadIconFont.mockResolvedValue(undefined);
    consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => undefined);
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it("shows the router only after database initialization succeeds", async () => {
    let resolvePromise: () => void = () => undefined;
    let resolveIconFontPromise: () => void = () => undefined;
    mockGetDb.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolvePromise = resolve;
        }),
    );
    mockLoadIconFont.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveIconFontPromise = resolve;
        }),
    );

    render(<RootLayout />);

    expect(screen.queryByText("router-stack")).toBeNull();

    resolvePromise();
    await waitFor(() => {
      expect(screen.queryByText("router-stack")).toBeNull();
    });

    resolveIconFontPromise();

    await waitFor(() => {
      expect(screen.getByText("router-stack")).toBeTruthy();
    });
  });

  it("shows an error state and retries database initialization", async () => {
    mockGetDb.mockRejectedValueOnce(new Error("boom")).mockResolvedValueOnce(undefined);

    render(<RootLayout />);

    await waitFor(() => {
      expect(screen.getByText("应用初始化失败")).toBeTruthy();
      expect(screen.getByText("boom")).toBeTruthy();
    });

    expect(consoleErrorSpy).toHaveBeenCalledWith("App init failed", expect.any(Error));

    fireEvent.press(screen.getByText("重试"));

    await waitFor(() => {
      expect(mockGetDb).toHaveBeenCalledTimes(2);
      expect(mockLoadIconFont).toHaveBeenCalledTimes(2);
      expect(screen.getByText("router-stack")).toBeTruthy();
    });
  });
});

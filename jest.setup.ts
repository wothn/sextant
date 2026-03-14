(
  globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

const originalConsoleError = console.error;

jest.mock("@expo/vector-icons", () => ({
  MaterialCommunityIcons: () => null,
}));

jest.mock("@expo/vector-icons/MaterialCommunityIcons", () => ({
  __esModule: true,
  default: () => null,
}));

beforeAll(() => {
  console.error = (...args: unknown[]) => {
    const hasActWarning = args.some(
      (arg) => typeof arg === "string" && arg.includes("not wrapped in act(...)"),
    );
    const isKnownReactNativeTestNoise = args.some(
      (arg) => arg === "HomeScreen" || arg === "VirtualizedList",
    );

    if (hasActWarning && isKnownReactNativeTestNoise) {
      return;
    }

    originalConsoleError(...args);
  };
});

afterEach(() => {
  jest.useRealTimers();
});

afterAll(() => {
  console.error = originalConsoleError;
});

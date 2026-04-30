import "@tamagui/native/setup-zeego";

(
  globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

require("react-native-reanimated").setUpTests();

const originalConsoleError = console.error;
const originalConsoleWarn = console.warn;

console.error = (...args: unknown[]) => {
  const hasActWarning = args.some(
    (arg) => typeof arg === "string" && arg.includes("not wrapped in act(...)"),
  );
  const isKnownReactNativeTestNoise = args.some(
    (arg) => arg === "HomeScreen" || arg === "VirtualizedList",
  );
  const isKnownTamaguiPortalTestNoise = args.some(
    (arg) => typeof arg === "string" && arg.includes("PortalProviderComponent"),
  );

  if (hasActWarning && (isKnownReactNativeTestNoise || isKnownTamaguiPortalTestNoise)) {
    return;
  }

  originalConsoleError(...args);
};

console.warn = (...args: unknown[]) => {
  const isKnownTamaguiNativeMenuTestNoise = args.some(
    (arg) =>
      typeof arg === "string" &&
      arg.includes("Must call import '@tamagui/native/setup-zeego'"),
  );

  if (isKnownTamaguiNativeMenuTestNoise) {
    return;
  }

  originalConsoleWarn(...args);
};

jest.mock("react-native-worklets", () => require("react-native-worklets/src/mock"));
jest.mock("react-native-safe-area-context", () => {
  const React = require("react");

  const insets = {
    top: 24,
    right: 0,
    bottom: 34,
    left: 0,
  };
  const frame = {
    x: 0,
    y: 0,
    width: 390,
    height: 844,
  };

  return {
    SafeAreaProvider: ({ children }: { children: React.ReactNode }) => children,
    SafeAreaView: ({ children }: { children: React.ReactNode }) => children,
    SafeAreaInsetsContext: React.createContext(insets),
    SafeAreaFrameContext: React.createContext(frame),
    initialWindowMetrics: {
      frame,
      insets,
    },
    useSafeAreaInsets: () => insets,
    useSafeAreaFrame: () => frame,
  };
});

jest.mock("@expo/vector-icons", () => {
  const MaterialCommunityIcons = () => null;
  MaterialCommunityIcons.loadFont = () => Promise.resolve();

  return {
    MaterialCommunityIcons,
  };
});

jest.mock("@expo/vector-icons/MaterialCommunityIcons", () => ({
  __esModule: true,
  default: () => null,
}));

afterEach(() => {
  jest.useRealTimers();
});

afterAll(() => {
  console.error = originalConsoleError;
  console.warn = originalConsoleWarn;
});

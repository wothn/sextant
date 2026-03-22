module.exports = {
  preset: "react-native",
  clearMocks: true,
  testMatch: ["**/?(*.)+(spec|test).ts?(x)"],
  setupFilesAfterEnv: ["<rootDir>/jest.setup.ts"],
  moduleNameMapper: {
    "^@/(.*)$": "<rootDir>/$1",
  },
  collectCoverageFrom: [
    "app/**/*.{ts,tsx}",
    "src/**/*.{ts,tsx}",
    "!app/**/__tests__/**",
    "!src/**/__tests__/**",
    "!src/test/**",
    "!src/types/**",
    "!src/db/client.ts",
    "!App.tsx",
  ],
  coverageThreshold: {
    global: {
      branches: 55,
      functions: 70,
      lines: 70,
      statements: 70,
    },
  },
  transformIgnorePatterns: [
    "node_modules/(?!(jest-)?react-native|@react-native|react-native|expo(nent)?|@expo(nent)?/.*|@expo/.*|expo-router|@unimodules/.*|unimodules|tamagui|@tamagui/.*|react-native-svg|@react-navigation/.*|react-native-reanimated|react-native-worklets)",
  ],
};

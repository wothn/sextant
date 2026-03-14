import { fireEvent, screen } from "@testing-library/react-native";

import TabsLayout from "@/app/(tabs)/_layout";
import { renderWithProviders } from "@/src/test/render";

const mockOpenQuickEntrySheet = jest.fn();

jest.mock("@expo/vector-icons", () => ({
  MaterialCommunityIcons: () => null,
}));

jest.mock("@/src/store/ui.store", () => ({
  useUIStore: (selector: (state: { openQuickEntrySheet: () => void }) => unknown) =>
    selector({
      openQuickEntrySheet: mockOpenQuickEntrySheet,
    }),
}));

jest.mock("@/src/features/transactions/components/QuickEntrySheetContainer", () => ({
  __esModule: true,
  default: () => null,
}));

jest.mock("expo-router", () => {
  const React = require("react");
  const { Text, TouchableOpacity, View } = require("react-native");

  const Tabs = ({ children }: { children: React.ReactNode }) =>
    React.createElement(View, null, children);

  Tabs.Screen = ({
    name,
    options,
  }: {
    name: string;
    options: {
      title: string;
      tabBarButton?: (props: {
        onPress?: () => void;
        children?: React.ReactNode;
      }) => React.ReactElement;
    };
  }) => {
    const button = options.tabBarButton
      ? options.tabBarButton({
          onPress: mockOpenQuickEntrySheet,
          children: React.createElement(Text, null, `${name}:icon`),
        })
      : null;

    const buttonElement = button as React.ReactElement<{
      onPress?: () => void;
    }> | null;

    return React.createElement(
      View,
      null,
      React.createElement(Text, null, `${name}:${options.title}`),
      buttonElement
        ? React.createElement(
            TouchableOpacity,
            {
              accessibilityRole: "button",
              accessibilityLabel: `${name}:${options.title}:button`,
              onPress: () => {
                if (buttonElement.props.onPress) {
                  buttonElement.props.onPress();
                }
              },
            },
            buttonElement,
          )
        : null,
    );
  };

  return { Tabs };
});

describe("TabsLayout", () => {
  beforeEach(() => {
    mockOpenQuickEntrySheet.mockClear();
  });

  it("declares all tab screens with expected titles", () => {
    renderWithProviders(<TabsLayout />);

    expect(screen.getByText("index:首页")).toBeTruthy();
    expect(screen.getByText("accounts:账户")).toBeTruthy();
    expect(screen.getByText("quick-entry:记账")).toBeTruthy();
    expect(screen.getByText("analytics:分析")).toBeTruthy();
    expect(screen.getByText("settings:设置")).toBeTruthy();
  });

  it("opens the quick entry bottom sheet when pressing the center tab button", () => {
    renderWithProviders(<TabsLayout />);

    fireEvent.press(screen.getByLabelText("quick-entry:记账:button"));

    expect(mockOpenQuickEntrySheet).toHaveBeenCalledTimes(1);
  });
});

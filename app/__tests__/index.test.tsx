import { render, screen } from "@testing-library/react-native";

import IndexPage from "@/app/index";

jest.mock("expo-router", () => {
  const React = require("react");
  const { Text } = require("react-native");

  return {
    Redirect: ({ href }: { href: string }) => React.createElement(Text, null, `redirect:${href}`),
  };
});

describe("IndexPage", () => {
  it("redirects to the tabs group", () => {
    render(<IndexPage />);

    expect(screen.getByText("redirect:/(tabs)")).toBeTruthy();
  });
});

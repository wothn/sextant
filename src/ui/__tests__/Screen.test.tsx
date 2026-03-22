import { ScrollView, Text } from "react-native";

import { Screen } from "@/src/ui";
import { renderWithProviders } from "@/src/test/render";

describe("Screen", () => {
  it("applies safe area insets to the scroll content paddings", () => {
    const { UNSAFE_getByType } = renderWithProviders(
      <Screen>
        <Text>safe-area-content</Text>
      </Screen>,
    );

    const scrollView = UNSAFE_getByType(ScrollView);

    expect(scrollView.props.contentContainerStyle).toMatchObject({
      paddingTop: 44,
      paddingRight: 20,
      paddingBottom: 154,
      paddingLeft: 20,
    });
    expect(scrollView.props.scrollIndicatorInsets).toEqual({
      top: 24,
      right: 0,
      bottom: 34,
      left: 0,
    });
  });
});

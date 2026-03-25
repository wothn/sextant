import { act } from "@testing-library/react-native";
import { ScrollView } from "react-native";

import { TimeWheelPicker } from "@/src/features/transactions/components/quick-entry/TimeWheelPicker";
import { renderWithProviders } from "@/src/test/render";

describe("TimeWheelPicker", () => {
  it("waits for momentum end before updating selection", () => {
    const handleSelect = jest.fn();
    const view = renderWithProviders(
      <TimeWheelPicker
        label="小时"
        accessibilityPrefix="小时"
        options={["09", "10", "11", "12"]}
        selectedValue="10"
        visible
        onSelect={handleSelect}
      />,
    );

    const scrollView = view.UNSAFE_getByType(ScrollView);

    act(() => {
      scrollView.props.onMomentumScrollBegin();
      scrollView.props.onScrollEndDrag({
        nativeEvent: {
          contentOffset: { x: 0, y: 288 },
          velocity: { x: 0, y: 1.2 },
        },
      });
    });

    expect(handleSelect).not.toHaveBeenCalled();

    act(() => {
      scrollView.props.onMomentumScrollEnd({
        nativeEvent: {
          contentOffset: { x: 0, y: 288 },
        },
      });
    });

    expect(handleSelect).toHaveBeenCalledWith("11");
  });

  it("updates selection immediately when drag ends without momentum", () => {
    const handleSelect = jest.fn();
    const view = renderWithProviders(
      <TimeWheelPicker
        label="分钟"
        accessibilityPrefix="分钟"
        options={["00", "15", "30", "45"]}
        selectedValue="00"
        visible
        onSelect={handleSelect}
      />,
    );

    const scrollView = view.UNSAFE_getByType(ScrollView);

    act(() => {
      scrollView.props.onScrollEndDrag({
        nativeEvent: {
          contentOffset: { x: 0, y: 288 },
          velocity: { x: 0, y: 0 },
        },
      });
    });

    expect(handleSelect).toHaveBeenCalledWith("30");
  });

  it("wraps across the end of the list", () => {
    const handleSelect = jest.fn();
    const view = renderWithProviders(
      <TimeWheelPicker
        label="小时"
        accessibilityPrefix="小时"
        options={["09", "10", "11", "12"]}
        selectedValue="12"
        visible
        onSelect={handleSelect}
      />,
    );

    const scrollView = view.UNSAFE_getByType(ScrollView);

    act(() => {
      scrollView.props.onMomentumScrollEnd({
        nativeEvent: {
          contentOffset: { x: 0, y: 384 },
        },
      });
    });

    expect(handleSelect).toHaveBeenCalledWith("09");
  });

  it("renders the focus band as transparent", () => {
    const view = renderWithProviders(
      <TimeWheelPicker
        label="小时"
        accessibilityPrefix="小时"
        options={["09", "10", "11", "12"]}
        selectedValue="10"
        visible
        onSelect={jest.fn()}
      />,
    );

    expect(view.getByTestId("小时-focus-band")).toHaveStyle({
      backgroundColor: "transparent",
    });
  });
});

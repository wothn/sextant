import { act, screen } from "@testing-library/react-native";
import { Modal as ReactNativeModal, Text } from "react-native";

import { BottomSheetModal } from "@/src/ui/BottomSheetModal";
import { renderWithProviders } from "@/src/test/render";

describe("BottomSheetModal", () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("mounts content when visibility turns on", () => {
    const view = renderWithProviders(
      <BottomSheetModal visible={false}>
        <Text>交易详情</Text>
      </BottomSheetModal>,
    );

    expect(screen.queryByText("交易详情")).toBeNull();

    view.rerender(
      <BottomSheetModal visible>
        <Text>交易详情</Text>
      </BottomSheetModal>,
    );

    expect(screen.getByText("交易详情")).toBeTruthy();
  });

  it("removes content after the exit animation completes", () => {
    const handleExited = jest.fn();
    const view = renderWithProviders(
      <BottomSheetModal visible onExited={handleExited}>
        <Text>交易详情</Text>
      </BottomSheetModal>,
    );

    expect(screen.getByText("交易详情")).toBeTruthy();

    view.rerender(
      <BottomSheetModal visible={false} onExited={handleExited}>
        <Text>交易详情</Text>
      </BottomSheetModal>,
    );

    act(() => {
      jest.runAllTimers();
    });

    expect(handleExited).toHaveBeenCalledTimes(1);
    expect(screen.queryByText("交易详情")).toBeNull();
  });

  it("forwards the native modal onShow callback", () => {
    const handleShow = jest.fn();
    const view = renderWithProviders(
      <BottomSheetModal visible onShow={handleShow}>
        <Text>交易详情</Text>
      </BottomSheetModal>,
    );

    const nativeModal = view.UNSAFE_root.findByType(ReactNativeModal);

    nativeModal.props.onShow();

    expect(handleShow).toHaveBeenCalledTimes(1);
  });
});

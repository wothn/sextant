import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { Modal as ReactNativeModal } from "react-native";

import QuickEntrySheetForm from "@/src/features/transactions/components/QuickEntrySheetForm";
import { setDatePart, setTimePart } from "@/src/features/transactions/components/quick-entry/utils";
import { renderWithProviders } from "@/src/test/render";

const animationScope = globalThis;
const originalRequestAnimationFrame = animationScope.requestAnimationFrame;
const originalCancelAnimationFrame = animationScope.cancelAnimationFrame;
const baseTransactionDate = new Date("2026-03-08T10:15:30.000Z").getTime();

function renderQuickEntryForm(handleChange = jest.fn()) {
  return renderWithProviders(
    <QuickEntrySheetForm
      visible
      categories={[
        {
          id: "cat-1",
          name: "餐饮",
          type: "expense",
          icon: "food",
          color: "#FF7043",
          isBuiltIn: 1,
          isActive: 1,
          includeInSpending: 1,
          parentCategoryId: null,
          createdAt: 1,
        },
      ]}
      paymentMethods={[
        {
          id: "pm-1",
          name: "现金",
          icon: "cash",
          color: "#D97706",
          isActive: 1,
          isBuiltIn: 1,
          createdAt: 1,
        },
      ]}
      value={{
        type: "expense",
        amountText: "12.5",
        description: "",
        categoryId: "cat-1",
        paymentMethodId: "pm-1",
        transactionDate: baseTransactionDate,
      }}
      onChange={handleChange}
      onDismiss={jest.fn()}
      onSubmit={jest.fn()}
    />,
  );
}

describe("QuickEntrySheetForm", () => {
  beforeEach(() => {
    jest.useFakeTimers().setSystemTime(new Date("2026-03-08T10:15:30.000Z"));
  });

  afterEach(() => {
    jest.useRealTimers();
    animationScope.requestAnimationFrame = originalRequestAnimationFrame;
    animationScope.cancelAnimationFrame = originalCancelAnimationFrame;
  });

  it("opens the note sheet in a native modal above the quick entry sheet", () => {
    const handleChange = jest.fn();
    const view = renderQuickEntryForm(handleChange);

    const nativeModalCount = view.UNSAFE_root.findAllByType(ReactNativeModal).length;

    fireEvent.press(screen.getByLabelText("备注"));

    expect(screen.getByPlaceholderText("在此输入备注...")).toBeTruthy();
    expect(view.UNSAFE_root.findAllByType(ReactNativeModal)).toHaveLength(nativeModalCount + 1);
  });

  it("schedules note focus with requestAnimationFrame", () => {
    const view = renderQuickEntryForm();

    const requestAnimationFrame: typeof animationScope.requestAnimationFrame = jest.fn(
      (_callback: FrameRequestCallback): number => {
        return 1;
      },
    );

    const cancelAnimationFrame: typeof animationScope.cancelAnimationFrame = jest.fn(
      (_handle: number): void => {
        return;
      },
    );

    animationScope.requestAnimationFrame = requestAnimationFrame;
    animationScope.cancelAnimationFrame = cancelAnimationFrame;

    fireEvent.press(screen.getByLabelText("备注"));
    const noteModal = view.UNSAFE_root
      .findAllByType(ReactNativeModal)
      .find((modal: { props: { onShow?: unknown } }) => typeof modal.props.onShow === "function");
    noteModal?.props.onShow();
    fireEvent(screen.getByPlaceholderText("在此输入备注..."), "layout", {
      nativeEvent: { layout: { x: 0, y: 0, width: 320, height: 84 } },
    });

    expect(screen.getByPlaceholderText("在此输入备注...")).toBeTruthy();
    expect(requestAnimationFrame).toHaveBeenCalled();
  });

  it("cancels pending note focus frame when note sheet closes", () => {
    const view = renderQuickEntryForm();

    const requestAnimationFrame: typeof animationScope.requestAnimationFrame = jest.fn(
      (_callback: FrameRequestCallback): number => {
        return 9;
      },
    );

    const cancelAnimationFrame: typeof animationScope.cancelAnimationFrame = jest.fn(
      (_handle: number): void => {
        return;
      },
    );

    animationScope.requestAnimationFrame = requestAnimationFrame;
    animationScope.cancelAnimationFrame = cancelAnimationFrame;

    fireEvent.press(screen.getByLabelText("备注"));
    const noteModal = view.UNSAFE_root
      .findAllByType(ReactNativeModal)
      .find((modal: { props: { onShow?: unknown } }) => typeof modal.props.onShow === "function");
    noteModal?.props.onShow();
    fireEvent(screen.getByPlaceholderText("在此输入备注..."), "layout", {
      nativeEvent: { layout: { x: 0, y: 0, width: 320, height: 84 } },
    });
    fireEvent.press(screen.getByLabelText("关闭备注弹窗"));

    expect(cancelAnimationFrame).toHaveBeenCalledWith(9);
  });

  it("updates transaction date from calendar selections", async () => {
    const handleChange = jest.fn();
    renderQuickEntryForm(handleChange);

    fireEvent.press(screen.getByLabelText("日期"));

    await waitFor(() => {
      expect(screen.getByLabelText("回到今天", { includeHiddenElements: true })).toBeTruthy();
    });

    expect(screen.queryByText("记账日期", { includeHiddenElements: true })).toBeNull();
    expect(screen.queryByText("当前记账日期", { includeHiddenElements: true })).toBeNull();

    fireEvent.press(screen.getByLabelText("选择2026年3月7日", { includeHiddenElements: true }));

    expect(handleChange).toHaveBeenCalledWith({
      transactionDate: setDatePart(baseTransactionDate, new Date(2026, 2, 7).getTime()),
    });
  });

  it("updates transaction time from wheel item selections", async () => {
    const handleChange = jest.fn();
    renderQuickEntryForm(handleChange);

    fireEvent.press(screen.getByLabelText("时间"));

    await waitFor(() => {
      expect(screen.getByLabelText("选择小时09", { includeHiddenElements: true })).toBeTruthy();
    });

    expect(screen.queryByText("记账时间", { includeHiddenElements: true })).toBeNull();
    expect(screen.queryByText("当前记账时间", { includeHiddenElements: true })).toBeNull();

    fireEvent.press(screen.getByLabelText("选择小时09", { includeHiddenElements: true }));
    fireEvent.press(screen.getByLabelText("选择分钟30", { includeHiddenElements: true }));

    expect(handleChange).toHaveBeenNthCalledWith(1, {
      transactionDate: setTimePart(baseTransactionDate, 9, 15),
    });
    expect(handleChange).toHaveBeenNthCalledWith(2, {
      transactionDate: setTimePart(baseTransactionDate, 18, 30),
    });
  });
});

import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { StyleSheet } from "react-native";

import QuickEntrySheetForm from "@/src/features/transactions/components/QuickEntrySheetForm";
import { setDatePart, setTimePart } from "@/src/features/transactions/components/quick-entry/utils";
import { renderWithProviders } from "@/src/test/render";

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

  it("opens a bottom note editor and updates description", async () => {
    const handleChange = jest.fn();
    renderQuickEntryForm(handleChange);

    fireEvent.press(screen.getByLabelText("添加备注"));

    await waitFor(() => {
      expect(screen.getByLabelText("备注输入框", { includeHiddenElements: true })).toBeTruthy();
    });

    fireEvent.changeText(
      screen.getByLabelText("备注输入框", { includeHiddenElements: true }),
      "和朋友晚餐",
    );

    expect(handleChange).toHaveBeenCalledWith({ description: "和朋友晚餐" });
  });

  it("renders the note editor inside a keyboard avoiding container", async () => {
    renderQuickEntryForm();

    fireEvent.press(screen.getByLabelText("添加备注"));

    await waitFor(() => {
      expect(screen.getByTestId("note-keyboard-avoiding-view")).toBeTruthy();
    });
  });

  it("keeps the note input compact without extra bottom padding", async () => {
    renderQuickEntryForm();

    fireEvent.press(screen.getByLabelText("添加备注"));

    await waitFor(() => {
      expect(screen.getByTestId("note-text-input")).toBeTruthy();
    });

    const inputStyle = StyleSheet.flatten(screen.getByTestId("note-text-input").props.style);

    expect(inputStyle).toEqual(
      expect.objectContaining({
        includeFontPadding: false,
        minHeight: 56,
        paddingBottom: 0,
        paddingTop: 0,
      }),
    );
  });

  it("keeps the note input close to the sheet bottom", async () => {
    renderQuickEntryForm();

    fireEvent.press(screen.getByLabelText("添加备注"));

    await waitFor(() => {
      expect(screen.getByTestId("note-sheet-frame")).toBeTruthy();
    });

    const frameStyle = StyleSheet.flatten(screen.getByTestId("note-sheet-frame").props.style);

    expect(frameStyle).toEqual(expect.objectContaining({ paddingBottom: 20 }));
  });

  it("requests the keyboard as soon as the note editor opens", async () => {
    renderQuickEntryForm();

    fireEvent.press(screen.getByLabelText("添加备注"));

    await waitFor(() => {
      expect(screen.getByTestId("note-text-input")).toBeTruthy();
    });

    const input = screen.getByTestId("note-text-input");

    expect(input.props.autoFocus).toBe(true);
    expect(input.props.showSoftInputOnFocus).toBe(true);
  });
});

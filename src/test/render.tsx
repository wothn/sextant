import { render } from "@testing-library/react-native";
import type { ReactElement, PropsWithChildren } from "react";

import { AppProviders } from "@/src/providers/AppProviders";

function Providers({ children }: PropsWithChildren) {
  return <AppProviders scheme="light">{children}</AppProviders>;
}

export function renderWithProviders(ui: ReactElement) {
  return render(ui, { wrapper: Providers });
}

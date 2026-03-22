import { Fragment } from "react";
import type { PropsWithChildren } from "react";

export function Portal({ children }: PropsWithChildren) {
  return <Fragment>{children}</Fragment>;
}

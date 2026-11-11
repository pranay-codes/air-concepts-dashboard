import { useEffect } from "react";
import type { DashboardApi } from "../api/dashboard";
import type { Entity } from "../api/contracts";
import { isAccessFailure } from "../api/client";
export interface ForecastContext {
  api: DashboardApi;
  entity: Entity;
  /** Calendar year the selected financial year starts in (2026 is 2026-2027); null until the server has answered. */
  financialYear: number | null;
  financialYearLabel: string;
  subject: string;
  onChanged(): void;
  onAccessDenied(): void;
  onDirty(dirty: boolean): void;
}
export function useDirty(dirty: boolean, onDirty: (dirty: boolean) => void) {
  useEffect(() => {
    onDirty(dirty);
    return () => onDirty(false);
  }, [dirty, onDirty]);
}
export function handleAccess(onAccessDenied: () => void, error: unknown) {
  if (isAccessFailure(error)) onAccessDenied();
}

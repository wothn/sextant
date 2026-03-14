export interface TimeRange {
  start: number;
  end: number;
}

export function getMonthRange(date: Date = new Date()): TimeRange {
  return {
    start: new Date(date.getFullYear(), date.getMonth(), 1).getTime(),
    end: new Date(date.getFullYear(), date.getMonth() + 1, 1).getTime(),
  };
}

export function getDayRange(date: Date = new Date()): TimeRange {
  return {
    start: new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime(),
    end: new Date(date.getFullYear(), date.getMonth(), date.getDate() + 1).getTime(),
  };
}

export function getMonthKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

export function getDateKey(timestamp: number): string {
  const date = new Date(timestamp);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(
    date.getDate(),
  ).padStart(2, "0")}`;
}

import { MaterialCommunityIcons } from "@expo/vector-icons";

import type { MaterialIconName } from "@/src/features/transactions/components/quick-entry/types";

export const WEEKDAY_LABELS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"] as const;

export function pad(value: number): string {
  return String(value).padStart(2, "0");
}

export function isSameDay(left: Date, right: Date): boolean {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  );
}

export function getDateLabel(timestamp: number): string {
  const date = new Date(timestamp);
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${WEEKDAY_LABELS[date.getDay()]}`;
}

export function getDateChipLabel(timestamp: number): string {
  const date = new Date(timestamp);
  return `${date.getMonth() + 1}月${date.getDate()}日`;
}

export function getTimeLabel(timestamp: number): string {
  const date = new Date(timestamp);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function setDatePart(baseTimestamp: number, sourceTimestamp: number): number {
  const base = new Date(baseTimestamp);
  const source = new Date(sourceTimestamp);

  return new Date(
    source.getFullYear(),
    source.getMonth(),
    source.getDate(),
    base.getHours(),
    base.getMinutes(),
    0,
    0,
  ).getTime();
}

export function setTimePart(baseTimestamp: number, hours: number, minutes: number): number {
  const base = new Date(baseTimestamp);

  return new Date(
    base.getFullYear(),
    base.getMonth(),
    base.getDate(),
    hours,
    minutes,
    0,
    0,
  ).getTime();
}

export function getMonthMatrix(displayDate: Date): Date[][] {
  const year = displayDate.getFullYear();
  const month = displayDate.getMonth();
  const firstDay = new Date(year, month, 1);
  const firstWeekday = firstDay.getDay();
  const startDate = new Date(year, month, 1 - firstWeekday);
  const weeks: Date[][] = [];

  for (let weekIndex = 0; weekIndex < 6; weekIndex += 1) {
    const week: Date[] = [];
    for (let dayIndex = 0; dayIndex < 7; dayIndex += 1) {
      const current = new Date(startDate);
      current.setDate(startDate.getDate() + weekIndex * 7 + dayIndex);
      week.push(current);
    }
    weeks.push(week);
  }

  return weeks;
}

export function buildHourOptions(): string[] {
  return Array.from({ length: 24 }, (_, index) => pad(index));
}

export function buildMinuteOptions(): string[] {
  return Array.from({ length: 60 }, (_, index) => pad(index));
}

export function getKeypadRows(): string[][] {
  return [
    ["1", "2", "3"],
    ["4", "5", "6"],
    ["7", "8", "9"],
    ["spacer", "0", "."],
  ];
}

export function getInitialLabel(name: string | null | undefined): string {
  return name?.trim().slice(0, 1) || "记";
}

function isMaterialIconName(value: string): value is MaterialIconName {
  const getRawGlyphMap = MaterialCommunityIcons.getRawGlyphMap;

  if (typeof getRawGlyphMap !== "function") {
    return true;
  }

  return Object.prototype.hasOwnProperty.call(getRawGlyphMap(), value);
}

export function getOptionIconName(icon: string | null | undefined): MaterialIconName | null {
  if (!icon || !isMaterialIconName(icon)) {
    return null;
  }

  return icon;
}

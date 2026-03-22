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
  const now = new Date();
  const base = `${date.getMonth() + 1}月${date.getDate()}日 ${WEEKDAY_LABELS[date.getDay()]}`;

  return isSameDay(date, now) ? `今天 · ${base}` : `${date.getFullYear()}年${base}`;
}

export function getDateChipLabel(timestamp: number): string {
  const date = new Date(timestamp);
  return isSameDay(date, new Date()) ? "今天" : `${date.getMonth() + 1}月${date.getDate()}日`;
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
  return Array.from({ length: 12 }, (_, index) => pad(index * 5));
}

export interface DateShortcutOption {
  key: "yesterday" | "today" | "tomorrow";
  label: string;
  hint: string;
  value: Date;
}

export function buildDateShortcutOptions(baseDate: Date = new Date()): DateShortcutOption[] {
  const current = new Date(
    baseDate.getFullYear(),
    baseDate.getMonth(),
    baseDate.getDate(),
    baseDate.getHours(),
    baseDate.getMinutes(),
    0,
    0,
  );

  const yesterday = new Date(current);
  yesterday.setDate(current.getDate() - 1);

  const tomorrow = new Date(current);
  tomorrow.setDate(current.getDate() + 1);

  return [
    {
      key: "yesterday",
      label: "昨天",
      hint: `${yesterday.getMonth() + 1}月${yesterday.getDate()}日`,
      value: yesterday,
    },
    {
      key: "today",
      label: "今天",
      hint: `${current.getMonth() + 1}月${current.getDate()}日`,
      value: current,
    },
    {
      key: "tomorrow",
      label: "明天",
      hint: `${tomorrow.getMonth() + 1}月${tomorrow.getDate()}日`,
      value: tomorrow,
    },
  ];
}

export interface TimePresetOption {
  key: "now" | "morning" | "lunch" | "afterWork" | "night";
  label: string;
  hint: string;
  hours: number;
  minutes: number;
  icon: MaterialIconName;
}

export function buildTimePresetOptions(baseDate: Date = new Date()): TimePresetOption[] {
  return [
    {
      key: "now",
      label: "现在",
      hint: getTimeLabel(baseDate.getTime()),
      hours: baseDate.getHours(),
      minutes: baseDate.getMinutes(),
      icon: "clock-check-outline",
    },
    {
      key: "morning",
      label: "上班前",
      hint: "09:00",
      hours: 9,
      minutes: 0,
      icon: "white-balance-sunny",
    },
    {
      key: "lunch",
      label: "午间",
      hint: "12:30",
      hours: 12,
      minutes: 30,
      icon: "food-outline",
    },
    {
      key: "afterWork",
      label: "下班后",
      hint: "18:00",
      hours: 18,
      minutes: 0,
      icon: "briefcase-outline",
    },
    {
      key: "night",
      label: "晚间",
      hint: "21:00",
      hours: 21,
      minutes: 0,
      icon: "weather-night",
    },
  ];
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

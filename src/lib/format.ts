const WEEKDAY_LABELS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];

function parseDateKey(dateKey: string) {
  const [yearText, monthText, dayText] = dateKey.split("-");
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);

  return new Date(year, month - 1, day);
}

export function formatCurrency(amount: number) {
  return `¥${amount.toFixed(2)}`;
}

export function formatSignedCurrency(amount: number) {
  const prefix = amount > 0 ? "+" : amount < 0 ? "-" : "";
  return `${prefix}${formatCurrency(Math.abs(amount))}`;
}

export function formatCompactPercent(value: number) {
  return `${(value * 100).toFixed(value >= 0.1 ? 0 : 1)}%`;
}

export function formatDateGroupTitle(dateKey: string) {
  const target = parseDateKey(dateKey);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const targetTime = target.getTime();
  const diffDays = Math.round((today - targetTime) / (24 * 60 * 60 * 1000));
  const base = `${target.getMonth() + 1}月${target.getDate()}日 ${WEEKDAY_LABELS[target.getDay()]}`;

  if (diffDays === 0) {
    return `今天 · ${base}`;
  }

  if (diffDays === 1) {
    return `昨天 · ${base}`;
  }

  return base;
}

export function formatMonthLabel(monthKey: string) {
  const [, monthText] = monthKey.split("-");
  return `${Number(monthText)}月`;
}

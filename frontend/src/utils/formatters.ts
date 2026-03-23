/**
 * Format a number as USD currency.
 */
export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount);
}

/**
 * Format a date string (YYYY-MM-DD) to a readable format (e.g., "Mar 15, 2026").
 *
 * Parses date components directly to avoid timezone shift issues.
 * Using `new Date('YYYY-MM-DD')` parses as UTC midnight, which can render
 * the wrong day when converted to local timezone via toLocaleDateString().
 */
export function formatDate(dateString: string): string {
  const [yearStr, monthStr, dayStr] = dateString.split('-');
  const year = parseInt(yearStr, 10);
  const month = parseInt(monthStr, 10) - 1; // Date months are 0-indexed
  const day = parseInt(dayStr, 10);
  const date = new Date(year, month, day);
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date);
}

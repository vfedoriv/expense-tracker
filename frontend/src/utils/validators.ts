/**
 * Validate that a string is not empty or whitespace-only.
 */
export function isNotBlank(value: string): boolean {
  return value.trim().length > 0;
}

/**
 * Validate that an amount is a positive number.
 */
export function isPositiveAmount(amount: number): boolean {
  return !isNaN(amount) && amount > 0;
}

/**
 * Validate that a date string is a valid date.
 */
export function isValidDate(dateString: string): boolean {
  if (!dateString) return false;
  const date = new Date(dateString);
  return !isNaN(date.getTime());
}

import { describe, it, expect } from 'vitest';
import { formatCurrency, formatDate } from '../utils/formatters';

describe('formatCurrency', () => {
  it('formats a positive amount as USD', () => {
    expect(formatCurrency(52.75)).toBe('$52.75');
  });

  it('formats zero as USD', () => {
    expect(formatCurrency(0)).toBe('$0.00');
  });

  it('formats large amounts with comma separators', () => {
    expect(formatCurrency(1234.56)).toBe('$1,234.56');
  });
});

describe('formatDate', () => {
  it('formats a standard date string correctly', () => {
    expect(formatDate('2026-03-15')).toBe('Mar 15, 2026');
  });

  it('formats January 1st correctly', () => {
    expect(formatDate('2026-01-01')).toBe('Jan 1, 2026');
  });

  it('formats December 31st correctly', () => {
    expect(formatDate('2025-12-31')).toBe('Dec 31, 2025');
  });

  it('does not shift date due to timezone conversion (regression)', () => {
    // This test ensures that date-only strings like "2026-03-15" always
    // render as "Mar 15, 2026" regardless of the local timezone.
    // The old implementation used `new Date('2026-03-15')` which parses
    // as UTC midnight and could shift backward a day in western timezones.
    const testCases = [
      { input: '2026-03-15', expected: 'Mar 15, 2026' },
      { input: '2026-01-01', expected: 'Jan 1, 2026' },
      { input: '2026-06-30', expected: 'Jun 30, 2026' },
      { input: '2026-12-31', expected: 'Dec 31, 2026' },
      { input: '2025-02-28', expected: 'Feb 28, 2025' },
    ];

    for (const { input, expected } of testCases) {
      expect(formatDate(input)).toBe(expected);
    }
  });

  it('handles single-digit month and day correctly', () => {
    expect(formatDate('2026-03-05')).toBe('Mar 5, 2026');
  });

  it('handles leap year date correctly', () => {
    expect(formatDate('2024-02-29')).toBe('Feb 29, 2024');
  });
});

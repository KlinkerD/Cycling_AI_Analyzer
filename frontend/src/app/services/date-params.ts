/** Datum als "YYYY-MM-DD" fuer die Query-Parameter der API (wie bisher ueber toISOString, also UTC). */
export function toIsoDate(date: Date): string {
  return date.toISOString().split('T')[0];
}

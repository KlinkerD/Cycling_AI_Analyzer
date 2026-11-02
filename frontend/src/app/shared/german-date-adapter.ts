import { Injectable } from '@angular/core';
import { MatDateFormats, NativeDateAdapter } from '@angular/material/core';

/**
 * NativeDateAdapter mit deutschem Eingabeformat (TT.MM.JJJJ) und Montag als Wochenbeginn.
 * Der Standard-Adapter parst über Date.parse und versteht "02.11.2026" nicht.
 */
@Injectable()
export class GermanDateAdapter extends NativeDateAdapter {

  override getFirstDayOfWeek(): number {
    return 1;
  }

  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (!trimmed) return null;
      const match = /^(\d{1,2})\.(\d{1,2})\.(\d{2}|\d{4})$/.exec(trimmed);
      if (!match) return this.invalid();
      const day = Number(match[1]);
      const month = Number(match[2]) - 1;
      let year = Number(match[3]);
      if (match[3].length === 2) year += 2000;
      if (month < 0 || month > 11 || day < 1 || day > this.getNumDaysInMonth(this.createDate(year, month, 1))) {
        return this.invalid();
      }
      return this.createDate(year, month, day);
    }
    return super.parse(value, parseFormat);
  }

  override format(date: Date, displayFormat: object): string {
    if (displayFormat === GERMAN_DATE_FORMATS.display.dateInput) {
      if (!this.isValid(date)) return '';
      const dd = String(date.getDate()).padStart(2, '0');
      const mm = String(date.getMonth() + 1).padStart(2, '0');
      return `${dd}.${mm}.${date.getFullYear()}`;
    }
    return super.format(date, displayFormat);
  }
}

export const GERMAN_DATE_FORMATS: MatDateFormats = {
  parse: { dateInput: 'TT.MM.JJJJ' },
  display: {
    dateInput: { year: 'numeric', month: '2-digit', day: '2-digit' },
    monthYearLabel: { year: 'numeric', month: 'short' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' }
  }
};

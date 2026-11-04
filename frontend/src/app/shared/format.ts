/** Kleine Format- und Datumshelfer, die mehrere Seiten nutzen. */

/** Sekunden als "h:mm". */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null || isNaN(seconds) || seconds <= 0) return '–';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return `${h}:${String(m).padStart(2, '0')}`;
}

/** Sekunden als "1:02:03" bzw. "12:03" für Achsen und Tooltips. */
export function formatClock(seconds: number): string {
  const s = Math.max(0, Math.round(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = String(m).padStart(h ? 2 : 1, '0');
  const ss = String(sec).padStart(2, '0');
  return h ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** Dauer der Power-Curve: 5s, 1m, 20m, 1h. */
export function formatCurveDuration(seconds: number): string {
  if (!isFinite(seconds) || seconds <= 0) return '';
  if (seconds < 60) return `${Math.round(seconds)}s`;
  if (seconds < 3600) {
    const m = seconds / 60;
    return (m % 1 === 0 ? m.toFixed(0) : m.toFixed(1)) + 'm';
  }
  const h = seconds / 3600;
  return (h % 1 === 0 ? h.toFixed(0) : h.toFixed(1)) + 'h';
}

/** Lokales Datum als "YYYY-MM-DD". */
export function isoDay(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function addDays(d: Date, days: number): Date {
  const out = new Date(d);
  out.setDate(out.getDate() + days);
  return out;
}

/** Montag 00:00 der Woche von {@code d}. */
export function startOfWeek(d: Date): Date {
  const out = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const dow = (out.getDay() + 6) % 7;
  out.setDate(out.getDate() - dow);
  return out;
}

/** "dd.MM." aus einem ISO-Datum. */
export function shortDate(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' });
}

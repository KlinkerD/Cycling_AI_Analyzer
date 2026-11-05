/**
 * Zugriff auf die Design-Tokens aus styles.scss. Canvas-Charts können keine CSS-Variablen
 * auflösen, deshalb werden die Werte zur Laufzeit gelesen.
 */
export type Metric =
  | 'power' | 'hr' | 'cadence' | 'speed' | 'altitude' | 'score'
  | 'fitness' | 'fatigue' | 'form' | 'temp' | 'wind';

const FALLBACK: Record<string, string> = {
  '--c-power': '#a78bfa',
  '--c-hr': '#f87171',
  '--c-cadence': '#2dd4bf',
  '--c-speed': '#60a5fa',
  '--c-altitude': '#6b7280',
  '--c-score': '#fbbf24',
  '--c-fitness': '#38bdf8',
  '--c-fatigue': '#f472b6',
  '--c-form': '#e5e7eb',
  '--c-temp': '#fb923c',
  '--c-wind': '#67e8f9',
  '--text-muted': '#8d96a5',
  '--text': '#e7e9ee',
  '--grid': 'rgba(255,255,255,0.06)',
  '--surface': '#151a22',
  '--surface-3': '#232a36',
  '--border-strong': '#343d4c',
  '--accent': '#c5f04a'
};

const cache = new Map<string, string>();

export function token(name: string): string {
  const hit = cache.get(name);
  if (hit) return hit;
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
    || FALLBACK[name] || '#888';
  cache.set(name, value);
  return value;
}

export function metricColor(metric: Metric): string {
  const key = metric === 'hr' ? 'hr' : metric;
  return token(`--c-${key}`);
}

/** Hex-Farbe (#rrggbb) mit Deckkraft als rgba(). */
export function alpha(hex: string, a: number): string {
  const m = /^#([0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return hex;
  const n = parseInt(m[1], 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

/** Farbe für einen KI-Score 1–10. */
export function scoreColor(score: number | null | undefined): string {
  if (score == null) return token('--text-muted');
  if (score >= 8) return '#4ade80';
  if (score >= 6) return '#fbbf24';
  return '#f87171';
}

import { StreamDataDto, ZoneBandDto } from '../../api/report';

/**
 * Färbt den Fahrtverlauf ein. Gleiche Regeln wie im report-service (RouteColoring.java), damit Karte im
 * Report und Kartenbild in Mail/Discord übereinstimmen: Werte geglättet (±15 Punkte), Leistung nach den
 * Zonen der Fahrt, HF nach den aktuellen HF-Zonen, Tempo in sechs Stufen zwischen 5. und 95. Perzentil.
 */
export type RouteMetric = 'power' | 'hr' | 'speed' | 'single';

export interface RoutePoint {
  lat: number;
  lng: number;
  /** Index in den Streams (für Tooltip-Werte). */
  index: number;
  /** Index in {@link ColoredRoute.colors}. */
  color: number;
}

export interface LegendEntry {
  color: string;
  label: string;
}

export interface ColoredRoute {
  points: RoutePoint[];
  colors: string[];
  legendTitle: string | null;
  legend: LegendEntry[];
}

const SMOOTH_HALF_WINDOW = 15;
const NO_VALUE_COLOR = '#cfd8dc';
const SINGLE_COLOR = '#c5f04a';
const SPEED_COLORS = ['#3b82f6', '#06b6d4', '#22c55e', '#eab308', '#f97316', '#ef4444'];

export const METRIC_LABELS: Record<RouteMetric, string> = {
  power: 'Leistung',
  hr: 'Herzfrequenz',
  speed: 'Tempo',
  single: 'Einfarbig'
};

/** Welche Einfärbungen für diese Fahrt möglich sind (in Anzeige-Reihenfolge). */
export function availableMetrics(streams: StreamDataDto, powerBands: ZoneBandDto[], hrBands: ZoneBandDto[]): RouteMetric[] {
  const out: RouteMetric[] = [];
  if (hasAny(streams.watts) && powerBands.length) out.push('power');
  if (hasAny(streams.heartrate) && hrBands.length) out.push('hr');
  if (hasAny(streams.velocity)) out.push('speed');
  out.push('single');
  return out;
}

/** Hat die Fahrt mindestens zwei gültige GPS-Punkte? */
export function hasRoute(streams: StreamDataDto | null | undefined): boolean {
  if (!streams?.lat?.length || !streams.lng?.length) return false;
  let count = 0;
  const n = Math.min(streams.lat.length, streams.lng.length);
  for (let i = 0; i < n && count < 2; i++) {
    if (validCoordinate(streams.lat[i], streams.lng[i])) count++;
  }
  return count >= 2;
}

export function colorRoute(streams: StreamDataDto, metric: RouteMetric,
                           powerBands: ZoneBandDto[], hrBands: ZoneBandDto[]): ColoredRoute {
  const n = Math.min(streams.lat?.length ?? 0, streams.lng?.length ?? 0);
  switch (metric) {
    case 'power':
      return byZones(streams, n, smooth(streams.watts, n), powerBands, 'Leistungszonen', 'W');
    case 'hr':
      return byZones(streams, n, smooth(streams.heartrate, n), hrBands, 'Herzfrequenzzonen', 'bpm');
    case 'speed':
      return bySpeed(streams, n, smooth(streams.velocity, n));
    default:
      return build(streams, n, () => 0, [SINGLE_COLOR], null, []);
  }
}

function byZones(streams: StreamDataDto, n: number, values: (number | null)[], bands: ZoneBandDto[],
                 title: string, unit: string): ColoredRoute {
  const colors = bands.map(b => b.color);
  const legend = bands.map((b, z) => ({
    color: b.color,
    label: `${zoneLabel(b.name, z)} (${b.upper == null ? `≥ ${b.lower}` : `${b.lower}–${b.upper}`} ${unit})`
  }));
  const none = colors.length;
  colors.push(NO_VALUE_COLOR);
  return build(streams, n, i => {
    const v = values[i];
    return v == null ? none : zoneIndex(bands, v);
  }, colors, title, legend);
}

function zoneIndex(bands: ZoneBandDto[], value: number): number {
  for (let z = 0; z < bands.length; z++) {
    const b = bands[z];
    if (value >= b.lower && (b.upper == null || value < b.upper)) return z;
  }
  return bands.length && value < bands[0].lower ? 0 : bands.length - 1;
}

function zoneLabel(name: string, index: number): string {
  const prefix = `Z${index + 1}`;
  if (!name?.trim()) return prefix;
  return /^z[0-9]/i.test(name) ? name : `${prefix} ${name}`;
}

function bySpeed(streams: StreamDataDto, n: number, velocity: (number | null)[]): ColoredRoute {
  const kmh = velocity.filter((v): v is number => v != null).map(v => v * 3.6).sort((a, b) => a - b);
  const lo = percentile(kmh, 0.05);
  const hi = percentile(kmh, 0.95);
  const steps = SPEED_COLORS.length;
  const step = (hi - lo) / steps;
  const colors = [...SPEED_COLORS, NO_VALUE_COLOR];
  const none = SPEED_COLORS.length;
  const fmt = (v: number) => v.toFixed(0);

  if (step < 0.5) {
    return build(streams, n, i => velocity[i] == null ? none : 2, colors, 'Tempo',
      [{ color: SPEED_COLORS[2], label: `${fmt(lo)} km/h` }]);
  }
  const legend = SPEED_COLORS.map((color, s) => ({
    color,
    label: s === 0 ? `< ${fmt(lo + step)} km/h`
      : s === steps - 1 ? `≥ ${fmt(lo + step * s)} km/h`
        : `${fmt(lo + step * s)}–${fmt(lo + step * (s + 1))} km/h`
  }));
  return build(streams, n, i => {
    const v = velocity[i];
    if (v == null) return none;
    return Math.max(0, Math.min(steps - 1, Math.floor((v * 3.6 - lo) / step)));
  }, colors, 'Tempo', legend);
}

function build(streams: StreamDataDto, n: number, colorOf: (i: number) => number, colors: string[],
               legendTitle: string | null, legend: LegendEntry[]): ColoredRoute {
  const points: RoutePoint[] = [];
  for (let i = 0; i < n; i++) {
    const lat = streams.lat[i];
    const lng = streams.lng[i];
    if (!validCoordinate(lat, lng)) continue;
    points.push({ lat, lng, index: i, color: colorOf(i) });
  }
  return { points, colors, legendTitle, legend };
}

/** intervals.icu liefert bei fehlendem Fix teils null oder 0/0. */
function validCoordinate(lat: number | null | undefined, lng: number | null | undefined): boolean {
  if (lat == null || lng == null) return false;
  if (lat === 0 && lng === 0) return false;
  return lat >= -85 && lat <= 85 && lng >= -180 && lng <= 180;
}

/** Gleitender Mittelwert; Lücken (null) zählen nicht mit. */
function smooth(values: (number | null)[] | null | undefined, n: number): (number | null)[] {
  const out: (number | null)[] = new Array(n).fill(null);
  if (!values) return out;
  const size = Math.min(n, values.length);
  const prefix = new Float64Array(size + 1);
  const counts = new Int32Array(size + 1);
  for (let i = 0; i < size; i++) {
    const v = values[i];
    prefix[i + 1] = prefix[i] + (v ?? 0);
    counts[i + 1] = counts[i] + (v != null ? 1 : 0);
  }
  for (let i = 0; i < size; i++) {
    const from = Math.max(0, i - SMOOTH_HALF_WINDOW);
    const to = Math.min(size, i + SMOOTH_HALF_WINDOW + 1);
    const c = counts[to] - counts[from];
    out[i] = c > 0 ? (prefix[to] - prefix[from]) / c : null;
  }
  return out;
}

function percentile(sorted: number[], p: number): number {
  if (!sorted.length) return 0;
  const idx = Math.round(p * (sorted.length - 1));
  return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
}

function hasAny(values: (number | null)[] | null | undefined): boolean {
  return !!values?.some(v => v != null);
}

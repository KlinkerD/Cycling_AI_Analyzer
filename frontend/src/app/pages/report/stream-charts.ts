import { ChartData, ChartOptions, Scale } from 'chart.js';
import { StreamDataDto, WeatherPointDto } from '../../api/report';
import { ZoomGroup } from '../../shared/charts/zoom-group';
import { SparkPoint } from '../../shared/charts/range-slider.component';
import { alpha, metricColor, token } from '../../shared/theme';
import { formatClock } from '../../shared/format';

export type Pt = { x: number; y: number };
type LineData = ChartData<'line', Pt[]>;

export interface StreamChart {
  key: 'speed' | 'weather' | 'powerHr' | 'cadence';
  title: string;
  /** Kurzer Hinweis neben der Legende, z. B. die Regenmenge. */
  note?: string;
  height: number;
  data: LineData;
  options: ChartOptions<'line'>;
}

export interface StreamCharts {
  axis: 'distance' | 'time';
  charts: StreamChart[];
  spark: SparkPoint[];
  sparkColor: string;
}

/** Zeitabschnitt der Fahrt (Sekunden laut time-Stream), der im Leistungs-Chart hinterlegt wird. */
export interface TimeHighlight {
  startSeconds: number;
  durationSeconds: number;
  label: string;
}

/** Feste Breite der Y-Achsen, damit die gestapelten Charts exakt übereinander liegen. */
const AXIS_WIDTH = 48;

/**
 * Baut die gestapelten Verlaufs-Charts (Speed+Höhe, Wetter, Leistung+HF, Kadenz) aus den Rohdaten.
 * Alle teilen sich eine lineare X-Achse; Chart.js dünnt je Zoomstufe aus (LTTB).
 * {@code highlight} (z. B. der erkannte FTP-Test) wird im Leistungs-Chart farbig hinterlegt.
 * {@code weather} (alle 10 Minuten, nur Outdoor) ergibt den Temperatur-/Wind-Chart.
 */
export function buildStreamCharts(streams: StreamDataDto, group: ZoomGroup,
                                  highlight: TimeHighlight | null = null,
                                  weather: WeatherPointDto[] = []): StreamCharts | null {
  const distance = streams.distance ?? [];
  const time = streams.time ?? [];
  const useDistance = range(distance) > 100; // > 100 m Bewegung, sonst (Rolle ohne Distanz) Zeit
  const xs = useDistance ? distance.map(d => d / 1000) : time;
  if (xs.length < 2) return null;

  const axis: 'distance' | 'time' = useDistance ? 'distance' : 'time';
  const xMin = firstNum(xs);
  const xMax = lastNum(xs);
  if (xMin == null || xMax == null || xMax <= xMin) return null;
  group.setBounds(xMin, xMax);
  const highlightX = highlight ? timeToX(time, xs, highlight) : null;

  const series = (src: number[] | undefined, transform = (v: number) => v, smooth = 1): Pt[] => {
    if (!src?.length) return [];
    const values = smooth > 1 ? rollingMean(src, smooth) : src;
    const out: Pt[] = [];
    for (let i = 0; i < xs.length && i < values.length; i++) {
      const x = xs[i];
      const v = values[i];
      if (x == null || v == null || isNaN(v) || isNaN(x)) continue;
      out.push({ x, y: transform(v) });
    }
    return out;
  };

  const speed = series(streams.velocity, v => v * 3.6, 5);
  const altitude = range(streams.altitude ?? []) >= 2 ? series(streams.altitude) : [];
  const power = series(streams.watts, v => v, 3);
  const hr = series(streams.heartrate);
  const cadence = series(streams.cadence);

  const charts: StreamChart[] = [];

  if (speed.length) {
    const datasets: LineData['datasets'] = [];
    if (altitude.length) {
      datasets.push(line('Höhe', altitude, token('--c-altitude'), 'yRight', { fill: 'start', fillAlpha: 0.22, width: 1, order: 2 }));
    }
    datasets.push(line('Geschwindigkeit', speed, metricColor('speed'), 'yLeft', { order: 1 }));
    charts.push({
      key: 'speed',
      title: 'Geschwindigkeit' + (altitude.length ? ' & Höhe' : ''),
      height: 190,
      data: { datasets },
      options: streamOptions(group, axis, {
        left: { label: 'km/h', color: metricColor('speed') },
        right: altitude.length ? { label: 'm', color: token('--text-muted'), min: minOf(altitude) } : null
      }, (label, v) => label === 'Höhe' ? `${v.toFixed(0)} m` : `${v.toFixed(1)} km/h`)
    });
  }

  const weatherChart = buildWeatherChart(weather, axis, group);
  if (weatherChart) charts.push(weatherChart);

  if (power.length || hr.length) {
    const datasets: LineData['datasets'] = [];
    if (power.length) datasets.push(line('Leistung', power, metricColor('power'), 'yLeft', { fill: 'start', fillAlpha: 0.14, order: 2 }));
    if (hr.length) datasets.push(line('Herzfrequenz', hr, metricColor('hr'), 'yRight', { order: 1 }));
    const options = streamOptions(group, axis, {
      left: power.length ? { label: 'W', color: metricColor('power'), min: 0 } : null,
      right: hr.length ? { label: 'bpm', color: metricColor('hr') } : null
    }, (label, v) => label === 'Leistung' ? `${v.toFixed(0)} W` : `${v.toFixed(0)} bpm`);
    if (highlightX && highlight) {
      options.plugins!.highlightRange = {
        ...highlightX,
        color: alpha(metricColor('power'), 0.16),
        label: highlight.label
      };
    }
    charts.push({
      key: 'powerHr',
      title: [power.length ? 'Leistung' : '', hr.length ? 'Herzfrequenz' : ''].filter(Boolean).join(' & '),
      height: 230,
      data: { datasets },
      options
    });
  }

  if (cadence.length && cadence.some(p => p.y > 0)) {
    charts.push({
      key: 'cadence',
      title: 'Kadenz',
      height: 150,
      data: { datasets: [line('Kadenz', cadence, metricColor('cadence'), 'yLeft', { fill: 'start', fillAlpha: 0.1 })] },
      options: streamOptions(group, axis, {
        left: { label: 'rpm', color: metricColor('cadence'), min: 0 },
        right: null
      }, (_l, v) => `${v.toFixed(0)} rpm`)
    });
  }

  if (!charts.length) return null;

  // Nur der unterste Chart zeigt die X-Beschriftung
  charts.forEach((c, i) => {
    const x = c.options.scales!['x']!;
    (x.ticks as { display?: boolean }).display = i === charts.length - 1;
    (x as { title?: { display?: boolean } }).title!.display = i === charts.length - 1;
  });

  const sparkSource = power.length ? power : speed.length ? speed : hr;
  const sparkColor = power.length ? metricColor('power') : speed.length ? metricColor('speed') : metricColor('hr');
  return { axis, charts, spark: sparkSource, sparkColor };
}

/** Wetterpunkt mit Windrichtung fürs Tooltip. */
type WeatherPt = Pt & { dir?: number | null };

/**
 * Temperatur (links) und Wind (rechts, Böen gestrichelt) alle 10 Minuten auf derselben X-Achse wie die
 * anderen Charts. Windrichtung nur im Tooltip, Regenmenge als Hinweis. Null bei weniger als zwei Temperaturen.
 */
function buildWeatherChart(weather: WeatherPointDto[], axis: 'distance' | 'time', group: ZoomGroup): StreamChart | null {
  const x = (w: WeatherPointDto): number | null =>
    axis === 'distance' ? (w.distanceKm ?? null) : w.minuteOfRide * 60;
  const series = (value: (w: WeatherPointDto) => number | null | undefined): WeatherPt[] => {
    const out: WeatherPt[] = [];
    for (const w of weather) {
      const xv = x(w);
      const v = value(w);
      if (xv == null || v == null) continue;
      out.push({ x: xv, y: v, dir: w.windDirectionDeg });
    }
    return out;
  };

  const temp = series(w => w.temperatureC);
  if (temp.length < 2) return null;
  const wind = series(w => w.windSpeedKmh);
  const gust = series(w => w.windGustKmh);

  const tempColor = metricColor('temp');
  const windColor = metricColor('wind');
  const datasets: LineData['datasets'] = [
    { ...line('Temperatur', temp, tempColor, 'yLeft', { width: 2, order: 1 }), pointRadius: 2 }
  ];
  if (wind.length) {
    datasets.push({ ...line('Wind', wind, windColor, 'yRight', { order: 2 }), pointRadius: 2 });
  }
  if (gust.length) {
    datasets.push({ ...line('Böen', gust, alpha(windColor, 0.7), 'yRight', { width: 1, order: 3 }), borderDash: [5, 4] });
  }

  const options = streamOptions(group, axis, {
    left: { label: '°C', color: tempColor },
    right: wind.length ? { label: 'km/h', color: windColor, min: 0 } : null
  }, (label, v) => label === 'Temperatur' ? `${formatDecimal(v, 1)} °C` : `${v.toFixed(0)} km/h`);
  options.plugins!.tooltip!.callbacks!.label = item => {
    const raw = item.raw as WeatherPt;
    const v = item.parsed.y ?? 0;
    if (item.dataset.label === 'Temperatur') return ` Temperatur: ${formatDecimal(v, 1)} °C`;
    const from = item.dataset.label === 'Wind' && raw.dir != null ? `${compass(raw.dir)}, ` : '';
    return ` ${item.dataset.label}: ${from}${v.toFixed(0)} km/h`;
  };

  return {
    key: 'weather',
    title: 'Temperatur' + (wind.length ? ' & Wind' : ''),
    note: rainNote(weather),
    height: 160,
    data: { datasets },
    options
  };
}

/** "Regen: 1,2 mm" aus Intensität mal Abstand zum vorherigen Punkt; undefined ohne Regen. */
function rainNote(weather: WeatherPointDto[]): string | undefined {
  let mm = 0;
  for (let i = 1; i < weather.length; i++) {
    const rate = weather[i].precipitationMmPerHour;
    if (rate != null) mm += rate * (weather[i].minuteOfRide - weather[i - 1].minuteOfRide) / 60;
  }
  return mm >= 0.05 ? `Regen: ${formatDecimal(mm, 1)} mm` : undefined;
}

/** Himmelsrichtung, aus der der Wind kommt (8 Sektoren). */
function compass(deg: number): string {
  const names = ['N', 'NO', 'O', 'SO', 'S', 'SW', 'W', 'NW'];
  return names[Math.round((((deg % 360) + 360) % 360) / 45) % 8];
}

function formatDecimal(v: number, digits: number): string {
  return v.toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

function line(
  label: string, data: Pt[], color: string, yAxisID: string,
  o: { fill?: 'start' | false; fillAlpha?: number; width?: number; order?: number } = {}
): LineData['datasets'][number] {
  return {
    label,
    data,
    parsing: false,
    normalized: true,
    borderColor: color,
    backgroundColor: alpha(color, o.fillAlpha ?? 0),
    borderWidth: o.width ?? 1.4,
    pointRadius: 0,
    pointHoverRadius: 3,
    pointHoverBackgroundColor: color,
    fill: o.fill ?? false,
    tension: 0,
    spanGaps: true,
    yAxisID,
    order: o.order ?? 1
  };
}

interface AxisSpec {
  label: string;
  color: string;
  min?: number;
}

function streamOptions(
  group: ZoomGroup,
  axis: 'distance' | 'time',
  y: { left: AxisSpec | null; right: AxisSpec | null },
  formatValue: (label: string, v: number) => string
): ChartOptions<'line'> {
  const fixWidth = (scale: Scale) => { scale.width = AXIS_WIDTH; };
  const yAxis = (spec: AxisSpec | null, position: 'left' | 'right') => spec
    ? {
        type: 'linear' as const,
        position,
        min: spec.min,
        afterFit: fixWidth,
        grid: { display: position === 'left', color: token('--grid') },
        border: { display: false },
        ticks: { color: spec.color, maxTicksLimit: 5 },
        title: { display: true, text: spec.label, color: spec.color, padding: 0, font: { size: 10 } }
      }
    : {
        // leere Achse hält den Platz frei, damit alle Charts gleich breit sind
        type: 'linear' as const,
        position,
        display: true,
        afterFit: fixWidth,
        grid: { display: false },
        border: { display: false },
        ticks: { display: false }
      };

  return {
    interaction: { mode: 'nearest', axis: 'x', intersect: false },
    layout: { padding: { top: 4 } },
    plugins: {
      ...(group.pluginOptions() as object),
      legend: { display: false },
      decimation: { enabled: true, algorithm: 'lttb', samples: 800, threshold: 1200 },
      tooltip: {
        mode: 'nearest',
        axis: 'x',
        intersect: false,
        callbacks: {
          title: items => items.length ? formatX(items[0].parsed.x ?? 0, axis) : '',
          label: item => ` ${item.dataset.label}: ${formatValue(item.dataset.label ?? '', item.parsed.y ?? 0)}`
        }
      }
    },
    scales: {
      x: {
        type: 'linear',
        bounds: 'data',
        grid: { display: false },
        border: { color: token('--border-strong') },
        ticks: {
          maxTicksLimit: 10,
          maxRotation: 0,
          callback: v => formatX(Number(v), axis, true)
        },
        title: { display: true, text: axis === 'distance' ? 'Distanz (km)' : 'Zeit', padding: 0 }
      },
      yLeft: yAxis(y.left, 'left'),
      yRight: yAxis(y.right, 'right')
    }
  } as ChartOptions<'line'>;
}

function formatX(v: number, axis: 'distance' | 'time', short = false): string {
  if (axis === 'time') return formatClock(v);
  const digits = short ? (v >= 10 ? 0 : 1) : 2;
  return v.toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits }) + (short ? '' : ' km');
}

/**
 * Rechnet einen Zeitabschnitt in X-Achsen-Werte um: bei Zeit-Achse direkt, bei Distanz-Achse ueber
 * die erste Probe ab Start bzw. Ende des Abschnitts.
 */
function timeToX(time: number[], xs: number[], h: TimeHighlight): { from: number; to: number } | null {
  const start = h.startSeconds;
  const end = h.startSeconds + h.durationSeconds;
  let from: number | null = null;
  let to: number | null = null;
  for (let i = 0; i < time.length && i < xs.length; i++) {
    const t = time[i];
    const x = xs[i];
    if (t == null || x == null || isNaN(x)) continue;
    if (from == null && t >= start) from = x;
    if (t <= end) to = x;
  }
  return from != null && to != null && to > from ? { from, to } : null;
}

function rollingMean(src: number[], window: number): (number | null)[] {
  const out: (number | null)[] = new Array(src.length);
  let sum = 0, n = 0;
  const half = Math.floor(window / 2);
  // einfacher zentrierter Mittelwert, null-Werte zählen nicht mit
  const vals = src.map(v => (v == null || isNaN(v) ? null : v));
  for (let i = 0; i < vals.length; i++) {
    sum = 0; n = 0;
    for (let j = Math.max(0, i - half); j <= Math.min(vals.length - 1, i + half); j++) {
      const v = vals[j];
      if (v != null) { sum += v; n++; }
    }
    out[i] = vals[i] == null ? null : (n ? sum / n : null);
  }
  return out;
}

function range(src: number[]): number {
  let min = Infinity, max = -Infinity;
  for (const v of src) {
    if (v == null || isNaN(v)) continue;
    if (v < min) min = v;
    if (v > max) max = v;
  }
  return max >= min ? max - min : 0;
}

function minOf(pts: Pt[]): number {
  let min = Infinity;
  for (const p of pts) if (p.y < min) min = p.y;
  return Math.floor(min);
}

function firstNum(src: number[]): number | null {
  for (const v of src) if (v != null && !isNaN(v)) return v;
  return null;
}

function lastNum(src: number[]): number | null {
  for (let i = src.length - 1; i >= 0; i--) {
    const v = src[i];
    if (v != null && !isNaN(v)) return v;
  }
  return null;
}

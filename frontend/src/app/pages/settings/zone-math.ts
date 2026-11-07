/**
 * Zonen-Rechnung der Einstellungsseite, ohne Angular. Gespeichert werden nur die Obergrenzen in Prozent
 * (Leistung: % FTP, HF: % LTHR); Watt und bpm werden daraus abgeleitet.
 *
 * Darstellung wie bei intervals.icu: Bereiche überlappen nicht (Z2 = 56 %–75 %, 138–187 W), die letzte
 * Zone ist nach oben offen ("151 % +", "376 W+").
 */

/** Ein Bereich in Watt oder bpm; {@code upper} null = nach oben offen. */
export interface ValueRange {
  lower: number;
  upper: number | null;
}

export interface ZoneRow {
  /** 1-basierte Nummer; 8 = Sweet Spot */
  index: number;
  label: string;
  name: string;
  color: string;
  lowerPct: number;
  /** null = nach oben offen */
  upperPct: number | null;
  primary: ValueRange | null;
  secondary: ValueRange | null;
}

export interface ZoneMeta {
  name: string;
  color: string;
}

/** Kleiner Puffer gegen Rundungsfehler (90 * 250 / 100 soll 225 bleiben, nicht 224,999…). */
const EPS = 1e-9;

/** Watt-Grenze wie intervals.icu: abgerundet. */
function watts(pct: number, ftp: number): number {
  return Math.floor((pct * ftp) / 100 + EPS);
}

/** bpm-Grenze wie im Backend gespeichert: kaufmännisch gerundet. */
export function bpm(pct: number, lthr: number): number {
  return Math.round((pct * lthr) / 100 + EPS);
}

/** Watt-Bereiche der regulären Zonen; Z1 beginnt bei 1 W, die letzte ist offen. */
function powerRanges(limits: number[], ftp: number | null): ValueRange[] | null {
  if (!ftp) return null;
  const out: ValueRange[] = [];
  for (let i = 0; i <= limits.length; i++) {
    const lower = i === 0 ? 1 : watts(limits[i - 1], ftp) + 1;
    const upper = i < limits.length ? watts(limits[i], ftp) : null;
    out.push({ lower, upper });
  }
  return out;
}

/** Prozent-Untergrenze einer Zone: 0 für Z1, sonst die vorige Obergrenze + 1. */
function lowerPct(limits: number[], i: number): number {
  return i === 0 ? 0 : limits[i - 1] + 1;
}

/**
 * Leistungszonen: {@code limits} sind die Obergrenzen Z1..Z(n-1) in % FTP, die letzte Zone ist offen.
 * Sweet Spot kommt als eigene Zeile ans Ende, wenn beide Grenzen gesetzt sind.
 */
export function powerZoneRows(limits: number[], meta: ZoneMeta[], ftp: number | null, indoorFtp: number | null,
                              ssMin: number | null, ssMax: number | null, ssMeta?: ZoneMeta): ZoneRow[] {
  const outdoor = powerRanges(limits, ftp);
  const indoor = powerRanges(limits, indoorFtp);
  const rows: ZoneRow[] = [];
  for (let i = 0; i <= limits.length; i++) {
    rows.push({
      index: i + 1,
      label: 'Z' + (i + 1),
      name: meta[i]?.name ?? 'Z' + (i + 1),
      color: meta[i]?.color ?? '#8d96a5',
      lowerPct: lowerPct(limits, i),
      upperPct: i < limits.length ? limits[i] : null,
      primary: outdoor?.[i] ?? null,
      secondary: indoor?.[i] ?? null
    });
  }
  if (ssMin != null && ssMax != null) {
    const range = (ref: number | null): ValueRange | null =>
      ref ? { lower: watts(ssMin, ref), upper: watts(ssMax, ref) } : null;
    rows.push({
      index: 8,
      label: 'SS',
      name: ssMeta?.name ?? 'Sweet Spot',
      color: ssMeta?.color ?? '#66bb6a',
      lowerPct: ssMin,
      upperPct: ssMax,
      primary: range(ftp),
      secondary: range(indoorFtp)
    });
  }
  return rows;
}

/** HF-Zonen: {@code limits} sind die Obergrenzen Z1..Z(n-1) in % LTHR, die letzte Zone ist offen. */
export function hrZoneRows(limits: number[], meta: ZoneMeta[], lthr: number | null): ZoneRow[] {
  const rows: ZoneRow[] = [];
  for (let i = 0; i <= limits.length; i++) {
    let range: ValueRange | null = null;
    if (lthr) {
      const lower = i === 0 ? 0 : bpm(limits[i - 1], lthr) + 1;
      range = { lower, upper: i < limits.length ? bpm(limits[i], lthr) : null };
    }
    rows.push({
      index: i + 1,
      label: 'Z' + (i + 1),
      name: meta[i]?.name ?? 'Z' + (i + 1),
      color: meta[i]?.color ?? '#8d96a5',
      lowerPct: lowerPct(limits, i),
      upperPct: i < limits.length ? limits[i] : null,
      primary: range,
      secondary: null
    });
  }
  return rows;
}

/** "56 % – 75 %" bzw. "151 % +" */
export function formatPctRange(row: ZoneRow): string {
  return row.upperPct == null ? `${row.lowerPct} % +` : `${row.lowerPct} % – ${row.upperPct} %`;
}

/** "138 – 187 W" bzw. "376 W+" */
export function formatValueRange(range: ValueRange | null, unit: string): string {
  if (!range) return '–';
  return range.upper == null ? `${range.lower} ${unit}+` : `${range.lower} – ${range.upper} ${unit}`;
}

/**
 * Fehlertext für eine Liste von Obergrenzen; null, wenn sie passt. Gleiche Regeln wie im Backend
 * (IntervalsThresholdsUpdate): ganze Zahlen im Bereich, streng aufsteigend.
 */
export function limitsProblem(label: string, limits: (number | null)[], min: number, max: number,
                              unit: string): string | null {
  for (let i = 0; i < limits.length; i++) {
    const v = limits[i];
    if (v == null || !Number.isInteger(v) || v < min || v > max) {
      return `${label}: Grenze von Z${i + 1} muss eine ganze Zahl zwischen ${min} und ${max} ${unit} sein.`;
    }
    const prev = limits[i - 1];
    if (i > 0 && prev != null && v <= prev) {
      return `${label}: Grenze von Z${i + 1} muss über der von Z${i} liegen.`;
    }
  }
  return null;
}

/** bpm-Grenzen nach dem Runden: müssen weiter aufsteigen. Null, wenn alles passt. */
export function hrBpmProblem(limits: number[], lthr: number | null): string | null {
  if (!lthr || !limits.length) return null;
  const values = limits.map(p => bpm(p, lthr));
  for (let i = 1; i < values.length; i++) {
    if (values[i] <= values[i - 1]) {
      return `HF-Zonen: Z${i} und Z${i + 1} ergeben dieselbe Grenze (${values[i]} bpm).`;
    }
  }
  return null;
}

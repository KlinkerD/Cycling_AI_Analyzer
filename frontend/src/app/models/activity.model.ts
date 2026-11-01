export interface AthleteThresholds {
  name: string | null;
  ftp: number | null;
  /** Indoor-FTP aus intervals.icu; null, wenn nicht eingetragen. */
  indoorFtp: number | null;
  lthr: number | null;
  maxHr: number | null;
  weight: number | null;
}

export interface Activity {
  id: string;
  name: string;
  startDateLocal: string;
  distance: number;
  movingTime: number;
  elapsedTime: number;
  averageWatts: number | null;
  maxWatts: number | null;
  averageHeartrate: number | null;
  maxHeartrate: number | null;
  averageSpeed: number | null;
  totalElevationGain: number | null;
  type: string;
}

export interface ZoneDistribution {
  zoneName: string;
  zoneLabel: string;
  seconds: number;
  percentage: number;
  color: string;
}

export interface TrendData {
  activityId: string;
  activityName: string;
  activityDate: string;
  avgPowerWatts: number | null;
  avgHeartRate: number | null;
  distanceKm: number | null;
  avgSpeedKmh: number | null;
  overallScore: number | null;
}

export interface ActivityAnalysis {
  id: number;
  activityId: string;
  activityName: string;
  activityDate: string;
  distanceKm: number;
  durationSeconds: number;
  avgPowerWatts: number | null;
  maxPowerWatts: number | null;
  normalizedPowerWatts: number | null;
  intensityFactor: number | null;
  trainingLoad: number | null;
  /** Indoor-Fahrt; null bei Analysen, die noch nicht nachgetragen wurden. */
  indoor: boolean | null;
  /** FTP in Watt, mit der IF/TSS berechnet wurden. */
  ftpUsed: number | null;
  avgHeartRate: number | null;
  maxHeartRate: number | null;
  avgSpeedKmh: number | null;
  elevationGainM: number | null;
  avgCadenceRpm: number | null;
  overallScore: number;
  strengths: string;
  anomalies: string;
  progress: string;
  summary: string;
  analyzedAt: string;
}

export interface StreamData {
  distance: number[];
  watts: number[];
  heartrate: number[];
  velocity: number[];
  altitude: number[];
  time: number[];
  cadence: number[];
}

export interface Report {
  analysis: ActivityAnalysis;
  streams: StreamData;
  powerZones: ZoneDistribution[];
  hrZones: ZoneDistribution[];
  trend: TrendData[];
  powerCurveSet: PowerCurveSet | null;
}

export interface PowerCurvePoint {
  durationSeconds: number;
  watts: number | null;
}

export interface PowerCurve {
  activityId: string;
  activityName: string;
  activityDate: string;
  points: PowerCurvePoint[];
}

export interface PowerCurveSet {
  current: PowerCurve;
  /** Neueste-zuerst sortiert (max. 3 Eintraege). */
  previous: PowerCurve[];
  ftpWatts: number | null;
}

// ---------------------------------------------------------------------------
// Overview-Seite
// ---------------------------------------------------------------------------

export interface WellnessPoint {
  date: string;     // YYYY-MM-DD
  ctl: number | null;
  atl: number | null;
  form: number | null;
}

export interface DailyRecommendation {
  category: string;        // REST | EASY | ENDURANCE | TEMPO | HARD_INTERVALS | INSUFFICIENT_DATA
  categoryLabel: string;
  color: string;
  text: string;
  form: number | null;
  rampRate: number | null;
  rampCapped: boolean;
  refDate: string | null;      // YYYY-MM-DD
  computedAt: string | null;   // ISO datetime
}

export interface Zone {
  index: number;
  name: string;
  lowerPct: number | null;
  upperPct: number | null;
  lowerValue: number | null;
  upperValue: number | null;
  color: string;
}

export interface AthleteZones {
  thresholds: AthleteThresholds;
  powerZones: Zone[];
  hrZones: Zone[];
  /** "lthr" oder "max_hr" - Referenz fuer die HF-Zonen-Prozente */
  hrReference: 'lthr' | 'max_hr';
}


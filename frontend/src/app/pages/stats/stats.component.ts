import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions, TooltipItem } from 'chart.js';
import { StatsDto, StatsPeriodDto, SyncStatusDto, ZoneTimeDto } from '../../api/stats';
import { StatsGrouping, StatsService } from '../../services/stats.service';
import { formatDuration } from '../../shared/format';
import { alpha, token } from '../../shared/theme';

type PresetKey = 'ytd' | 'lastYear' | '12m' | 'custom';
type ChartMetric = 'distance' | 'time' | 'load' | 'elevation' | 'power' | 'hr';

interface Kpi {
  key: string;
  label: string;
  value: string;
  unit: string;
  metric: string;
  /** Veränderung zum Vorjahreszeitraum in Prozent; null, wenn nicht sinnvoll */
  delta: number | null;
  tooltip: string;
}

/** HF-Zonen haben in intervals.icu keine Farben: von ruhig (grau/blau) bis rot. */
const HR_COLORS = ['#6b7280', '#60a5fa', '#4ade80', '#fbbf24', '#fb923c', '#f87171', '#dc2626'];
const MONTHS = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];

@Component({
  selector: 'app-stats',
  standalone: true,
  imports: [
    DecimalPipe, NgTemplateOutlet, FormsModule,
    MatButtonModule, MatButtonToggleModule, MatIconModule, MatProgressSpinnerModule,
    MatTooltipModule, MatFormFieldModule, MatInputModule, MatDatepickerModule,
    BaseChartDirective
  ],
  templateUrl: './stats.component.html',
  styleUrl: './stats.component.scss'
})
export class StatsComponent implements OnInit {
  private readonly statsService = inject(StatsService);

  readonly presets: { key: Exclude<PresetKey, 'custom'>; label: string }[] = [
    { key: 'ytd', label: 'Dieses Jahr' },
    { key: 'lastYear', label: 'Letztes Jahr' },
    { key: '12m', label: '12 Monate' }
  ];
  readonly groupings: { key: StatsGrouping; label: string }[] = [
    { key: 'WEEK', label: 'Woche' },
    { key: 'MONTH', label: 'Monat' },
    { key: 'YEAR', label: 'Jahr' }
  ];
  readonly chartMetrics: { key: ChartMetric; label: string }[] = [
    { key: 'distance', label: 'km' },
    { key: 'time', label: 'Zeit' },
    { key: 'load', label: 'TSS' },
    { key: 'elevation', label: 'Hm' },
    { key: 'power', label: 'Leistungszonen' },
    { key: 'hr', label: 'HF-Zonen' }
  ];

  preset: PresetKey = 'ytd';
  from: Date = startOfYear(new Date());
  to: Date = today();
  groupBy: StatsGrouping = 'MONTH';
  readonly chartMetric = signal<ChartMetric>('distance');

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly current = signal<StatsDto | null>(null);
  readonly previous = signal<StatsDto | null>(null);
  readonly status = signal<SyncStatusDto | null>(null);

  readonly kpis = computed<Kpi[]>(() => {
    const cur = this.current()?.total;
    if (!cur) return [];
    const prev = this.previous()?.total ?? null;
    return [
      {
        key: 'rides', label: 'Fahrten', value: String(cur.rides), unit: '', metric: 'score',
        delta: change(cur.rides, prev?.rides), tooltip: 'Anzahl Rad-Fahrten (draußen und Rolle)'
      },
      {
        key: 'distance', label: 'Distanz', value: formatNumber(cur.distance / 1000, 0), unit: 'km', metric: 'speed',
        delta: change(cur.distance, prev?.distance), tooltip: 'Gefahrene Kilometer'
      },
      {
        key: 'time', label: 'Fahrzeit', value: formatDuration(cur.movingTime), unit: 'h', metric: 'fitness',
        delta: change(cur.movingTime, prev?.movingTime),
        tooltip: `Bewegungszeit · Gesamtzeit ${formatDuration(cur.elapsedTime)} h`
      },
      {
        key: 'elevation', label: 'Höhenmeter', value: formatNumber(cur.elevationGain, 0), unit: 'm', metric: 'altitude',
        delta: change(cur.elevationGain, prev?.elevationGain), tooltip: 'Positive Höhenmeter'
      },
      {
        key: 'load', label: 'Trainingslast', value: formatNumber(cur.trainingLoad, 0), unit: 'TSS', metric: 'fatigue',
        delta: change(cur.trainingLoad, prev?.trainingLoad), tooltip: 'Summe der Trainingslast laut intervals.icu'
      }
    ];
  });

  readonly powerZones = computed(() => this.current()?.total.powerZones ?? []);
  readonly hrZones = computed(() => this.current()?.total.hrZones ?? []);

  readonly rangeLabel = computed(() => {
    const c = this.current();
    return c ? `${germanDate(c.from)} – ${germanDate(c.to)}` : '';
  });

  readonly previousLabel = computed(() => {
    const p = this.previous();
    return p ? `${germanDate(p.from)} – ${germanDate(p.to)}` : '';
  });

  readonly chartData = computed<ChartData<'bar'>>(() => this.buildChartData());
  readonly chartOptions = computed<ChartOptions<'bar'>>(() => this.buildChartOptions());

  ngOnInit(): void {
    this.load();
  }

  selectPreset(key: Exclude<PresetKey, 'custom'>): void {
    this.preset = key;
    const now = today();
    if (key === 'ytd') {
      this.from = startOfYear(now);
      this.to = now;
      if (this.groupBy === 'YEAR') this.groupBy = 'MONTH';
    } else if (key === 'lastYear') {
      this.from = new Date(now.getFullYear() - 1, 0, 1);
      this.to = new Date(now.getFullYear() - 1, 11, 31);
      if (this.groupBy === 'YEAR') this.groupBy = 'MONTH';
    } else {
      this.from = new Date(now.getFullYear() - 1, now.getMonth(), now.getDate() + 1);
      this.to = now;
    }
    this.load();
  }

  onCustomDateChange(): void {
    if (!this.from || !this.to || this.from > this.to) return;
    this.preset = 'custom';
    this.load();
  }

  selectGrouping(key: StatsGrouping): void {
    this.groupBy = key;
    this.load();
  }

  reload(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      current: this.statsService.getStats(this.from, this.to, this.groupBy),
      previous: this.statsService.getStats(minusYear(this.from), minusYear(this.to), 'NONE'),
      status: this.statsService.getStatus()
    }).subscribe({
      next: ({ current, previous, status }) => {
        this.current.set(current);
        this.previous.set(previous);
        this.status.set(status);
        this.loading.set(false);
      },
      error: e => {
        console.error('Statistik nicht ladbar:', e);
        this.error.set(e?.error?.detail || 'Der stats-service ist nicht erreichbar.');
        this.loading.set(false);
      }
    });
  }

  zoneColor(zone: ZoneTimeDto, index: number, kind: 'power' | 'hr'): string {
    if (zone.color) return zone.color;
    return kind === 'hr' ? HR_COLORS[index % HR_COLORS.length] : token('--c-power');
  }

  /** Breite im gestapelten Balken; sehr kleine Zonen bleiben sichtbar. */
  segmentWidth(zone: ZoneTimeDto): number {
    return Math.max(zone.percentage, 0.6);
  }

  formatDuration(seconds: number): string {
    return formatDuration(seconds);
  }

  deltaLabel(delta: number): string {
    const rounded = Math.round(delta);
    return `${rounded > 0 ? '+' : ''}${formatNumber(rounded === 0 ? 0 : delta, 0)} %`;
  }

  // ---- Chart --------------------------------------------------------------------------------

  private buildChartData(): ChartData<'bar'> {
    const periods = this.current()?.periods ?? [];
    const labels = periods.map(p => this.periodLabel(p));
    const metric = this.chartMetric();

    if (metric === 'power' || metric === 'hr') {
      const zonesOf = (p: StatsPeriodDto) => metric === 'power' ? p.powerZones : p.hrZones;
      const total = this.current()?.total;
      const order = (metric === 'power' ? total?.powerZones : total?.hrZones) ?? [];
      return {
        labels,
        datasets: order.map((zone, i) => {
          const color = this.zoneColor(zone, i, metric);
          return {
            label: zone.label ? `${zone.zone} ${zone.label}` : zone.zone,
            data: periods.map(p => (zonesOf(p).find(z => z.zone === zone.zone)?.seconds ?? 0) / 3600),
            backgroundColor: color,
            hoverBackgroundColor: color,
            borderRadius: 2,
            stack: 'zones'
          };
        })
      };
    }

    const { color, value } = this.metricSpec(metric);
    return {
      labels,
      datasets: [{
        label: this.chartMetrics.find(m => m.key === metric)?.label ?? '',
        data: periods.map(value),
        backgroundColor: alpha(color, 0.75),
        hoverBackgroundColor: color,
        borderRadius: 4,
        maxBarThickness: 48
      }]
    };
  }

  private buildChartOptions(): ChartOptions<'bar'> {
    const metric = this.chartMetric();
    const zones = metric === 'power' || metric === 'hr';
    const unit = zones || metric === 'time' ? 'h' : this.metricSpec(metric).unit;
    return {
      interaction: { mode: 'index', intersect: false },
      scales: {
        x: { stacked: zones, grid: { display: false } },
        y: {
          stacked: zones,
          beginAtZero: true,
          ticks: { callback: v => `${formatNumber(Number(v), 0)} ${unit}` }
        }
      },
      plugins: {
        legend: { display: zones, position: 'bottom' },
        tooltip: {
          filter: (item: TooltipItem<'bar'>) => !zones || (item.parsed.y ?? 0) > 0,
          callbacks: {
            label: (item: TooltipItem<'bar'>) => {
              const v = item.parsed.y ?? 0;
              if (zones || metric === 'time') return `${item.dataset.label}: ${formatDuration(v * 3600)} h`;
              return `${item.dataset.label}: ${formatNumber(v, metric === 'distance' ? 1 : 0)} ${unit}`;
            },
            footer: (items: TooltipItem<'bar'>[]) => {
              if (!zones) return '';
              const sum = items.reduce((s, i) => s + (i.parsed.y ?? 0), 0);
              return `Summe: ${formatDuration(sum * 3600)} h`;
            }
          }
        }
      }
    };
  }

  private metricSpec(metric: ChartMetric): { color: string; unit: string; value: (p: StatsPeriodDto) => number } {
    switch (metric) {
      case 'time': return { color: token('--c-fitness'), unit: 'h', value: p => p.movingTime / 3600 };
      case 'load': return { color: token('--c-fatigue'), unit: 'TSS', value: p => p.trainingLoad };
      case 'elevation': return { color: token('--c-altitude'), unit: 'm', value: p => p.elevationGain };
      default: return { color: token('--c-speed'), unit: 'km', value: p => p.distance / 1000 };
    }
  }

  private periodLabel(p: StatsPeriodDto): string {
    const d = parseDay(p.from);
    switch (this.current()?.groupBy) {
      case 'WEEK': return `KW ${isoWeek(parseDay(p.to))}`;
      case 'YEAR': return String(d.getFullYear());
      default: return `${MONTHS[d.getMonth()]} ${String(d.getFullYear()).slice(2)}`;
    }
  }
}

// ---- Helfer ---------------------------------------------------------------------------------

function today(): Date {
  const n = new Date();
  return new Date(n.getFullYear(), n.getMonth(), n.getDate());
}

function startOfYear(d: Date): Date {
  return new Date(d.getFullYear(), 0, 1);
}

/** Gleicher Tag im Vorjahr; der 29.2. wird zum 28.2. */
function minusYear(d: Date): Date {
  const out = new Date(d.getFullYear() - 1, d.getMonth(), d.getDate());
  if (out.getMonth() !== d.getMonth()) out.setDate(0);
  return out;
}

function parseDay(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function germanDate(iso: string): string {
  const d = parseDay(iso);
  return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

/** ISO-Kalenderwoche. */
function isoWeek(d: Date): number {
  const t = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  const day = t.getUTCDay() || 7;
  t.setUTCDate(t.getUTCDate() + 4 - day);
  const yearStart = new Date(Date.UTC(t.getUTCFullYear(), 0, 1));
  return Math.ceil(((t.getTime() - yearStart.getTime()) / 864e5 + 1) / 7);
}

function formatNumber(value: number, decimals: number): string {
  return value.toLocaleString('de-DE', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

/** Prozentuale Veränderung; null ohne Vergleichswert. */
function change(current: number, previous: number | undefined | null): number | null {
  if (previous == null || previous === 0) return null;
  return (current - previous) / previous * 100;
}

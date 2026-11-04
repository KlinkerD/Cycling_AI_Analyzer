import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, switchMap, take, takeWhile, timer } from 'rxjs';
import { DecimalPipe, SlicePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { OverviewService } from '../../services/overview.service';
import { AthleteStore } from '../../services/athlete.store';
import { DailyRecommendationDto, WellnessPointDto, ZoneDto } from '../../api/training';
import { ZoomGroup } from '../../shared/charts/zoom-group';
import { RangeSliderComponent, SparkPoint } from '../../shared/charts/range-slider.component';
import { alpha, metricColor, token } from '../../shared/theme';
import { addDays } from '../../shared/format';

/** Nachfrage-Takt für den asynchron nachgelieferten KI-Empfehlungstext (5 s x 24 = 2 Min.). */
const RECOMMENDATION_POLL_MS = 5000;
const RECOMMENDATION_POLL_MAX = 24;
const DAY_MS = 864e5;

type RangeKey = '1m' | '3m' | '6m' | '1y' | 'custom';

interface RangeOption {
  key: RangeKey;
  label: string;
  days: number;
}

interface FormStatus {
  label: string;
  color: string;
  hint: string;
}

type Pt = { x: number; y: number };

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [
    DecimalPipe, SlicePipe, FormsModule,
    MatButtonModule, MatButtonToggleModule, MatIconModule, MatProgressSpinnerModule,
    MatTooltipModule, MatFormFieldModule, MatInputModule, MatDatepickerModule, MatSnackBarModule,
    BaseChartDirective, RangeSliderComponent
  ],
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.scss'
})
export class OverviewComponent implements OnInit {
  private readonly overviewService = inject(OverviewService);
  private readonly store = inject(AthleteStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly ranges: RangeOption[] = [
    { key: '1m', label: '1 M', days: 30 },
    { key: '3m', label: '3 M', days: 90 },
    { key: '6m', label: '6 M', days: 180 },
    { key: '1y', label: '1 J', days: 365 }
  ];

  selectedRange: RangeKey = '3m';
  from: Date = addDays(new Date(), -90);
  to: Date = new Date();

  readonly loadingWellness = signal(false);
  /** Zonen und Schwellenwerte teilt sich die Übersicht mit der Seitenleiste (dort auch bearbeitbar). */
  readonly loadingZones = this.store.loading;
  readonly zones = this.store.zones;
  readonly loadingRecommendation = signal(false);
  readonly wellness = signal<WellnessPointDto[]>([]);
  readonly recommendation = signal<DailyRecommendationDto | null>(null);
  private textPollSub?: Subscription;

  /** Letzter Tag mit Werten und derselbe Tag eine Woche davor. */
  readonly today = computed(() => {
    const list = this.wellness().filter(w => w.ctl != null);
    const last = list[list.length - 1] ?? null;
    if (!last) return null;
    const weekAgoDate = Date.parse(last.date) - 7 * DAY_MS;
    const weekAgo = [...list].reverse().find(w => Date.parse(w.date) <= weekAgoDate) ?? null;
    return { last, weekAgo, status: this.formStatus(last.form) };
  });

  readonly sortedPowerZones = computed(() => this.sortedZonesForBar(this.zones()?.powerZones ?? []));

  // Fitness-Chart
  readonly fitnessGroup = new ZoomGroup();
  fitnessData: ChartData<'line', Pt[]> = { datasets: [] };
  fitnessSpark: SparkPoint[] = [];
  readonly fitnessOptions: ChartOptions<'line'> = this.buildFitnessOptions();

  ngOnInit(): void {
    this.loadAll();
  }

  selectRange(key: RangeKey): void {
    const opt = this.ranges.find(r => r.key === key);
    if (!opt) return;
    this.selectedRange = key;
    this.to = new Date();
    this.from = addDays(this.to, -opt.days);
    this.loadWellness();
  }

  onCustomDateChange(): void {
    if (!this.from || !this.to) return;
    this.selectedRange = 'custom';
    this.loadWellness();
  }

  refresh(): void {
    this.loadAll(true);
  }

  private loadAll(refresh = false): void {
    this.loadWellness(refresh);
    this.loadZones(refresh);
    this.loadRecommendation(refresh);
  }

  /**
   * Lädt die Tagesempfehlung. Bei {@code refresh=true} wird serverseitig neu berechnet
   * (POST; den KI-Text liefert der ai-service nach), sonst nur der gespeicherte Stand geholt (GET).
   */
  private loadRecommendation(refresh = false): void {
    this.loadingRecommendation.set(true);
    this.textPollSub?.unsubscribe();
    const req = refresh ? this.overviewService.refreshRecommendation() : this.overviewService.getRecommendation();
    req.subscribe({
      next: data => {
        this.recommendation.set(data);
        this.loadingRecommendation.set(false);
        if (data.textPending) this.pollRecommendationText();
      },
      error: err => {
        this.loadingRecommendation.set(false);
        this.snackBar.open('Fehler beim Laden der Empfehlung: ' + (err.error?.message || err.message), 'OK', { duration: 5000 });
      }
    });
  }

  /** Der KI-Text kommt asynchron; bis dahin steht der Fallback-Text da (max. ~2 Min. nachfragen). */
  private pollRecommendationText(): void {
    this.textPollSub = timer(RECOMMENDATION_POLL_MS, RECOMMENDATION_POLL_MS).pipe(
      take(RECOMMENDATION_POLL_MAX),
      switchMap(() => this.overviewService.getRecommendation()),
      takeWhile(data => {
        this.recommendation.set(data);
        return data.textPending;
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({ error: () => {} });
  }

  private loadWellness(refresh = false): void {
    this.loadingWellness.set(true);
    this.overviewService.getWellness(this.from, this.to, refresh).subscribe({
      next: data => {
        this.wellness.set(data);
        this.buildFitnessChart(data);
        this.loadingWellness.set(false);
      },
      error: err => {
        this.loadingWellness.set(false);
        this.snackBar.open('Fehler beim Laden der Fitness-Daten: ' + (err.error?.message || err.message), 'OK', { duration: 5000 });
      }
    });
  }

  private loadZones(refresh = false): void {
    // Beim ersten Öffnen hat die Seitenleiste die Daten meist schon geladen
    if (!refresh && (this.zones() || this.loadingZones())) return;
    this.store.load(refresh).subscribe({
      error: err => this.snackBar.open('Fehler beim Laden der Zonen: ' + (err.error?.message || err.message), 'OK', { duration: 5000 })
    });
  }

  // ---- Fitness-Chart ---------------------------------------------------------

  private buildFitnessChart(points: WellnessPointDto[]): void {
    const pts = (pick: (w: WellnessPointDto) => number | null): Pt[] =>
      points.flatMap(w => {
        const v = pick(w);
        const x = Date.parse(w.date);
        return v == null || isNaN(x) ? [] : [{ x, y: v }];
      });

    const ctl = pts(w => w.ctl);
    const fitness = metricColor('fitness');
    const fatigue = metricColor('fatigue');
    const form = metricColor('form');

    this.fitnessData = {
      datasets: [
        {
          label: 'Fitness (CTL)', data: ctl, yAxisID: 'y',
          borderColor: fitness, backgroundColor: alpha(fitness, 0.14), fill: 'start'
        },
        {
          label: 'Ermüdung (ATL)', data: pts(w => w.atl), yAxisID: 'y',
          borderColor: fatigue, backgroundColor: fatigue, fill: false, borderWidth: 1.5
        },
        {
          label: 'Form (TSB)', data: pts(w => w.form), yAxisID: 'yTsb',
          borderColor: alpha(form, 0.8), backgroundColor: form, fill: false, borderDash: [4, 4], borderWidth: 1.5
        }
      ]
    };
    this.fitnessSpark = ctl;
    if (ctl.length > 1) this.fitnessGroup.setBounds(ctl[0].x, ctl[ctl.length - 1].x);
  }

  private buildFitnessOptions(): ChartOptions<'line'> {
    const dateShort = (ms: number) => new Date(ms).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' });
    const dateLong = (ms: number) => new Date(ms).toLocaleDateString('de-DE', { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' });
    return {
      interaction: { mode: 'index', intersect: false },
      elements: { point: { radius: 0, hoverRadius: 4 }, line: { tension: 0.25, borderWidth: 2 } },
      plugins: {
        ...(this.fitnessGroup.pluginOptions() as object),
        legend: { position: 'top', align: 'end' },
        tooltip: {
          callbacks: {
            title: items => items.length ? dateLong(Number(items[0].parsed.x)) : '',
            label: item => ` ${item.dataset.label}: ${item.parsed.y?.toFixed(1)}`
          }
        }
      },
      scales: {
        x: {
          type: 'linear',
          grid: { display: false },
          ticks: { maxTicksLimit: 10, maxRotation: 0, callback: v => dateShort(Number(v)) }
        },
        y: {
          position: 'left',
          grid: { color: token('--grid') },
          border: { display: false },
          title: { display: true, text: 'Fitness / Ermüdung' }
        },
        yTsb: {
          position: 'right',
          grid: { display: false },
          border: { display: false },
          title: { display: true, text: 'Form' }
        }
      }
    } as ChartOptions<'line'>;
  }

  // ---- Form-Status ----------------------------------------------------------

  /** Einordnung der Form (TSB) wie bei intervals.icu. */
  formStatus(form: number | null): FormStatus | null {
    if (form == null) return null;
    if (form > 25) return { label: 'Übergang', color: '#fbbf24', hint: 'Lange erholt, Fitness sinkt' };
    if (form > 5) return { label: 'Frisch', color: '#60a5fa', hint: 'Gut erholt, bereit für Leistung' };
    if (form >= -10) return { label: 'Neutral', color: '#8d96a5', hint: 'Weder Aufbau noch Erholung' };
    if (form >= -30) return { label: 'Optimal', color: '#4ade80', hint: 'Produktiver Trainingsreiz' };
    return { label: 'Überlastet', color: '#f87171', hint: 'Hohe Ermüdung, Erholung einplanen' };
  }

  delta(now: number | null, before: number | null | undefined): number | null {
    if (now == null || before == null) return null;
    return Math.round((now - before) * 10) / 10;
  }

  // ---- Zonen ----------------------------------------------------------------

  /** Sweet Spot (Index 8) wird zwischen Z3 und Z4 eingeordnet, damit der Balken dem Leistungsbereich folgt. */
  private sortedZonesForBar(zones: ZoneDto[]): ZoneDto[] {
    if (!zones.length) return [];
    const regular = zones.filter(z => z.index <= 7);
    const ss = zones.find(z => z.index === 8);
    if (!ss) return regular;
    const result = [...regular];
    const z3 = result.findIndex(z => z.index === 3);
    result.splice(z3 >= 0 ? z3 + 1 : result.length, 0, ss);
    return result;
  }

  /** Breite (in %) eines Segments im Zonen-Balken; die offene Top-Zone bekommt 1,3x der vorletzten Grenze. */
  zoneSegmentWidth(zones: ZoneDto[], zone: ZoneDto): number {
    const total = this.scaleMax(zones);
    if (total <= 0) return 0;
    const upper = zone.upperValue ?? this.openEndedUpper(zones);
    const lower = zone.lowerValue ?? 0;
    return Math.max(0, ((upper - lower) / total) * 100);
  }

  private scaleMax(zones: ZoneDto[]): number {
    if (!zones.length) return 0;
    return zones[zones.length - 1].upperValue ?? this.openEndedUpper(zones);
  }

  private openEndedUpper(zones: ZoneDto[]): number {
    for (let i = zones.length - 1; i >= 0; i--) {
      if (zones[i].upperValue != null) return Math.round(zones[i].upperValue! * 1.3);
    }
    return 100;
  }

  zoneLabel(z: ZoneDto): string {
    return z.index === 8 ? 'SS' : 'Z' + z.index;
  }

  formatPct(p: number | null): string {
    return p == null ? '–' : Math.round(p * 100) + ' %';
  }

  formatRange(zone: ZoneDto): string {
    const lo = zone.lowerValue ?? '–';
    const hi = zone.upperValue ?? '∞';
    return `${lo} – ${hi}`;
  }

  /** Zonengrenzen in Watt für eine andere FTP (Indoor), gerundet wie im Backend. */
  formatScaledRange(zone: ZoneDto, ftp: number): string {
    const lo = zone.lowerPct != null ? Math.round(zone.lowerPct * ftp) : '–';
    const hi = zone.upperPct != null ? Math.round(zone.upperPct * ftp) : '∞';
    return `${lo} – ${hi}`;
  }

  hrReferenceLabel(): string {
    return this.zones()?.hrReference === 'max_hr' ? 'HFmax' : 'LTHR';
  }

  hrReferenceValue(): number | null {
    const z = this.zones();
    if (!z) return null;
    return z.hrReference === 'max_hr' ? z.thresholds.maxHr : z.thresholds.lthr;
  }

  recommendationStand(): string {
    const at = this.recommendation()?.computedAt;
    if (!at) return '';
    const d = new Date(at);
    return isNaN(d.getTime()) ? '' : d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + ' Uhr';
  }
}

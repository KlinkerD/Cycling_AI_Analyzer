import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, switchMap, take, takeWhile, timer } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { OverviewService } from '../../services/overview.service';
import { AthleteZonesDto, DailyRecommendationDto, WellnessPointDto, ZoneDto } from '../../api/training';

/** Nachfrage-Takt fuer den asynchron nachgelieferten KI-Empfehlungstext (5 s x 24 = 2 Min.). */
const RECOMMENDATION_POLL_MS = 5000;
const RECOMMENDATION_POLL_MAX = 24;

type RangeKey = '7d' | '1m' | '3m' | '6m' | '1y' | 'custom';

interface RangeOption {
  key: RangeKey;
  label: string;
  days: number;
}

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatTooltipModule, MatFormFieldModule, MatInputModule, MatDatepickerModule,
    MatSnackBarModule, MatDividerModule,
    BaseChartDirective
  ],
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.scss'
})
export class OverviewComponent implements OnInit {

  readonly ranges: RangeOption[] = [
    { key: '7d', label: '7 Tage',   days: 7 },
    { key: '1m', label: '1 Monat',  days: 30 },
    { key: '3m', label: '3 Monate', days: 90 },
    { key: '6m', label: '6 Monate', days: 180 },
    { key: '1y', label: '1 Jahr',   days: 365 }
  ];

  selectedRange: RangeKey = '3m';
  from: Date;
  to: Date;

  loadingWellness = false;
  loadingZones = false;
  loadingRecommendation = false;
  wellness: WellnessPointDto[] = [];
  zones: AthleteZonesDto | null = null;
  recommendation: DailyRecommendationDto | null = null;
  private textPollSub?: Subscription;
  private readonly destroyRef = inject(DestroyRef);
  sortedPowerZonesForBar: ZoneDto[] = []; // für Stacked-Bar Visualisierung

  // ---- Chart-Konfiguration -------------------------------------------------
  fitnessChartData: ChartData<'line'> = { labels: [], datasets: [] };
  fitnessChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { position: 'top' },
      tooltip: { mode: 'index', intersect: false }
    },
    scales: {
      x: { ticks: { maxTicksLimit: 12, autoSkip: true } },
      y:    { position: 'left',  title: { display: true, text: 'Fitness / Ermüdung' } },
      yTsb: { position: 'right', title: { display: true, text: 'Form (TSB)' }, grid: { drawOnChartArea: false } }
    },
    elements: { point: { radius: 0 }, line: { tension: 0.25, borderWidth: 2 } }
  };

  constructor(
    private overviewService: OverviewService,
    private snackBar: MatSnackBar
  ) {
    this.to = new Date();
    this.from = this.subDays(this.to, 90);
  }

  ngOnInit(): void {
    this.loadAll();
  }

  selectRange(key: RangeKey): void {
    const opt = this.ranges.find(r => r.key === key);
    if (!opt) return;
    this.selectedRange = key;
    this.to = new Date();
    this.from = this.subDays(this.to, opt.days);
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
   * Laedt die Tagesempfehlung. Bei {@code refresh=true} (Button) wird serverseitig neu berechnet
   * (POST; den KI-Text liefert der ai-service nach), sonst nur der gespeicherte Stand geholt (GET).
   */
  private loadRecommendation(refresh = false): void {
    this.loadingRecommendation = true;
    this.textPollSub?.unsubscribe();
    const req = refresh
      ? this.overviewService.refreshRecommendation()
      : this.overviewService.getRecommendation();
    req.subscribe({
      next: (data) => {
        this.recommendation = data;
        this.loadingRecommendation = false;
        if (data.textPending) {
          this.pollRecommendationText();
        }
      },
      error: (err) => {
        this.loadingRecommendation = false;
        this.snackBar.open('Fehler beim Laden der Empfehlung: ' + (err.error?.message || err.message), 'OK', { duration: 5000 });
      }
    });
  }

  /**
   * Der KI-Text kommt asynchron aus dem ai-service. Bis dahin steht der Fallback-Text da;
   * wir fragen einige Male nach, bis das Backend nicht mehr "textPending" meldet (max. ~2 Min.).
   */
  private pollRecommendationText(): void {
    this.textPollSub = timer(RECOMMENDATION_POLL_MS, RECOMMENDATION_POLL_MS).pipe(
      take(RECOMMENDATION_POLL_MAX),
      switchMap(() => this.overviewService.getRecommendation()),
      takeWhile(data => {
        this.recommendation = data;
        return data.textPending;
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({ error: () => {} });
  }

  private loadWellness(refresh = false): void {
    this.loadingWellness = true;
    this.overviewService.getWellness(this.from, this.to, refresh).subscribe({
      next: (data) => {
        this.wellness = data;
        this.buildFitnessChart();
        this.loadingWellness = false;
      },
      error: (err) => {
        this.loadingWellness = false;
        this.snackBar.open('Fehler beim Laden der Fitness-Daten: ' + (err.error?.message || err.message), 'OK', { duration: 5000 });
      }
    });
  }

  private loadZones(refresh = false): void {
    this.loadingZones = true;
    this.overviewService.getAthlete(refresh).subscribe({
      next: (data) => {
        this.zones = data;
        this.sortedPowerZonesForBar = this.sortedZonesForBar(data.powerZones);
        this.loadingZones = false;
      },
      error: (err) => {
        this.loadingZones = false;
        this.snackBar.open('Fehler beim Laden der Zonen: ' + (err.error?.message || err.message), 'OK', { duration: 5000 });
      }
    });
  }

  private buildFitnessChart(): void {
    const labels = this.wellness.map(w => this.formatChartLabel(w.date));
    const ctl  = this.wellness.map(w => w.ctl);
    const atl  = this.wellness.map(w => w.atl);
    const form = this.wellness.map(w => w.form);

    this.fitnessChartData = {
      labels,
      datasets: [
        {
          label: 'Fitness (CTL)',
          data: ctl as number[],
          borderColor: '#42a5f5',
          backgroundColor: 'rgba(66,165,245,0.15)',
          fill: true,
          yAxisID: 'y'
        },
        {
          label: 'Ermüdung (ATL)',
          data: atl as number[],
          borderColor: '#e91e63',
          backgroundColor: 'rgba(233,30,99,0.10)',
          fill: false,
          yAxisID: 'y'
        },
        {
          label: 'Form (TSB)',
          data: form as number[],
          borderColor: '#9e9e9e',
          backgroundColor: 'rgba(158,158,158,0.10)',
          fill: false,
          borderDash: [4, 4],
          yAxisID: 'yTsb'
        }
      ]
    };
  }

  // ---- Stacked-Bar fuer Zonen ---------------------------------------------

  /**
   * Sortiert Power-Zonen für Stacked-Bar: Sweet Spot (SS, Index 8) wird zwischen Z3 und Z4
   * eingeordnet, damit die Bar visuell dem Leistungsbereich entspricht.
   */
  sortedZonesForBar(zones: ZoneDto[]): ZoneDto[] {
    if (!zones || zones.length === 0) return [];
    const regular = zones.filter(z => z.index <= 7);
    const ss = zones.find(z => z.index === 8);
    if (!ss) return regular;
    // SS (84-97%) liegt zwischen Z3 (76-90%) und Z4 (91-105%) → nach Z3 einfügen
    const result = [...regular];
    const z3Idx = result.findIndex(z => z.index === 3);
    if (z3Idx >= 0) {
      result.splice(z3Idx + 1, 0, ss);
    } else {
      result.push(ss);
    }
    return result;
  }

  /**
   * Berechnet die Breite (in %) eines Segments fuer den Stacked-Bar.
   * Die Skala laeuft von 0 bis upperOfLastZoneWithBound; die letzte (open-ended) ZoneDto
   * bekommt eine sinnvolle visuelle Breite (proportional zur vorletzten).
   */
  zoneSegmentWidth(zones: ZoneDto[], zone: ZoneDto): number {
    const total = this.scaleMax(zones);
    if (total <= 0) return 0;
    const upper = zone.upperValue ?? this.openEndedUpper(zones);
    const lower = zone.lowerValue ?? 0;
    return Math.max(0, ((upper - lower) / total) * 100);
  }

  private scaleMax(zones: ZoneDto[]): number {
    if (!zones.length) return 0;
    const last = zones[zones.length - 1];
    return last.upperValue ?? this.openEndedUpper(zones);
  }

  /** Heuristik fuer die Breite der open-ended Top-ZoneDto: 1.3x der vorletzten Obergrenze. */
  private openEndedUpper(zones: ZoneDto[]): number {
    for (let i = zones.length - 1; i >= 0; i--) {
      if (zones[i].upperValue != null) return Math.round(zones[i].upperValue! * 1.3);
    }
    return 100;
  }

  // ---- Helpers -------------------------------------------------------------

  formatPct(p: number | null): string {
    if (p == null) return '–';
    return Math.round(p * 100) + ' %';
  }

  formatRange(zone: ZoneDto, unit: string): string {
    const lo = zone.lowerValue != null ? zone.lowerValue : '–';
    const hi = zone.upperValue != null ? zone.upperValue : '∞';
    return `${lo} – ${hi} ${unit}`;
  }

  /** Zonengrenzen in Watt fuer eine andere FTP (z. B. Indoor), gerundet wie im Backend. */
  formatScaledRange(zone: ZoneDto, ftp: number, unit: string): string {
    const lo = zone.lowerPct != null ? Math.round(zone.lowerPct * ftp) : '–';
    const hi = zone.upperPct != null ? Math.round(zone.upperPct * ftp) : '∞';
    return `${lo} – ${hi} ${unit}`;
  }

  hrReferenceLabel(): string {
    if (!this.zones) return '';
    return this.zones.hrReference === 'max_hr' ? 'HFmax' : 'LTHR';
  }

  private subDays(d: Date, days: number): Date {
    const out = new Date(d);
    out.setDate(out.getDate() - days);
    return out;
  }

  /** "Stand: HH:MM Uhr" aus dem computedAt-Zeitstempel der Empfehlung. */
  recommendationStand(): string {
    if (!this.recommendation?.computedAt) return '';
    const d = new Date(this.recommendation.computedAt);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + ' Uhr';
  }

  private formatChartLabel(date: string): string {
    const d = new Date(date);
    if (isNaN(d.getTime())) return date;
    return d.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' });
  }
}


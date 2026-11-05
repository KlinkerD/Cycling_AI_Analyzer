import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { ActivityService } from '../../services/activity.service';
import { FtpDetectionDto, PowerCurveSetDto, ReportDto, TrendDataDto, ZoneDistributionDto } from '../../api/report';
import { ZoomGroup } from '../../shared/charts/zoom-group';
import { RangeSliderComponent, SparkPoint } from '../../shared/charts/range-slider.component';
import { alpha, metricColor, scoreColor, token } from '../../shared/theme';
import { formatCurveDuration, formatDuration, shortDate } from '../../shared/format';
import { Pt, StreamCharts, TimeHighlight, buildStreamCharts } from './stream-charts';
import { RouteMapComponent } from './route-map.component';
import { hasRoute } from './route-coloring';

/** Laenge des FTP-Testblocks (entspricht ftp.detection.window-seconds im ftp-service). */
const FTP_TEST_SECONDS = 20 * 60;

interface ZoneRow {
  name: string;
  label: string;
  color: string;
  pct: number;
  seconds: number;
}

@Component({
  selector: 'app-report',
  standalone: true,
  imports: [
    DatePipe, DecimalPipe, NgTemplateOutlet, RouterLink,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatSnackBarModule,
    MatTabsModule, MatTooltipModule,
    BaseChartDirective, RangeSliderComponent, RouteMapComponent
  ],
  templateUrl: './report.component.html',
  styleUrl: './report.component.scss'
})
export class ReportComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly activityService = inject(ActivityService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly report = signal<ReportDto | null>(null);
  readonly loading = signal(true);
  readonly resending = signal(false);
  activityId = '';

  // FTP-Test-Erkennung
  readonly Ftp = FtpDetectionDto.StatusEnum;
  readonly ftpDetection = signal<FtpDetectionDto | null>(null);
  readonly ftpStarting = signal(false);
  private ftpPoll: Subscription | null = null;

  readonly formatDuration = formatDuration;
  readonly scoreColor = scoreColor;

  // Verläufe
  readonly streamGroup = new ZoomGroup();
  streams: StreamCharts | null = null;

  // Karte (nur mit GPS)
  hasRoute = false;

  // Zonen
  powerZones: ZoneRow[] = [];
  hrZones: ZoneRow[] = [];

  // Power-Curve
  readonly curveGroup = new ZoomGroup(true);
  curveData: ChartData<'line', Pt[]> | null = null;
  curveOptions!: ChartOptions<'line'>;
  curveSpark: SparkPoint[] = [];

  // Trend
  readonly trendGroup = new ZoomGroup();
  trendData: ChartData<'line', Pt[]> | null = null;
  trendOptions!: ChartOptions<'line'>;
  trendSpark: SparkPoint[] = [];
  private trend: TrendDataDto[] = [];

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      this.activityId = params.get('id') ?? '';
      this.load();
    });
  }

  private load(): void {
    this.loading.set(true);
    this.stopFtpPolling();
    this.ftpDetection.set(null);
    this.activityService.getReport(this.activityId).subscribe({
      next: raw => {
        const report = this.normalize(raw);
        this.build(report);
        this.report.set(report);
        this.loading.set(false);
        this.loadFtpDetection();
      },
      error: () => {
        this.loading.set(false);
        this.snackBar.open('Report nicht gefunden. Bitte die Fahrt zuerst analysieren.', 'OK', { duration: 4000 });
        this.router.navigate(['/']);
      }
    });
  }

  private build(report: ReportDto): void {
    this.streams = buildStreamCharts(report.streams, this.streamGroup, this.ftpHighlight(), report.weather ?? []);
    this.hasRoute = hasRoute(report.streams);
    this.powerZones = this.zoneRows(report.powerZones);
    this.hrZones = this.zoneRows(report.hrZones);
    this.buildCurve(report.powerCurveSet);
    this.buildTrend(report.trend ?? []);
  }

  // ---- Zonen ----------------------------------------------------------------

  private zoneRows(zones: ZoneDistributionDto[]): ZoneRow[] {
    return (zones ?? []).map(z => ({
      name: z.zoneName,
      label: z.zoneLabel,
      color: z.color,
      pct: z.percentage,
      seconds: z.seconds
    }));
  }

  zoneBarWidth(rows: ZoneRow[], row: ZoneRow): number {
    const max = Math.max(...rows.map(r => r.pct), 1);
    return (row.pct / max) * 100;
  }

  // ---- Power-Curve ----------------------------------------------------------

  private buildCurve(set: PowerCurveSetDto | null | undefined): void {
    this.curveData = null;
    if (!set?.current?.points?.length) return;

    const toPts = (points: { durationSeconds: number; watts: number | null }[]): Pt[] =>
      points.filter(p => p.watts != null && p.durationSeconds > 0).map(p => ({ x: p.durationSeconds, y: p.watts! }));

    const current = toPts(set.current.points);
    if (current.length < 2) return;

    const power = metricColor('power');
    const muted = token('--text-muted');
    const datasets: ChartData<'line', Pt[]>['datasets'] = [];

    // Ältere Trainings zuerst, damit das aktuelle obenauf liegt; älter = blasser
    const alphas = [0.55, 0.35, 0.2];
    (set.previous ?? []).slice(0, alphas.length).forEach((prev, i) => {
      datasets.push({
        label: `${shortDate(prev.activityDate)} ${this.shortName(prev.activityName)}`.trim(),
        data: toPts(prev.points),
        borderColor: alpha('#8d96a5', alphas[i]),
        backgroundColor: 'transparent',
        borderWidth: 1.5,
        pointRadius: 0,
        pointHoverRadius: 3,
        tension: 0.3,
        spanGaps: true
      });
    });

    datasets.push({
      label: `${shortDate(set.current.activityDate)} ${this.shortName(set.current.activityName)}`.trim() || 'Diese Fahrt',
      data: current,
      borderColor: power,
      backgroundColor: alpha(power, 0.12),
      fill: 'start',
      borderWidth: 2.5,
      pointRadius: 2,
      pointBackgroundColor: power,
      pointHoverRadius: 5,
      tension: 0.3,
      spanGaps: true
    });

    if (set.ftpWatts && set.ftpWatts > 0) {
      const ftp = set.ftpWatts;
      datasets.push({
        label: `FTP ${ftp} W`,
        data: [{ x: current[0].x, y: ftp }, { x: current[current.length - 1].x, y: ftp }],
        borderColor: alpha('#e7e9ee', 0.5),
        backgroundColor: 'transparent',
        borderWidth: 1,
        borderDash: [6, 4],
        pointRadius: 0,
        pointHoverRadius: 0,
        tension: 0
      });
    }

    this.curveGroup.setBounds(current[0].x, current[current.length - 1].x);
    this.curveSpark = current;
    this.curveData = { datasets };
    this.curveOptions = {
      interaction: { mode: 'nearest', axis: 'x', intersect: false },
      plugins: {
        ...(this.curveGroup.pluginOptions() as object),
        legend: { position: 'bottom', labels: { color: muted } },
        tooltip: {
          callbacks: {
            title: items => items.length ? 'Dauer ' + formatCurveDuration(Number(items[0].parsed.x)) : '',
            label: item => ` ${item.dataset.label}: ${item.parsed.y == null ? '–' : item.parsed.y.toFixed(0) + ' W'}`
          }
        }
      },
      scales: {
        x: {
          type: 'logarithmic',
          grid: { color: token('--grid') },
          // feste, "runde" Dauern statt der automatischen Log-Ticks
          afterBuildTicks: scale => {
            scale.ticks = [1, 5, 10, 30, 60, 300, 600, 1200, 3600, 7200]
              .filter(v => v >= scale.min && v <= scale.max)
              .map(value => ({ value }));
          },
          ticks: {
            autoSkip: false,
            maxRotation: 0,
            callback: v => formatCurveDuration(Number(v))
          },
          title: { display: true, text: 'Dauer' }
        },
        y: {
          title: { display: true, text: 'Watt', color: power },
          ticks: { color: power },
          grid: { color: token('--grid') },
          border: { display: false }
        }
      }
    } as ChartOptions<'line'>;
  }

  // ---- Trend ---------------------------------------------------------------

  private buildTrend(trendDesc: TrendDataDto[]): void {
    this.trendData = null;
    this.trend = [...trendDesc].reverse();
    if (this.trend.length < 2) return;

    const pts = (pick: (t: TrendDataDto) => number | null): Pt[] =>
      this.trend.flatMap((t, i) => {
        const v = pick(t);
        return v == null ? [] : [{ x: i, y: v }];
      });

    const ds = (label: string, data: Pt[], color: string, yAxisID: string, dashed = false) => ({
      label, data, yAxisID,
      borderColor: color,
      backgroundColor: color,
      borderWidth: 2,
      borderDash: dashed ? [4, 4] : [],
      pointRadius: 4,
      pointHoverRadius: 6,
      cubicInterpolationMode: 'monotone' as const
    });

    const score = pts(t => t.overallScore);
    this.trendData = {
      datasets: [
        ds('Score', score, metricColor('score'), 'yScore'),
        ds('Ø Leistung', pts(t => t.avgPowerWatts != null ? Math.round(t.avgPowerWatts) : null), metricColor('power'), 'yPower'),
        ds('Ø HF', pts(t => t.avgHeartRate != null ? Math.round(t.avgHeartRate) : null), metricColor('hr'), 'yHr', true),
        ds('Ø Geschw.', pts(t => t.avgSpeedKmh != null ? +t.avgSpeedKmh.toFixed(1) : null), metricColor('speed'), 'ySpeed', true)
      ]
    };
    this.trendSpark = score;
    this.trendGroup.setBounds(0, this.trend.length - 1);

    const units: Record<string, string> = { 'Score': '/10', 'Ø Leistung': ' W', 'Ø HF': ' bpm', 'Ø Geschw.': ' km/h' };
    this.trendOptions = {
      interaction: { mode: 'index', axis: 'x', intersect: false },
      onClick: (_e, elements) => {
        const hit = elements[0];
        const pt = hit ? this.trendData?.datasets[hit.datasetIndex]?.data[hit.index] : null;
        const target = pt ? this.trend[Math.round(pt.x)] : null;
        if (target && target.activityId !== this.activityId) this.router.navigate(['/report', target.activityId]);
      },
      plugins: {
        ...(this.trendGroup.pluginOptions() as object),
        legend: { position: 'bottom' },
        tooltip: {
          callbacks: {
            title: items => {
              const t = items.length ? this.trend[Math.round(Number(items[0].parsed.x))] : null;
              return t ? `${shortDate(t.activityDate)} · ${t.activityName ?? ''}` : '';
            },
            label: item => ` ${item.dataset.label}: ${item.parsed.y}${units[item.dataset.label ?? ''] ?? ''}`,
            footer: () => 'Klick öffnet den Report'
          }
        }
      },
      scales: {
        x: {
          type: 'linear',
          offset: true,
          grid: { display: false },
          ticks: {
            stepSize: 1,
            maxRotation: 0,
            callback: v => Number.isInteger(Number(v)) ? shortDate(this.trend[Number(v)]?.activityDate) : ''
          }
        },
        yScore: {
          position: 'left', min: 0, max: 10,
          ticks: { color: metricColor('score'), stepSize: 2 },
          grid: { color: token('--grid') },
          border: { display: false },
          title: { display: true, text: 'Score', color: metricColor('score') }
        },
        yPower: {
          position: 'right',
          ticks: { color: metricColor('power') },
          grid: { display: false },
          border: { display: false },
          title: { display: true, text: 'W', color: metricColor('power') }
        },
        yHr: { position: 'right', display: false },
        ySpeed: { position: 'right', display: false }
      }
    } as ChartOptions<'line'>;
  }

  // ---- Aktionen -------------------------------------------------------------

  resend(): void {
    if (this.resending()) return;
    this.resending.set(true);
    this.activityService.resendReport(this.activityId).subscribe({
      next: res => {
        this.resending.set(false);
        const labels = res.channels.map(c => c === 'MAIL' ? 'E-Mail' : 'Discord').join(' und ');
        this.snackBar.open(`Report-Versand an ${labels} angestoßen.`, 'OK', { duration: 4000 });
      },
      error: err => {
        this.resending.set(false);
        const message = err?.status === 409
          ? 'Kein Versandkanal aktiv (E-Mail und Discord sind ausgeschaltet).'
          : err?.status === 503
            ? 'Versand-Service nicht erreichbar. Bitte später erneut versuchen.'
            : 'Versand fehlgeschlagen. Bitte Logs prüfen.';
        this.snackBar.open(message, 'OK', { duration: 5000 });
      }
    });
  }

  // ---- FTP-Test-Erkennung ----------------------------------------------------

  /** Startet die Erkennung beim ftp-service und fragt das Ergebnis ab. */
  detectFtp(): void {
    if (this.ftpStarting() || this.ftpDetection()?.status === this.Ftp.PENDING) return;
    this.ftpStarting.set(true);
    const activityId = this.activityId;
    this.activityService.startFtpDetection(activityId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: detection => {
        this.ftpStarting.set(false);
        if (activityId !== this.activityId) return;
        this.applyFtpDetection(detection);
      },
      error: () => {
        this.ftpStarting.set(false);
        this.snackBar.open('FTP-Test-Auswertung konnte nicht gestartet werden.', 'OK', { duration: 5000 });
      }
    });
  }

  /** Letztes Ergebnis beim Laden der Seite; 404 = nie ausgeloest, dann bleibt das Panel weg. */
  private loadFtpDetection(): void {
    const activityId = this.activityId;
    this.activityService.getFtpDetection(activityId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: detection => {
        if (activityId === this.activityId) this.applyFtpDetection(detection);
      },
      error: () => { /* 404: noch nie ausgeloest */ }
    });
  }

  private applyFtpDetection(detection: FtpDetectionDto): void {
    const wasDetected = this.ftpDetection()?.status === this.Ftp.DETECTED;
    this.ftpDetection.set(detection);
    if (detection.status === this.Ftp.PENDING) {
      this.startFtpPolling();
    }
    // Chart neu bauen, damit der erkannte Block hinterlegt wird (oder die alte Markierung verschwindet)
    const report = this.report();
    if (report && (wasDetected || detection.status === this.Ftp.DETECTED)) {
      this.streams = buildStreamCharts(report.streams, this.streamGroup, this.ftpHighlight(), report.weather ?? []);
    }
  }

  private startFtpPolling(): void {
    this.stopFtpPolling();
    const activityId = this.activityId;
    this.ftpPoll = this.activityService.pollFtpDetection(activityId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: detection => {
          if (activityId === this.activityId) this.applyFtpDetection(detection);
        },
        error: () => this.snackBar.open('Status der FTP-Test-Auswertung nicht abrufbar.', 'OK', { duration: 5000 })
      });
  }

  private stopFtpPolling(): void {
    this.ftpPoll?.unsubscribe();
    this.ftpPoll = null;
  }

  private ftpHighlight(): TimeHighlight | null {
    const d = this.ftpDetection();
    if (d?.status !== this.Ftp.DETECTED || d.windowStartSeconds == null) return null;
    return { startSeconds: d.windowStartSeconds, durationSeconds: FTP_TEST_SECONDS, label: 'FTP-Test 20 min' };
  }

  // ---- Format ---------------------------------------------------------------

  formatLongDate(iso: string | null): string {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  }

  formatZoneTime(seconds: number): string {
    const m = Math.round(seconds / 60);
    return m >= 60 ? `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')} h` : `${m} min`;
  }

  /** Umfang des Score-Rings (r = 34). */
  scoreDash(score: number | null): string {
    const c = 2 * Math.PI * 34;
    const f = score == null ? 0 : Math.max(0, Math.min(1, score / 10));
    return `${(c * f).toFixed(1)} ${c.toFixed(1)}`;
  }

  private shortName(name: string | null | undefined, max = 22): string {
    if (!name) return '';
    return name.length <= max ? name : name.substring(0, max - 1) + '…';
  }

  private normalize(report: ReportDto): ReportDto {
    const clean = (text: string | null | undefined) => (text ?? '')
      .replace(/\r\n/g, '\n')
      .replace(/^\s*(ÜBERSCHRIFT|HEADING|TITLE|BULLETS?|STICHPUNKTE)\s*:\s*/gim, '')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
    return {
      ...report,
      analysis: {
        ...report.analysis,
        strengths: clean(report.analysis.strengths),
        anomalies: clean(report.analysis.anomalies),
        progress: clean(report.analysis.progress),
        summary: clean(report.analysis.summary)
      }
    };
  }
}

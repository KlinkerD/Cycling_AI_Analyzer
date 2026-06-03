import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { BaseChartDirective } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { ActivityService } from '../../services/activity.service';
import { Report, ZoneDistribution } from '../../models/activity.model';

@Component({
  selector: 'app-report',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatChipsModule,
    MatSnackBarModule,
    MatProgressBarModule,
    BaseChartDirective
  ],
  templateUrl: './report.component.html',
  styleUrl: './report.component.scss'
})
export class ReportComponent implements OnInit {

  report: Report | null = null;
  loading = true;
  activityId!: string;

  // Chart: Leistung vs. Distanz
  powerChartData!: ChartData<'line'>;
  powerChartOptions: ChartOptions<'line'> = this.lineChartOptions('Leistung (W)');

  // Chart: Herzfrequenz vs. Distanz
  hrChartData!: ChartData<'line'>;
  hrChartOptions: ChartOptions<'line'> = this.lineChartOptions('Herzfrequenz (bpm)');

  // Chart: Kadenz vs. Distanz
  cadenceChartData!: ChartData<'line'>;
  cadenceChartOptions: ChartOptions<'line'> = this.lineChartOptions('Kadenz (rpm)');

  // Chart: Power Zones
  powerZoneChartData!: ChartData<'doughnut'>;
  powerZoneChartOptions: ChartOptions<'doughnut'> = this.zoneChartOptions();

  // Chart: HR Zones
  hrZoneChartData!: ChartData<'doughnut'>;
  hrZoneChartOptions: ChartOptions<'doughnut'> = this.zoneChartOptions();

  // Chart: Trend (letzte Trainings)
  trendChartData!: ChartData<'line'>;
  trendChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: true, position: 'top' },
      tooltip: {
        callbacks: {
          label: (item) => {
            const label = item.dataset.label ?? '';
            const val = item.raw as number;
            if (label.includes('Score')) return ` Score: ${val}/10`;
            if (label.includes('km/h')) return ` Ø Geschw.: ${val.toFixed(1)} km/h`;
            if (label.includes('Leistung')) return ` Ø Leistung: ${val.toFixed(0)} W`;
            if (label.includes('HF')) return ` Ø HF: ${val.toFixed(0)} bpm`;
            return ` ${label}: ${val}`;
          }
        }
      }
    },
    scales: {
      x: { grid: { display: false } },
      yScore: {
        type: 'linear',
        position: 'left',
        min: 0, max: 10,
        title: { display: true, text: 'Score' },
        grid: { color: 'rgba(255,152,0,0.1)' },
        ticks: { color: '#FF9800' }
      },
      ySpeed: {
        type: 'linear',
        position: 'right',
        title: { display: true, text: 'km/h' },
        grid: { display: false },
        ticks: { color: '#4CAF50' }
      },
      yPower: {
        type: 'linear',
        position: 'right',
        title: { display: true, text: 'Watt' },
        grid: { display: false },
        ticks: { color: '#3F51B5' }
      },
      yHr: {
        type: 'linear',
        position: 'left',
        title: { display: true, text: 'bpm' },
        grid: { display: false },
        ticks: { color: '#E53935' }
      }
    }
  };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private activityService: ActivityService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.activityId = this.route.snapshot.paramMap.get('id')!;
    this.loadReport();
  }

  loadReport(): void {
    this.loading = true;
    this.activityService.getReport(this.activityId).subscribe({
      next: (report) => {
        this.report = report;
        this.buildCharts(report);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.snackBar.open('Report nicht gefunden. Bitte zuerst analysieren.', 'OK', { duration: 4000 });
        this.router.navigate(['/']);
      }
    });
  }

  private buildCharts(report: Report): void {
    const streams = report.streams;
    const distKm = (streams.distance || []).map(d => +(d / 1000).toFixed(2));

    // Downsampling für Performance (max 500 Punkte)
    const step = Math.max(1, Math.floor(distKm.length / 500));
    const sampledDist = distKm.filter((_, i) => i % step === 0);
    const sampledWatts = (streams.watts || []).filter((_, i) => i % step === 0);
    const sampledHr = (streams.heartrate || []).filter((_, i) => i % step === 0);
    const sampledCadence = (streams.cadence || []).filter((_, i) => i % step === 0);

    // Power Chart
    this.powerChartData = {
      labels: sampledDist,
      datasets: [{
        label: 'Leistung (W)',
        data: sampledWatts,
        borderColor: '#3F51B5',
        backgroundColor: 'rgba(63,81,181,0.08)',
        borderWidth: 1.5,
        pointRadius: 0,
        fill: true,
        tension: 0.2
      }]
    };

    // HR Chart
    this.hrChartData = {
      labels: sampledDist,
      datasets: [{
        label: 'Herzfrequenz (bpm)',
        data: sampledHr,
        borderColor: '#F44336',
        backgroundColor: 'rgba(244,67,54,0.08)',
        borderWidth: 1.5,
        pointRadius: 0,
        fill: true,
        tension: 0.2
      }]
    };

    // Cadence Chart
    this.cadenceChartData = {
      labels: sampledDist,
      datasets: [{
        label: 'Kadenz (rpm)',
        data: sampledCadence,
        borderColor: '#4CAF50',
        backgroundColor: 'rgba(76,175,80,0.08)',
        borderWidth: 1.5,
        pointRadius: 0,
        fill: true,
        tension: 0.2
      }]
    };

    // Power Zones Chart
    this.powerZoneChartData = this.buildZoneChartData(report.powerZones);

    // HR Zones Chart
    this.hrZoneChartData = this.buildZoneChartData(report.hrZones);

    // Trend Chart
    const trend = [...(report.trend || [])].reverse();
    this.trendChartData = {
      labels: trend.map(t => new Date(t.activityDate).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' })),
      datasets: [
        {
          label: 'Score (1-10)',
          data: trend.map(t => t.overallScore ?? null),
          borderColor: '#FF9800',
          backgroundColor: 'rgba(255,152,0,0.1)',
          borderWidth: 2,
          pointRadius: 5,
          fill: false,
          tension: 0.3,
          yAxisID: 'yScore'
        },
        {
          label: 'Ø Geschw. (km/h)',
          data: trend.map(t => t.avgSpeedKmh != null ? +t.avgSpeedKmh.toFixed(1) : null),
          borderColor: '#4CAF50',
          backgroundColor: 'rgba(76,175,80,0.1)',
          borderWidth: 2,
          pointRadius: 5,
          fill: false,
          tension: 0.3,
          yAxisID: 'ySpeed'
        },
        {
          label: 'Ø Leistung (W)',
          data: trend.map(t => t.avgPowerWatts != null ? +t.avgPowerWatts.toFixed(0) : null),
          borderColor: '#3F51B5',
          backgroundColor: 'rgba(63,81,181,0.1)',
          borderWidth: 2,
          pointRadius: 5,
          fill: false,
          tension: 0.3,
          yAxisID: 'yPower'
        },
        {
          label: 'Ø HF (bpm)',
          data: trend.map(t => t.avgHeartRate != null ? +t.avgHeartRate.toFixed(0) : null),
          borderColor: '#E53935',
          backgroundColor: 'rgba(229,57,53,0.1)',
          borderWidth: 2,
          pointRadius: 5,
          fill: false,
          tension: 0.3,
          yAxisID: 'yHr'
        }
      ]
    };
  }

  private buildZoneChartData(zones: ZoneDistribution[]): ChartData<'doughnut'> {
    return {
      labels: zones.map(z => `${z.zoneName} – ${z.zoneLabel}`),
      datasets: [{
        data: zones.map(z => z.percentage),
        backgroundColor: zones.map(z => z.color),
        borderWidth: 2,
        borderColor: '#fff'
      }]
    };
  }

  private lineChartOptions(yLabel: string): ChartOptions<'line'> {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          mode: 'index',
          intersect: false,
          callbacks: {
            title: (items) => `${items[0].label} km`
          }
        }
      },
      scales: {
        x: {
          title: { display: true, text: 'Distanz (km)' },
          grid: { display: false },
          ticks: { maxTicksLimit: 10 }
        },
        y: {
          title: { display: true, text: yLabel },
          beginAtZero: false
        }
      }
    };
  }

  private zoneChartOptions(): ChartOptions<'doughnut'> {
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: true,
          position: 'right',
          labels: { font: { size: 12 }, padding: 12 }
        },
        tooltip: {
          callbacks: {
            label: (item) => ` ${item.label}: ${(item.raw as number).toFixed(1)}%`
          }
        }
      }
    };
  }

  getScoreColor(score: number): string {
    if (score >= 8) return '#4CAF50';
    if (score >= 6) return '#FF9800';
    return '#F44336';
  }

  formatDuration(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return `${h}:${String(m).padStart(2, '0')} h`;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('de-DE', {
      weekday: 'long', day: '2-digit', month: 'long', year: 'numeric'
    });
  }

  goBack(): void {
    this.router.navigate(['/']);
  }
}


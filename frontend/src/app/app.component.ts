import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivityService } from './services/activity.service';
import { AthleteThresholds } from './models/activity.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, CommonModule, MatToolbarModule, MatIconModule, MatChipsModule, MatTooltipModule],
  template: `
    <mat-toolbar color="primary" class="app-toolbar">
      <mat-icon>directions_bike</mat-icon>
      <span class="toolbar-title">Cycling Analysis</span>

      @if (thresholds) {
        <div class="athlete-info">
          @if (thresholds.name) {
            <span class="athlete-name">
              <mat-icon>person</mat-icon> {{ thresholds.name }}
            </span>
            <span class="divider">|</span>
          }
          @if (thresholds.ftp) {
            <span class="threshold-chip" matTooltip="Functional Threshold Power">
              <mat-icon>bolt</mat-icon> FTP {{ thresholds.ftp }} W
            </span>
          }
          @if (thresholds.lthr) {
            <span class="threshold-chip" matTooltip="Laktatschwellen-Herzfrequenz">
              <mat-icon>favorite</mat-icon> LTHR {{ thresholds.lthr }} bpm
            </span>
          }
          @if (thresholds.maxHr) {
            <span class="threshold-chip" matTooltip="Maximale Herzfrequenz">
              <mat-icon>monitor_heart</mat-icon> HFmax {{ thresholds.maxHr }} bpm
            </span>
          }
          @if (thresholds.weight) {
            <span class="threshold-chip" matTooltip="Gewicht">
              <mat-icon>scale</mat-icon> {{ thresholds.weight }} kg
            </span>
          }
        </div>
      }

      <span class="spacer"></span>
      <span class="toolbar-subtitle">Powered by Gemini AI</span>
    </mat-toolbar>
    <router-outlet />
  `,
  styles: [`
    .app-toolbar {
      position: sticky;
      top: 0;
      z-index: 100;
      gap: 12px;
      flex-wrap: wrap;
      min-height: 56px;
    }
    .toolbar-title {
      font-size: 1.2rem;
      font-weight: 500;
      letter-spacing: 0.5px;
      white-space: nowrap;
    }
    .toolbar-subtitle {
      font-size: 0.8rem;
      opacity: 0.7;
      white-space: nowrap;
    }
    .spacer { flex: 1; }
    .athlete-info {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .athlete-name {
      display: flex;
      align-items: center;
      gap: 4px;
      font-weight: 600;
      font-size: 0.9rem;
    }
    .divider {
      opacity: 0.4;
    }
    .threshold-chip {
      display: flex;
      align-items: center;
      gap: 3px;
      background: rgba(255,255,255,0.15);
      border-radius: 16px;
      padding: 3px 10px;
      font-size: 0.8rem;
      font-weight: 500;
      white-space: nowrap;
      cursor: default;
      mat-icon {
        font-size: 14px;
        height: 14px;
        width: 14px;
      }
    }
  `]
})
export class AppComponent implements OnInit {
  thresholds: AthleteThresholds | null = null;

  constructor(private activityService: ActivityService) {}

  ngOnInit(): void {
    this.activityService.getAthleteThresholds().subscribe({
      next: (t) => {
        console.log('Athleten-Daten:', t);
        this.thresholds = t;
      },
      error: (e) => console.error('Fehler beim Laden der Athleten-Daten:', e)
    });
  }
}


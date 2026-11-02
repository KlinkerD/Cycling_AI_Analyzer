import { Component, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';
import { ActivityService } from './services/activity.service';
import { AthleteThresholdsDto } from './api';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, CommonModule,
    MatToolbarModule, MatIconModule, MatChipsModule, MatTooltipModule, MatButtonModule
  ],
  template: `
    <mat-toolbar color="primary" class="app-toolbar">
      <mat-icon>directions_bike</mat-icon>
      <span class="toolbar-title">Cycling Analysis</span>

      <nav class="main-nav">
        <a mat-button routerLink="/" routerLinkActive="active-link"
           [routerLinkActiveOptions]="{ exact: true }">
          <mat-icon>list</mat-icon> Aktivitäten
        </a>
        <a mat-button routerLink="/overview" routerLinkActive="active-link">
          <mat-icon>insights</mat-icon> Übersicht
        </a>
      </nav>

      @if (thresholds) {
        <div class="athlete-info">
          @if (thresholds.name) {
            <span class="athlete-name">
              <mat-icon>person</mat-icon> {{ thresholds.name }}
            </span>
            <span class="divider">|</span>
          }
          @if (thresholds.ftp) {
            <span class="threshold-chip" matTooltip="Functional Threshold Power für Outdoor-Fahrten">
              <mat-icon>bolt</mat-icon> FTP Outdoor {{ thresholds.ftp }} W
            </span>
          }
          @if (thresholds.indoorFtp) {
            <span class="threshold-chip" matTooltip="Functional Threshold Power für Indoor-Fahrten (Rolle / Smart-Trainer)">
              <mat-icon>home</mat-icon> Indoor {{ thresholds.indoorFtp }} W
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
      <button mat-icon-button (click)="toggleTheme()"
              [matTooltip]="darkMode ? 'Heller Modus' : 'Dunkler Modus'">
        <mat-icon>{{ darkMode ? 'light_mode' : 'dark_mode' }}</mat-icon>
      </button>
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
    .main-nav {
      display: flex;
      gap: 4px;
      margin-left: 8px;
      a {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        color: rgba(255,255,255,0.85);
        font-weight: 500;
        mat-icon { font-size: 18px; height: 18px; width: 18px; }
        &.active-link {
          background: rgba(255,255,255,0.18);
          color: #fff;
        }
      }
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
  thresholds: AthleteThresholdsDto | null = null;
  darkMode = false;

  private static readonly THEME_KEY = 'cycling-theme';

  constructor(private activityService: ActivityService) {}

  toggleTheme(): void {
    this.darkMode = !this.darkMode;
    this.applyTheme();
    localStorage.setItem(AppComponent.THEME_KEY, this.darkMode ? 'dark' : 'light');
  }

  private applyTheme(): void {
    document.body.classList.toggle('dark-theme', this.darkMode);
  }

  private initTheme(): void {
    const stored = localStorage.getItem(AppComponent.THEME_KEY);
    if (stored) {
      this.darkMode = stored === 'dark';
    } else {
      this.darkMode = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false;
    }
    this.applyTheme();
  }

  ngOnInit(): void {
    this.initTheme();
    this.activityService.getAthleteThresholds().subscribe({
      next: (t) => {
        console.log('Athleten-Daten:', t);
        this.thresholds = t;
      },
      error: (e) => console.error('Fehler beim Laden der Athleten-Daten:', e)
    });
  }
}


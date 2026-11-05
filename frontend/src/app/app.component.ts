import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { filter, map } from 'rxjs';
import { MatSidenavContent, MatSidenavModule } from '@angular/material/sidenav';
import { MatIconModule, MatIconRegistry } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AthleteStore } from './services/athlete.store';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  exact: boolean;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatIconModule, MatButtonModule,
    MatTooltipModule, MatDialogModule, MatSnackBarModule
  ],
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav class="sidebar"
                   [mode]="mobile() ? 'over' : 'side'"
                   [opened]="!mobile() || menuOpen()"
                   (closedStart)="menuOpen.set(false)">
        <div class="brand">
          <span class="logo"><mat-icon>directions_bike</mat-icon></span>
          <span class="brand-name">Cycling Analyzer</span>
        </div>

        <nav class="nav">
          @for (item of nav; track item.path) {
            <a class="nav-item" [routerLink]="item.path"
               routerLinkActive="active" [routerLinkActiveOptions]="{ exact: item.exact }"
               (click)="menuOpen.set(false)">
              <mat-icon>{{ item.icon }}</mat-icon>
              <span>{{ item.label }}</span>
            </a>
          }
        </nav>

        <span class="grow"></span>

        @if (athlete(); as a) {
          <section class="athlete">
            <div class="athlete-name">
              <mat-icon>person</mat-icon>
              <span class="grow">{{ a.name || 'Athlet' }}</span>
              <button mat-icon-button class="edit" (click)="editThresholds()"
                      matTooltip="Schwellenwerte bearbeiten" aria-label="Schwellenwerte bearbeiten">
                <mat-icon>edit</mat-icon>
              </button>
            </div>
            <dl class="athlete-values">
              @if (a.ftp) {
                <div matTooltip="Functional Threshold Power outdoor" matTooltipPosition="right">
                  <dt>FTP</dt><dd>{{ a.ftp }}<small>W</small></dd>
                </div>
              }
              @if (a.indoorFtp) {
                <div matTooltip="FTP auf der Rolle / Smart-Trainer" matTooltipPosition="right">
                  <dt>FTP indoor</dt><dd>{{ a.indoorFtp }}<small>W</small></dd>
                </div>
              }
              @if (a.lthr) {
                <div matTooltip="Laktatschwellen-Herzfrequenz" matTooltipPosition="right">
                  <dt>LTHR</dt><dd>{{ a.lthr }}<small>bpm</small></dd>
                </div>
              }
              @if (a.maxHr) {
                <div matTooltip="Maximale Herzfrequenz" matTooltipPosition="right">
                  <dt>HFmax</dt><dd>{{ a.maxHr }}<small>bpm</small></dd>
                </div>
              }
              @if (a.weight) {
                <div>
                  <dt>Gewicht</dt><dd>{{ a.weight }}<small>kg</small></dd>
                </div>
              }
              @if (a.ftp && a.weight) {
                <div matTooltip="FTP pro Kilogramm" matTooltipPosition="right">
                  <dt>W/kg</dt><dd>{{ (a.ftp / a.weight).toFixed(2) }}</dd>
                </div>
              }
            </dl>
          </section>
        }

        <p class="powered">KI-Analyse mit Gemini</p>
      </mat-sidenav>

      <mat-sidenav-content class="content">
        @if (mobile()) {
          <header class="topbar">
            <button mat-icon-button (click)="menuOpen.set(true)" aria-label="Menü öffnen">
              <mat-icon>menu</mat-icon>
            </button>
            <span class="brand-name">Cycling Analyzer</span>
          </header>
        }
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .shell {
      height: 100vh;
      height: 100dvh;
      background: var(--bg);
    }
    .sidebar {
      width: var(--sidebar-w);
      background: var(--surface);
      border-right: 1px solid var(--border);
      border-radius: 0;
      padding: 18px 12px 14px;
      --mat-sidenav-container-shape: 0;
    }
    ::ng-deep .sidebar .mat-drawer-inner-container {
      display: flex;
      flex-direction: column;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 0 8px 22px;
    }
    .logo {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: 9px;
      background: var(--accent);
      color: var(--on-accent);
      mat-icon { font-size: 20px; width: 20px; height: 20px; }
    }
    .brand-name {
      font-weight: 700;
      font-size: 15px;
      letter-spacing: -0.01em;
    }
    .nav {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .nav-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 9px 10px;
      border-radius: var(--radius-sm);
      color: var(--text-muted);
      text-decoration: none;
      font-weight: 500;
      transition: background .12s, color .12s;
      mat-icon { font-size: 20px; width: 20px; height: 20px; }
      &:hover { background: var(--surface-2); color: var(--text); }
      &.active {
        background: var(--accent-soft);
        color: var(--accent);
      }
    }
    .grow { flex: 1; }
    .athlete {
      border-top: 1px solid var(--border);
      padding: 14px 8px 4px;
    }
    .athlete-name {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      margin-bottom: 6px;
      mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--text-muted); }
      .edit {
        --mat-icon-button-state-layer-size: 32px;
        width: 32px;
        height: 32px;
        padding: 7px;
        margin-right: -6px;
        &:hover mat-icon { color: var(--accent); }
      }
    }
    .athlete-values {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px 10px;
      margin: 0;
      div { min-width: 0; cursor: default; }
      dt {
        font-size: 10px;
        text-transform: uppercase;
        letter-spacing: .06em;
        color: var(--text-faint);
      }
      dd {
        margin: 0;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
        small { font-weight: 500; color: var(--text-muted); margin-left: 2px; font-size: 11px; }
      }
    }
    .powered {
      margin-top: 14px;
      padding: 0 8px;
      font-size: 11px;
      color: var(--text-faint);
    }
    .content {
      background: var(--bg);
    }
    .topbar {
      position: sticky;
      top: 0;
      z-index: 10;
      display: flex;
      align-items: center;
      gap: 6px;
      height: 52px;
      padding: 0 6px;
      background: rgba(13, 16, 22, 0.92);
      backdrop-filter: blur(8px);
      border-bottom: 1px solid var(--border);
    }
  `]
})
export class AppComponent implements OnInit {
  readonly nav: NavItem[] = [
    { path: '/', label: 'Kalender', icon: 'calendar_month', exact: true },
    { path: '/overview', label: 'Übersicht', icon: 'insights', exact: false },
    { path: '/stats', label: 'Statistik', icon: 'bar_chart', exact: false }
  ];

  readonly menuOpen = signal(false);
  private readonly content = viewChild(MatSidenavContent);
  readonly mobile = toSignal(
    inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map(s => s.matches)),
    { initialValue: false }
  );

  private readonly store = inject(AthleteStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  readonly athlete = this.store.thresholds;

  constructor() {
    inject(MatIconRegistry).setDefaultFontSetClass('material-icons-round');
    // Nach jeder Navigation oben anfangen (gescrollt wird im Inhaltsbereich, nicht im Fenster)
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => {
      this.content()?.scrollTo({ top: 0 });
    });
  }

  ngOnInit(): void {
    this.store.load().subscribe({
      error: e => console.error('Fehler beim Laden der Athleten-Daten:', e)
    });
  }

  /** Dialog wird erst beim ersten Öffnen geladen (Formularfelder gehören nicht ins Start-Bundle). */
  async editThresholds(): Promise<void> {
    this.menuOpen.set(false);
    const { ThresholdsDialogComponent } = await import('./shared/athlete/thresholds-dialog.component');
    this.dialog.open(ThresholdsDialogComponent, {
      data: this.athlete(),
      width: '520px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'first-tabbable'
    }).afterClosed().subscribe(saved => {
      if (saved) this.snackBar.open('In intervals.icu gespeichert, Zonen sind neu berechnet.', 'OK', { duration: 4000 });
    });
  }
}

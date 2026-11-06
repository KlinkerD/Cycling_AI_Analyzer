import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Kalender · Cycling Analyzer',
    loadComponent: () => import('./pages/calendar/calendar.component')
      .then(m => m.CalendarComponent)
  },
  {
    path: 'overview',
    title: 'Übersicht · Cycling Analyzer',
    loadComponent: () => import('./pages/overview/overview.component')
      .then(m => m.OverviewComponent)
  },
  {
    path: 'stats',
    title: 'Statistik · Cycling Analyzer',
    loadComponent: () => import('./pages/stats/stats.component')
      .then(m => m.StatsComponent)
  },
  {
    path: 'settings',
    title: 'Einstellungen · Cycling Analyzer',
    loadComponent: () => import('./pages/settings/settings.component')
      .then(m => m.SettingsComponent),
    canDeactivate: [(c: import('./pages/settings/settings.component').SettingsComponent) => c.confirmLeave()]
  },
  {
    path: 'report/:id',
    title: 'Report · Cycling Analyzer',
    loadComponent: () => import('./pages/report/report.component')
      .then(m => m.ReportComponent)
  },
  { path: '**', redirectTo: '' }
];

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
    path: 'report/:id',
    title: 'Report · Cycling Analyzer',
    loadComponent: () => import('./pages/report/report.component')
      .then(m => m.ReportComponent)
  },
  { path: '**', redirectTo: '' }
];

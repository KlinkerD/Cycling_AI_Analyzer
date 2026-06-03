import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/activity-list/activity-list.component')
      .then(m => m.ActivityListComponent)
  },
  {
    path: 'report/:id',
    loadComponent: () => import('./pages/report/report.component')
      .then(m => m.ReportComponent)
  },
  { path: '**', redirectTo: '' }
];


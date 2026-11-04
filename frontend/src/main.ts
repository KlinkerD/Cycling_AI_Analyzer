import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { setupCharts } from './app/shared/charts/chart-setup';

setupCharts();

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));


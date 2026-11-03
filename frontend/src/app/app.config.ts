import { ApplicationConfig, LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeDe from '@angular/common/locales/de';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE } from '@angular/material/core';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { routes } from './app.routes';
import { provideApi as provideReportApi } from './api/report';
import { provideApi as provideFitnessApi } from './api/fitness';
import { provideApi as provideRecommendationApi } from './api/recommendation';
import { GERMAN_DATE_FORMATS, GermanDateAdapter } from './shared/german-date-adapter';

registerLocaleData(localeDe);

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(),
    // Generierte API-Clients: relative URLs (/api/reports, /api/fitness, /api/recommendation);
    // Dev-Proxy bzw. nginx leiten je nach Praefix an den zustaendigen Service weiter.
    provideReportApi(''),
    provideFitnessApi(''),
    provideRecommendationApi(''),
    provideCharts(withDefaultRegisterables()),
    { provide: LOCALE_ID, useValue: 'de-DE' },
    { provide: MAT_DATE_LOCALE, useValue: 'de-DE' },
    { provide: DateAdapter, useClass: GermanDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: GERMAN_DATE_FORMATS }
  ]
};

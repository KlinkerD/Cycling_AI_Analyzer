import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AthleteZonesDto, DailyRecommendationDto, OverviewApi, WellnessPointDto } from '../api';
import { toIsoDate } from './date-params';

/** Duenne Fassade ueber den generierten {@link OverviewApi}-Client (OpenAPI Generator). */
@Injectable({ providedIn: 'root' })
export class OverviewService {

  constructor(private api: OverviewApi) {}

  getWellness(from: Date, to: Date, refresh = false): Observable<WellnessPointDto[]> {
    return this.api.getWellness(toIsoDate(from), toIsoDate(to), refresh || undefined);
  }

  getZones(refresh = false): Observable<AthleteZonesDto> {
    return this.api.getZones(refresh || undefined);
  }

  getRecommendation(): Observable<DailyRecommendationDto> {
    return this.api.getRecommendation();
  }

  refreshRecommendation(): Observable<DailyRecommendationDto> {
    return this.api.refreshRecommendation();
  }
}

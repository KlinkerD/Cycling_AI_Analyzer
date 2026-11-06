import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AthleteThresholdsUpdateDto,
  AthleteZonesDto,
  DailyRecommendationDto,
  FitnessApi,
  RecommendationApi,
  WellnessPointDto,
} from '../api/training';
import { toIsoDate } from './date-params';

/**
 * Duenne Fassade ueber die generierten Clients des training-service ({@link FitnessApi},
 * {@link RecommendationApi}).
 */
@Injectable({ providedIn: 'root' })
export class OverviewService {

  constructor(private fitness: FitnessApi, private recommendation: RecommendationApi) {}

  getWellness(from: Date, to: Date, refresh = false): Observable<WellnessPointDto[]> {
    return this.fitness.getWellness(toIsoDate(from), toIsoDate(to), refresh || undefined);
  }

  /** Schwellenwerte und Zonen des Athleten. */
  getAthlete(refresh = false): Observable<AthleteZonesDto> {
    return this.fitness.getAthlete(refresh || undefined);
  }

  /** Schreibt Schwellenwerte und Zonen nach intervals.icu (null bzw. leere Liste = unveraendert). */
  updateThresholds(update: AthleteThresholdsUpdateDto): Observable<AthleteZonesDto> {
    return this.fitness.updateThresholds(update);
  }

  getRecommendation(): Observable<DailyRecommendationDto> {
    return this.recommendation.getRecommendation();
  }

  refreshRecommendation(): Observable<DailyRecommendationDto> {
    return this.recommendation.refreshRecommendation();
  }
}

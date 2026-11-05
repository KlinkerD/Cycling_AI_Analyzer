import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { StatsApi, StatsDto, SyncStatusDto } from '../api/stats';
import { isoDay } from '../shared/format';

export type StatsGrouping = StatsDto.GroupByEnum;

/**
 * Dünne Fassade über den generierten Client des stats-service ({@link StatsApi}).
 * Datumswerte gehen als lokales Datum raus (nicht UTC), damit der 1.1. auch der 1.1. bleibt.
 */
@Injectable({ providedIn: 'root' })
export class StatsService {

  constructor(private api: StatsApi) {}

  getStats(from: Date, to: Date, groupBy: StatsGrouping): Observable<StatsDto> {
    return this.api.getStats(isoDay(from), isoDay(to), groupBy);
  }

  getStatus(): Observable<SyncStatusDto> {
    return this.api.getStatus();
  }
}

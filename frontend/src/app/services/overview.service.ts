import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AthleteZones, WellnessPoint } from '../models/activity.model';

@Injectable({ providedIn: 'root' })
export class OverviewService {

  private readonly baseUrl = '/api/overview';

  constructor(private http: HttpClient) {}

  getWellness(from: Date, to: Date, refresh = false): Observable<WellnessPoint[]> {
    let params = new HttpParams()
      .set('from', this.formatDate(from))
      .set('to', this.formatDate(to));
    if (refresh) params = params.set('refresh', 'true');
    return this.http.get<WellnessPoint[]>(`${this.baseUrl}/wellness`, { params });
  }

  getZones(refresh = false): Observable<AthleteZones> {
    let params = new HttpParams();
    if (refresh) params = params.set('refresh', 'true');
    return this.http.get<AthleteZones>(`${this.baseUrl}/zones`, { params });
  }

  private formatDate(date: Date): string {
    return date.toISOString().split('T')[0];
  }
}


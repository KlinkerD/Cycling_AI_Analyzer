import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Activity, AthleteThresholds, Report } from '../models/activity.model';

@Injectable({ providedIn: 'root' })
export class ActivityService {

  private readonly baseUrl = '/api/activities';

  constructor(private http: HttpClient) {}

  getAthleteThresholds(): Observable<AthleteThresholds> {
    return this.http.get<AthleteThresholds>(`${this.baseUrl}/athlete-thresholds`);
  }

  getAnalyzedIds(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/analyzed-ids`);
  }

  getActivities(from: Date, to: Date): Observable<Activity[]> {
    const params = new HttpParams()
      .set('from', this.formatDate(from))
      .set('to', this.formatDate(to));
    return this.http.get<Activity[]>(this.baseUrl, { params });
  }

  analyzeActivity(activity: Activity): Observable<Report> {
    return this.http.post<Report>(`${this.baseUrl}/${activity.id}/analyze`, activity);
  }

  getReport(activityId: string): Observable<Report> {
    return this.http.get<Report>(`${this.baseUrl}/${activityId}/report`);
  }

  private formatDate(date: Date): string {
    return date.toISOString().split('T')[0];
  }
}


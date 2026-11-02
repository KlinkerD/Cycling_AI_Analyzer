import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, first, of, switchMap, timer } from 'rxjs';
import { Activity, AnalysisJob, AthleteThresholds, Report } from '../models/activity.model';

/** Abfrage-Takt fuer laufende Analysen. */
const POLL_INTERVAL_MS = 3000;

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

  /** Startet die KI-Analyse (202 + Job). Laeuft schon eine, kommt deren Job zurueck. */
  startAnalysis(activity: Activity): Observable<AnalysisJob> {
    return this.http.post<AnalysisJob>(`${this.baseUrl}/${activity.id}/analyze`, activity);
  }

  getAnalysisJob(jobId: string): Observable<AnalysisJob> {
    return this.http.get<AnalysisJob>(`${this.baseUrl}/analysis-jobs/${jobId}`);
  }

  /**
   * Startet die Analyse und fragt den Job ab, bis er fertig ist. Liefert genau einen Job
   * mit Status DONE oder FAILED (Timeout erledigt das Backend).
   */
  analyzeActivity(activity: Activity): Observable<AnalysisJob> {
    return this.startAnalysis(activity).pipe(
      switchMap(job => job.status !== 'PENDING'
        ? of(job)
        : timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS).pipe(
            switchMap(() => this.getAnalysisJob(job.jobId)),
            first(current => current.status !== 'PENDING')
          ))
    );
  }

  getReport(activityId: string): Observable<Report> {
    return this.http.get<Report>(`${this.baseUrl}/${activityId}/report`);
  }

  /** Sendet den Report an alle aktiven Kanaele (Mail, Discord). 409 = kein Kanal aktiv. */
  resendReport(activityId: string): Observable<{ status: string; channels: ('MAIL' | 'DISCORD')[] }> {
    return this.http.post<{ status: string; channels: ('MAIL' | 'DISCORD')[] }>(
      `${this.baseUrl}/${activityId}/resend`, {}
    );
  }

  private formatDate(date: Date): string {
    return date.toISOString().split('T')[0];
  }
}


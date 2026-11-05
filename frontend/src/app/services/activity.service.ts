import { Injectable } from '@angular/core';
import { Observable, first, of, switchMap, timer } from 'rxjs';
import {
  ActivityDto,
  AnalysisJobDto,
  FtpDetectionDto,
  ReportDto,
  ReportsApi,
  ResendResultDto
} from '../api/report';
import { toIsoDate } from './date-params';

/** Abfrage-Takt fuer laufende Analysen. */
const POLL_INTERVAL_MS = 3000;
/** Abfrage-Takt fuer die FTP-Test-Erkennung (dauert meist nur wenige Sekunden). */
const FTP_POLL_INTERVAL_MS = 2000;

/**
 * Duenne Fassade ueber den generierten {@link ReportsApi}-Client des report-service (OpenAPI Generator):
 * wandelt Datumswerte um und kapselt das Polling der asynchronen Analyse.
 */
@Injectable({ providedIn: 'root' })
export class ActivityService {

  constructor(private api: ReportsApi) {}

  /** Aktivitaeten im Zeitraum; {@code analyzed} sagt, ob es schon einen Report gibt. */
  getActivities(from: Date, to: Date): Observable<ActivityDto[]> {
    return this.api.getActivities(toIsoDate(from), toIsoDate(to));
  }

  /** Startet die KI-Analyse (202 + Job). Laeuft schon eine, kommt deren Job zurueck. */
  startAnalysis(activity: ActivityDto): Observable<AnalysisJobDto> {
    return this.api.startAnalysis(activity.id, activity);
  }

  getAnalysisJob(jobId: string): Observable<AnalysisJobDto> {
    return this.api.getAnalysisJob(jobId);
  }

  /**
   * Startet die Analyse und fragt den Job ab, bis er fertig ist. Liefert genau einen Job
   * mit Status DONE oder FAILED (Timeout erledigt der report-service).
   */
  analyzeActivity(activity: ActivityDto): Observable<AnalysisJobDto> {
    return this.startAnalysis(activity).pipe(
      switchMap(job => job.status !== AnalysisJobDto.StatusEnum.PENDING
        ? of(job)
        : timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS).pipe(
            switchMap(() => this.getAnalysisJob(job.jobId)),
            first(current => current.status !== AnalysisJobDto.StatusEnum.PENDING)
          ))
    );
  }

  getReport(activityId: string): Observable<ReportDto> {
    return this.api.getReport(activityId);
  }

  /** Sendet den Report an alle aktiven Kanaele (Mail, Discord). 409 = kein Kanal aktiv, 503 = Service weg. */
  resendReport(activityId: string): Observable<ResendResultDto> {
    return this.api.resendReport(activityId);
  }

  /** Letztes Ergebnis der FTP-Test-Erkennung; 404, wenn sie fuer die Fahrt nie ausgeloest wurde. */
  getFtpDetection(activityId: string): Observable<FtpDetectionDto> {
    return this.api.getFtpDetection(activityId);
  }

  /** Startet die FTP-Test-Erkennung (202). Laeuft schon eine, kommt deren Status zurueck. */
  startFtpDetection(activityId: string): Observable<FtpDetectionDto> {
    return this.api.startFtpDetection(activityId);
  }

  /**
   * Fragt die FTP-Test-Erkennung ab, bis sie nicht mehr PENDING ist, und liefert genau dieses Ergebnis
   * (Timeout erledigt der report-service).
   */
  pollFtpDetection(activityId: string): Observable<FtpDetectionDto> {
    return timer(FTP_POLL_INTERVAL_MS, FTP_POLL_INTERVAL_MS).pipe(
      switchMap(() => this.getFtpDetection(activityId)),
      first(d => d.status !== FtpDetectionDto.StatusEnum.PENDING)
    );
  }
}

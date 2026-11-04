import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, tap } from 'rxjs';
import { AthleteThresholdsUpdateDto, AthleteZonesDto } from '../api/training';
import { OverviewService } from './overview.service';

/**
 * Schwellenwerte und Zonen des Athleten, einmal geladen und von Seitenleiste und Übersicht geteilt.
 * Nach dem Speichern sehen alle Ansichten sofort die neuen Werte.
 */
@Injectable({ providedIn: 'root' })
export class AthleteStore {
  private readonly api = inject(OverviewService);

  readonly zones = signal<AthleteZonesDto | null>(null);
  readonly loading = signal(false);
  readonly thresholds = computed(() => this.zones()?.thresholds ?? null);

  /** Lädt die Daten; mit {@code refresh} werden die Caches im Backend vorher geleert. */
  load(refresh = false): Observable<AthleteZonesDto> {
    this.loading.set(true);
    return this.api.getAthlete(refresh).pipe(
      tap(z => this.zones.set(z)),
      finalize(() => this.loading.set(false))
    );
  }

  /** Schreibt neue Werte nach intervals.icu; Zonen kommen neu berechnet zurück. */
  save(update: AthleteThresholdsUpdateDto): Observable<AthleteZonesDto> {
    return this.api.updateThresholds(update).pipe(tap(z => this.zones.set(z)));
  }
}

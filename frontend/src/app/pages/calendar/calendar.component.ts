import {
  AfterViewInit, Component, DestroyRef, ElementRef, OnDestroy, inject, signal, viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivityService } from '../../services/activity.service';
import { ActivityDto } from '../../api/report';
import { addDays, formatDuration, isoDay, startOfWeek } from '../../shared/format';

interface Day {
  date: Date;
  iso: string;
  today: boolean;
  future: boolean;
  activities: ActivityDto[];
}

interface Week {
  key: string;
  label: string;
  /** Monatsüberschrift, wenn mit dieser Woche ein neuer Monat beginnt. */
  monthLabel: string | null;
  days: Day[];
  empty: boolean;
}

/** Wochen pro Nachladen. */
const CHUNK_WEEKS = 8;
/** So viele leere Blöcke in Folge, dann wird nicht mehr automatisch nachgeladen. */
const MAX_EMPTY_CHUNKS = 3;

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [
    DecimalPipe, NgTemplateOutlet, MatButtonModule, MatIconModule, MatMenuModule,
    MatProgressSpinnerModule, MatSnackBarModule, MatTooltipModule
  ],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.scss'
})
export class CalendarComponent implements AfterViewInit, OnDestroy {
  readonly weekdays = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
  readonly weeks = signal<Week[]>([]);
  readonly loading = signal(false);
  readonly exhausted = signal(false);
  readonly analyzing = signal<ReadonlySet<string>>(new Set());

  readonly formatDuration = formatDuration;

  private readonly sentinel = viewChild<ElementRef<HTMLElement>>('sentinel');
  private readonly top = viewChild<ElementRef<HTMLElement>>('top');
  private readonly activityService = inject(ActivityService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  /** Montag der ältesten bereits geladenen Woche. */
  private oldestMonday = addDays(startOfWeek(new Date()), 7);
  private emptyChunks = 0;
  private lastMonth: string | null = null;
  private observer?: IntersectionObserver;

  constructor() {
    this.loadMore();
  }

  ngAfterViewInit(): void {
    const el = this.sentinel()?.nativeElement;
    if (!el || typeof IntersectionObserver === 'undefined') return;
    this.observer = new IntersectionObserver(entries => {
      if (entries.some(e => e.isIntersecting) && !this.exhausted()) this.loadMore();
    }, { rootMargin: '800px 0px' });
    this.observer.observe(el);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  /** Lädt die nächsten {@link CHUNK_WEEKS} älteren Wochen. */
  loadMore(force = false): void {
    if (this.loading()) return;
    if (force) {
      this.exhausted.set(false);
      this.emptyChunks = 0;
    }
    const to = addDays(this.oldestMonday, -1);
    const from = addDays(this.oldestMonday, -7 * CHUNK_WEEKS);
    this.loading.set(true);

    // Mittag statt Mitternacht, damit die UTC-Umrechnung in toIsoDate den Tag nicht verschiebt.
    this.activityService.getActivities(this.atNoon(from), this.atNoon(to)).subscribe({
      next: data => {
        this.oldestMonday = from;
        const weeks = this.buildWeeks(from, data);
        this.weeks.update(list => [...list, ...weeks]);
        this.emptyChunks = data.length === 0 ? this.emptyChunks + 1 : 0;
        if (this.emptyChunks >= MAX_EMPTY_CHUNKS) this.exhausted.set(true);
        this.loading.set(false);
        // Sentinel noch sichtbar (z. B. grosser Bildschirm)? Dann weiterladen.
        queueMicrotask(() => this.checkSentinel());
      },
      error: err => {
        this.loading.set(false);
        this.exhausted.set(true);
        this.snackBar.open('Fehler beim Laden: ' + (err.error?.message || err.message), 'OK', { duration: 5000 });
      }
    });
  }

  scrollToToday(): void {
    this.top()?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  open(activity: ActivityDto): void {
    this.router.navigate(['/report', activity.id]);
  }

  analyze(activity: ActivityDto): void {
    this.analyzing.update(s => new Set(s).add(activity.id));
    const done = () => this.analyzing.update(s => {
      const next = new Set(s);
      next.delete(activity.id);
      return next;
    });
    // Die Analyse läuft im report-service weiter, auch wenn die Seite verlassen wird.
    this.activityService.analyzeActivity(activity).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: job => {
        done();
        if (job.status === 'DONE') {
          this.markAnalyzed(activity.id);
          const ref = this.snackBar.open(`„${activity.name ?? 'Fahrt'}“ ist analysiert.`, 'Report öffnen', { duration: 8000 });
          ref.onAction().subscribe(() => this.open(activity));
        } else {
          this.snackBar.open('Analyse fehlgeschlagen: ' + (job.error || 'unbekannter Fehler'), 'OK', { duration: 6000 });
        }
      },
      error: err => {
        done();
        this.snackBar.open('Analyse fehlgeschlagen: ' + (err.error?.message || err.message), 'OK', { duration: 6000 });
      }
    });
  }

  isAnalyzing(id: string): boolean {
    return this.analyzing().has(id);
  }

  km(meters: number | null): string {
    return meters == null ? '–' : (meters / 1000).toFixed(1);
  }

  kmh(ms: number | null): string {
    return ms == null ? '–' : (ms * 3.6).toFixed(1);
  }

  isIndoor(a: ActivityDto): boolean {
    return (a.type ?? '').toLowerCase().includes('virtual');
  }

  startTime(a: ActivityDto): string {
    const t = (a.startDateLocal ?? '').replace(' ', 'T').substring(11, 16);
    return t || '';
  }

  private markAnalyzed(id: string): void {
    this.weeks.update(list => list.map(w => ({
      ...w,
      days: w.days.map(d => ({
        ...d,
        activities: d.activities.map(a => a.id === id ? { ...a, analyzed: true } : a)
      }))
    })));
  }

  private checkSentinel(): void {
    const el = this.sentinel()?.nativeElement;
    if (!el || this.exhausted() || this.loading()) return;
    const rect = el.getBoundingClientRect();
    if (rect.top < window.innerHeight + 800) this.loadMore();
  }

  /** Baut die Wochen ab {@code from} (Montag), neueste zuerst. */
  private buildWeeks(from: Date, activities: ActivityDto[]): Week[] {
    const byDay = new Map<string, ActivityDto[]>();
    for (const a of activities) {
      const key = (a.startDateLocal ?? '').substring(0, 10);
      const list = byDay.get(key) ?? [];
      list.push(a);
      byDay.set(key, list);
    }
    for (const list of byDay.values()) {
      list.sort((x, y) => (x.startDateLocal ?? '').localeCompare(y.startDateLocal ?? ''));
    }

    const todayIso = isoDay(new Date());
    const weeks: Week[] = [];
    for (let w = CHUNK_WEEKS - 1; w >= 0; w--) {
      const monday = addDays(from, w * 7);
      const days: Day[] = [];
      for (let d = 0; d < 7; d++) {
        const date = addDays(monday, d);
        const iso = isoDay(date);
        days.push({ date, iso, today: iso === todayIso, future: iso > todayIso, activities: byDay.get(iso) ?? [] });
      }
      const sunday = days[6].date;
      // Monat der Woche = Monat ihres Donnerstags (wie bei Kalenderwochen)
      const thursday = days[3].date;
      const monthKey = `${thursday.getFullYear()}-${thursday.getMonth()}`;
      const monthLabel = monthKey !== this.lastMonth
        ? thursday.toLocaleDateString('de-DE', { month: 'long', year: 'numeric' })
        : null;
      this.lastMonth = monthKey;

      weeks.push({
        key: isoDay(monday),
        label: `KW ${this.isoWeek(thursday)} · ${monday.getDate()}.${monday.getMonth() !== sunday.getMonth() ? ' ' + this.shortMonth(monday) : ''} – ${sunday.getDate()}. ${this.shortMonth(sunday)}`,
        monthLabel,
        days,
        empty: days.every(d => d.activities.length === 0)
      });
    }
    return weeks;
  }

  private shortMonth(d: Date): string {
    return d.toLocaleDateString('de-DE', { month: 'short' }).replace('.', '');
  }

  private isoWeek(thursday: Date): number {
    const jan4 = new Date(thursday.getFullYear(), 0, 4);
    const week1Monday = startOfWeek(jan4);
    return Math.round((startOfWeek(thursday).getTime() - week1Monday.getTime()) / (7 * 864e5)) + 1;
  }

  private atNoon(d: Date): Date {
    const out = new Date(d);
    out.setHours(12, 0, 0, 0);
    return out;
  }
}

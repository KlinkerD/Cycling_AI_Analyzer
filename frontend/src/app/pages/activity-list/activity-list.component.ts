import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { ActivityService } from '../../services/activity.service';
import { ActivityDto } from '../../api';

interface MonthGroup {
  key: string;
  label: string;
  activities: ActivityDto[];
}

@Component({
  selector: 'app-activity-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatDividerModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatBadgeModule
  ],
  templateUrl: './activity-list.component.html',
  styleUrl: './activity-list.component.scss'
})
export class ActivityListComponent implements OnInit {

  filterForm: FormGroup;
  activities: ActivityDto[] = [];
  monthGroups: MonthGroup[] = [];
  analyzedIds = new Set<string>();
  loading = false;
  analyzingId: string | null = null;
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private fb: FormBuilder,
    private activityService: ActivityService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - 90);

    this.filterForm = this.fb.group({
      from: [from, Validators.required],
      to: [to, Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadActivities();
  }

  loadActivities(): void {
    if (this.filterForm.invalid) return;
    this.loading = true;
    this.activities = [];
    this.monthGroups = [];

    const { from, to } = this.filterForm.value;
    this.activityService.getActivities(from, to).subscribe({
      next: (data) => {
        this.activities = data;
        this.monthGroups = this.groupByMonth(data);
        this.loading = false;
        this.loadAnalyzedIds();
      },
      error: (err) => {
        this.loading = false;
        this.snackBar.open('Fehler beim Laden: ' + (err.error?.message || err.message), 'OK', { duration: 5000 });
      }
    });
  }

  /** Gruppiert nach Kalendermonat (lokales Startdatum), neueste Monate und Aktivitäten zuerst. */
  private groupByMonth(activities: ActivityDto[]): MonthGroup[] {
    const sorted = [...activities].sort((a, b) =>
      (b.startDateLocal ?? '').localeCompare(a.startDateLocal ?? ''));
    const groups = new Map<string, MonthGroup>();
    for (const activity of sorted) {
      const key = (activity.startDateLocal ?? '').substring(0, 7); // "JJJJ-MM"
      let group = groups.get(key);
      if (!group) {
        group = { key, label: this.formatMonth(key), activities: [] };
        groups.set(key, group);
      }
      group.activities.push(activity);
    }
    return [...groups.values()];
  }

  private formatMonth(key: string): string {
    const match = /^(\d{4})-(\d{2})$/.exec(key);
    if (!match) return 'Ohne Datum';
    const d = new Date(Number(match[1]), Number(match[2]) - 1, 1);
    return d.toLocaleDateString('de-DE', { month: 'long', year: 'numeric' });
  }

  private loadAnalyzedIds(): void {
    this.activityService.getAnalyzedIds().subscribe({
      next: (ids) => this.analyzedIds = new Set(ids),
      error: () => {}
    });
  }

  isAnalyzed(activityId: string): boolean {
    return this.analyzedIds.has(activityId);
  }

  analyzeActivity(activity: ActivityDto): void {
    this.analyzingId = activity.id;
    // Die Analyse laeuft asynchron im Backend weiter, auch wenn die Seite verlassen wird.
    this.activityService.analyzeActivity(activity).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (job) => {
        this.analyzingId = null;
        if (job.status === 'DONE') {
          this.analyzedIds.add(activity.id);
          this.router.navigate(['/report', activity.id]);
        } else {
          this.snackBar.open('Analyse fehlgeschlagen: ' + (job.error || 'unbekannter Fehler'), 'OK', { duration: 6000 });
        }
      },
      error: (err) => {
        this.analyzingId = null;
        this.snackBar.open('Analyse fehlgeschlagen: ' + (err.error?.message || err.message), 'OK', { duration: 6000 });
      }
    });
  }

  viewReport(activity: ActivityDto): void {
    this.router.navigate(['/report', activity.id]);
  }

  formatDistance(meters: number | null): string {
    if (meters == null) return '–';
    return (meters / 1000).toFixed(1) + ' km';
  }

  formatSpeed(ms: number): string {
    return (ms * 3.6).toFixed(1) + ' km/h';
  }

  formatDate(dateStr: string | null): string {
    if (!dateStr) return '–';
    // Normalize: replace space with T for ISO 8601 compatibility
    const normalized = dateStr.replace(' ', 'T');
    const d = new Date(normalized);
    if (isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString('de-DE', {
      weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric'
    });
  }

  formatDuration(seconds: number | null): string {
    if (seconds == null || isNaN(seconds)) return '–';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return `${h}:${String(m).padStart(2, '0')} h`;
  }
}

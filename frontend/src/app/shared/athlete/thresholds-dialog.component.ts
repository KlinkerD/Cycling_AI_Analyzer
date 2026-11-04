import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AthleteThresholdsDto, AthleteThresholdsUpdateDto } from '../../api/training';
import { AthleteStore } from '../../services/athlete.store';

type Field = 'ftp' | 'indoorFtp' | 'lthr' | 'maxHr' | 'weight';

interface FieldSpec {
  key: Field;
  label: string;
  unit: string;
  min: number;
  max: number;
  step: number;
  hint: string;
}

/** Grenzen wie im Backend (IntervalsThresholdsUpdate). */
const FIELDS: FieldSpec[] = [
  { key: 'ftp', label: 'FTP outdoor', unit: 'W', min: 50, max: 700, step: 1, hint: 'Basis der Leistungszonen' },
  { key: 'indoorFtp', label: 'FTP indoor', unit: 'W', min: 50, max: 700, step: 1, hint: 'Rolle / Smart-Trainer' },
  { key: 'lthr', label: 'LTHR', unit: 'bpm', min: 80, max: 230, step: 1, hint: 'Laktatschwellen-HF' },
  { key: 'maxHr', label: 'HFmax', unit: 'bpm', min: 100, max: 240, step: 1, hint: 'Maximale Herzfrequenz' },
  { key: 'weight', label: 'Gewicht', unit: 'kg', min: 30, max: 200, step: 0.1, hint: 'Für W/kg' }
];

@Component({
  selector: 'app-thresholds-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatProgressSpinnerModule
  ],
  template: `
    <h2 mat-dialog-title>Schwellenwerte bearbeiten</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content>
        <p class="intro">Die Werte werden direkt in deinen intervals.icu-Einstellungen gespeichert. Zonen werden danach neu berechnet.</p>

        <div class="grid">
          @for (f of fields; track f.key) {
            <mat-form-field appearance="outline">
              <mat-label>{{ f.label }}</mat-label>
              <input matInput type="number" inputmode="decimal"
                     [formControlName]="f.key" [min]="f.min" [max]="f.max" [step]="f.step">
              <span matTextSuffix>{{ f.unit }}</span>
              @if (form.controls[f.key].invalid) {
                <mat-error>{{ f.min }}–{{ f.max }} {{ f.unit }}</mat-error>
              } @else {
                <mat-hint>{{ f.hint }}</mat-hint>
              }
            </mat-form-field>
          }
        </div>

        @if (form.hasError('lthrAboveMax')) {
          <p class="error"><mat-icon>error_outline</mat-icon> LTHR muss unter HFmax liegen.</p>
        }
        @if (error()) {
          <p class="error"><mat-icon>error_outline</mat-icon> {{ error() }}</p>
        }
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close [disabled]="saving()">Abbrechen</button>
        <button mat-flat-button type="submit" [disabled]="saving() || form.invalid || !changed()">
          @if (saving()) {
            <mat-spinner diameter="16" />
          }
          Speichern
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .intro {
      color: var(--text-muted);
      font-size: 13px;
      margin-bottom: 16px;
      max-width: 460px;
    }
    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 4px 14px;
      @media (max-width: 520px) { grid-template-columns: 1fr; }
    }
    .error {
      display: flex;
      align-items: center;
      gap: 6px;
      color: var(--bad);
      font-size: 13px;
      margin-top: 8px;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
    }
    mat-spinner { display: inline-block; margin-right: 6px; }
  `]
})
export class ThresholdsDialogComponent {
  private readonly store = inject(AthleteStore);
  private readonly ref = inject(MatDialogRef<ThresholdsDialogComponent, boolean>);
  private readonly initial = inject<AthleteThresholdsDto | null>(MAT_DIALOG_DATA);

  readonly fields = FIELDS;
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = inject(FormBuilder).group(
    Object.fromEntries(FIELDS.map(f => [
      f.key,
      [this.initial?.[f.key] ?? null, [Validators.min(f.min), Validators.max(f.max)]]
    ])) as Record<Field, [number | null, ReturnType<typeof Validators.min>[]]>,
    { validators: lthrBelowMax }
  );

  /** Nur geänderte Felder; alle anderen bleiben in intervals.icu unverändert. */
  private diff(): AthleteThresholdsUpdateDto {
    const v = this.form.getRawValue() as Record<Field, number | string | null>;
    const out: AthleteThresholdsUpdateDto = { ftp: null, indoorFtp: null, lthr: null, maxHr: null, weight: null };
    for (const f of FIELDS) {
      const raw = v[f.key];
      if (raw === null || raw === '') continue;
      const num = f.key === 'weight' ? Math.round(Number(raw) * 10) / 10 : Math.round(Number(raw));
      if (num !== (this.initial?.[f.key] ?? null)) out[f.key] = num;
    }
    return out;
  }

  changed(): boolean {
    return Object.values(this.diff()).some(v => v != null);
  }

  save(): void {
    if (this.form.invalid || !this.changed()) return;
    this.saving.set(true);
    this.error.set(null);
    this.store.save(this.diff()).subscribe({
      next: () => this.ref.close(true),
      error: err => {
        this.saving.set(false);
        this.error.set(err?.error?.detail || 'Speichern fehlgeschlagen. Ist intervals.icu erreichbar?');
      }
    });
  }
}

function lthrBelowMax(group: AbstractControl): ValidationErrors | null {
  const lthr = Number(group.get('lthr')?.value);
  const max = Number(group.get('maxHr')?.value);
  return lthr && max && lthr >= max ? { lthrAboveMax: true } : null;
}

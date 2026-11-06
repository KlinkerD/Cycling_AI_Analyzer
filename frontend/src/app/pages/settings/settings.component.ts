import { Component, HostListener, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AthleteThresholdsUpdateDto, AthleteZonesDto, ZoneDto } from '../../api/training';
import { AthleteStore } from '../../services/athlete.store';
import {
  ZoneMeta, ZoneRow, formatPctRange, formatValueRange, hrBpmProblem, hrZoneRows, limitsProblem, powerZoneRows
} from './zone-math';

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
  { key: 'lthr', label: 'LTHR', unit: 'bpm', min: 80, max: 230, step: 1, hint: 'Basis der HF-Zonen' },
  { key: 'maxHr', label: 'HFmax', unit: 'bpm', min: 100, max: 240, step: 1, hint: 'Maximale Herzfrequenz' },
  { key: 'weight', label: 'Gewicht', unit: 'kg', min: 30, max: 200, step: 0.1, hint: 'Für W/kg' }
];

const POWER_PCT = { min: 1, max: 500 };
const HR_PCT = { min: 30, max: 150 };
const SS_PCT = { min: 50, max: 150 };

/** Der Entwurf: alles, was man auf der Seite ändern kann. Zonen als Obergrenzen in %, ohne die offene letzte. */
interface Draft {
  ftp: number | null;
  indoorFtp: number | null;
  lthr: number | null;
  maxHr: number | null;
  weight: number | null;
  power: (number | null)[];
  hr: (number | null)[];
  ssMin: number | null;
  ssMax: number | null;
}

interface BarSegment {
  label: string;
  name: string;
  color: string;
  width: number;
  tooltip: string;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, MatSnackBarModule, MatTooltipModule
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit {
  private readonly store = inject(AthleteStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly fields = FIELDS;
  readonly pct = { power: POWER_PCT, hr: HR_PCT, ss: SS_PCT };
  readonly loading = this.store.loading;
  readonly saving = signal(false);
  readonly saveError = signal<string | null>(null);

  /** Stand aus intervals.icu, in Entwurfsform. */
  readonly baseline = computed(() => {
    const z = this.store.zones();
    return z ? toDraft(z) : null;
  });
  readonly draft = signal<Draft | null>(null);
  /** Hat der Nutzer etwas geändert? Dann überschreibt ein Neuladen den Entwurf nicht. */
  private touched = false;

  private readonly powerMeta = computed(() => meta(regularPowerZones(this.store.zones()?.powerZones ?? [])));
  private readonly ssMeta = computed(() => {
    const ss = this.store.zones()?.powerZones.find(z => z.index === 8);
    return ss ? { name: ss.name, color: ss.color } : undefined;
  });
  private readonly hrMeta = computed(() => meta(this.store.zones()?.hrZones ?? []));

  readonly powerRows = computed<ZoneRow[]>(() => {
    const d = this.draft();
    if (!d || !d.power.length) return [];
    return powerZoneRows(numbers(d.power), this.powerMeta(), d.ftp, d.indoorFtp, d.ssMin, d.ssMax, this.ssMeta());
  });

  readonly hrRows = computed<ZoneRow[]>(() => {
    const d = this.draft();
    if (!d || !d.hr.length) return [];
    return hrZoneRows(numbers(d.hr), this.hrMeta(), d.lthr);
  });

  readonly powerBar = computed(() => barSegments(this.powerRows(), 'W'));
  readonly hrBar = computed(() => barSegments(this.hrRows(), 'bpm'));

  readonly dirty = computed(() => {
    const d = this.draft();
    const b = this.baseline();
    return !!d && !!b && JSON.stringify(d) !== JSON.stringify(b);
  });

  /** Was an intervals.icu geht: nur Geändertes. Leere Zonen-Liste = unverändert. */
  readonly payload = computed<AthleteThresholdsUpdateDto | null>(() => {
    const d = this.draft();
    const b = this.baseline();
    if (!d || !b) return null;
    const changed = (key: Field) => d[key] != null && d[key] !== b[key] ? d[key] : null;
    const powerChanged = !sameList(d.power, b.power);
    // Ändert sich LTHR oder HFmax, gehen die HF-Zonen mit: sonst würde intervals.icu sie mit eigenen
    // Vorgaben neu berechnen und die Grenzen hier überschreiben.
    const hrChanged = d.hr.length > 0
      && (!sameList(d.hr, b.hr) || changed('lthr') != null || changed('maxHr') != null);
    const ssChanged = d.ssMin !== b.ssMin || d.ssMax !== b.ssMax;
    return {
      ftp: changed('ftp'),
      indoorFtp: changed('indoorFtp'),
      lthr: changed('lthr'),
      maxHr: changed('maxHr'),
      weight: changed('weight') == null ? null : Math.round(d.weight! * 10) / 10,
      powerZones: powerChanged ? numbers(d.power) : [],
      hrZones: hrChanged ? numbers(d.hr) : [],
      sweetSpotMin: ssChanged ? d.ssMin : null,
      sweetSpotMax: ssChanged ? d.ssMax : null
    };
  });

  /** Alle Fehler im Entwurf; Speichern geht erst, wenn die Liste leer ist. */
  readonly problems = computed<string[]>(() => {
    const d = this.draft();
    if (!d) return [];
    const out: string[] = [];
    for (const f of FIELDS) {
      if (this.fieldInvalid(f)) out.push(`${f.label} muss zwischen ${f.min} und ${f.max} ${f.unit} liegen.`);
    }
    if (d.lthr != null && d.maxHr != null && d.lthr >= d.maxHr) out.push('LTHR muss unter HFmax liegen.');
    const power = limitsProblem('Leistungszonen', d.power, POWER_PCT.min, POWER_PCT.max, '% FTP');
    if (power) out.push(power);
    const hr = limitsProblem('HF-Zonen', d.hr, HR_PCT.min, HR_PCT.max, '% LTHR');
    if (hr) out.push(hr);
    else if (this.payload()?.hrZones.length) {
      const bpmProblem = hrBpmProblem(numbers(d.hr), d.lthr, d.maxHr);
      if (bpmProblem) out.push(bpmProblem);
    }
    if (d.ssMin != null || d.ssMax != null) {
      if (!this.ssValid(d.ssMin) || !this.ssValid(d.ssMax)) {
        out.push(`Sweet Spot muss zwischen ${SS_PCT.min} und ${SS_PCT.max} % FTP liegen.`);
      } else if (d.ssMin! >= d.ssMax!) {
        out.push('Sweet Spot: Untergrenze muss unter der Obergrenze liegen.');
      }
    }
    return out;
  });

  readonly canSave = computed(() => this.dirty() && !this.problems().length && !this.saving());

  constructor() {
    // Neuer Stand aus intervals.icu ersetzt den Entwurf nur, solange nichts geändert wurde.
    effect(() => {
      const b = this.baseline();
      untracked(() => {
        if (b && (!this.touched || !this.draft())) this.draft.set(clone(b));
      });
    });
  }

  ngOnInit(): void {
    if (!this.store.zones() && !this.store.loading()) {
      this.store.load().subscribe({
        error: err => this.snackBar.open('Fehler beim Laden der Einstellungen: ' + (err.error?.detail || err.message),
          'OK', { duration: 5000 })
      });
    }
  }

  // ---- Eingaben -------------------------------------------------------------

  value(key: Field): number | null {
    return this.draft()?.[key] ?? null;
  }

  setField(key: Field, value: number | null): void {
    this.patch({ [key]: toNumber(value) });
  }

  setPowerLimit(i: number, value: number | null): void {
    const power = [...(this.draft()?.power ?? [])];
    power[i] = toNumber(value);
    this.patch({ power });
  }

  setHrLimit(i: number, value: number | null): void {
    const hr = [...(this.draft()?.hr ?? [])];
    hr[i] = toNumber(value);
    this.patch({ hr });
  }

  setSweetSpot(which: 'ssMin' | 'ssMax', value: number | null): void {
    this.patch({ [which]: toNumber(value) });
  }

  private patch(change: Partial<Draft>): void {
    const d = this.draft();
    if (!d) return;
    this.touched = true;
    this.saveError.set(null);
    this.draft.set({ ...d, ...change });
  }

  fieldInvalid(f: FieldSpec): boolean {
    const v = this.value(f.key);
    return v != null && (v < f.min || v > f.max);
  }

  limitInvalid(list: (number | null)[], i: number, range: { min: number; max: number }): boolean {
    const v = list[i];
    if (v == null || !Number.isInteger(v) || v < range.min || v > range.max) return true;
    const prev = list[i - 1];
    return i > 0 && prev != null && v <= prev;
  }

  ssValid(v: number | null): boolean {
    return v != null && Number.isInteger(v) && v >= SS_PCT.min && v <= SS_PCT.max;
  }

  // ---- Speichern / Verwerfen ----------------------------------------------

  save(): void {
    const update = this.payload();
    if (!update || !this.canSave()) return;
    this.saving.set(true);
    this.saveError.set(null);
    this.store.save(update).subscribe({
      next: () => {
        this.saving.set(false);
        this.touched = false;
        const b = this.baseline();
        if (b) this.draft.set(clone(b));
        this.snackBar.open('In intervals.icu gespeichert.', 'OK', { duration: 4000 });
      },
      error: err => {
        this.saving.set(false);
        this.saveError.set(err?.error?.detail || 'Speichern fehlgeschlagen. Ist intervals.icu erreichbar?');
        // Echten Stand neu laden (ein Teil kann schon gespeichert sein); der Entwurf bleibt.
        this.store.load(true).subscribe({ error: () => {} });
      }
    });
  }

  discard(): void {
    const b = this.baseline();
    if (!b) return;
    this.touched = false;
    this.saveError.set(null);
    this.draft.set(clone(b));
  }

  // ---- Seite verlassen ------------------------------------------------------

  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.dirty()) {
      event.preventDefault();
      event.returnValue = '';
    }
  }

  /** Für den Router: bei ungespeicherten Änderungen nachfragen. */
  confirmLeave(): boolean | Observable<boolean> {
    if (!this.dirty()) return true;
    return this.dialog.open(LeaveDialogComponent, { width: '380px', maxWidth: 'calc(100vw - 32px)' })
      .afterClosed() as Observable<boolean>;
  }

  // ---- Anzeige --------------------------------------------------------------

  formatPct = formatPctRange;
  formatRange = formatValueRange;
}

@Component({
  selector: 'app-leave-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Ungespeicherte Änderungen</h2>
    <mat-dialog-content>Deine Änderungen an Schwellenwerten und Zonen sind noch nicht gespeichert.</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false" cdkFocusInitial>Bleiben</button>
      <button mat-flat-button [mat-dialog-close]="true">Verwerfen</button>
    </mat-dialog-actions>
  `
})
export class LeaveDialogComponent {}

// ---- Hilfsfunktionen ---------------------------------------------------------

function regularPowerZones(zones: ZoneDto[]): ZoneDto[] {
  return zones.filter(z => z.index <= 7).sort((a, b) => a.index - b.index);
}

function meta(zones: ZoneDto[]): ZoneMeta[] {
  return zones.map(z => ({ name: z.name, color: z.color }));
}

function toDraft(z: AthleteZonesDto): Draft {
  const t = z.thresholds;
  const power = regularPowerZones(z.powerZones).slice(0, -1).map(p => Math.round((p.upperPct ?? 0) * 100));
  const ss = z.powerZones.find(p => p.index === 8);
  // HF-Grenzen liegen in intervals.icu als bpm; hier als % LTHR.
  const hr = t.lthr
    ? z.hrZones.slice(0, -1).map(h => Math.round(((h.upperValue ?? 0) / t.lthr!) * 100))
    : [];
  return {
    ftp: t.ftp, indoorFtp: t.indoorFtp, lthr: t.lthr, maxHr: t.maxHr, weight: t.weight,
    power,
    hr,
    ssMin: ss?.lowerPct != null ? Math.round(ss.lowerPct * 100) : null,
    ssMax: ss?.upperPct != null ? Math.round(ss.upperPct * 100) : null
  };
}

function clone(d: Draft): Draft {
  return { ...d, power: [...d.power], hr: [...d.hr] };
}

function toNumber(v: unknown): number | null {
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function numbers(list: (number | null)[]): number[] {
  return list.map(v => v ?? 0);
}

function sameList(a: (number | null)[], b: (number | null)[]): boolean {
  return a.length === b.length && a.every((v, i) => v === b[i]);
}

/**
 * Segmente für den Zonen-Balken, Breite nach Prozent. Sweet Spot steht zwischen Z3 und Z4; die offene
 * letzte Zone bekommt 30 % der vorletzten Grenze dazu.
 */
function barSegments(rows: ZoneRow[], unit: string): BarSegment[] {
  const regular = rows.filter(r => r.index <= 7);
  const ss = rows.find(r => r.index === 8);
  const ordered = [...regular];
  if (ss) ordered.splice(Math.min(3, ordered.length), 0, ss);
  const span = (r: ZoneRow) => Math.max(0, (r.upperPct ?? r.lowerPct) - r.lowerPct);
  // Z1 (ab 0 %) und die offene letzte Zone haben keine sinnvolle Breite: so breit wie die breiteste
  // geschlossene Zone dazwischen, damit die mittleren Zonen lesbar bleiben.
  const middle = regular.filter((r, i) => i > 0 && r.upperPct != null);
  const widest = Math.max(1, ...middle.map(span));
  return ordered.map(r => {
    const edge = r.index === 1 || (r.index <= 7 && r.upperPct == null);
    return {
      label: r.label,
      name: r.name,
      color: r.color,
      width: edge ? Math.min(widest, r.upperPct ?? widest) : span(r),
      tooltip: `${r.name} · ${formatValueRange(r.primary, unit)}`
    };
  });
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { inMadrid, madridToday } from '../../../shared/ui/calendar/madrid-time';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { FieldComponent } from '../../../shared/ui/field.component';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import type { ValueListItem } from '../../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../../shared/ui/value-list/value-list.component';
import { cameraOffered } from './qr-reader';
import { QrScannerComponent } from './qr-scanner.component';
import { dayOfMonth, isBookingCode, relativeDay, typedCode, weekdayAndDay } from './validate';
import { TodayActivitiesComponent } from './today-activities.component';
import { ValidateStore } from './validate.store';

/** Como se le o código. */
type Mode = 'camera' | 'code';

const upperFirst = (text: string) => text.charAt(0).toLocaleUpperCase() + text.slice(1);

/**
 * «Validar QR» (ADR-0032, puntos 4, 12 e 14), para Xestión e Recepción. Sempre nesta
 * orde: código (ou cámara) → datos da reserva → aviso → motivo → botóns. Ao completar o código (ou
 * ler o QR) valida soa: en prazo, «Reserva validada» con «Validar outra» e «Desfacer»; fóra de
 * prazo, o aviso, o motivo obrigatorio e «Validar igualmente» ou «Non validar»; xa validada (cando
 * e quen), cancelada e inexistente, só o aviso. A cámara só se ofrece no móbil; se falla ou a
 * persoa nega o permiso, queda o código.
 */
@Component({
  selector: 'furar-validate-page',
  imports: [
    ButtonDirective,
    CalloutComponent,
    FieldComponent,
    IconComponent,
    InputDirective,
    QrScannerComponent,
    SegmentedDirective,
    TodayActivitiesComponent,
    TranslocoPipe,
    ValueListComponent,
  ],
  providers: [ValidateStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './validate.page.html',
})
export class ValidatePage {
  protected readonly store = inject(ValidateStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly view = inject(DOCUMENT).defaultView;
  private readonly codeInput = viewChild<ElementRef<HTMLInputElement>>('codeInput');
  private readonly reasonInput = viewChild<ElementRef<HTMLTextAreaElement>>('reasonInput');

  private readonly slug = computed(() => this.municipality.slug() ?? '');
  /** Se esta pantalla ofrece a cámara: só no móbil, e mentres non falle. */
  protected readonly camera = signal(cameraOffered(this.view));
  protected readonly mode = signal<Mode>(this.camera() ? 'camera' : 'code');
  protected readonly code = signal('');
  protected readonly reason = signal('');
  protected readonly reasonError = signal<ControlError | null>(null);
  /** O erro de «Validar igualmente» ou de «Desfacer». */
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly busy = signal<'force' | 'undo' | null>(null);

  /** A cámara mira mentres non hai nada lido. */
  protected readonly scanning = computed(() => this.mode() === 'camera' && this.store.state() === 'idle');
  /** O resultado xa trae os seus botóns («Validar outra», «Non validar»). */
  protected readonly actionable = computed(() => {
    const outcome = this.store.result()?.outcome;
    return outcome === 'VALIDATED' || outcome === 'OUT_OF_WINDOW';
  });

  protected readonly cover = computed(() => {
    const id = this.store.result()?.booking.facilityId;
    return this.store.facilities().find((f) => f.id === id)?.cover ?? null;
  });

  protected readonly rows = computed<ValueListItem[]>(() => {
    this.langChanges();
    const booking = this.store.result()?.booking;
    if (!booking) return [];
    const t = (key: string) => this.transloco.translate(`validate.row.${key}`);
    const start = inMadrid(booking.start);
    const end = inMadrid(booking.end);
    const lang = this.language.lang();
    const relative = this.relative(start.date);
    return [
      {
        id: 'person',
        label: t('person'),
        value: booking.holderName ?? this.transloco.translate('booking.manage.anonymized'),
      },
      { id: 'use', label: t('use'), value: [booking.useName, booking.partName].filter(Boolean).join(' · ') },
      {
        id: 'day',
        label: t('day'),
        value: relative ? `${relative}, ${weekdayAndDay(start.date, lang)}` : weekdayAndDay(start.date, lang),
      },
      { id: 'time', label: t('time'), value: `${start.time}–${end.time}` },
    ];
  });

  /** «Hoxe ás 19:02, por Antón Rei.» */
  protected readonly already = computed(() => {
    this.langChanges();
    const result = this.store.result();
    if (!result?.attendanceAt) return '';
    const date = upperFirst(this.dayAndTime(result.attendanceAt));
    return result.attendanceBy
      ? this.transloco.translate('validate.result.already.body', { date, name: result.attendanceBy })
      : this.transloco.translate('validate.result.already.bodyNoName', { date });
  });

  /** «Cancelouna a persoa o 8 de novembro.» */
  protected readonly cancelled = computed(() => {
    this.langChanges();
    const booking = this.store.result()?.booking;
    if (!booking?.cancelledAt) return '';
    const date = dayOfMonth(inMadrid(booking.cancelledAt).date, this.language.lang());
    const by = booking.status === 'CANCELLED_BY_PERSON' ? 'byPerson' : 'byMunicipality';
    return this.transloco.translate(`validate.result.cancelled.${by}`, { date });
  });

  /** «mañá ás 18:00». */
  protected readonly when = computed(() => {
    this.langChanges();
    const booking = this.store.result()?.booking;
    return booking ? this.dayAndTime(booking.start) : '';
  });

  constructor() {
    void this.store.loadFacilities(this.slug());
  }

  protected setMode(mode: Mode): void {
    this.mode.set(mode);
    this.reset();
    if (mode === 'code') this.focusCode();
  }

  /** O código tecleado, en maiúsculas: ao completalo, valida soa. */
  protected typed(text: string): void {
    const code = typedCode(text);
    const input = this.codeInput()?.nativeElement;
    if (input && input.value !== code) input.value = code;
    this.code.set(code);
    this.clearAnswer();
    if (isBookingCode(code)) void this.store.read(this.slug(), code);
    else this.store.clear();
  }

  /** O código lido pola cámara. */
  protected scanned(code: string): void {
    this.code.set(code);
    this.clearAnswer();
    void this.store.read(this.slug(), code);
  }

  /** Sen cámara ou sen permiso: queda o código. */
  protected cameraFailed(): void {
    this.camera.set(false);
    this.setMode('code');
  }

  /** «Validar igualmente»: sen motivo, o erro debaixo do campo e o foco nel. */
  protected async force(): Promise<void> {
    const reason = this.reason().trim();
    this.problem.set(null);
    if (!reason) {
      this.reasonError.set({ key: 'validate.reason.required', params: {} });
      this.reasonInput()?.nativeElement.focus();
      return;
    }
    this.reasonError.set(null);
    this.busy.set('force');
    try {
      await this.store.force(this.slug(), this.code(), reason);
      this.reason.set('');
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(null);
    }
  }

  protected async undo(): Promise<void> {
    this.problem.set(null);
    this.busy.set('undo');
    try {
      await this.store.undo(this.slug());
      this.next();
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(null);
    }
  }

  /** «Validar outra» e «Non validar»: volve ao principio. */
  protected next(): void {
    this.reset();
    if (this.mode() === 'code') this.focusCode();
  }

  private reset(): void {
    this.code.set('');
    this.clearAnswer();
    this.store.clear();
  }

  private clearAnswer(): void {
    this.reason.set('');
    this.reasonError.set(null);
    this.problem.set(null);
  }

  private focusCode(): void {
    // O campo pode aínda non estar pintado (ao cambiar de cámara a código).
    setTimeout(() => this.codeInput()?.nativeElement.focus());
  }

  private relative(date: string): string | null {
    return relativeDay(date, madridToday(), {
      today: this.transloco.translate('booking.manage.today'),
      tomorrow: this.transloco.translate('booking.manage.tomorrow'),
    });
  }

  /** «hoxe ás 19:02», «mañá ás 18:00» ou «o luns 16 de novembro ás 18:00». */
  private dayAndTime(instant: string): string {
    const wall = inMadrid(instant);
    const day =
      this.relative(wall.date) ??
      this.transloco.translate('validate.onDay', { day: weekdayAndDay(wall.date, this.language.lang()) });
    return this.transloco.translate('validate.at', { day, time: wall.time });
  }
}

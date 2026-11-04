// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  inject,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { CalendarComponent } from '../../../shared/ui/calendar/calendar.component';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { FieldPopoverComponent } from '../../../shared/ui/field-popover/field-popover.component';
import { formatCents } from '../../../shared/ui/money/money';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import type { OfferedSlot } from './reserve';
import { formatDay, lastBookableDay, slotTime, useOpensOn } from './reserve';
import type { PublicUseResponse } from './reserve.store';
import { ReserveStore } from './reserve.store';
import { RequiredNoteComponent } from '../../../shared/ui/required/required.component';

/**
 * Os campos da páxina de reservar (maqueta 11): Que · Data · Hora (coa duración enriba das horas)
 * · Parte (só nos usos por partes), cada un en `furar-field-popover`, nun mesmo marco. Ao escoller,
 * pasan sós ao seguinte; ao abrir a Data, o foco vai ao día escollido. Só se ofrecen os ocos que
 * manda a API. O escollido é do `ReserveStore` da páxina (URL).
 */
@Component({
  selector: 'furar-reserve-fields',
  imports: [
    RequiredNoteComponent,
    CalendarComponent,
    FieldPopoverComponent,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reserve-fields.component.html',
})
export class ReserveFieldsComponent {
  protected readonly store = inject(ReserveStore);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly calendar = viewChild(CalendarComponent);
  protected readonly partPopover = viewChild<FieldPopoverComponent>('partField');

  protected readonly today = computed(() => this.store.availability()?.today ?? null);
  /** Última data: a antelación máxima do uso, se a ten. */
  protected readonly maxDate = computed(() => lastBookableDay(this.today(), this.store.use()?.rules));
  protected readonly closedDay = computed(() => {
    const use = this.store.use();
    return (day: IsoDate) => !!use && !useOpensOn(use, day);
  });

  protected readonly dateText = computed(() => {
    const date = this.store.date();
    return date ? formatDay(date, this.language.lang(), 'short') : null;
  });
  protected readonly timeText = computed(() => {
    const chosen = this.store.chosen();
    const use = this.store.use();
    if (!chosen || !use) return null;
    const end = chosen.slot.startMinute + use.slotMinutes * this.store.slots();
    return `${chosen.time}–${slotTime(end)}`;
  });
  protected readonly partText = computed(() => {
    this.langChanges();
    const part = this.store.use()?.parts.find((each) => each.id === this.store.partId());
    return part?.name ?? this.transloco.translate('booking.reserve.anyPart');
  });
  /** O uso pola súa forma e duración: «Metade norte ou Metade sur · 60 minutos». */
  protected useDetail(use: PublicUseResponse): string {
    this.langChanges();
    const t = (key: string, params: Record<string, unknown> = {}) =>
      this.transloco.translate(`facility.public.use.${key}`, params);
    const mode =
      use.rentalMode === 'PARTS'
        ? t('parts', { names: use.parts.map((part) => part.name).join(t('or')) })
        : use.rentalMode === 'SEATS'
          ? t('seats', { count: use.capacity })
          : t('whole');
    return `${mode} · ${t('minutes', { minutes: use.slotMinutes })}`;
  }

  protected usePrice(use: PublicUseResponse): string | null {
    this.langChanges();
    if (use.price.kind === 'FREE') return this.transloco.translate('booking.reserve.free');
    if (use.price.kind !== 'PAID' || use.price.amountCents === undefined) return null;
    const amount = formatCents(use.price.amountCents);
    return use.price.per === 'PERSON'
      ? `${amount} ${this.transloco.translate('facility.public.price.perPerson')}`
      : amount;
  }

  /** «1 hora», «2 horas», «90 min»: a duración de `slots` franxas do uso. */
  protected durationText(slots: number): string {
    this.langChanges();
    const minutes = (this.store.use()?.slotMinutes ?? 60) * slots;
    return minutes % 60 === 0
      ? this.transloco.translate(minutes === 60 ? 'booking.reserve.hour' : 'booking.reserve.hours', {
          count: minutes / 60,
        })
      : this.transloco.translate('booking.reserve.minutes', { minutes });
  }

  /** Debaixo da hora: as partes ou as prazas que quedan libres. */
  protected slotNote(slot: OfferedSlot): string | null {
    this.langChanges();
    const count = slot.freeParts?.length ?? slot.freeSeats;
    if (count === null || count === undefined) return null;
    const key = slot.freeParts ? 'partsFree' : 'seatsFree';
    return this.transloco.translate(`booking.reserve.${key}.${count === 1 ? 'one' : 'other'}`, { count });
  }

  protected partFree(partId: string): boolean {
    const free = this.store.chosen()?.freeParts;
    return !free || free.includes(partId);
  }

  protected pickUse(useId: string, field: FieldPopoverComponent): void {
    if (useId !== this.store.use()?.id) this.store.selectUse(useId);
    field.next();
  }

  protected pickDate(date: IsoDate, field: FieldPopoverComponent): void {
    this.store.selectDate(date);
    field.next();
  }

  protected pickTime(time: string, field: FieldPopoverComponent): void {
    this.store.selectTime(time);
    field.next();
  }

  protected pickPart(partId: string | null, field: FieldPopoverComponent): void {
    this.store.selectPart(partId);
    field.close();
  }

  protected pickDuration(value: unknown): void {
    if (typeof value === 'number') this.store.selectDuration(value);
  }

  /** Ao abrir a Data, o foco vai ao día escollido da grella (despois do do panel). */
  protected focusCalendar(): void {
    afterNextRender({ read: () => this.calendar()?.focusActive() }, { injector: this.injector });
  }
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { DependentsStore } from '../../../shared/dependents/dependents.store';
import { firstName, joinNames } from '../../../shared/dependents/participants';
import { CalendarComponent } from '../../../shared/ui/calendar/calendar.component';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { FieldPopoverComponent } from '../../../shared/ui/field-popover/field-popover.component';
import { FieldComponent } from '../../../shared/ui/field.component';
import type { SelectOption } from '../../../shared/ui/select/select-option';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import { SessionService } from '../../../core/auth/session.service';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../shared/ui/segmented/segmented.directive';
import type { HourOption, OfferedSlot } from './reserve';
import { formatDay, hourOptions, lastBookableDay, slotTime, useOpensOn } from './reserve';
import { ReserveAs } from './reserve-as';
import { ReserveHours } from './reserve-hours';
import { ReserveStore } from './reserve.store';
import { ReserveWhoComponent } from './reserve-who.component';

/** «Reservar como» a propia persoa. */
const AS_ME = 'me';

/**
 * Os campos da páxina de reservar: Que · Data · Hora (coa duración enriba das horas)
 * · Parte (só nos usos por partes), nun mesmo marco. Que e Parte son o despregable de sempre
 * (`furar-select`, igual no móbil); Data e Hora, `furar-field-popover`. Ao escoller, pasan sós ao
 * seguinte; ao abrir a Data, o foco vai ao día escollido. Só se ofrecen os ocos que manda a API.
 * O escollido é do `ReserveStore` da páxina (URL). Con `who`, nos usos por prazas, tamén «Para
 * quen»: quen reserva e as súas persoas a cargo, varias á vez.
 */
@Component({
  selector: 'furar-reserve-fields',
  imports: [
    CalendarComponent,
    FieldComponent,
    FieldPopoverComponent,
    ReserveWhoComponent,
    SelectComponent,
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
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly whenPopover = viewChild<FieldPopoverComponent>('whenField');
  private readonly hourPopover = viewChild<FieldPopoverComponent>('hourField');
  protected readonly whoPopover = viewChild<FieldPopoverComponent>('whoField');
  private readonly dependents = inject(DependentsStore);

  /** Con «Para quen» nos usos por prazas (a páxina de reservar; no mostrador vai en «Prazas»). */
  readonly who = input(false, { transform: booleanAttribute });

  private readonly session = inject(SessionService);
  private readonly as = inject(ReserveAs, { optional: true });
  /**
   * «Reservar como» (ADR-0039): a persoa responsable dunha entidade reserva para ela
   * ou para a entidade, coas mesmas condicións. Só na páxina da persoa (`who`), nunha reserva nova
   * e fóra dos usos por prazas.
   */
  protected readonly asOptions = computed<SelectOption<string>[]>(() => {
    this.langChanges();
    const organizations = this.as?.organizations() ?? [];
    if (!this.who() || !organizations.length || this.store.bySeats() || this.store.selection().change) return [];
    const name = this.session.name();
    const me = name
      ? this.transloco.translate('booking.seasons.as.me', { name })
      : this.transloco.translate('booking.who.me');
    return [
      { value: AS_ME, label: me },
      ...organizations.map((organization) => ({ value: organization.id, label: organization.name })),
    ];
  });
  protected readonly asValue = computed(() => this.as?.organizationId() ?? AS_ME);

  protected pickAs(value: string): void {
    this.store.submitError.set(null);
    this.as?.select(value === AS_ME ? null : value);
  }

  /**
   * Coas horas ocupadas no selector, en gris e elixibles (ADR-0033); sen el, só as libres (o
   * mostrador).
   */
  readonly showBusy = input(false, { transform: booleanAttribute });
  private readonly busyHours = inject(ReserveHours, { optional: true });
  /** As horas do selector: libres e, con `showBusy`, tamén as ocupadas. */
  protected readonly hours = computed(() =>
    this.showBusy() && this.busyHours ? this.busyHours.hours() : hourOptions(this.store.offered(), []),
  );
  /** A hora ocupada escollida, só con `showBusy`. */
  private readonly chosenBusy = computed(() => (this.showBusy() ? (this.busyHours?.chosenBusy() ?? null) : null));
  /** A hora marcada no selector: a libre escollida ou a ocupada. */
  protected readonly pressed = computed(() => this.store.chosen()?.time ?? this.chosenBusy()?.time ?? null);

  /** «Eu e Uxía · 2 prazas»: os primeiros nomes das persoas marcadas e, se son varias, cantas. */
  protected readonly whoText = computed(() => {
    this.langChanges();
    const t = (key: string, params = {}) => this.transloco.translate(`booking.who.${key}`, params);
    const who = this.store.who;
    const names = [
      ...(who.self() ? [t('me')] : []),
      ...this.dependents
        .items()
        .filter((person) => who.dependentIds().includes(person.id))
        .map((person) => firstName(person.name)),
    ];
    // As que xa non están na lista (a reserva que se cambia) contan igual como prazas.
    const seats = who.seats();
    if (!seats) return null;
    const text = joinNames(names, t('and'));
    return seats > 1 ? [text, t('seats', { count: seats })].filter(Boolean).join(' · ') : text || null;
  });

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
    this.langChanges();
    const chosen = this.store.chosen();
    const use = this.store.use();
    if (!use) return null;
    if (chosen) {
      const end = chosen.slot.startMinute + use.slotMinutes * this.store.slots();
      return `${chosen.time}–${slotTime(end)}`;
    }
    // Ocupada: a súa franxa (espérase por unha soa) e «· ocupada».
    const busy = this.chosenBusy();
    if (!busy) return null;
    const range = `${busy.time}–${slotTime(busy.startMinute + use.slotMinutes)}`;
    return this.transloco.translate('booking.reserve.busyValue', { range });
  });
  /** Os usos da instalación, polo seu nome. */
  protected readonly useOptions = computed<SelectOption<string>[]>(() =>
    (this.store.facility()?.uses ?? []).map((use) => ({ value: use.id, label: use.name })),
  );
  /** As partes do uso (as ocupadas á hora escollida, desactivadas) e, ao final, «Tanto me ten». */
  protected readonly partOptions = computed<SelectOption<string | null>[]>(() => {
    this.langChanges();
    const free = this.store.chosen()?.freeParts;
    return [
      ...(this.store.use()?.parts ?? []).map((part) => ({
        value: part.id,
        label: part.name,
        disabled: !!free && !free.includes(part.id),
      })),
      { value: null, label: this.transloco.translate('booking.reserve.anyPart') },
    ];
  });

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

  /** Outro uso: abre a Data, xa pechada a lista (o clic que escolleu non a pecha). */
  protected pickUse(useId: string | null): void {
    if (!useId) return;
    this.store.selectUse(useId);
    afterNextRender({ write: () => this.whenPopover()?.open() }, { injector: this.injector });
  }

  protected pickDate(date: IsoDate, field: FieldPopoverComponent): void {
    this.store.selectDate(date);
    field.next();
  }

  /** Despois da Hora, «Para quen» ou, nos usos por partes, a lista de «Parte». */
  protected pickTime(time: string, field: FieldPopoverComponent): void {
    this.store.selectTime(time);
    field.next();
    if (!this.whoPopover() && this.store.use()?.rentalMode === 'PARTS') {
      afterNextRender({ write: () => this.openSelect('reserve-part') }, { injector: this.injector });
    }
  }

  /** «Só unha parte»: pasa ao uso por partes coa hora e a parte libre; o panel péchase. */
  protected pickPartHour(hour: HourOption, field: FieldPopoverComponent): void {
    if (!hour.part) return;
    this.store.selectUse(hour.part.useId, hour.time, hour.part.partId);
    field.close();
  }

  /** Unha ocupada non segue cara a «Para quen» nin «Parte»: o panel péchase e sae «Avísame». */
  protected pickBusy(time: string, field: FieldPopoverComponent): void {
    this.store.selectTime(time);
    field.close();
  }

  protected pickPart(partId: string | null): void {
    this.store.selectPart(partId);
  }

  protected pickDuration(value: unknown): void {
    // Unha hora ocupada segue escollida: espérase por ela sexa cal sexa a duración.
    if (typeof value === 'number') this.store.selectDuration(value, !!this.chosenBusy());
  }

  /**
   * O botón de confirmar non se desactiva: premido sen algo, abre o primeiro campo que falta (Que,
   * Data, Hora ou «Para quen»), á vista, como ao pasar dun campo ao seguinte. Devolve se faltaba.
   */
  openMissing(): boolean {
    const store = this.store;
    if (!store.use()) return this.openSelect('reserve-what', true);
    const field = !store.date()
      ? this.whenPopover()
      : !store.chosen()
        ? this.hourPopover()
        : store.nobody()
          ? this.whoPopover()
          : undefined;
    if (!field) return false;
    field.reveal();
    return true;
  }

  /** Abre o despregable `id` (Que ou Parte) se está pechado; con `reveal`, antes lévao á vista. */
  private openSelect(id: string, reveal = false): boolean {
    const button = this.host.querySelector<HTMLButtonElement>(`#${id}`);
    if (!button) return false;
    if (reveal) button.scrollIntoView?.({ block: 'center', inline: 'nearest' });
    if (button.getAttribute('aria-expanded') !== 'true') button.click();
    return true;
  }

  /** Ao abrir a Data, o foco vai ao día escollido da grella (despois do do panel). */
  protected focusCalendar(): void {
    afterNextRender({ read: () => this.calendar()?.focusActive() }, { injector: this.injector });
  }
}

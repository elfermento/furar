// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { CustomFieldDefinition } from '../../../shared/custom-fields/custom-field.types';
import { localized } from '../../../shared/custom-fields/localized';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { formatCents } from '../../../shared/ui/money/money';
import { QrComponent } from '../../../shared/ui/qr/qr.component';
import type { ValueListItem } from '../../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../../shared/ui/value-list/value-list.component';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { bookingFieldRows } from '../booking-fields';
import { formatDay } from './reserve';
import type { MyBookingResponse } from './confirmed-booking.store';
import type { NoteResponse, PublicFacilityDetailResponse } from './reserve.store';

/**
 * «Reserva confirmada» (maqueta 13): o check e o título (o foco vai a el ao aparecer), o correo
 * ao que se enviou, a instalación, os valores (uso, día, hora, parte, prezo coa nota de pago do
 * concello), o QR co código, o prazo exacto para cancelar sen custo e «Engadir ao calendario» e
 * «Ver as miñas reservas». Datas e horas en Europe/Madrid.
 */
@Component({
  selector: 'furar-reserve-confirmed',
  imports: [ButtonDirective, IconComponent, QrComponent, RouterLink, TranslocoPipe, ValueListComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.reserve.done.';
    <div class="f-reserve-done">
      <div class="f-reserve-done__ok">
        <furar-icon name="success" [size]="24" />
        <h1 #title tabindex="-1">{{ t + 'title' | transloco }}</h1>
        @if (email()) {
          <p>{{ t + 'sent' | transloco: { email: email() } }}</p>
        }
      </div>
      <section class="f-card f-reserve__card" [attr.aria-label]="t + 'card' | transloco">
        <div class="f-reserve__facility" data-done-facility>
          @if (thumb(); as image) {
            <img [src]="image.thumb" [width]="image.width" [height]="image.height" alt="" />
          }
          <div>
            <b>{{ booking().facilityName }}</b>
            @if (address()) {
              <span>{{ address() }}</span>
            }
          </div>
        </div>
        <furar-value-list [items]="rows()" />
        <div class="f-reserve-done__qr">
          <b>{{ t + 'show' | transloco }}</b>
          <furar-qr [size]="booking().qr.size" [path]="booking().qr.path" [code]="booking().code" />
          <span class="f-reserve-done__code" data-done-code>{{ booking().code }}</span>
        </div>
        <p class="f-reserve-done__cancel" data-done-cancel>
          <furar-icon name="calendar-month" />
          <span>
            {{ t + 'cancelUntil' | transloco }}
            <b>{{ deadline() }}</b>
          </span>
        </p>
        <div class="f-reserve-done__acts">
          <button type="button" furarButton="secondary" data-done-calendar (click)="calendar.emit()">
            <furar-icon name="calendar" />
            {{ t + 'addToCalendar' | transloco }}
          </button>
          <a furarButton="primary" [routerLink]="['/', slug(), 'reservas']" data-done-mine>
            {{ t + 'mine' | transloco }}
          </a>
        </div>
      </section>
    </div>
  `,
})
export class ReserveConfirmedComponent {
  readonly booking = input.required<MyBookingResponse>();
  readonly facility = input<PublicFacilityDetailResponse | null>(null);
  /** Correo da sesión: a el mandou a API a confirmación. */
  readonly email = input<string | null>(null);
  readonly paymentNote = input<NoteResponse | null>(null);
  /** Os campos propios da reserva (etiquetas): os valores que ve quen reservou. */
  readonly fields = input<readonly CustomFieldDefinition[]>([]);
  /** «Engadir ao calendario». */
  readonly calendar = output();

  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly title = viewChild.required<ElementRef<HTMLElement>>('title');

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly thumb = computed(() => this.facility()?.images[0] ?? null);
  protected readonly address = computed(() => {
    const address = this.facility()?.address;
    if (!address) return '';
    const street = [address.street, address.streetNumber].filter(Boolean).join(', ');
    return [street, address.locality].filter(Boolean).join(' · ');
  });

  protected readonly rows = computed<ValueListItem[]>(() => {
    this.langChanges();
    const lang = this.language.lang();
    const booking = this.booking();
    const t = (key: string) => this.transloco.translate(`booking.reserve.done.${key}`);
    const start = inMadrid(booking.start);
    const end = inMadrid(booking.end);
    const rows: ValueListItem[] = [
      { id: 'use', label: t('use'), value: booking.useName },
      { id: 'day', label: t('day'), value: formatDay(start.date, lang, 'long') },
      { id: 'time', label: t('time'), value: `${start.time}–${end.time}` },
    ];
    if (booking.partName) rows.push({ id: 'part', label: t('part'), value: booking.partName });
    rows.push(...bookingFieldRows(this.fields(), booking.customFields, lang, (key) => this.transloco.translate(key)));
    const price = this.priceText();
    if (price) rows.push({ id: 'price', label: t('price'), value: price });
    return rows;
  });

  /** «30,00 € · No mostrador, ao chegar» (a nota é do concello); «Gratis»; sen prezo, nada. */
  private readonly priceText = computed(() => {
    this.langChanges();
    const price = this.booking().price;
    if (price.kind === 'FREE') return this.transloco.translate('booking.reserve.free');
    if (price.kind !== 'PAID' || price.amountCents === undefined) return null;
    const note = localized(this.paymentNote(), this.language.lang());
    return [formatCents(price.amountCents), note].filter(Boolean).join(' · ');
  });

  /** «luns, 26 de outubro ás 18:00»: o prazo exacto para cancelar sen custo. */
  protected readonly deadline = computed(() => {
    this.langChanges();
    const until = inMadrid(this.booking().cancelUntil);
    return this.transloco.translate('booking.reserve.done.at', {
      day: formatDay(until.date, this.language.lang(), 'long'),
      time: until.time,
    });
  });

  constructor() {
    // O título recibe o foco ao aparecer: o lector anuncia que a reserva está feita.
    const injector = inject(Injector);
    afterNextRender(() => this.title().nativeElement.focus(), { injector });
  }
}

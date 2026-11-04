// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CalendarComponent } from '../shared/ui/calendar/calendar.component';
import type { IsoDate } from '../shared/ui/calendar/local-date';
import { formatShort, localToday } from '../shared/ui/calendar/local-date';
import { CardComponent } from '../shared/ui/card/card.component';
import { FieldPopoverComponent } from '../shared/ui/field-popover/field-popover.component';
import type { FilterChipTone } from '../shared/ui/filter-chip/filter-chip.directive';
import { FilterBarComponent, FilterFieldComponent } from '../shared/ui/filter-bar/filter-bar.component';
import { FilterChipDirective } from '../shared/ui/filter-chip/filter-chip.directive';
import { QrComponent } from '../shared/ui/qr/qr.component';
import type { SelectOption } from '../shared/ui/select/select.component';
import { SelectComponent } from '../shared/ui/select/select.component';
import type { TagTone } from '../shared/ui/tag/tag.component';
import { TagComponent } from '../shared/ui/tag/tag.component';
import type { WeekdayMask } from '../shared/ui/weekday-picker/weekday-picker.component';
import type { ValueListItem } from '../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../shared/ui/value-list/value-list.component';
import { MONDAY_TO_FRIDAY, WeekdayPickerComponent } from '../shared/ui/weekday-picker/weekday-picker.component';

/** Os tres cadros de posición dun QR de 21 módulos (de mostra: non codifica nada). */
const FINDER = (x: number, y: number) => `M${x} ${y}h7v7h-7zM${x + 1} ${y + 1}v5h5v-5zM${x + 2} ${y + 2}h3v3h-3z`;
const SAMPLE_QR = `${FINDER(0, 0)}${FINDER(14, 0)}${FINDER(0, 14)}M8 8h1v1h-1zM10 9h2v1h-2zM9 11h1v2h-1zM12 12h2v1h-2zM16 16h3v1h-3zM18 10h1v3h-1z`;

/** Sección do dominio 2 do catálogo (só desenvolvemento): chips de filtro, campos con panel, días, valores e QR. */
@Component({
  selector: 'furar-catalog-booking',
  imports: [
    TranslocoPipe,
    ReactiveFormsModule,
    CalendarComponent,
    CardComponent,
    FieldPopoverComponent,
    FilterBarComponent,
    FilterChipDirective,
    FilterFieldComponent,
    QrComponent,
    SelectComponent,
    TagComponent,
    ValueListComponent,
    WeekdayPickerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './catalog-booking.component.html',
})
export class CatalogBookingComponent {
  /** Os estados de pago coa paleta (P11): o mesmo ton na etiqueta e no chip. */
  protected readonly payments: { key: string; tone: FilterChipTone & TagTone; pressed: boolean }[] = [
    { key: 'pending', tone: 'warn', pressed: true },
    { key: 'paid', tone: 'success', pressed: true },
    { key: 'toRefund', tone: 'danger', pressed: false },
    { key: 'refunded', tone: 'muted', pressed: false },
    { key: 'free', tone: 'info', pressed: false },
  ];
  protected readonly uses = ['football11', 'football7'] as const;
  protected readonly use = signal<string | null>(null);
  protected readonly today = localToday();
  protected readonly date = signal<IsoDate | null>(null);
  protected readonly dateText = computed(() => {
    const date = this.date();
    return date ? formatShort(date) : null;
  });
  /** A fila de filtros (furar-filter-bar): despregable, campo con panel, chip neutro e chip de estado. */
  protected readonly court = signal('');
  protected readonly filterDay = signal<IsoDate | null>(null);
  protected readonly filterDateText = computed(() => {
    const day = this.filterDay();
    return day ? formatShort(day) : null;
  });
  protected readonly covered = signal(false);
  protected readonly pending = signal(true);
  protected readonly days = new FormControl<WeekdayMask>(MONDAY_TO_FRIDAY, { nonNullable: true });
  protected readonly qrPath = SAMPLE_QR;

  private readonly transloco = inject(TranslocoService);
  private readonly lang = toSignal(this.transloco.selectTranslation());
  protected readonly courts = computed<SelectOption<string>[]>(() => {
    this.lang();
    const t = (key: string) => this.transloco.translate(`catalog.booking.${key}`);
    return [
      { value: '', label: t('allCourts') },
      { value: '1', label: t('court1') },
      { value: '2', label: t('court2') },
    ];
  });
  protected readonly values = computed<ValueListItem[]>(() => {
    this.lang();
    const t = (key: string) => this.transloco.translate(`catalog.booking.${key}`);
    return [
      { id: 'use', label: t('use'), value: t('football7') },
      { id: 'day', label: t('day'), value: t('dayValue') },
      { id: 'time', label: t('time'), value: '18:00–19:00' },
    ];
  });
}

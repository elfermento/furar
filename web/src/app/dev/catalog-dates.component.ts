// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { CalendarComponent } from '../shared/ui/calendar/calendar.component';
import type { IsoDate } from '../shared/ui/calendar/local-date';
import { addMonths, localToday, weekdayIndex } from '../shared/ui/calendar/local-date';
import { CardComponent } from '../shared/ui/card/card.component';
import { DateFieldComponent } from '../shared/ui/date-field/date-field.component';
import { FieldComponent } from '../shared/ui/field.component';

/** Sección «Calendario e campo de data» do catálogo (só desenvolvemento). */
@Component({
  selector: 'furar-catalog-dates',
  imports: [TranslocoPipe, ReactiveFormsModule, CalendarComponent, CardComponent, DateFieldComponent, FieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './catalog-dates.component.html',
})
export class CatalogDatesComponent {
  /** Rango de exemplo con dous campos (P14): desde hoxe ata dentro dun ano, sen domingos. */
  protected readonly today = localToday();
  protected readonly inOneYear = addMonths(this.today, 12);
  protected readonly isSunday = (date: IsoDate) => weekdayIndex(date) === 6;
  protected readonly dates = new FormGroup({
    from: new FormControl<IsoDate | null>(null),
    to: new FormControl<IsoDate | null>(null),
    closed: new FormControl<IsoDate | null>({ value: '2026-12-24', disabled: true }),
  });
  protected readonly looseDate = signal<IsoDate | null>(this.inOneYear);
}

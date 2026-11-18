// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { ActionItemComponent, ActionListDirective } from '../../../shared/ui/action-list/action-list.component';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import type { ValueListItem } from '../../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../../shared/ui/value-list/value-list.component';
import type { MySeriesRequestResponse } from './my-organizations.store';
import { scheduleParts } from './seasons';

/** «Retirar a solicitude»; pechar sen escoller: `undefined`. */
export type MySeasonRequestResult = 'withdraw';

/**
 * Detalle dunha solicitude de tempada da entidade (coma o detalle dunha reserva):
 * a instalación e os días, as horas e as datas na cabeceira; o uso, a parte e a nota; se non se
 * aprobou, a mensaxe do concello; e, mentres está pendente, «Retirar a solicitude» en vermello.
 */
@Component({
  selector: 'furar-my-season-request-detail',
  imports: [
    ActionItemComponent,
    ActionListDirective,
    CalloutComponent,
    DialogComponent,
    TranslocoPipe,
    ValueListComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.seasons.';
    <furar-dialog size="sm" [heading]="request.facilityName" [subheading]="when">
      <div class="f-mydetail" [attr.data-my-season-request]="request.id">
        <furar-value-list [items]="rows" />
        @if (request.rejectionMessage) {
          <furar-callout tone="warn" data-request-rejection [heading]="t + 'request.message' | transloco">
            <p>{{ request.rejectionMessage }}</p>
          </furar-callout>
        }
        @if (request.status === 'PENDING') {
          <ul furarActionList [attr.aria-label]="'booking.mine.detail.actions' | transloco">
            <li>
              <furar-action-item icon="event-busy" tone="danger">
                <button type="button" data-request-withdraw (click)="ref.close('withdraw')">
                  {{ t + 'request.withdraw' | transloco }}
                </button>
              </furar-action-item>
            </li>
          </ul>
        }
      </div>
    </furar-dialog>
  `,
})
export class MySeasonRequestDetailComponent {
  protected readonly request = inject<MySeriesRequestResponse>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<MySeasonRequestResult>>(DialogRef);
  private readonly transloco = inject(TranslocoService);
  private readonly lang = inject(LanguageService).lang();
  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  protected readonly when = scheduleParts(this.request, this.lang, this.t).join(' · ');
  protected readonly rows: ValueListItem[] = [
    { id: 'use', label: this.t('booking.manage.detail.use'), value: this.request.useName },
    ...(this.request.partName
      ? [{ id: 'part', label: this.t('booking.manage.detail.part'), value: this.request.partName }]
      : []),
    ...(this.request.note
      ? [{ id: 'note', label: this.t('booking.seasons.form.note'), value: this.request.note }]
      : []),
  ];
}

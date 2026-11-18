// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SessionService } from '../../core/auth/session.service';
import { LanguageService } from '../../core/i18n/language.service';
import { ActionItemComponent, ActionListDirective } from '../ui/action-list/action-list.component';
import { DialogComponent } from '../ui/dialog/dialog.component';
import { eventWhen, inLang, isScheduled, placeText, priceText, scheduleWhen } from './event-format';
import type { MyEventRegistrationResponse } from './public-events.store';
import { RegistrationBandComponent } from './registration-band.component';

/** O que abre o detalle: a inscrición e o concello (a ligazón á ficha). */
export interface MyEventDetailData {
  readonly item: MyEventRegistrationResponse;
  readonly slug: string;
}

/** «Darme de baixa» ou «Saír da lista» (`leave`), «Confirmar a praza» (`confirm`); pechar, `undefined`. */
export type MyEventDetailResult = 'leave' | 'confirm';

/**
 * Detalle dun evento ou dunha actividade de «As miñas reservas» (coma o da reserva): `furar-dialog`
 * (folla no móbil) co nome e, debaixo, o día e a hora ou o horario. Arriba, a mesma banda de estado
 * ca na ficha (`furar-registration-band`: con praza, «Darme de baixa» e as próximas sesións; na
 * lista, «Saír da lista»; avisada, «Confirmar a praza» e «Non a quero»); despois os datos (lugar,
 * para quen e prezo) e «Ver a actividade» (ou «Ver o evento»). As accións pechan o detalle co que
 * se escolleu; quen o abre fai a petición. Pecha con «Pechar», Esc ou picar fóra.
 */
@Component({
  selector: 'furar-my-event-detail',
  imports: [
    ActionItemComponent,
    ActionListDirective,
    DialogComponent,
    RegistrationBandComponent,
    RouterLink,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog size="sm" [heading]="name" [subheading]="when">
      <div class="f-mydetail" [attr.data-my-event-detail]="event.id">
        <furar-registration-band [item]="data.item" (leave)="ref.close('leave')" (confirm)="ref.close('confirm')" />
        <div class="f-value-list" data-variant="lined">
          <dl class="f-value-list__list">
            @if (place) {
              <div class="f-value-list__row" data-value="place">
                <dt>{{ 'event.manage.col.place' | transloco }}</dt>
                <dd>{{ place }}</dd>
              </div>
            }
            @if (person) {
              <div class="f-value-list__row" data-value="person">
                <dt>{{ 'event.mine.for' | transloco }}</dt>
                <dd>{{ person }}</dd>
              </div>
            }
            @if (price) {
              <div class="f-value-list__row" data-value="price">
                <dt>{{ 'event.public.price' | transloco }}</dt>
                <dd>
                  <b data-detail-amount>{{ price }}</b>
                </dd>
              </div>
            }
          </dl>
        </div>
        <ul furarActionList [attr.aria-label]="'booking.mine.detail.actions' | transloco">
          <li>
            <furar-action-item icon="local-activity">
              <a data-my-event-open [routerLink]="['/', data.slug, 'eventos', event.id]" (click)="ref.close()">{{
                (scheduled ? 'event.mine.seeActivity' : 'event.mine.see') | transloco
              }}</a>
            </furar-action-item>
          </li>
        </ul>
      </div>
    </furar-dialog>
  `,
})
export class MyEventDetailComponent {
  protected readonly data = inject<MyEventDetailData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<MyEventDetailResult>>(DialogRef);
  private readonly lang = inject(LanguageService).lang();
  private readonly transloco = inject(TranslocoService);
  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  protected readonly event = this.data.item.event;
  protected readonly scheduled = isScheduled(this.event);
  protected readonly name = inLang(this.event.name, this.lang);
  protected readonly when = isScheduled(this.event)
    ? scheduleWhen(this.event.schedule, this.lang, this.t)
    : eventWhen(this.event.startsAt, this.lang);
  protected readonly place = placeText(this.event);
  protected readonly person = this.data.item.dependent?.name ?? inject(SessionService).name() ?? '';
  protected readonly price = priceText(this.event, this.t);
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { ActionItemComponent, ActionListDirective } from '../ui/action-list/action-list.component';
import { DialogComponent } from '../ui/dialog/dialog.component';
import { TagComponent } from '../ui/tag/tag.component';
import { openMatchCountsText, openMatchTitle, openMatchWhenParts } from './open-match-format';
import type { MyOpenMatchResponse } from './open-matches.store';

/** O que abre o detalle: a partida e o concello (a ligazón á páxina). */
export interface MyOpenMatchDetailData {
  readonly match: MyOpenMatchResponse;
  readonly slug: string;
}

/** «Saír» (ou «Retirar a solicitude»); pechar sen escoller (ou «Ver a partida», que navega): `undefined`. */
export type MyOpenMatchDetailResult = 'leave';

/**
 * Detalle dunha partida na que está ou pediu entrar, en «As miñas reservas» (coma o
 * dun evento): `furar-dialog` (folla no móbil) co nome e o día e a hora na cabeceira; os datos
 * (instalación, quen organiza, as prazas e o estado) e as accións en lista: «Ver a partida» e, ao
 * final en vermello, «Saír» ou «Retirar a solicitude».
 */
@Component({
  selector: 'furar-my-open-match-detail',
  imports: [ActionItemComponent, ActionListDirective, DialogComponent, RouterLink, TagComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'openMatch.mine.';
    <furar-dialog size="sm" [heading]="name" [subheading]="when">
      <div class="f-mydetail" [attr.data-my-open-match-detail]="match.id">
        <div class="f-value-list" data-variant="lined">
          <dl class="f-value-list__list">
            <div class="f-value-list__row" data-value="facility">
              <dt>{{ 'openMatch.page.where' | transloco }}</dt>
              <dd>{{ match.facilityName }}</dd>
            </div>
            <div class="f-value-list__row" data-value="organizer">
              <dt>{{ 'openMatch.page.organizer' | transloco }}</dt>
              <dd>{{ match.organizerName }}</dd>
            </div>
            <div class="f-value-list__row" data-value="seats">
              <dt>{{ 'openMatch.page.seats' | transloco }}</dt>
              <dd>{{ counts }}</dd>
            </div>
            <div class="f-value-list__row" data-value="status">
              <dt>{{ t + 'status' | transloco }}</dt>
              <dd class="f-mydetail__state">
                <furar-tag look="solid" [tone]="pending ? 'warn' : 'success'" [attr.data-chip]="match.me.role">{{
                  t + match.me.role | transloco
                }}</furar-tag>
              </dd>
            </div>
          </dl>
        </div>
        <ul furarActionList [attr.aria-label]="'booking.mine.detail.actions' | transloco">
          <li>
            <furar-action-item icon="group">
              <a data-my-open-match-open [routerLink]="['/', data.slug, 'partidas', match.id]" (click)="ref.close()">{{
                'openMatch.detail.see' | transloco
              }}</a>
            </furar-action-item>
          </li>
          <li>
            <furar-action-item icon="event-busy" tone="danger">
              <button type="button" data-my-open-match-leave (click)="ref.close('leave')">
                {{ (pending ? 'openMatch.page.withdraw' : 'openMatch.page.leave') | transloco }}
              </button>
            </furar-action-item>
          </li>
        </ul>
      </div>
    </furar-dialog>
  `,
})
export class MyOpenMatchDetailComponent {
  protected readonly data = inject<MyOpenMatchDetailData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<MyOpenMatchDetailResult>>(DialogRef);
  private readonly lang = inject(LanguageService).lang();
  private readonly transloco = inject(TranslocoService);

  protected readonly match = this.data.match;
  protected readonly pending = this.match.me.role === 'PENDING';
  protected readonly name = openMatchTitle(this.match);
  protected readonly when = openMatchWhenParts(this.match, this.lang).join(' · ');
  protected readonly counts = openMatchCountsText(this.match, (key, params) => this.transloco.translate(key, params));
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import type { Params } from '@angular/router';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { TagTone } from '../../../shared/ui/tag/tag.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { formatShort } from '../../../shared/ui/calendar/local-date';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import type { ActivityOutcome, ActivityScope, RoleChip } from './activity';
import { OUTCOME_TONE, targetView } from './activity';
import type { ChangeView, Translate } from './activity-rows';
import { changeView, facilityOf, summaryText } from './activity-rows';
import { ActivityRolesComponent } from './activity-roles.component';
import type { ActivityRow } from './activity-view';

/** Unha acción dentro do detalle: a hora, o resumo e a petición só se o detalle é dun grupo. */
export interface ActivityDetailItem {
  readonly id: string;
  readonly time: string | null;
  readonly summary: string | null;
  readonly request: string | null;
  readonly changes: readonly ChangeView[];
}

/** O detalle dunha fila, xa en texto (a páxina prepárao). */
export interface ActivityDetail {
  readonly title: string;
  /** O nome (ou o texto: «Persoa anonimizada») e, debaixo, o correo. */
  readonly person: string;
  readonly email: string | null;
  readonly roles: readonly RoleChip[];
  /** O día; a hora vai á parte (nun grupo, a franxa: «12:14–12:31»). */
  readonly date: string;
  readonly time: string;
  readonly place: string | null;
  readonly link: readonly string[] | null;
  readonly query?: Params;
  readonly outcome: ActivityOutcome;
  readonly tone: TagTone;
  /** Petición (X-Request-Id), só dunha acción soa; nun grupo vai en cada acción. */
  readonly request: string | null;
  readonly grouped: boolean;
  readonly items: readonly ActivityDetailItem[];
}

/**
 * Detalle dunha fila do rexistro: o resumo de título; os datos en tres
 * columnas entre dúas liñas (Persoa co nome en negra e o correo debaixo, Rol; Data, Hora; Onde,
 * Resultado; e a Petición a todo o ancho); e o que cambiou nunha lista con bordo, en columnas
 * aliñadas «Campo · antes → despois» (antes riscado suave; «—» sen riscar se non había nada; un
 * dato persoal ou un texto longo, só «cambiou»). Nun grupo, cada acción leva unha liña (hora ·
 * resumo · petición) e a súa lista. No móbil, folla desde abaixo, datos en dúas columnas e o campo
 * enriba de «antes → despois». Só lectura.
 */
@Component({
  selector: 'furar-activity-detail-dialog',
  imports: [
    ActivityRolesComponent,
    DialogComponent,
    IconComponent,
    NgTemplateOutlet,
    RouterLink,
    TagComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'activity.detail.';
    <furar-dialog size="lg" [heading]="data.title">
      <div class="f-activity-detail" data-activity-detail>
        <dl class="f-activity-detail__meta">
          <div>
            <dt>{{ t + 'person' | transloco }}</dt>
            <dd data-detail-person>
              <span class="font-bold">{{ data.person }}</span>
              @if (data.email) {
                {{ ' ' }}<span class="f-activity__soft">{{ data.email }}</span>
              }
            </dd>
          </div>
          <div>
            <dt>{{ t + 'role' | transloco }}</dt>
            <dd data-detail-roles><furar-activity-roles [roles]="data.roles" /></dd>
          </div>
          <div>
            <dt>{{ t + 'date' | transloco }}</dt>
            <dd>{{ data.date }}</dd>
          </div>
          <div>
            <dt>{{ t + 'time' | transloco }}</dt>
            <dd class="f-activity-detail__time">{{ data.time }}</dd>
          </div>
          <div>
            <dt>{{ t + 'place' | transloco }}</dt>
            <dd>
              @if (data.place && data.link) {
                <a [routerLink]="data.link" [queryParams]="data.query" (click)="ref.close()">{{ data.place }}</a>
              } @else {
                {{ data.place ?? '—' }}
              }
            </dd>
          </div>
          <div>
            <dt>{{ t + 'outcome' | transloco }}</dt>
            <dd>
              <furar-tag [tone]="data.tone">{{ 'activity.outcome.' + data.outcome | transloco }}</furar-tag>
            </dd>
          </div>
          @if (!data.grouped) {
            <div class="f-activity-detail__wide">
              <dt>{{ t + 'request' | transloco }}</dt>
              <dd class="f-activity-detail__mono" data-request>{{ data.request ?? '—' }}</dd>
            </div>
          }
        </dl>
        <section class="f-activity-detail__changes" aria-labelledby="activity-detail-changes">
          <h3 id="activity-detail-changes">
            {{
              data.grouped
                ? (t + 'changesCount' | transloco: { count: data.items.length })
                : (t + 'changes' | transloco)
            }}
          </h3>
          @if (data.grouped) {
            <div class="f-activity-detail__acts">
              @for (item of data.items; track item.id) {
                <section class="f-activity-detail__act" data-detail-item>
                  <p class="f-activity-detail__act-head">
                    <span class="f-activity-detail__act-time">{{ item.time }}</span>
                    {{ ' ' }}<span class="f-activity-detail__act-summary">{{ item.summary }}</span>
                    @if (item.request) {
                      {{ ' ' }}<span class="f-activity-detail__mono f-activity__soft">{{ item.request }}</span>
                    }
                  </p>
                  @if (item.changes.length) {
                    {{ ' '
                    }}<ng-container [ngTemplateOutlet]="list" [ngTemplateOutletContext]="{ $implicit: item.changes }" />
                  }
                </section>
              }
            </div>
          } @else if (data.items[0]?.changes?.length) {
            <ng-container [ngTemplateOutlet]="list" [ngTemplateOutletContext]="{ $implicit: data.items[0].changes }" />
          } @else {
            <p class="f-activity-detail__none" data-no-changes>{{ t + 'noChanges' | transloco }}</p>
          }
        </section>
      </div>
      <ng-template #list let-changes>
        <ul class="f-activity-detail__list">
          @for (change of changes; track $index) {
            @if (change.more) {
              <li class="f-activity-detail__change" data-more>
                <span class="f-activity-detail__field f-activity-detail__field--wide">{{ change.field }}</span>
              </li>
            } @else if (change.hidden) {
              <li class="f-activity-detail__change" data-hidden>
                <span class="f-activity-detail__field">{{ change.field }}</span>
                {{ ' ' }}<span class="f-activity-detail__changed">{{ t + 'changed' | transloco }}</span>
              </li>
            } @else {
              <li class="f-activity-detail__change" data-diff>
                <span class="f-activity-detail__field">{{ change.field }}</span>
                @if (change.created) {
                  {{ ' ' }}<span class="f-activity-detail__empty">—</span>
                } @else {
                  {{ ' ' }}<del>{{ change.before }}</del>
                }
                {{ ' '
                }}<span class="f-activity-detail__arrow"
                  ><furar-icon name="arrow-right" /><span class="sr-only">{{ t + 'to' | transloco }}</span></span
                >{{ ' ' }}<ins>{{ change.after }}</ins>
              </li>
            }
          }
        </ul>
      </ng-template>
    </furar-dialog>
  `,
})
export class ActivityDetailDialogComponent {
  protected readonly data = inject<ActivityDetail>(DIALOG_DATA);
  protected readonly ref = inject(DialogRef);
}

export interface DetailContext {
  readonly scope: ActivityScope;
  readonly slug: string | null;
  readonly facilities: ReadonlyMap<string, string>;
  readonly canViewBookings: boolean;
  readonly t: Translate;
}

/**
 * O detalle dunha fila: unha reserva leva a «Reservas» co seu detalle (`targetView`), antes que á
 * súa instalación; o resto, á ficha da instalación se aínda existe. Nun grupo, a franxa horaria e
 * cada acción coa súa hora, o seu resumo e a súa petición.
 */
export function buildDetail(row: ActivityRow, ctx: DetailContext): ActivityDetail {
  const { t } = ctx;
  const first = row.events[0];
  const last = row.events[row.events.length - 1];
  const grouped = row.events.length > 1;
  const target = targetView(first, ctx.scope, ctx.slug, ctx.facilities, ctx.canViewBookings);
  const facility = facilityOf(first);
  // Unha acción sobre unha reserva leva á reserva, aínda que tamén coñeza a súa instalación.
  const bookingLink = !grouped && first.target?.kind === 'BOOKING' && !!target.link;
  const link: { link: readonly string[] | null; query?: Params } = bookingLink
    ? { link: target.link, query: target.query }
    : ctx.scope === 'municipality' && ctx.slug && facility && ctx.facilities.has(facility)
      ? { link: ['/', ctx.slug, 'xestion', 'instalacions', facility] }
      : target.link && !grouped
        ? { link: target.link, query: target.query }
        : { link: null };
  const time = (at: string) => inMadrid(at).time;
  return {
    title: row.summary,
    person: row.name,
    email: row.email,
    roles: row.roles,
    date: formatShort(inMadrid(first.at).date),
    time: grouped ? `${time(last.at)}–${time(first.at)}` : time(first.at),
    place: row.place,
    ...link,
    outcome: row.outcome,
    tone: OUTCOME_TONE[row.outcome],
    request: grouped ? null : (first.requestId ?? null),
    grouped,
    items: row.events.map((e) => ({
      id: e.id,
      time: grouped ? time(e.at) : null,
      summary: grouped ? summaryText(e, t) : null,
      request: grouped ? (e.requestId ?? null) : null,
      changes: e.changes.map((c) => changeView(c, t)),
    })),
  };
}

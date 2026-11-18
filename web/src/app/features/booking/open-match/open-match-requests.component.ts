// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import type { MyOpenMatchResponse } from '../../../shared/open-matches/open-matches.store';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { agoText } from '../manage/manage-bookings';

export type OpenMatchRequest = MyOpenMatchResponse['requests'][number];

/**
 * As solicitudes dunha partida con aprobación, para quen a organiza (ADR-0040): o título
 * fóra e unha tarxeta por solicitude co nome e «pediu unirse hai 2 h»; abaixo, á dereita e con aire,
 * «Rexeitar» (vermello cheo) e «Aceptar» (cheo), os dous con texto branco. Sen solicitudes, nada.
 */
@Component({
  selector: 'furar-open-match-requests',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @let t = 'openMatch.page.';
    @if (requests().length) {
      <section class="f-public__section" aria-labelledby="match-requests" data-match-requests>
        <h2 id="match-requests">
          {{ t + 'requests' | transloco }} <span class="f-match-count">{{ requests().length }}</span>
        </h2>
        <ul class="f-match-requests">
          @for (request of requests(); track request.id) {
            <li class="f-card f-match-request" [attr.data-request]="request.id">
              <b>{{ request.name }}</b>
              <span class="text-ink-soft">{{ t + 'requestedAt' | transloco: { ago: ago(request) } }}</span>
              <span class="f-match-request__acts">
                <button
                  type="button"
                  furarButton="danger-fill"
                  data-request-reject
                  [attr.aria-label]="t + 'rejectLabel' | transloco: { name: request.name }"
                  [busy]="busy() === request.id + 'reject'"
                  (click)="decided.emit({ request, accept: false })"
                >
                  <furar-icon name="close" />
                  {{ t + 'reject' | transloco }}
                </button>
                <button
                  type="button"
                  furarButton="primary"
                  data-request-accept
                  [attr.aria-label]="t + 'acceptLabel' | transloco: { name: request.name }"
                  [busy]="busy() === request.id + 'accept'"
                  (click)="decided.emit({ request, accept: true })"
                >
                  <furar-icon name="check" />
                  {{ t + 'accept' | transloco }}
                </button>
              </span>
            </li>
          }
        </ul>
      </section>
    }
  `,
})
export class OpenMatchRequestsComponent {
  readonly requests = input.required<readonly OpenMatchRequest[]>();
  /** A acción en curso na páxina (`<id>accept`, `<id>reject`…). */
  readonly busy = input<string | null>(null);
  readonly decided = output<{ request: OpenMatchRequest; accept: boolean }>();

  private readonly language = inject(LanguageService);

  protected ago(request: OpenMatchRequest): string {
    return agoText(request.requestedAt, this.language.lang());
  }
}

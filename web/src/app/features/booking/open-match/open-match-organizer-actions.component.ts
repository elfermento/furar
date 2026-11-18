// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { IconComponent } from '../../../shared/ui/icon/icon.component';

/**
 * As accións de quen organiza ao pé da tarxeta da partida: buscando, «Deixar de
 * buscar» (secundario) e «Cancelar a reserva» (perigo), un debaixo do outro; pechada, o estado e
 * «Cancelar a reserva»; cancelada, só o estado. «Cancelar a reserva», só se aínda se pode. Cada
 * botón avisa e a páxina pregunta.
 */
@Component({
  selector: 'furar-open-match-organizer-actions',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @switch (status()) {
      @case ('OPEN') {
        <button type="button" furarButton="secondary" block data-match-stop (click)="stopped.emit()">
          <furar-icon name="lock" />
          {{ 'openMatch.detail.stop' | transloco }}
        </button>
      }
      @case ('CLOSED') {
        <p class="text-ink-soft" data-match-closed>{{ 'openMatch.page.organizerClosed' | transloco }}</p>
      }
      @default {
        <p class="text-ink-soft" data-match-cancelled>{{ 'openMatch.page.cancelled' | transloco }}</p>
      }
    }
    @if (status() !== 'CANCELLED' && canCancel()) {
      <button type="button" furarButton="danger" block data-match-cancel (click)="cancelled.emit()">
        <furar-icon name="event-busy" />
        {{ 'booking.mine.cancel.confirm' | transloco }}
      </button>
    }
  `,
})
export class OpenMatchOrganizerActionsComponent {
  /** O estado da partida. */
  readonly status = input.required<'OPEN' | 'CLOSED' | 'CANCELLED'>();
  /** A reserva aínda se pode cancelar (confirmada, por vir e dentro do prazo). */
  readonly canCancel = input(false, { transform: booleanAttribute });
  /** «Deixar de buscar». */
  readonly stopped = output();
  /** «Cancelar a reserva». */
  readonly cancelled = output();
}

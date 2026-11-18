// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';

export type StatusBandTone = 'success' | 'info' | 'warn';

/**
 * Banda de estado: o estado de quen mira con respecto a algo (unha praza, a lista de espera),
 * enriba da tarxeta de datos da ficha ou no detalle. Tinte suave e icona do ton á esquerda; a frase
 * principal en negra (`heading`) e, debaixo, o contido proxectado (unha liña curta, chips); despois
 * as accións (`[bandActions]`: a principal a todo o ancho, as discretas centradas) e, ao final, un
 * pregable (`[bandMore]`, `furar-disclosure` «Como funciona») co detalle. Só a frase e as accións á
 * vista: o resto vai pregado.
 *
 * A frase e a liña van nunha rexión `role="status"` (`live`), así o lector anuncia o estado novo
 * cando cambia sen mover o foco. Estilos en `styles/components/status-band.css`.
 *
 * ```html
 * <furar-status-band tone="warn" icon="schedule" heading="Hai unha praza libre para ti">
 *   <p>Ata as 18:42 só o sabes ti.</p>
 *   <button bandActions furarButton="primary" block>Confirmar a praza</button>
 *   <furar-disclosure bandMore heading="Como funciona">…</furar-disclosure>
 * </furar-status-band>
 * ```
 */
@Component({
  selector: 'furar-status-band',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-status-band', '[attr.data-tone]': 'tone()' },
  template: `
    <furar-icon class="f-status-band__icon" [name]="icon()" [size]="24" />
    <div class="f-status-band__body">
      <div class="f-status-band__message" [attr.role]="live() === 'off' ? null : live()">
        <p class="f-status-band__heading">{{ heading() }}</p>
        <ng-content />
      </div>
      <div class="f-status-band__actions"><ng-content select="[bandActions]" /></div>
      <ng-content select="[bandMore]" />
    </div>
  `,
})
export class StatusBandComponent {
  readonly tone = input<StatusBandTone>('info');
  /** A icona do estado (decorativa: a frase xa o di). */
  readonly icon = input<IconName>('info');
  /** A frase principal. */
  readonly heading = input.required<string>();
  /** `status` (por defecto) anuncia os cambios; `off` só pinta. */
  readonly live = input<'status' | 'off'>('status');
}

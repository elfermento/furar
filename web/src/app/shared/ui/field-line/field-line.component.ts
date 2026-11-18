// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { HelpTipComponent } from '../help-tip/help-tip.component';
import { RequiredMarkComponent } from '../required/required.component';

let nextId = 0;

/**
 * Fila solta do sistema de campos (styles/components/fields.css): sen caixa, o texto á esquerda ao
 * tamaño do valor dun campo (17 px, normal), empezando onde empezan os campos, e o control proxectado á
 * dereita: un chip de estado (`furar-chip-menu`) ou un selector de dúas opcións
 * (`[furarSegmented] mode="radio"`). O interruptor (`furar-switch`) xa é unha fila así por si
 * mesmo e non vai aquí. Se o control non cabe ao carón do texto, baixa á liña seguinte, á dereita
 * (con `wrap="start"`, á esquerda: p. ex. o «Tipo de reserva»).
 *
 * O texto leva o id `labelId` (por defecto, un xerado): o segmentado noméase con
 * `[attr.aria-labelledby]="line.labelId()"`; o chip-menú xa leva o seu nome (`label`).
 * Con `tip`, a axuda vai nun «?» tras o texto (`furar-help-tip`, id `<labelId>-help`).
 */
@Component({
  selector: 'furar-field-line',
  imports: [HelpTipComponent, NgTemplateOutlet, RequiredMarkComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-field-line', '[attr.data-wrap]': "wrap() === 'start' ? 'start' : null" },
  template: `
    <span class="f-field-line__text">
      @if (glued(); as parts) {
        <!--
          Co «?», a última palabra e o «?» van xuntos (nunca baixa só o «?»): o texto
          enteiro, só para o lector, co id que nomea o control; á vista, partido.
        -->
        <span class="f-field-line__label sr-only" [id]="labelId()"
          >{{ label() }}
          @if (required()) {
            <furar-required-mark />
          }
        </span>
        @if (parts.head) {
          <span class="f-field-line__label" aria-hidden="true">{{ parts.head }} </span>
        }
        <span class="f-field-line__glue">
          <span class="f-field-line__label" aria-hidden="true"
            >{{ parts.last }}
            @if (required()) {
              <span class="f-required" aria-hidden="true">*</span>
            }
          </span>
          <ng-container *ngTemplateOutlet="help" />
        </span>
      } @else {
        <span class="f-field-line__label" [id]="labelId()"
          >{{ label() }}
          @if (required()) {
            <furar-required-mark />
          }
        </span>
        @if (tip()) {
          <ng-container *ngTemplateOutlet="help" />
        }
      }
      <ng-template #help>
        <furar-help-tip
          [label]="tipLabel() || ('ui.helpTip.label' | transloco: { label: label() })"
          [text]="tip()"
          [textId]="tipId() || labelId() + '-help'"
        />
      </ng-template>
    </span>
    <span class="f-field-line__control"><ng-content /></span>
  `,
})
export class FieldLineComponent {
  /** Texto da fila (xa traducido): que se escolle («Pago», «Idioma por defecto»). */
  readonly label = input.required<string>();
  /** Id do texto, para o `aria-labelledby` do control. */
  readonly labelId = input(`furar-field-line-${nextId++}`);
  /** Axuda nun «?» tras o texto. */
  readonly tip = input('');
  /** Nome do «?»; por defecto, «Axuda sobre «texto»». */
  readonly tipLabel = input('');
  /** Id do texto do «?» (para o `aria-describedby` do control); por defecto, `<labelId>-help`. */
  readonly tipId = input('');
  readonly required = input(false, { transform: booleanAttribute });
  /** Onde queda o control se baixa á liña seguinte: á dereita (`end`, por defecto) ou á esquerda. */
  readonly wrap = input<'end' | 'start'>('end');

  /** Co «?»: o texto antes da última palabra e a última (que vai co «?»). */
  protected readonly glued = computed<{ head: string; last: string } | null>(() => {
    if (!this.tip()) return null;
    const words = this.label().trim().split(/\s+/);
    const last = words.pop() ?? '';
    return { head: words.join(' '), last };
  });
}

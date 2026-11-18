// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';

let nextId = 0;

/**
 * Unha liña de datos da tarxeta. Os anacos (`parts`) van unidos por « · » e cada un sen partir: a
 * liña só baixa nos puntos, nunca polo medio dun dato.
 */
export interface StatusCardLine {
  /** Clave estable (vai en `data-line`, para as probas e os estilos). */
  readonly id: string;
  readonly parts: readonly string[];
  /** Icona á esquerda, na cor principal (a data: `calendar-month`). */
  readonly icon?: IconName | null;
  /** En gris (o uso e a parte, debaixo da data). */
  readonly soft?: boolean;
}

/** A miniatura (decorativa). */
export interface StatusCardCover {
  readonly thumb: string;
  readonly width?: number;
  readonly height?: number;
}

/**
 * Tarxeta dunha cousa con estado que se abre: unha por elemento,
 * pulsable enteira (un só botón, o nome, estirado sobre a tarxeta) e co chevron á dereita; sen
 * botóns dentro. Arriba o nome destacado e, á dereita, o importe (`amount`), que nunca empuxa o
 * nome: o nome parte en dúas liñas. Debaixo, as liñas de datos (`lines`) e calquera outro contido
 * proxectado; ao final, a fila de chips (`[cardChips]`: `furar-tag`, estrelas, un chip cunha nota),
 * aliñada co texto. Ao premela emite `opened`: quen a usa abre o detalle.
 *
 * ```html
 * <furar-status-card [heading]="name" amount="20,00 €" [lines]="lines" [cover]="cover" (opened)="open()">
 *   <furar-tag cardChips tone="warn">Pendente de pago</furar-tag>
 * </furar-status-card>
 * ```
 *
 * O botón leva como descrición o importe, as liñas e os chips. Estilos en
 * `styles/components/status-card.css`.
 */
@Component({
  selector: 'furar-status-card',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-card f-status-card', '[attr.data-past]': 'past() || null' },
  template: `
    @if (cover(); as c) {
      <img
        class="f-status-card__img"
        data-card-cover
        [src]="c.thumb"
        [attr.width]="c.width ?? null"
        [attr.height]="c.height ?? null"
        alt=""
        loading="lazy"
      />
    }
    <div class="f-status-card__body">
      <div class="f-status-card__top">
        @if (level() === 3) {
          <h3 class="f-status-card__name">
            <button
              type="button"
              class="f-status-card__open"
              [attr.aria-describedby]="describedBy()"
              (click)="opened.emit()"
            >
              {{ heading() }}
            </button>
          </h3>
        } @else {
          <h2 class="f-status-card__name">
            <button
              type="button"
              class="f-status-card__open"
              [attr.aria-describedby]="describedBy()"
              (click)="opened.emit()"
            >
              {{ heading() }}
            </button>
          </h2>
        }
        <span class="f-status-card__end">
          @if (amount(); as value) {
            <b class="f-status-card__amount" data-card-amount [id]="amountId">{{ value }}</b>
          }
          <furar-icon class="f-status-card__chevron" name="chevron-right" />
        </span>
      </div>
      <div class="f-status-card__details" [id]="detailsId">
        @for (line of lines(); track line.id) {
          <p class="f-status-card__line" [attr.data-line]="line.id" [attr.data-soft]="line.soft || null">
            @if (line.icon; as icon) {
              <furar-icon [name]="icon" />
            }
            <span>
              @for (part of line.parts; track $index) {
                <span class="f-status-card__seg"
                  ><span class="f-status-card__part">{{ part }}</span>
                  @if (!$last) {
                    <span>{{ separator }}</span>
                  }
                </span>
                @if (!$last) {
                  {{ ' ' }}
                }
              }
            </span>
          </p>
        }
        <ng-content />
        <div class="f-status-card__chips" data-card-chips><ng-content select="[cardChips]" /></div>
      </div>
    </div>
  `,
})
export class StatusCardComponent {
  /** O nome (a instalación): é o nome do botón que abre o detalle. */
  readonly heading = input.required<string>();
  /** Nivel do título: 2 nunha páxina con `h1`; 3 dentro dunha sección con título. */
  readonly level = input<2 | 3>(2);
  /** Importe xa formateado («20,00 €»), arriba á dereita; sen el, só o chevron. */
  readonly amount = input<string | null>(null);
  /** « ·» pegado ao dato de antes (espazo sen corte): o punto nunca empeza unha liña. */
  protected readonly separator = '\u00a0·';
  readonly lines = input<readonly StatusCardLine[]>([]);
  readonly cover = input<StatusCardCover | null>(null);
  /** Xa pasou (historial): a miniatura apagada e sen sombra. */
  readonly past = input(false, { transform: booleanAttribute });
  /** Premeu a tarxeta. */
  readonly opened = output();

  private readonly id = `furar-status-card-${nextId++}`;
  protected readonly amountId = `${this.id}-amount`;
  protected readonly detailsId = `${this.id}-details`;
  protected readonly describedBy = computed(() =>
    this.amount() ? `${this.amountId} ${this.detailsId}` : this.detailsId,
  );
}

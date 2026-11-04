// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, signal } from '@angular/core';
import type { Params } from '@angular/router';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';

/** Un paso da lista: o seu texto, se está feito e, mentres non, a ligazón para facelo. */
export interface ChecklistItem {
  readonly key: string;
  readonly label: string;
  readonly done: boolean;
  /** O paso como seguinte cousa que facer, para o modo compacto («completa os datos legais»). */
  readonly next?: string;
  readonly action?: {
    readonly label: string;
    readonly link: string | readonly unknown[];
    readonly queryParams?: Params;
  };
}

let nextId = 0;

/**
 * Lista de pasos (maqueta 48): título, «1 de 2», unha barra de progreso só visual e os pasos en
 * orde, cada un cunha marca (o «feito» vai tamén en texto para lectores, non só no visto) e, se
 * falta, a ligazón para facelo. Só informa: non bloquea nada. Rexión co título como nome.
 *
 * `compact` (maqueta 56, a portada): unha fila pregada co título, o seguinte paso, «paso N de M» e
 * «Ir» á súa ligazón; o título é un botón (`aria-expanded`) que desprega a lista enteira.
 */
@Component({
  selector: 'furar-checklist',
  imports: [ButtonDirective, IconComponent, NgTemplateOutlet, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-checklist',
    role: 'region',
    '[attr.aria-labelledby]': 'headingId',
    '[attr.data-compact]': 'compact() ? "" : null',
    '[attr.data-open]': 'compact() && open() ? "" : null',
  },
  template: `
    @if (compact()) {
      <div class="f-checklist__row">
        <button
          type="button"
          class="f-checklist__toggle"
          [attr.aria-expanded]="open()"
          [attr.aria-controls]="bodyId"
          (click)="open.set(!open())"
        >
          <span class="f-checklist__chev" aria-hidden="true"><furar-icon name="chevron-right" /></span>
          <span class="f-checklist__summary">
            <span class="f-checklist__title" [id]="headingId">{{ heading() }}</span>
            @if (!open() && next(); as step) {
              <span class="f-checklist__colon">:</span>
              <span class="f-checklist__next" data-next>{{ step.next ?? step.label }}</span>
              <span class="f-checklist__count" data-count>
                <span class="f-checklist__step">{{ 'ui.checklist.step' | transloco }} </span
                >{{ 'ui.checklist.count' | transloco: { done: nextIndex(), total: items().length } }}
              </span>
            } @else {
              <span class="f-checklist__count" data-count>
                {{ 'ui.checklist.count' | transloco: { done: done(), total: items().length } }}
              </span>
            }
          </span>
        </button>
        @if (!open() && next(); as step) {
          @if (step.action; as action) {
            <a
              furarButton="primary"
              size="sm"
              class="f-checklist__cta"
              data-go
              [routerLink]="action.link"
              [queryParams]="action.queryParams"
              [attr.aria-label]="'ui.checklist.goTo' | transloco: { step: step.next ?? step.label }"
              >{{ 'ui.checklist.go' | transloco }}</a
            >
          }
        }
      </div>
      <div class="f-checklist__body" [id]="bodyId" [hidden]="!open()">
        <ng-container [ngTemplateOutlet]="list" />
      </div>
    } @else {
      <div class="f-checklist__head">
        <h2 class="f-checklist__title" [id]="headingId">{{ heading() }}</h2>
        <span class="f-checklist__count" data-count>
          {{ 'ui.checklist.count' | transloco: { done: done(), total: items().length } }}
        </span>
      </div>
      <ng-container [ngTemplateOutlet]="list" />
    }
    <ng-template #list>
      <div class="f-checklist__bar" aria-hidden="true"><i [style.width.%]="percent()"></i></div>
      <ol class="f-checklist__items">
        @for (item of items(); track item.key) {
          <li [attr.data-item]="item.key" [attr.data-done]="item.done">
            <span class="f-checklist__mark" aria-hidden="true">
              @if (item.done) {
                <furar-icon name="check" />
              }
            </span>
            <span class="f-checklist__label">
              {{ item.label }}
              @if (item.done) {
                <span class="sr-only">{{ 'ui.checklist.done' | transloco }}</span>
              }
            </span>
            @if (!item.done && item.action; as action) {
              <a class="f-checklist__go" [routerLink]="action.link" [queryParams]="action.queryParams">{{
                action.label
              }}</a>
            }
          </li>
        }
      </ol>
    </ng-template>
  `,
})
export class ChecklistComponent {
  readonly heading = input.required<string>();
  readonly items = input.required<readonly ChecklistItem[]>();
  /** Pregada nunha fila co seguinte paso; desprégase co título. */
  readonly compact = input(false, { transform: booleanAttribute });

  /** Só no modo compacto: se a lista está despregada. */
  protected readonly open = signal(false);
  protected readonly headingId = `furar-checklist-${nextId++}`;
  protected readonly bodyId = `${this.headingId}-body`;
  protected readonly done = computed(() => this.items().filter((item) => item.done).length);
  protected readonly percent = computed(() =>
    this.items().length ? Math.round((this.done() / this.items().length) * 100) : 0,
  );
  /** O primeiro paso sen facer e a súa posición (desde 1). */
  protected readonly next = computed(() => this.items().find((item) => !item.done) ?? null);
  protected readonly nextIndex = computed(() => {
    const next = this.next();
    return next ? this.items().indexOf(next) + 1 : 0;
  });
}

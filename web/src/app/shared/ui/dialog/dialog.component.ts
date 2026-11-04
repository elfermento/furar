// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { CdkDialogContainer } from '@angular/cdk/dialog';
import { DialogRef } from '@angular/cdk/dialog';
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActionScope } from '../action/action-scope';
import { IconComponent } from '../icon/icon.component';

let nextId = 0;

export type DialogSize = 'sm' | 'md' | 'lg' | 'full';

/**
 * Corpo dunha capa de diálogo (D1-05a; docs/deseno.md §8): título (`h2`, que nomea a capa),
 * texto opcional, contido proxectado e pé coas accións (`[dialogActions]`; cancelar á esquerda e a
 * acción á dereita). O botón «Pechar» vese sempre. Ábrese con `DialogService.open(Compoñente)`:
 * o compoñente da feature ten `<furar-dialog>` como raíz do seu modelo.
 *
 * Foco (CDK Dialog): ao abrir vai ao título, queda atrapado dentro e, ao pechar, volve a quen
 * abriu. Esc ou picar fóra pecha, salvo con `busy` ou cunha acción da capa en curso (mentres se
 * garda non se pode pechar).
 *
 * Se o corpo non cabe e hai que desprazalo (unha táboa longa sen controis), o corpo é unha rexión
 * co título de nome e `tabindex="0"`, para movelo co teclado (2.1.1); só entón.
 */
@Component({
  selector: 'furar-dialog',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-dialog', '[attr.data-size]': 'size()', '[attr.aria-busy]': 'locked() ? "true" : null' },
  template: `
    <header class="f-dialog__head">
      @if (back()) {
        <button
          type="button"
          class="f-dialog__close f-dialog__back"
          data-dialog-back
          [attr.aria-label]="back()"
          [disabled]="locked()"
          (click)="close()"
        >
          <furar-icon name="chevron-left" />
        </button>
      }
      @if (subheading()) {
        <div class="f-dialog__titles">
          <h2 class="f-dialog__title" [id]="titleId">{{ heading() }}</h2>
          <p class="f-dialog__subheading">{{ subheading() }}</p>
        </div>
      } @else {
        <h2 class="f-dialog__title" [id]="titleId">{{ heading() }}</h2>
      }
      <button
        type="button"
        class="f-dialog__close"
        [attr.aria-label]="'ui.dialog.close' | transloco"
        [disabled]="locked()"
        (click)="close()"
      >
        <furar-icon name="close" />
      </button>
    </header>
    @if (description()) {
      <p class="f-dialog__text" [id]="descriptionId()">{{ description() }}</p>
    }
    <div
      #body
      class="f-dialog__body"
      [attr.role]="overflows() ? 'region' : null"
      [attr.tabindex]="overflows() ? 0 : null"
      [attr.aria-labelledby]="overflows() ? titleId : null"
    >
      <ng-content />
    </div>
    <footer class="f-dialog__foot">
      <div class="f-dialog__status"><ng-content select="[dialogStatus]" /></div>
      <ng-content select="[dialogActions]" />
    </footer>
  `,
})
export class DialogComponent {
  private readonly ref = inject(DialogRef, { optional: true });
  private readonly scope = inject(ActionScope);

  /** Título curto (≤ 5 palabras): é o nome accesible da capa. */
  readonly heading = input.required<string>();
  /** Liña en gris xusto debaixo do título, na cabeceira (de que é a capa: «Campo de fútbol 1»). */
  readonly subheading = input('');
  /** Texto debaixo do título. Para un `alertdialog`, quen abre pasa o seu id como `ariaDescribedBy`. */
  readonly description = input('');
  /**
   * Ancho máximo: `sm` 28rem (confirmacións), `md` 36rem, `lg` 46rem (listas) e `full` (ata 80 ×
   * 54rem, para traballar con imaxes; no móbil, a pantalla enteira). Sempre cabe a 320 px.
   */
  readonly size = input<DialogSize>('md');
  /** Nome dun botón «←» antes do título que pecha esta capa (volver á de debaixo). */
  readonly back = input('');
  /** Mentres se garda: «Pechar», Esc e picar fóra non pechan. */
  readonly busy = input(false, { transform: booleanAttribute });
  /** Ocupada: `busy` ou calquera acción en curso da capa (o seu `ActionScope`). */
  protected readonly locked = computed(() => this.busy() || this.scope.busy());
  /** Id do texto, para que quen abre o pase como `ariaDescribedBy` (por defecto, xerado). */
  readonly descriptionId = input(`furar-dialog-${nextId}-text`);
  protected readonly titleId = `furar-dialog-${nextId++}-title`;
  private readonly body = viewChild.required<ElementRef<HTMLElement>>('body');
  /** O corpo desprázase (máis contido ca sitio). */
  protected readonly overflows = signal(false);

  constructor() {
    // O título nomea a capa: aria-labelledby no contedor do CDK.
    const container = this.ref?.containerInstance as CdkDialogContainer | undefined;
    container?._addAriaLabelledBy?.(this.titleId);
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => container?._removeAriaLabelledBy?.(this.titleId));
    // Mídese co tamaño do corpo e co do seu contido (p. ex. cambiar un gráfico pola súa táboa).
    const view = inject(DOCUMENT).defaultView;
    afterNextRender(() => {
      const body = this.body().nativeElement;
      const measure = () => this.overflows.set(body.scrollHeight > body.clientHeight + 1);
      measure();
      if (!view?.ResizeObserver) return;
      const observer = new view.ResizeObserver(measure);
      observer.observe(body);
      for (const child of Array.from(body.children)) observer.observe(child);
      destroyRef.onDestroy(() => observer.disconnect());
    });
    // Mentres está ocupada, Esc e picar fóra non pechan; despois, o que pediu quen abriu.
    const ref = this.ref;
    if (ref) {
      const disableClose = ref.disableClose;
      effect(() => {
        ref.disableClose = this.locked() || disableClose;
      });
    }
  }

  protected close(): void {
    if (!this.locked()) {
      this.ref?.close();
    }
  }
}

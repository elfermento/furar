// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  DestroyRef,
  Directive,
  ElementRef,
  afterNextRender,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
} from '@angular/core';
import { ActionScope } from './action-scope';

/**
 * Un botón que lanza unha acción do seu contexto (`ActionScope`; docs/deseno.md §8, «Accións en
 * curso»). Vai sempre en `[furarButton]`; noutros botóns (o do chip de estado) ponse a man.
 *
 * - `busy`: esta é a acción en curso. Marca `aria-busy` (o indicador pinta o CSS) e o contexto
 *   queda ocupado ata que volve a `false`.
 * - Mentres o contexto está ocupado, este botón e todos os do contexto levan `aria-disabled="true"`
 *   e o clic (tamén o envío dun formulario co Intro) non chega á feature. Un segundo clic antes de
 *   que se pinte o `busy` tampouco pasa. Sen `disabled`: o foco non sae do botón premido.
 * - `unavailable`: non dispoñible por outro motivo (unha conta atrás), igual de bloqueado.
 */
@Directive({
  selector: '[furarAction]',
  host: {
    '[attr.aria-busy]': 'busy() ? "true" : null',
    '[attr.aria-disabled]': 'locked() ? "true" : null',
  },
})
export class ActionDirective {
  /** Esta é a acción en curso do seu contexto. */
  readonly busy = input(false, { transform: booleanAttribute });
  /** Non dispoñible (`aria-disabled`, clic bloqueado) sen saír da orde do foco. */
  readonly unavailable = input(false, { transform: booleanAttribute });

  private readonly scope = inject(ActionScope);
  /** Non se pode premer: hai unha acción en curso no contexto (esta ou outra) ou non está dispoñible. */
  protected readonly locked = computed(() => this.unavailable() || this.scope.busy());

  constructor() {
    effect(() => (this.busy() ? this.scope.claim(this) : this.scope.release(this)));
    inject(DestroyRef).onDestroy(() => this.scope.release(this));
    // En captura: no propio elemento corre antes que os `(click)` da feature, do menú e do RouterLink.
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const block = (event: Event) => {
      event.preventDefault();
      event.stopImmediatePropagation();
    };
    host.addEventListener(
      'click',
      (event) => {
        if (this.unavailable() || !this.scope.admit(this)) block(event);
      },
      { capture: true },
    );
    // Un envío sen clic (requestSubmit) tampouco pasa mentres o contexto está ocupado.
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const form = host instanceof HTMLButtonElement && host.type === 'submit' ? host.form : null;
      if (!form) return;
      const guard = (event: Event) => {
        if (this.scope.busy()) block(event);
      };
      form.addEventListener('submit', guard, { capture: true });
      destroyRef.onDestroy(() => form.removeEventListener('submit', guard, { capture: true }));
    });
  }
}

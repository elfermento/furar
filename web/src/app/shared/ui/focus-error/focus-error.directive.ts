// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DestroyRef, Directive, ElementRef, Injector, afterNextRender, inject } from '@angular/core';
import { focusErrorsAfterSubmit } from './focus-error-watch';

/**
 * Foco ao erro ao fallar un gardado (regra para toda a web), nun `<form>` ou nun
 * contedor de formularios: `<form furarFocusError …>`. Ao enviar (`submit`; tamén o dun botón de
 * fóra con `form="…"`, se o evento chega aquí), despois de pintar, o foco vai ao primeiro campo
 * con erro na orde de lectura, á vista coa súa mensaxe; se o erro non é dun campo, ao aviso de
 * erro. Se o servidor responde despois cun erro, fai o mesmo, unha vez, mentres a persoa non toque
 * nada (`focusErrorsAfterSubmit`). Quen xa leva o foco ao erro pola súa conta non estorba: é o
 * mesmo campo.
 *
 * `furar-dialog` fai o mesmo por si (todas as capas con formulario o herdan) sen esta directiva,
 * para non engadir o código á carga inicial.
 */
@Directive({
  selector: '[furarFocusError]',
  host: { '(submit)': 'onSubmit()' },
})
export class FocusErrorDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);
  private stop: (() => void) | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop?.());
  }

  protected onSubmit(): void {
    this.stop?.();
    afterNextRender(() => (this.stop = focusErrorsAfterSubmit(this.host)), { injector: this.injector });
  }
}

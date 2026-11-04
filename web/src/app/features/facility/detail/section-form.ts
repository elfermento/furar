// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Injector } from '@angular/core';
import { DestroyRef, effect, inject, untracked } from '@angular/core';
import type { FormGroup } from '@angular/forms';
import { PristineChangeEvent } from '@angular/forms';

/**
 * Enche o formulario dunha sección coa instalación **só mentres non estea sucio** (contrato de
 * estado da ficha, D1-21a): cando chega unha versión nova (outra sección gardou, recargouse) e
 * cando o formulario volve a limpo (tras gardar ou cancelar). Un formulario sucio non se toca:
 * o escrito do xestor manda ata que garde ou cancele.
 *
 * `project` devolve o valor do formulario para a instalación actual, ou `null` se aínda non hai.
 * Chámase nun contexto de inxección (construtor) ou con `injector`.
 */
export function syncWhenPristine<V extends Record<string, unknown>>(
  form: FormGroup,
  project: () => V | null,
  injector?: Injector,
): void {
  const apply = (value: V | null): void => {
    if (value === null || form.dirty) return;
    if (JSON.stringify(form.getRawValue()) === JSON.stringify(value)) return;
    form.reset(value);
  };
  effect(
    () => {
      const value = project();
      untracked(() => apply(value));
    },
    { injector },
  );
  const subscription = form.events.subscribe((event) => {
    if (event instanceof PristineChangeEvent && event.pristine) {
      apply(untracked(project));
    }
  });
  (injector?.get(DestroyRef) ?? inject(DestroyRef)).onDestroy(() => subscription.unsubscribe());
}

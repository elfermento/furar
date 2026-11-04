// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Signal } from '@angular/core';
import { DestroyRef, computed, effect, inject, signal } from '@angular/core';
import type { AbstractControl } from '@angular/forms';
import { UnsavedChangesService } from '../../../core/unsaved-changes/unsaved-changes.service';
import type { SectionTabKey } from './section';
import { MunicipalitySection } from './section';

/** O formulario dunha pestana de «Concello» e se ten cambios sen gardar. */
export interface TabForm<F extends AbstractControl> {
  /** O formulario á vista (`null` mentres carga). */
  readonly form: Signal<F | null>;
  /** O valor difire do gardado (volver ao valor de antes quita a marca). */
  readonly dirty: Signal<boolean>;
  /** Pon un formulario novo co valor gardado (ao cargar, gardar ou descartar). */
  set(form: F): void;
}

/**
 * O formulario dunha pestana co seu propio «Gardar» (maqueta 49): compara o valor co gardado, pon
 * o punto na súa pestana (`MunicipalitySection.dirty`) e, mentres ten cambios, o navegador avisa ao
 * pechar ou recargar. Chámase no inicializador dun campo da pestana (contexto de inxección).
 */
export function tabForm<F extends AbstractControl>(key: SectionTabKey): TabForm<F> {
  const section = inject(MunicipalitySection);
  const current = signal<F | null>(null);
  const saved = signal('');
  const revision = signal(0);
  const dirty = computed(() => {
    revision();
    const form = current();
    return !!form && JSON.stringify(form.getRawValue()) !== saved();
  });
  effect((onCleanup) => {
    const form = current();
    if (!form) return;
    const subscription = form.valueChanges.subscribe(() => revision.update((n) => n + 1));
    onCleanup(() => subscription.unsubscribe());
  });
  effect(() => {
    const isDirty = dirty();
    section.dirty.update((tab) => (isDirty ? key : tab === key ? null : tab));
  });
  const untrack = inject(UnsavedChangesService).track(dirty);
  inject(DestroyRef).onDestroy(() => {
    untrack();
    section.dirty.update((tab) => (tab === key ? null : tab));
  });
  return {
    form: current.asReadonly(),
    dirty,
    set(form: F) {
      saved.set(JSON.stringify(form.getRawValue()));
      current.set(form);
      revision.update((n) => n + 1);
    },
  };
}

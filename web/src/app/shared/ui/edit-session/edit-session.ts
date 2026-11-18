// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Signal } from '@angular/core';
import { DestroyRef, Injectable, Injector, computed, inject, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { UnsavedChangesService } from '../../../core/unsaved-changes/unsaved-changes.service';
import { DialogService } from '../dialog/dialog.service';

/** Unha parte da pantalla que entra no modo edición global (unha sección, unha lista). */
export interface EditParticipant {
  /** Ten cambios sen gardar. */
  readonly dirty: Signal<boolean>;
  /** Marca os seus erros á vista; falso se non se pode gardar así. */
  validate(): boolean;
  /**
   * Garda os seus cambios (unha ou varias peticións). Se algo falla, rexeita despois de mostrar o
   * erro no seu sitio e de quedar só co que aínda falta por gardar.
   */
  save(): Promise<void>;
  /** Volve ao gardado, sen peticións (Cancelar). */
  reset(): void;
}

/**
 * Modo edición global dunha pantalla (docs/deseno.md §8; especificación, bloque 1): «Editar» pon
 * todas as partes en edición e unha barra fixa (`furar-action-bar`) leva «Cancelar» e «Gardar».
 * Cada parte (`EditParticipant`) sabe validar, gardar e desfacer o seu; a sesión orquesta: valida
 * todo antes de mandar nada, garda as partes con cambios unha detrás doutra (cada unha co seu
 * `If-Match`) e só sae da edición se todas foron ben. Cancelar desfai todo sen gardar nada; con
 * cambios, `cancelAsking` pregunta antes co mesmo diálogo ca a garda de saída. Mentres hai
 * cambios, conta para a garda de saída. Unha por pantalla (`providers`).
 */
@Injectable()
export class EditSession {
  private readonly parts = signal<readonly EditParticipant[]>([]);
  private readonly active = signal(false);
  private readonly busy = signal(false);
  private readonly injector = inject(Injector);

  readonly editing = this.active.asReadonly();
  readonly saving = this.busy.asReadonly();
  /** Partes con cambios sen gardar. */
  readonly dirtyCount = computed(() => this.parts().filter((part) => part.dirty()).length);
  readonly dirty = computed(() => this.active() && this.dirtyCount() > 0);

  constructor() {
    const untrack = inject(UnsavedChangesService).track(this.dirty);
    inject(DestroyRef).onDestroy(untrack);
  }

  /** Dá de alta unha parte ata que se chame á función que devolve. */
  register(part: EditParticipant): () => void {
    this.parts.update((parts) => [...parts, part]);
    return () => this.parts.update((parts) => parts.filter((p) => p !== part));
  }

  start(): void {
    this.active.set(true);
  }

  /**
   * Garda todo. `invalid`: había erros á vista e non se mandou nada; `failed`: algunha parte non
   * se gardou (as demais si) e a pantalla segue en edición; `saved`: todo gardado, fóra da edición.
   */
  async save(): Promise<'saved' | 'invalid' | 'failed'> {
    if (this.busy()) return 'failed';
    const parts = this.parts();
    const valid = parts.map((part) => part.validate()).every(Boolean);
    if (!valid) return 'invalid';
    this.busy.set(true);
    let failed = false;
    try {
      for (const part of parts) {
        if (!part.dirty()) continue;
        try {
          await part.save();
        } catch {
          failed = true;
        }
      }
    } finally {
      this.busy.set(false);
    }
    if (failed) return 'failed';
    this.active.set(false);
    return 'saved';
  }

  /**
   * «Cancelar» da barra: sen cambios, sae; con cambios, pregunta antes de descartalos (o mesmo
   * diálogo ca ao saír da páxina, foco en «Seguir editando»). Verdadeiro se saíu da edición.
   */
  async cancelAsking(): Promise<boolean> {
    if (this.dirty()) {
      const i18n = this.injector.get(TranslocoService);
      const discard = await this.injector.get(DialogService).confirm({
        heading: i18n.translate('ui.unsavedChanges.discardTitle'),
        message: i18n.translate('ui.unsavedChanges.discardMessage'),
        confirmLabel: i18n.translate('ui.unsavedChanges.discard'),
        cancelLabel: i18n.translate('ui.unsavedChanges.stay'),
        tone: 'danger',
      });
      if (!discard) return false;
    }
    this.cancel();
    return !this.active();
  }

  /** Sae da edición sen gardar nada. */
  cancel(): void {
    if (this.busy()) return;
    for (const part of this.parts()) part.reset();
    this.active.set(false);
  }
}

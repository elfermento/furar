// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import { Injector, afterNextRender, inject, signal } from '@angular/core';
import type { AbstractControl } from '@angular/forms';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { countInvalid, focusFirstInvalid } from '../../../shared/ui/field-errors';

/**
 * O «Gardar» dunha pestana de «Concello»: valida no cliente (erros á vista e foco no primeiro),
 * garda unha vez (o botón queda ocupado sen `disabled`, para non perder o foco), avisa co toast e
 * deixa o erro da API para a pestana. Se outra persoa gardou antes (412), «Cargar os datos actuais»
 * recarga o concello. Chámase no inicializador dun campo (contexto de inxección).
 */
export class TabSave {
  private readonly injector = inject(Injector);
  private readonly toast = inject(ToastService);
  private readonly current = inject(CurrentMunicipality);

  readonly submitted = signal(false);
  readonly errorCount = signal(0);
  readonly saving = signal(false);
  readonly problem = signal<ApiProblem | null>(null);

  /**
   * Garda `form` con `save` se é válido. `onProblem` coloca os erros da API nos seus campos e
   * devolve cantos colocou. Devolve se gardou.
   */
  async run(
    form: AbstractControl,
    element: ElementRef<HTMLElement> | undefined,
    save: () => Promise<void>,
    onProblem: (problem: ApiProblem) => number = () => 0,
    beforeFocus?: () => void,
  ): Promise<boolean> {
    if (this.saving()) return false;
    this.submitted.set(true);
    this.problem.set(null);
    form.markAllAsTouched();
    const invalid = countInvalid(form);
    this.errorCount.set(invalid);
    if (invalid > 0) {
      beforeFocus?.();
      this.focusError(element);
      return false;
    }
    this.saving.set(true);
    try {
      await save();
      this.submitted.set(false);
      this.errorCount.set(0);
      this.toast.show('settings.saved');
      return true;
    } catch (error: unknown) {
      const problem = asProblem(error);
      this.problem.set(problem);
      const placed = onProblem(problem);
      if (placed) {
        this.errorCount.set(placed);
        beforeFocus?.();
        this.focusError(element);
      }
      return false;
    } finally {
      this.saving.set(false);
    }
  }

  /** Volve ao principio: sen erros á vista. */
  clear(): void {
    this.submitted.set(false);
    this.errorCount.set(0);
    this.problem.set(null);
  }

  /** Outra persoa gardou antes: recarga o concello (perdendo os cambios propios). */
  async reloadMunicipality(): Promise<boolean> {
    const slug = this.current.slug();
    if (!slug) return false;
    this.current.clear();
    const fresh = await this.current.load(slug);
    if (fresh) this.clear();
    return !!fresh;
  }

  private focusError(element: ElementRef<HTMLElement> | undefined): void {
    afterNextRender(
      () => {
        if (element) focusFirstInvalid(element.nativeElement);
      },
      { injector: this.injector },
    );
  }
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../shared/ui/edit-section/section-errors';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import type { UseForm } from '../uses/use-form';
import { applyUseErrors, partForm, useBody, useForm } from '../uses/use-form';
import type { Use } from '../uses/uses.store';
import { UsesStore } from '../uses/uses.store';
import type { UsePreset } from './wizard';
import { WizardForms } from './wizard-forms';

/** O formulario dun uso aberto no paso «Usos»: un novo (`use` nulo) ou un dos gardados. */
export interface UseEditor {
  readonly use: Use | null;
  readonly form: UseForm;
}

/**
 * O uso que se está a escribir no paso «Usos» (maqueta 53): un só á vez, no sitio. «Engadir uso»
 * ou «Aceptar» vai á API ao momento (o uso ten a súa propia versión); os erros, no seu campo e o
 * resto, no aviso do formulario. Un por páxina.
 */
@Injectable()
export class WizardUseEditor {
  private readonly uses = inject(UsesStore);
  private readonly detail = inject(FacilityDetailStore);
  private readonly forms = inject(WizardForms);

  readonly editor = signal<UseEditor | null>(null);
  readonly busy = signal(false);
  readonly problem = signal<ApiProblem | null>(null);

  /** Abre un uso para editalo ou un novo (baleiro ou cun atallo). */
  open(use: Use | null, preset?: UsePreset): void {
    const form = useForm(use ?? undefined);
    if (preset) {
      form.controls.name.setValue(preset.name);
      form.controls.rentalMode.setValue(preset.rentalMode);
      if (preset.rentalMode === 'PARTS') {
        form.controls.parts.clear();
        for (const part of preset.parts) form.controls.parts.push(partForm(null, part));
      }
      if (preset.capacity !== null) form.controls.capacity.setValue(preset.capacity);
    }
    this.problem.set(null);
    this.editor.set({ use, form });
  }

  close(): void {
    this.problem.set(null);
    this.editor.set(null);
  }

  /** Garda o uso aberto (se o hai). `false` se ten erros ou a API dixo que non. */
  async apply(): Promise<boolean> {
    const editor = this.editor();
    if (!editor) return true;
    editor.form.markAllAsTouched();
    if (editor.form.invalid) return false;
    const body = useBody(editor.form);
    this.busy.set(true);
    this.problem.set(null);
    try {
      if (editor.use) await this.uses.update(editor.use.id, body);
      else await this.uses.create(body);
      this.editor.set(null);
      // Co primeiro uso deixa de faltar: o que falta vén da API.
      await this.detail.reload();
      return true;
    } catch (error) {
      const problem = asProblem(error);
      if (!applyUseErrors(editor.form, problem)) this.problem.set(problem);
      return false;
    } finally {
      this.busy.set(false);
      this.forms.touch();
    }
  }
}

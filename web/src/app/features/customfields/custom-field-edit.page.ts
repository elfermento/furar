// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { EditableDefinition } from './customfields.store';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { LanguageService } from '../../core/i18n/language.service';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import type { CustomFieldDefinitionDraft } from '../../shared/custom-fields/custom-field.types';
import { CustomFieldEditorComponent } from '../../shared/custom-fields/editor/custom-field-editor.component';
import { localized } from '../../shared/custom-fields/localized';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { CustomFieldsStore } from './customfields.store';

/** Alta (/novo) ou edición (/:id) dun campo personalizado do concello. */
@Component({
  selector: 'furar-custom-field-edit-page',
  imports: [
    TranslocoPipe,
    CustomFieldEditorComponent,
    PageHeaderComponent,
    ProblemAlertComponent,
    StatusStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-page-header
      [heading]="(id() ? 'customFields.editor.editTitle' : 'customFields.editor.newTitle') | transloco"
      [intro]="'customFields.editor.intro' | transloco"
      [level]="inSection ? 2 : 1"
    />
    @if (!id() || definition()) {
      <furar-custom-field-editor
        [definition]="definition()"
        [problem]="problem()"
        [saving]="saving()"
        (save)="save($event)"
        (cancelled)="back()"
      />
    } @else if (loadProblem()) {
      <furar-problem-alert [problem]="loadProblem()" />
    } @else {
      <furar-status-state mode="loading" />
    }
  `,
})
export class CustomFieldEditPage implements OnInit {
  /** Dentro da sección «Concello» (`data.section`): o h1 é o da sección. */
  protected readonly inSection = inject(ActivatedRoute).snapshot.data['section'] === true;

  private readonly store = inject(CustomFieldsStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly language = inject(LanguageService);
  private readonly municipality = inject(CurrentMunicipality);
  /** Parámetro :id da ruta (ausente en /novo). */
  readonly id = input<string>();

  protected readonly definition = signal<EditableDefinition | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly loadProblem = signal<ApiProblem | null>(null);
  protected readonly saving = signal(false);

  ngOnInit(): void {
    void this.initialize();
  }

  private async initialize(): Promise<void> {
    const id = this.id();
    const slug = this.municipality.slug();
    if (id && slug) {
      try {
        this.definition.set(await this.store.get(slug, id));
      } catch (error: unknown) {
        this.loadProblem.set(asProblem(error));
      }
    }
  }

  protected async save(draft: CustomFieldDefinitionDraft): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) {
      return;
    }
    this.saving.set(true);
    this.problem.set(null);
    try {
      const current = this.definition();
      // O editor devolve todos os usos: os sitios desmarcados quítanse (a API rexeita quitar un con valores).
      const usages = draft.usages;
      const saved = current
        ? await this.store.update(slug, current, {
            label: draft.label,
            help: draft.help,
            rules: draft.rules,
            options: draft.options,
            personalData: draft.personalData,
            usages,
          })
        : await this.store.create(slug, {
            code: draft.code,
            type: draft.type,
            label: draft.label,
            help: draft.help,
            rules: draft.rules,
            options: draft.options,
            personalData: draft.personalData,
            usages,
          });
      this.toast.show('customFields.editor.saved', { label: localized(saved.label, this.language.lang()) });
      await this.back();
    } catch (error: unknown) {
      this.problem.set(asProblem(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected back(): Promise<boolean> {
    const slug = this.municipality.slug() ?? '';
    return this.router.navigate(['/', slug, 'xestion', 'concello', 'campos']);
  }
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { EditableDefinition } from './customfields.store';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { LanguageService } from '../../core/i18n/language.service';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { localized } from '../../shared/custom-fields/localized';
import { ActiveToggleButtonComponent } from '../../shared/ui/active-toggle-button/active-toggle-button.component';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { DeleteButtonComponent } from '../../shared/ui/delete-button/delete-button.component';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { RowListDirective } from '../../shared/ui/row/row-list.directive';
import { RowComponent } from '../../shared/ui/row/row.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import { CUSTOM_FIELD_TARGETS } from '../../shared/custom-fields/custom-field.types';
import { ACTIVE_LIMIT, CustomFieldsStore } from './customfields.store';

/**
 * Lista de campos do concello (activos e desactivados) para o xestor, en orde alfabética da API, con
 * onde se usa cada un (un campo, un sitio). Cada fila: editar, desactivar ou activar e eliminar (só
 * sen valores; con eles, o diálogo ofrece desactivalo). Sen orde manual nin grupos.
 */
@Component({
  selector: 'furar-custom-fields-admin-page',
  imports: [
    ActiveToggleButtonComponent,
    DeleteButtonComponent,
    RouterLink,
    TranslocoPipe,
    ProblemAlertComponent,
    ButtonDirective,
    IconComponent,
    PageHeaderComponent,
    RowListDirective,
    RowComponent,
    StatusStateComponent,
    TagComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './custom-fields-admin.page.html',
})
export class CustomFieldsAdminPage implements OnInit {
  /** Dentro da sección «Concello» (`data.section`): o h1 é o da sección. */
  protected readonly inSection = inject(ActivatedRoute).snapshot.data['section'] === true;

  private readonly store = inject(CustomFieldsStore);
  private readonly toast = inject(ToastService);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  protected readonly municipality = inject(CurrentMunicipality);
  protected readonly language = inject(LanguageService);
  protected readonly definitions = signal<EditableDefinition[] | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly busy = signal<string | null>(null);
  protected readonly limit = ACTIVE_LIMIT;
  protected readonly localized = localized;
  protected readonly knownTarget = (target: string) => CUSTOM_FIELD_TARGETS.includes(target);

  protected readonly active = computed(() => (this.definitions() ?? []).filter((d) => d.status === 'ACTIVE'));
  protected readonly inactive = computed(() => (this.definitions() ?? []).filter((d) => d.status === 'INACTIVE'));

  ngOnInit(): void {
    void this.initialize();
  }

  private async initialize(): Promise<void> {
    await this.load();
  }

  protected async toggleActive(definition: EditableDefinition): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) {
      return;
    }
    this.busy.set(definition.id);
    this.problem.set(null);
    try {
      const activate = definition.status !== 'ACTIVE';
      const updated = await this.store.setActive(slug, definition, activate);
      this.replace(updated);
      this.toast.show(activate ? 'customFields.admin.activatedNotice' : 'customFields.admin.deactivatedNotice', {
        label: localized(updated.label, this.language.lang()),
      });
    } catch (error: unknown) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(null);
    }
  }

  /**
   * Eliminar pregunta sempre. Sen valores, borra; con eles, di que non se pode e ofrece desactivalo
   * (ou, se xa o está, só informa). A API compróbao igual (`customfield.has-values`).
   */
  protected async remove(definition: EditableDefinition): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) {
      return;
    }
    const label = localized(definition.label, this.language.lang());
    const t = (key: string) => this.transloco.translate(`customFields.admin.${key}`, { label });
    this.problem.set(null);
    if (definition.hasValues) {
      const active = definition.status === 'ACTIVE';
      const deactivate = await this.dialogs.confirm({
        heading: t('cannotDeleteTitle'),
        message: t(active ? 'cannotDelete' : 'cannotDeleteInactive'),
        confirmLabel: this.transloco.translate(active ? 'customFields.admin.deactivate' : 'ui.dialog.close'),
        informOnly: !active,
      });
      if (deactivate && active) {
        await this.toggleActive(definition);
      }
      return;
    }
    try {
      const deleted = await this.dialogs.confirm({
        heading: t('deleteTitle'),
        message: t('deleteMessage'),
        confirmLabel: this.transloco.translate('customFields.admin.delete'),
        busyLabel: this.transloco.translate('customFields.admin.deleting'),
        tone: 'danger',
        action: () => this.store.delete(slug, definition),
      });
      if (deleted) {
        this.definitions.update((list) => (list ?? []).filter((item) => item.id !== definition.id));
        this.toast.show('customFields.admin.deletedNotice', { label });
      }
    } catch (error: unknown) {
      this.problem.set(asProblem(error));
    }
  }

  private replace(updated: EditableDefinition): void {
    this.definitions.update((list) => (list ?? []).map((item) => (item.id === updated.id ? updated : item)));
  }

  private async load(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) {
      return;
    }
    try {
      this.definitions.set(await this.store.list(slug));
    } catch (error: unknown) {
      this.problem.set(asProblem(error));
    }
  }
}

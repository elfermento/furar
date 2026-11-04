// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inject } from '@angular/core';
import type { CanDeactivateFn } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { UnsavedChangesService } from './unsaved-changes.service';

/**
 * Garda de saída (D1-05b): se hai seccións con cambios sen gardar, pregunta co diálogo propio
 * (`furar-confirm`, foco en «Seguir editando») antes de deixar a ruta. Sen cambios, deixa pasar.
 * Úsase en `canDeactivate` das rutas que teñen `furar-edit-section`.
 */
export const unsavedChangesGuard: CanDeactivateFn<unknown> = () => {
  if (!inject(UnsavedChangesService).hasUnsaved()) {
    return true;
  }
  const i18n = inject(TranslocoService);
  return inject(DialogService).confirm({
    heading: i18n.translate('ui.unsavedChanges.title'),
    message: i18n.translate('ui.unsavedChanges.message'),
    confirmLabel: i18n.translate('ui.unsavedChanges.leave'),
    cancelLabel: i18n.translate('ui.unsavedChanges.stay'),
    tone: 'danger',
  });
};

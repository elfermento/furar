// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { firstValueFrom } from 'rxjs';
import type { DialogService } from '../ui/dialog/dialog.service';

/**
 * Abre «Persoas ao meu cargo» (menú da conta e «Xestionar» de «Para quen»). O diálogo cárgase á
 * parte: non entra na carga inicial (orzamento, D27).
 */
export async function openDependents(dialogs: DialogService): Promise<void> {
  const { DependentsDialogComponent } = await import('./dependents-dialog.component');
  dialogs.open(DependentsDialogComponent);
}

/** Abre «Persoa ao meu cargo» para gardar unha nova e devolve o seu id (ou `null` se se pecha). */
export async function addDependent(dialogs: DialogService): Promise<string | null> {
  const { DependentDialogComponent } = await import('./dependent-dialog.component');
  const ref = dialogs.open<{ id: string } | null, { dependent: null }>(DependentDialogComponent, {
    data: { dependent: null },
    autoFocus: '[data-dependent-name]',
  });
  return (await firstValueFrom(ref.closed, { defaultValue: null }))?.id ?? null;
}

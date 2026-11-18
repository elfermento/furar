// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Injector } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { DialogService } from '../ui/dialog/dialog.service';
import type { Translate } from './account-deletion';
import { blockerRows } from './account-deletion';
import type { AccountDeletionResponse } from './account-deletion.store';
import { AccountDeletionStore } from './account-deletion.store';

/** O que precisa «Eliminar a miña conta» de quen o abre (o menú da conta). */
export interface AccountDeletionDeps {
  /** Pregunta á API (`AccountDeletionStore.load`). */
  readonly load: () => Promise<AccountDeletionResponse>;
  readonly dialogs: DialogService;
  readonly t: Translate;
  /** Vai a Keycloak coa acción que elimina a conta (`SessionService.deleteAccount`). */
  readonly deleteAccount: () => Promise<void>;
}

/**
 * «Eliminar a miña conta» (ADR-0043): primeiro pregunta á API se se pode. Se non queda nada
 * pendente, vai a Keycloak, que pide entrar outra vez e mostra a confirmación co que se borra e o
 * que se conserva. Se queda algo, o diálogo lístao coas ligazóns e non se vai a ningún sitio. Se a
 * API non responde, un aviso: sen resposta non se elimina nada.
 */
export async function openAccountDeletion(deps: AccountDeletionDeps): Promise<void> {
  const { t } = deps;
  let answer: AccountDeletionResponse;
  try {
    answer = await deps.load();
  } catch {
    await deps.dialogs.confirm({
      heading: t('accountDeletion.title'),
      message: t('accountDeletion.unavailable'),
      confirmLabel: t('accountDeletion.close'),
      informOnly: true,
    });
    return;
  }
  if (answer.allowed) {
    await deps.deleteAccount();
    return;
  }
  const { AccountDeletionDialogComponent } = await import('./account-deletion-dialog.component');
  deps.dialogs.open(AccountDeletionDialogComponent, { data: { rows: blockerRows(answer.blockers, t) } });
}

/** O mesmo, cos servizos do inxector de quen o abre (o menú da conta, que se carga á parte). */
export function openAccountDeletionWith(injector: Injector, deleteAccount: () => Promise<void>): Promise<void> {
  const store = injector.get(AccountDeletionStore);
  const transloco = injector.get(TranslocoService);
  return openAccountDeletion({
    load: () => store.load(),
    dialogs: injector.get(DialogService),
    t: (key, params) => transloco.translate(key, params),
    deleteAccount,
  });
}

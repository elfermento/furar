// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Signal } from '@angular/core';
import { inject } from '@angular/core';
import type { ActivatedRouteSnapshot, CanDeactivateFn, RouterStateSnapshot } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { DialogService } from '../../shared/ui/dialog/dialog.service';

/** Unha pestana cun formulario e o seu propio «Gardar». */
export interface LeavableTab {
  /** Ten cambios sen gardar. */
  readonly dirty: Signal<boolean>;
  /** Nome da pestana para a pregunta («Aparencia»), xa traducido. */
  readonly tabName: Signal<string>;
  /** Garda; `false` se non se puido (erros á vista ou da API): entón non se sae. */
  save(): Promise<boolean>;
  /** Volve aos datos gardados. */
  discard(): void;
}

/**
 * Saír dunha pestana con cambios (Concello): pregunta «Gardar os cambios de …?» con Gardar,
 * Descartar e Cancelar. Sen cambios, ou se o destino segue dentro da mesma pestana (as páxinas
 * legais dentro de «Legal»), deixa pasar sen preguntar. Úsase en `canDeactivate`.
 */
export const leaveTabGuard = async (
  tab: LeavableTab | null,
  route: ActivatedRouteSnapshot,
  _current: RouterStateSnapshot,
  next?: RouterStateSnapshot,
): Promise<boolean> => {
  if (!tab?.dirty() || (next && staysIn(route, next.url))) return true;
  const i18n = inject(TranslocoService);
  const choice = await inject(DialogService).unsavedChoice({
    heading: i18n.translate('ui.unsavedChoice.title', { tab: tab.tabName() }),
  });
  if (choice === 'save') return tab.save();
  if (choice === 'discard') {
    tab.discard();
    return true;
  }
  return false;
};
leaveTabGuard satisfies CanDeactivateFn<LeavableTab>;

/**
 * O destino segue na mesma ruta da pestana: o seu primeiro segmento (`legal` de `legal/:page`) e os
 * de enriba coinciden (`/naron/xestion/concello/legal/aviso-legal` desde `…/legal/privacidade`).
 */
function staysIn(route: ActivatedRouteSnapshot, url: string): boolean {
  const segments = route.pathFromRoot.flatMap((r) => r.url.map((segment) => segment.path));
  const prefix = segments.slice(0, segments.length - route.url.length + 1);
  const [path = ''] = url.split(/[?#]/);
  const target = path.split('/').filter(Boolean);
  return prefix.length > 0 && prefix.every((segment, i) => target[i] === segment);
}

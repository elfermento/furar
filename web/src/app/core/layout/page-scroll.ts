// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ViewportScroller } from '@angular/common';
import type { EnvironmentProviders } from '@angular/core';
import { DestroyRef, inject, provideEnvironmentInitializer } from '@angular/core';
import { NavigationEnd, Router, Scroll } from '@angular/router';

/**
 * Clave do `state` dunha navegación que non move a páxina: as pestanas que son páxinas
 * (`furarTabLink`) cambian só o que hai debaixo, así que a páxina queda onde estaba.
 */
export const KEEP_SCROLL_STATE = 'furarKeepScroll';

/**
 * Desprazamento tras navegar, no canto do `scrollPositionRestoration` do router (que vai
 * `disabled`; as áncoras, `anchorScrolling: 'enabled'`): atrás e adiante volven á posición que
 * tiña a páxina; unha navegación nova vai arriba de todo, salvo que leve `KEEP_SCROLL_STATE`
 * no `state`.
 */
export function providePageScroll(): EnvironmentProviders {
  return provideEnvironmentInitializer(() => {
    const router = inject(Router);
    const scroller = inject(ViewportScroller);
    scroller.setHistoryScrollRestoration('manual');
    const subscription = router.events.subscribe((event) => {
      if (!(event instanceof Scroll) || event.scrollBehavior === 'manual') return;
      if (event.position) {
        scroller.scrollToPosition(event.position, { behavior: 'instant' });
      } else if (!event.anchor && !keepsScroll(router, event)) {
        scroller.scrollToPosition([0, 0]);
      }
    });
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  });
}

function keepsScroll(router: Router, event: Scroll): boolean {
  if (!(event.routerEvent instanceof NavigationEnd)) return false;
  const navigation = router.lastSuccessfulNavigation();
  return navigation?.id === event.routerEvent.id && !!navigation.extras.state?.[KEEP_SCROLL_STATE];
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { HOME_STYLESHEET, homeStylesResolver } from './home-styles';

describe('folla das portadas', () => {
  it('pon o seu propio <link> unha soa vez e a ruta agarda a que cargue', async () => {
    const resolve = () =>
      TestBed.runInInjectionContext(() =>
        homeStylesResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
      ) as Promise<true> | true;
    const first = resolve();
    const links = document.querySelectorAll<HTMLLinkElement>('link#furar-home-css');
    expect(links).toHaveLength(1);
    expect(links[0].href).toBe(new URL(HOME_STYLESHEET, document.baseURI).href);
    links[0].dispatchEvent(new Event('load'));
    await expect(first).resolves.toBe(true);
    await expect(Promise.resolve(resolve())).resolves.toBe(true);
    expect(document.querySelectorAll('link#furar-home-css')).toHaveLength(1);
  });
});

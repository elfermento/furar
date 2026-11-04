// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import type { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { MANAGEMENT_STYLESHEET, managementStylesResolver } from './management-styles';

const resolve = () =>
  TestBed.runInInjectionContext(() =>
    managementStylesResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
  ) as Promise<true> | true;

describe('folla da xestión', () => {
  // Os specs comparten documento: outro (a portada) pode deixar a folla xa posta.
  beforeEach(() => document.querySelectorAll('link#furar-management-css').forEach((l) => l.remove()));

  it('pon un só <link> á mesma orixe e a ruta agarda a que cargue', async () => {
    const first = resolve();
    const links = document.querySelectorAll<HTMLLinkElement>('link#furar-management-css');
    expect(links).toHaveLength(1);
    expect(links[0].rel).toBe('stylesheet');
    expect(links[0].href).toBe(new URL(MANAGEMENT_STYLESHEET, document.baseURI).href);

    let done = false;
    void Promise.resolve(first).then(() => (done = true));
    await Promise.resolve();
    expect(done).toBe(false);
    links[0].dispatchEvent(new Event('load'));
    await expect(first).resolves.toBe(true);

    // Outra ruta de xestión: o mesmo <link>, xa cargado.
    await expect(Promise.resolve(resolve())).resolves.toBe(true);
    expect(document.querySelectorAll('link#furar-management-css')).toHaveLength(1);
  });
});

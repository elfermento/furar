// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { Routes } from '@angular/router';
import { Router, provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { municipalityRoutes } from './features/municipality/municipality.routes';
import { municipalitySectionRoutes } from './municipality-section.routes';

@Component({ template: '' })
class Blank {}

/** As rutas da sección «Concello» (maqueta 49) e os enderezos de antes. */
describe('rutas da sección «Concello»', () => {
  it('a configuración é válida (o router valídaa ao crearse)', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([...routes])] });
    expect(TestBed.inject(Router).config.length).toBeGreaterThan(0);
  });

  it('cada pestana co seu URL; «Legal» vai á privacidade; as de formulario preguntan ao saír', () => {
    const byPath = new Map(municipalitySectionRoutes.map((route) => [route.path, route]));
    expect([...byPath.keys()]).toEqual([
      '',
      'xeral',
      'aparencia',
      'servizo',
      'legal',
      'legal/:page',
      'persoal',
      'campos',
      'rexistro',
    ]);
    expect(byPath.get('')?.redirectTo).toBe('xeral');
    expect(byPath.get('legal')?.redirectTo).toBe('legal/privacidade');
    for (const path of ['xeral', 'aparencia', 'servizo', 'legal/:page']) {
      expect(byPath.get(path)?.canDeactivate, path).toHaveLength(1);
    }
    for (const path of ['persoal', 'campos', 'rexistro']) {
      expect(byPath.get(path)?.data?.['section'], path).toBe(true);
    }
  });

  it('os enderezos de antes levan ao seu sitio novo, tamén a páxina legal do fragmento', async () => {
    // Coa mesma árbore ca a app: as redireccións e, despois, as fillas de '' (a portada).
    const children = (routes.find((route) => route.path === ':municipality')?.children ?? []) as Routes;
    const redirects = children.filter((route) => route.redirectTo);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: ':municipality',
            children: [
              ...redirects,
              { path: 'xestion/concello', children: [{ path: '**', component: Blank }] },
              {
                path: '',
                children: [
                  ...municipalityRoutes.map((route) => ({ ...route, loadComponent: undefined, component: Blank })),
                ],
              },
            ],
          },
        ]),
      ],
    });
    const router = TestBed.inject(Router);
    const go = async (url: string) => {
      await router.navigateByUrl(url);
      return router.url;
    };
    expect(await go('/naron/xestion')).toBe('/naron/xestion/concello');
    expect(await go('/naron/xestion#aviso-legal')).toBe('/naron/xestion/concello/legal/aviso-legal#aviso-legal');
    expect(await go('/naron/xestion/persoal')).toBe('/naron/xestion/concello/persoal');
    expect(await go('/naron/xestion/rexistro?area=staff&fallidos=1')).toBe(
      '/naron/xestion/concello/rexistro?area=staff&fallidos=1',
    );
    expect(await go('/naron/xestion/campos')).toBe('/naron/xestion/concello/campos');
    expect(await go('/naron/xestion/campos/novo')).toBe('/naron/xestion/concello/campos/novo');
  });
});

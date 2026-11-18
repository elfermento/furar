// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { Route, Routes } from '@angular/router';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { restOfUrl } from './route-group';

@Component({ template: 'grupo' })
class Grouped {}
@Component({ template: 'portada' })
class Home {}

async function render(url: string, guarded: boolean): Promise<string> {
  const group: Route = {
    path: '',
    loadChildren: () => Promise.resolve([{ path: 'xestion/reservas', component: Grouped }]),
    ...(guarded ? { canMatch: [restOfUrl] } : {}),
  };
  const config: Routes = [
    {
      path: ':municipality',
      children: [group, { path: '', loadChildren: () => Promise.resolve([{ path: '', component: Home }]) }],
    },
  ];
  TestBed.configureTestingModule({ providers: [provideRouter(config)] });
  const harness = await RouterTestingHarness.create(url);
  return (harness.fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';
}

describe('grupos de rutas sen prefixo baixo /:concello', () => {
  it('sen restOfUrl, o grupo deixaría a portada en branco', async () => {
    expect(await render('/naron', false)).toBe('');
  });

  it('con restOfUrl, /:concello chega á portada e o resto, ao grupo', async () => {
    expect(await render('/naron', true)).toBe('portada');
    TestBed.resetTestingModule();
    expect(await render('/naron/xestion/reservas', true)).toBe('grupo');
  });

  it("en app.routes, todo grupo `''` con loadChildren antes do último leva restOfUrl", () => {
    const children = routes.find((route) => route.path === ':municipality')?.children ?? [];
    const groups = children.filter((route) => route.path === '' && route.loadChildren);
    expect(groups.length).toBeGreaterThan(1);
    for (const group of groups.slice(0, -1)) {
      expect(group.canMatch, String(group.loadChildren)).toContain(restOfUrl);
    }
  });
});

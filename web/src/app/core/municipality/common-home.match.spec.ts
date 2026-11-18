// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import type { Route } from '@angular/router';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { severalMunicipalitiesMatch } from './common-home.match';

const LIST = '/api/v1/municipalities?page=0&size=100';
const ROUTE: Route = { path: '' };

describe('a raíz é a portada común só con varios concellos', () => {
  afterEach(() => backend().verify());

  const page = (slugs: string[]) => ({
    items: slugs.map((slug, i) => ({ id: String(i), slug, name: slug })),
    page: 0,
    size: 100,
    totalItems: slugs.length,
  });

  function match(): Promise<boolean> {
    return TestBed.runInInjectionContext(() => severalMunicipalitiesMatch(ROUTE, [], {} as never)) as Promise<boolean>;
  }

  it('con varios, si; cun só ou ningún, segue a ruta de sempre', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const several = match();
    (await expectRequest(LIST)).flush(page(['naron', 'ferrol']));
    expect(await several).toBe(true);

    // Cada entrada na raíz le a lista outra vez: un concello que se quitou ou engadiu nótase.
    const one = match();
    (await expectRequest(LIST)).flush(page(['naron']));
    expect(await one).toBe(false);

    const none = match();
    (await expectRequest(LIST)).flush(page([]));
    expect(await none).toBe(false);
  });

  it('se a lista non se pode ler, segue a ruta de sempre, que mostra o erro', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const failed = match();
    flushProblem(await expectRequest(LIST), 500, 'common.internal');
    expect(await failed).toBe(false);
  });
});

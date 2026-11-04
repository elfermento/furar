// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { @Name@ListPage } from './@name-kebab@-list.page';

const LIST = '/api/v1/municipalities/naron/@names@';

describe('@Name@ListPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
  });
  afterEach(() => backend().verify());

  it('pinta a lista do concello; quen xestiona ve as ligazóns de alta e edición', async () => {
    const fixture = TestBed.createComponent(@Name@ListPage);
    (await expectRequest(LIST)).flush({
      items: [{ id: 'a', name: 'Primeiro', version: 0, createdAt: '2026-10-22T10:00:00Z', updatedAt: '2026-10-22T10:00:00Z' }],
      page: 0,
      size: 20,
      totalItems: 1,
    });
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('li')?.textContent).toContain('Primeiro');
    });
    expect(el.querySelector('li a')?.getAttribute('href')).toBe('/a');
    expect(el.querySelector('a[href="/novo"]')).not.toBeNull();
  });

  it('sen permiso, só texto', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: provideTestApi(fakeSession({ manager: 'ferrol' })) });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(@Name@ListPage);
    (await expectRequest(LIST)).flush({ items: [], page: 0, size: 20, totalItems: 0 });
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.textContent).toContain('Aínda non hai');
    });
    expect(el.querySelector('a')).toBeNull();
  });

  it('mostra o erro da API', async () => {
    const fixture = TestBed.createComponent(@Name@ListPage);
    flushProblem(await expectRequest(LIST), 500, 'common.internal');
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('[role="alert"]')).not.toBeNull();
    });
  });
});

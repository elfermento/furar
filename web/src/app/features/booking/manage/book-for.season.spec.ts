// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, fakeSession, flushMatching, provideTestApi } from '../../../../testing/api';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { BASE, FACILITY_URL, FIELDS_URL, NOTES, NOTES_URL, availability, facility } from '../reserve/reserve.fixture';
import { BookForPage } from './book-for.page';

const FACILITIES = {
  items: [{ id: 'f1', name: 'Pavillón', status: 'ACTIVE', published: true, version: 1, missing: [], sports: [] }],
  page: 0,
  size: 100,
  totalItems: 1,
};

describe('Nova reserva: «Unha vez · Tempada»', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/availability'), availability());
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('a xestión cambia a Tempada: o formulario da tempada no canto do de sempre, e volta', async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession()),
        provideRouter([{ path: 'naron/xestion/reservas/nova', component: BookForPage }], withComponentInputBinding()),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create('/naron/xestion/reservas/nova?tipo=tempada');
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    (await expectRequest((r) => r.url === `${BASE}/facilities`)).flush(FACILITIES);
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushMatching((r) => r.url === FIELDS_URL, []);
    (await expectRequest(FACILITY_URL)).flush(facility());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-book-for-season]')).not.toBeNull();
    });
    expect(el.querySelector('[data-book-for-go]')).toBeNull();
    flushMatching((r) => r.url === `${BASE}/organizations`, []);
    flushMatching((r) => r.url === `${BASE}/booking-series-requests`, []);
    flushMatching((r) => r.url === `${BASE}/public/facilities`, []);
    el.querySelector<HTMLButtonElement>('[data-kind="once"]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-book-for-go]')).not.toBeNull();
    });
  });
});

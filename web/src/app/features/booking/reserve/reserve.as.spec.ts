// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, fakeSession, flushMatching, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { pickOption, selectText } from '../../../../testing/select';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import {
  AVAILABILITY_URL,
  BASE,
  BLOCK_URL,
  DAY,
  FACILITY_URL,
  FIELDS_URL,
  NOTES,
  NOTES_URL,
  NOT_BLOCKED,
  availability,
  booking,
  facility,
} from './reserve.fixture';
import { ReservePage } from './reserve.page';

const MY_ORGANIZATIONS = `${BASE}/me/organizations`;

describe('Páxina de reservar: «Reservar como» unha entidade (ADR-0039, punto 10)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(url: string, organizations: object[]) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({})),
        provideRouter(
          [{ path: 'naron/instalacions/:id/reservar', component: ReservePage }],
          withComponentInputBinding(),
        ),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushMatching((r) => r.url === BLOCK_URL, NOT_BLOCKED);
    flushMatching((r) => r.url === FIELDS_URL, []);
    (await expectRequest(MY_ORGANIZATIONS)).flush(organizations);
    (await expectRequest(FACILITY_URL)).flush(facility());
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(availability());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-reserve-field="time"] .f-field-popover__value')?.textContent).toContain('18:00');
    });
    return { el, settle };
  }

  const url = `/naron/instalacions/f1/reservar?uso=u1&data=${DAY}&hora=18:00&parte=p2`;

  it('sen entidades: sen o campo, coma sempre', async () => {
    const { el } = await render(url, []);
    expect(el.querySelector('[data-reserve-as]')).toBeNull();
  });

  it('responsable: «Eu (nome)» ou a entidade; reservar como a entidade vai pola súa ruta; axe', async () => {
    const { el, settle } = await render(url, [{ id: 'o1', name: 'Club Baloncesto' }]);
    const field = el.querySelector<HTMLElement>('[data-reserve-as]')!;
    expect(selectText(field)).toBe('Eu (Xestora Narón)');
    await expectNoAxeViolations(el);
    await pickOption(field, 'Club Baloncesto', settle);
    await vi.waitFor(async () => {
      await settle();
      expect(selectText(el.querySelector<HTMLElement>('[data-reserve-as]')!)).toBe('Club Baloncesto');
    });
    el.querySelector<HTMLButtonElement>('[data-reserve-go]')!.click();
    const post = await expectRequest({ method: 'POST', url: `${MY_ORGANIZATIONS}/o1/bookings` });
    expect(post.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(post.request.body).toMatchObject({ facilityId: 'f1', useId: 'u1', partId: 'p2', slots: 1 });
    expect(post.request.body).not.toHaveProperty('participants');
    post.flush(booking({ organization: { id: 'o1', name: 'Club Baloncesto' } }));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('furar-reserve-confirmed')).not.toBeNull();
    });
  });

  it('recargar a confirmada dunha entidade (`?como=`) pídea á entidade', async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({})),
        provideRouter(
          [{ path: 'naron/instalacions/:id/reservar', component: ReservePage }],
          withComponentInputBinding(),
        ),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    await RouterTestingHarness.create('/naron/instalacions/f1/reservar?reserva=b1&como=o1');
    (await expectRequest(`${MY_ORGANIZATIONS}/o1/bookings/b1`)).flush(booking());
    flushMatching((r) => r.url === NOTES_URL, NOTES);
    flushMatching((r) => r.url === FIELDS_URL, []);
    flushMatching((r) => r.url === MY_ORGANIZATIONS, []);
    flushMatching((r) => r.url === FACILITY_URL, facility());
  });
});

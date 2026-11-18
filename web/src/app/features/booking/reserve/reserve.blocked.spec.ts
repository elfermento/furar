// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import {
  backend,
  expectRequest,
  fakeSession,
  flushMatching,
  flushProblem,
  provideTestApi,
} from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import {
  AVAILABILITY_URL,
  BLOCK_URL,
  BOOKINGS_URL,
  CONTACT_URL,
  DAY,
  FACILITY_URL,
  FIELDS_URL,
  NOTES,
  NOTES_URL,
  NOT_BLOCKED,
  availability,
  facility,
} from './reserve.fixture';
import { ReservePage } from './reserve.page';

const BLOCKED = { blocked: true, until: '2026-11-24', faults: 3, windowDays: 30 };

describe('Páxina de reservar: bloqueada por faltas', () => {
  afterEach(() => {
    // As entidades das que é responsable (ADR-0039): «Eu · ‹Entidade›» e «Reservar como».
    flushMatching((r) => r.url.endsWith('/me/organizations'), []);
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  /** Monta a páxina e responde a todo; ao bloqueo, con `block` e o contacto do servizo. */
  async function render(url: string, block: object, contact: object = { phone: '981 000 000' }) {
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
    (await expectRequest(BLOCK_URL)).flush(block);
    if (block === NOT_BLOCKED) backend().expectNone(CONTACT_URL);
    else (await expectRequest(CONTACT_URL)).flush(contact);
    flushMatching((r) => r.url === FIELDS_URL, []);
    (await expectRequest(FACILITY_URL)).flush(facility());
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(availability());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-reserve-field="time"] .f-field-popover__value')?.textContent).toContain('18:00');
    });
    return { el, settle };
  }

  const url = `/naron/instalacions/f1/reservar?uso=u1&data=${DAY}&hora=18:00&parte=p2`;

  it('ata cando, por que e o teléfono do servizo; o escollido á vista, sen total nin «Confirmar reserva»; axe', async () => {
    const { el } = await render(url, BLOCKED);
    const notice = el.querySelector<HTMLElement>('[data-reserve-blocked]')!;
    expect(notice.getAttribute('data-tone')).toBe('danger');
    expect(notice.textContent).toContain('Non podes reservar por internet ata o 24 de novembro');
    expect(notice.textContent).toContain(
      'Tes 3 faltas nos últimos 30 días. No mostrador si podes reservar: 981 000 000.',
    );
    expect(el.querySelector('[data-reserve-go]')).toBeNull();
    expect(el.querySelector('[data-reserve-total]')).toBeNull();
    await expectNoAxeViolations(el);
  });

  it('cunha soa falta e sen teléfono do servizo: en singular e sen el', async () => {
    const { el } = await render(url, { ...BLOCKED, faults: 1 }, {});
    expect(el.querySelector('[data-reserve-blocked]')?.textContent).toContain(
      'Tes 1 falta nos últimos 30 días. No mostrador si podes reservar.',
    );
  });

  it('sen bloqueo non hai aviso; se a API a rexeita ao confirmar (403), sae o mesmo aviso e non o erro', async () => {
    const { el, settle } = await render(url, NOT_BLOCKED);
    expect(el.querySelector('[data-reserve-blocked]')).toBeNull();
    el.querySelector<HTMLButtonElement>('[data-reserve-go]')!.click();
    flushProblem(await expectRequest(BOOKINGS_URL), 403, 'booking.blocked-no-show', {
      params: { until: '2026-11-24', faults: 3, windowDays: 30 },
    });
    (await expectRequest(BLOCK_URL)).flush(BLOCKED);
    (await expectRequest(CONTACT_URL)).flush({ phone: '981 000 000' });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-reserve-blocked]')).not.toBeNull();
    });
    expect(el.querySelector('[data-reserve-error]')).toBeNull();
    expect(el.querySelector('[data-reserve-go]')).toBeNull();
  });
});

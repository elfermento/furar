// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushMatching, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { useMobile } from '../../../testing/staff-members';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { EventPeopleComponent } from './event-people.component';
import { managedActivity } from './event.fixture';

const BASE = '/api/v1/municipalities/naron/events/manage/a1';
const PEOPLE = [
  {
    id: 'r1',
    status: 'CONFIRMED',
    name: 'Marta Souto',
    dependentName: 'Lúa Souto',
    birthYear: 2015,
    payment: 'PAID',
    createdAt: '2026-11-01T10:00:00Z',
  },
  {
    id: 'r2',
    status: 'CONFIRMED',
    name: 'Antía Rial',
    birthDate: '2014-02-01',
    payment: 'PENDING',
    createdAt: '2026-11-01T10:00:00Z',
  },
  {
    id: 'w1',
    status: 'NOTIFIED',
    name: 'Breixo Seoane',
    position: 1,
    exclusiveUntil: '2999-01-01T17:42:00Z',
    createdAt: '2026-11-02T10:00:00Z',
  },
  { id: 'w2', status: 'WAITING', name: 'Uxía Prado', position: 2, createdAt: '2026-11-02T11:00:00Z' },
];
const SUMMARY = [
  { registrationId: 'r1', present: 14, absent: 0, sessions: 14 },
  { registrationId: 'r2', present: 10, absent: 4, sessions: 14 },
];

async function render() {
  TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({ staff: 'naron' }))] });
  TestBed.inject(CurrentMunicipality).set(municipality());
  const fixture = TestBed.createComponent(EventPeopleComponent);
  fixture.componentRef.setInput('slug', 'naron');
  fixture.componentRef.setInput('event', managedActivity());
  (await expectRequest(`${BASE}/registrations`)).flush(PEOPLE);
  (await expectRequest(`${BASE}/attendance`)).flush(SUMMARY);
  const el = fixture.nativeElement as HTMLElement;
  await vi.waitFor(async () => {
    await fixture.whenStable();
    expect(el.querySelector('[data-person="r1"]')).not.toBeNull();
  });
  return el;
}

describe('furar-event-people: as persoas dunha actividade', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/pending-count'), { count: 0 });
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('a táboa: Persoa, A cargo de, Nacemento, Faltas e Pago; e a lista de espera', async () => {
    const el = await render();
    const head = [...el.querySelectorAll('[data-people-table] thead th')].map((th) => th.textContent?.trim());
    expect(head).toEqual(['Persoa', 'A cargo de', 'Nacemento', 'Faltas', 'Pago']);
    const cells = (id: string) =>
      [...el.querySelectorAll(`[data-person="${id}"] > *`)].map((c) => c.textContent?.replace(/\s+/g, ' ').trim());
    expect(cells('r1')).toEqual(['Lúa Souto', 'Marta Souto', '2015', '0 de 14', 'Pagada']);
    expect(cells('r2')).toEqual(['Antía Rial', '—', '2014', '4 de 14', 'Pendente de pago']);
    const waits = [...el.querySelectorAll('[data-waits-table] tbody tr')].map((tr) =>
      [...tr.children]
        .map((c) => c.textContent?.trim())
        .filter(Boolean)
        .join(' '),
    );
    expect(waits[0]).toMatch(/^1 Breixo Seoane Avisada · só ela ata as \d\d:42$/);
    expect(waits[1]).toBe('2 Uxía Prado');
    await expectNoAxeViolations(el);
  });

  it('as faltas ordénanse e o pago fíltrase desde a súa cabeceira', async () => {
    const el = await render();
    const order = () =>
      [...el.querySelectorAll('[data-people-table] tbody tr')].map((tr) => tr.getAttribute('data-person'));
    expect(order()).toEqual(['r2', 'r1']);
    el.querySelector<HTMLButtonElement>('[data-col="absences"] button')!.click();
    await vi.waitFor(() => expect(document.querySelector('[data-option="asc"]')).not.toBeNull());
    expect(document.querySelector('[data-option="asc"]')?.textContent).toContain('Menos faltas primeiro');
    document.querySelector<HTMLButtonElement>('[data-option="asc"]')!.click();
    await vi.waitFor(() => expect(order()).toEqual(['r1', 'r2']));
  });

  it('cambiar o pago desde o chip', async () => {
    const el = await render();
    el.querySelector<HTMLButtonElement>('[data-person="r2"] [data-person-payment] button')!.click();
    await vi.waitFor(() => expect(document.querySelector('[role="menuitemradio"]')).not.toBeNull());
    [...document.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]')]
      .find((b) => b.textContent?.includes('Pagada'))!
      .click();
    const pay = await expectRequest((r) => r.method === 'PUT' || r.method === 'POST');
    expect(pay.request.url).toBe(`${BASE}/registrations/r2/payment`);
    expect(pay.request.body).toEqual({ status: 'PAID' });
    pay.flush({ ...PEOPLE[1], payment: 'PAID' });
    await vi.waitFor(() =>
      expect(el.querySelector('[data-person="r2"] [data-person-payment]')?.textContent).toContain('Pagada'),
    );
  });

  describe('no móbil', () => {
    useMobile();

    it('unha tarxeta por persoa, con rótulo e valor', async () => {
      const el = await render();
      expect(el.querySelector('[data-people-table]')).toBeNull();
      const card = el.querySelector('[data-people-cards] [data-person="r2"]')!;
      expect([...card.querySelectorAll('dt')].map((d) => d.textContent?.trim())).toEqual([
        'A cargo de',
        'Nacemento',
        'Faltas',
      ]);
      expect(el.querySelector('[data-waits-cards]')?.textContent).toContain('2. Uxía Prado');
      await expectNoAxeViolations(el);
    });
  });
});

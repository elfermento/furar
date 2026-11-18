// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushMatching, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { managedActivity, managedEvent } from './event.fixture';
import { EventsPage } from './events.page';

const LIST = '/api/v1/municipalities/naron/events/manage';

describe('EventsPage: eventos na xestión', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/pending-count'), { count: 2 });
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(items: object[], roles: Parameters<typeof fakeSession>[0] = { manager: 'naron' }) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession(roles))] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(EventsPage);
    const el = fixture.nativeElement as HTMLElement;
    (await expectRequest(LIST)).flush(items);
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    return { fixture, el };
  }

  it('a táboa: cando, nome, tipo, lugar, prazas, o estado en chip e o menú; e o mesmo en tarxetas', async () => {
    const { el } = await render([
      managedEvent(),
      managedEvent({
        id: 'e2',
        published: false,
        capacity: undefined,
        confirmed: 0,
        place: 'Praza',
        facility: undefined,
      }),
      managedEvent({ id: 'e3', approval: true, capacity: 8, confirmed: 3, pending: 2 }),
    ]);
    const rows = [...el.querySelectorAll('[data-events-table] tbody tr')];
    expect(rows).toHaveLength(3);
    const cells = (row: Element) => [...row.querySelectorAll('td, th')].map((c) => c.textContent?.trim());
    expect(cells(rows[0]!)).toEqual([
      'dom. 20 dec. · 11:00',
      'Carreira popular de Nadal',
      'Un día',
      'Pista de atletismo',
      '162 de 200',
      'Publicado',
      '',
    ]);
    expect(rows[0]!.querySelector('[data-status]')?.getAttribute('data-tone')).toBe('success');
    expect(cells(rows[1]!)[3]).toBe('Praza');
    expect(cells(rows[1]!)[4]).toBe('—');
    expect(cells(rows[1]!)[5]).toBe('Borrador');
    expect(rows[2]!.querySelector('[data-event-pending]')?.textContent?.trim()).toBe('2 solicitudes');
    expect(el.querySelectorAll('[data-event-item]')).toHaveLength(3);
    await expectNoAxeViolations(el);
  });

  it('sen eventos, o baleiro con «Novo evento»', async () => {
    const { el } = await render([]);
    expect(el.querySelector('[data-events-empty]')?.textContent).toContain('Aínda non hai eventos');
    expect(el.querySelector('[data-event-new]')).not.toBeNull();
  });

  it('unha actividade: os días e a hora, as datas debaixo, «Con horario» e a espera nas prazas', async () => {
    const { el } = await render([managedActivity()]);
    const row = el.querySelector('[data-event-row="a1"]')!;
    expect(row.querySelector('td')?.textContent).toContain('Luns e mércores · 17:00');
    expect(row.querySelector('.f-events__sub')?.textContent?.trim()).toBeTruthy();
    expect(row.querySelector('[data-cell="kind"]')?.textContent?.trim()).toBe('Con horario');
    expect(row.textContent).toContain('20 de 20 · 3 en espera');
    await expectNoAxeViolations(el);
  });

  it('o filtro do tipo vai na súa cabeceira', async () => {
    const { el, fixture } = await render([managedEvent(), managedActivity()]);
    el.querySelector<HTMLButtonElement>('[data-col="kind"] button')!.click();
    await vi.waitFor(() => expect(document.querySelector('[data-option="SCHEDULED"]')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('[data-option="SCHEDULED"]')!.click();
    await fixture.whenStable();
    expect(
      [...el.querySelectorAll('[data-events-table] tbody tr')].map((r) => r.getAttribute('data-event-row')),
    ).toEqual(['a1']);
  });

  it('o menú da fila: «Editar» e «Duplicar»', async () => {
    const { el, fixture } = await render([managedEvent()]);
    el.querySelector<HTMLButtonElement>('[data-event-more="e1"]')!.click();
    await fixture.whenStable();
    expect([...document.querySelectorAll('[role="menuitem"]')].map((b) => b.textContent?.trim())).toEqual([
      'Editar',
      'Duplicar',
    ]);
  });

  it('Recepción le a lista sen «Novo» nin menú da fila', async () => {
    const { el } = await render([managedEvent()], { staff: 'naron' });
    expect(el.querySelector('[data-event-new]')).toBeNull();
    expect(el.querySelector('[data-event-more]')).toBeNull();
    expect(el.querySelector('[data-event-row="e1"]')).not.toBeNull();
  });
});

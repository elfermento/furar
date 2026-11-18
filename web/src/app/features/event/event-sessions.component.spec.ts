// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushMatching, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { madridToday } from '../../shared/ui/calendar/madrid-time';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { EventSessionsComponent } from './event-sessions.component';
import { managedActivity, session } from './event.fixture';

const BASE = '/api/v1/municipalities/naron/events/manage/a1/sessions';
const stable = () => TestBed.inject(ApplicationRef).whenStable();

async function render(manage = true) {
  TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({ manager: 'naron' }))] });
  TestBed.inject(CurrentMunicipality).set(municipality());
  const fixture = TestBed.createComponent(EventSessionsComponent);
  fixture.componentRef.setInput('slug', 'naron');
  fixture.componentRef.setInput('event', managedActivity());
  fixture.componentRef.setInput('canManage', manage);
  fixture.componentRef.setInput('canAttend', true);
  const today = madridToday();
  (await expectRequest(BASE)).flush([
    session('2020-01-06', { attendanceTaken: true, present: 18, absent: 2 }),
    session(today),
    session('2099-12-02'),
    session('2099-12-07', { cancelledAt: '2026-11-10T10:00:00Z' }),
  ]);
  const el = fixture.nativeElement as HTMLElement;
  await vi.waitFor(async () => {
    await fixture.whenStable();
    expect(el.querySelector('[data-session]')).not.toBeNull();
  });
  return { fixture, el };
}

describe('furar-event-sessions: as sesións dunha actividade', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/pending-count'), { count: 0 });
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('a fila: a data, a asistencia ou as horas e o chip na súa columna; hoxe e as pasadas ábrense enteiras', async () => {
    const { el, fixture } = await render();
    const rows = [...el.querySelectorAll('[data-session]')];
    expect(rows.map((r) => r.getAttribute('data-state'))).toEqual(['other', 'today', 'other', 'cancelled']);
    expect(rows[0].textContent).toContain('Asistencia: 18 de 20');
    expect(rows[1].querySelector('.f-event-sessions__chip [data-session-chip]')?.textContent?.trim()).toBe('Hoxe');
    expect(rows[2].textContent).toMatch(/\d\d:00–\d\d:00/);
    expect(rows[3].querySelector('[data-session-chip]')?.textContent?.trim()).toBe('Cancelada');
    // Nunca un botón na mesma columna ca o chip.
    for (const row of rows) expect(row.querySelector('.f-event-sessions__chip button')).toBeNull();
    // Hoxe e as pasadas abren «Pasar lista»; as que veñen e a cancelada, non.
    expect(rows.map((r) => !!r.querySelector('[data-session-attend]'))).toEqual([true, true, false, false]);
    expect(rows[3].querySelector('[data-session-more]')).toBeNull();
    const attend = vi.fn();
    fixture.componentInstance.attend.subscribe(attend);
    rows[1].querySelector<HTMLButtonElement>('[data-session-attend]')!.click();
    expect(attend).toHaveBeenCalledWith(expect.objectContaining({ date: madridToday() }));
    await expectNoAxeViolations(el);
  });

  it('«Cancelar a sesión» desde o menú da fila: confirma, cancela e volve ler; un erro queda á vista', async () => {
    const { el } = await render();
    const confirm = vi.spyOn(TestBed.inject(DialogService), 'confirm');
    const more = () => el.querySelector<HTMLButtonElement>('[data-session="s-2099-12-02"] [data-session-more]')!;
    more().click();
    await stable();
    document.querySelector<HTMLButtonElement>('[data-session-cancel]')!.click();
    await vi.waitFor(() => expect(document.querySelector('[data-confirm-accept]')).not.toBeNull());
    expect(confirm.mock.calls[0][0].message).toContain('20 persoas apuntadas');
    document.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    flushProblem(await expectRequest(`${BASE}/s-2099-12-02/cancel`), 409, 'event.session-started');
    await vi.waitFor(() => expect(el.querySelector('furar-problem-alert')?.textContent?.trim()).toBeTruthy());

    more().click();
    await stable();
    document.querySelector<HTMLButtonElement>('[data-session-cancel]')!.click();
    await vi.waitFor(() => expect(document.querySelector('[data-confirm-accept]')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    (await expectRequest(`${BASE}/s-2099-12-02/cancel`)).flush(
      session('2099-12-02', { cancelledAt: '2026-11-16T10:00:00Z' }),
    );
    (await expectRequest(BASE)).flush([session('2099-12-02', { cancelledAt: '2026-11-16T10:00:00Z' })]);
    await vi.waitFor(() =>
      expect(el.querySelector('[data-session="s-2099-12-02"]')?.getAttribute('data-state')).toBe('cancelled'),
    );
  });

  it('sen `event.manage`, as que veñen non teñen menú; as pasadas seguen abrindo a lista para corrixila', async () => {
    const { el } = await render(false);
    expect(el.querySelector('[data-session-more]')).toBeNull();
    expect(el.querySelector('[data-session="s-2020-01-06"] [data-session-attend]')).not.toBeNull();
  });
});

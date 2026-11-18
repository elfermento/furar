// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { TodayActivitiesComponent } from './today-activities.component';

const TODAY = '/api/v1/municipalities/naron/events/manage/sessions/today';

function today(id: string, startsAt: string, attendanceTaken: boolean) {
  return {
    sessionId: id,
    eventId: 'a1',
    name: { gl: `Actividade ${id}`, es: `Actividad ${id}` },
    startsAt,
    endsAt: startsAt.replace('T16', 'T17'),
    facility: { id: 'f1', name: 'Piscina municipal' },
    registered: 20,
    attendanceTaken,
    present: attendanceTaken ? 18 : 0,
    absent: attendanceTaken ? 2 : 0,
  };
}

async function render(roles: Parameters<typeof fakeSession>[0] = { staff: 'naron' }) {
  TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession(roles))] });
  TestBed.inject(CurrentMunicipality).set(municipality());
  const fixture = TestBed.createComponent(TodayActivitiesComponent);
  await fixture.whenStable();
  return fixture;
}

describe('«Actividades de hoxe» debaixo do validador', () => {
  afterEach(() => backend().verify());

  it('as sen pasar primeiro, en ámbar; as pasadas en verde; a tarxeta abre «Pasar lista»', async () => {
    const fixture = await render();
    (await expectRequest(TODAY)).flush([
      today('s1', '2026-11-18T15:00:00Z', true),
      today('s2', '2026-11-18T16:00:00Z', false),
    ]);
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('[data-today-session]')).not.toBeNull();
    });
    expect(el.querySelector('h2')?.textContent?.trim()).toBe('Actividades de hoxe');
    const cards = [...el.querySelectorAll('[data-today-session]')];
    expect(cards.map((c) => c.getAttribute('data-today-session'))).toEqual(['s2', 's1']);
    expect(cards[0].querySelector('[data-chip="pending"]')?.textContent?.trim()).toBe('Sen pasar');
    expect(cards[0].querySelector('[data-chip="pending"]')?.getAttribute('data-tone')).toBe('warn');
    expect(cards[1].querySelector('[data-chip="taken"]')?.getAttribute('data-tone')).toBe('success');
    expect(cards[1].querySelector('[data-chip="taken"]')?.textContent?.trim()).toBe('Lista pasada · 18 de 20');
    expect(cards[0].textContent?.replace(/\s+/g, ' ')).toContain('17:00–18:00 · Piscina municipal · 20 apuntadas');
    await expectNoAxeViolations(el);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    cards[0].querySelector<HTMLButtonElement>('button')!.click();
    expect(navigate).toHaveBeenCalledWith(['/', 'naron', 'xestion', 'eventos', 'a1', 'sesions', 's2'], {
      queryParams: { desde: 'validar' },
    });
  });

  it('sen sesións hoxe ou se falla, o bloque non sae', async () => {
    const fixture = await render({ manager: 'naron' });
    flushProblem(await expectRequest(TODAY), 500, 'common.internal');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-today-activities]')).toBeNull();
  });

  it('sen rol no concello non se pide', async () => {
    await render({});
    backend().expectNone(TODAY);
  });
});

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { backend, expectRequest, fakeSession, flushMatching, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { AttendancePage } from './attendance.page';
import { managedActivity } from './event.fixture';

const BASE = '/api/v1/municipalities/naron/events/manage/a1';
const LIST = `${BASE}/sessions/s1/attendance`;
const stable = () => TestBed.inject(ApplicationRef).whenStable();

function attendance(taken: boolean, present?: boolean) {
  return {
    sessionId: 's1',
    date: '2026-11-18',
    startsAt: '2026-11-18T16:00:00Z',
    endsAt: '2026-11-18T17:00:00Z',
    taken,
    ...(taken ? { takenAt: '2026-11-18T16:10:00Z', takenByName: 'Xestora Narón' } : {}),
    attendees: [
      { registrationId: 'r2', name: 'Noa Vilar', present },
      { registrationId: 'r1', name: 'Carme Lema', dependentName: 'Lúa Souto', present: taken ? false : undefined },
    ],
  };
}

async function render(taken = false, desde: string | null = 'validar') {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(fakeSession({ staff: 'naron' })),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: {
            paramMap: convertToParamMap({ id: 'a1', sessionId: 's1' }),
            queryParamMap: convertToParamMap(desde ? { desde } : {}),
          },
        },
      },
    ],
  });
  TestBed.inject(CurrentMunicipality).set(municipality());
  const fixture = TestBed.createComponent(AttendancePage);
  (await expectRequest(BASE)).flush(managedActivity());
  (await expectRequest(LIST)).flush(attendance(taken, taken ? true : undefined));
  const el = fixture.nativeElement as HTMLElement;
  await vi.waitFor(async () => {
    await fixture.whenStable();
    expect(el.querySelector('[data-attendance-what]')).not.toBeNull();
  });
  return el;
}

describe('Pasar lista dunha sesión', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/pending-count'), { count: 0 });
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('sen lista: todas presentes, unha fila por persoa co interruptor co seu nome; gardar', async () => {
    const el = await render();
    expect(el.querySelector('h1')?.textContent).toContain('Pasar lista');
    expect(el.querySelector('[data-attendance-what]')?.textContent).toContain('Natación · Iniciación');
    expect(el.querySelector('[data-attendance-what]')?.textContent).toContain('Mércores, 18 de novembro · 17:00–18:00');
    expect(el.querySelector('[data-attendance-back]')?.getAttribute('href')).toBe('/naron/xestion/validar');
    const switches = [...el.querySelectorAll<HTMLButtonElement>('[data-attendee] button[role="switch"]')];
    expect(switches.map((s) => s.getAttribute('aria-checked'))).toEqual(['true', 'true']);
    expect([...el.querySelectorAll('[data-attendee] label')].map((l) => l.textContent?.trim())).toEqual([
      'Lúa Souto',
      'Noa Vilar',
    ]);
    expect(el.querySelector('[data-attendance-count]')?.textContent).toContain('2 de 2');
    expect(el.querySelector('[data-attendance-save]')?.textContent?.trim()).toBe('Gardar');
    expect(el.querySelector('[data-attendance-taken]')).toBeNull();
    await expectNoAxeViolations(el);

    switches[0].click();
    await stable();
    expect(el.querySelector('[data-attendance-count]')?.textContent).toContain('1 de 2');
    const toasts = TestBed.inject(ToastService);
    const show = vi.spyOn(toasts, 'show');
    el.querySelector<HTMLButtonElement>('[data-attendance-save]')!.click();
    const put = await expectRequest((r) => r.method === 'PUT' && r.url === LIST);
    expect(put.request.body).toEqual({
      marks: [
        { registrationId: 'r1', present: false },
        { registrationId: 'r2', present: true },
      ],
    });
    put.flush(attendance(true, true));
    await vi.waitFor(() =>
      expect(el.querySelector('[data-attendance-save]')?.textContent?.trim()).toBe('Gardar cambios'),
    );
    expect(show).toHaveBeenCalledWith('event.attendance.saved', { count: 1, total: 2 });
  });

  it('xa pasada: co que se gardou, «Gardar cambios»; un erro ao gardar, aviso de erro', async () => {
    const el = await render(true, null);
    expect(el.querySelector('[data-attendance-back]')?.getAttribute('href')).toBe('/naron/xestion/eventos?ver=a1');
    expect(el.querySelector('[data-attendance-count]')?.textContent).toContain('1 de 2');
    expect(el.querySelector('[data-attendance-save]')?.textContent?.trim()).toBe('Gardar cambios');
    expect(el.querySelector('[data-attendance-taken]')?.textContent?.trim()).toBe('Pasada ás 17:10 por Xestora Narón');
    const error = vi.spyOn(TestBed.inject(ToastService), 'error');
    el.querySelector<HTMLButtonElement>('[data-attendance-save]')!.click();
    flushProblem(await expectRequest((r) => r.method === 'PUT'), 422, 'event.attendance-too-early');
    await vi.waitFor(() => expect(error).toHaveBeenCalled());
    expect(error.mock.calls[0][0]).toBe('event.attendance.saveError');
  });

  it('se non se pode cargar, o erro con «Tentar de novo»', async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({ staff: 'naron' })),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'a1', sessionId: 's1' }),
              queryParamMap: convertToParamMap({}),
            },
          },
        },
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(AttendancePage);
    flushProblem(await expectRequest(BASE), 404, 'event.not-found');
    (await expectRequest(LIST)).flush(attendance(false));
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.textContent).toContain('Non se puido cargar a lista');
    });
  });
});

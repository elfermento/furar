// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushMatching, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { preview, seasonFacility } from '../../../testing/seasons';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import type { EventDialogData } from './event-dialog.component';
import { EventDialogComponent } from './event-dialog.component';
import type { EventForm } from './event-form';
import { managedActivity, managedEvent } from './event.fixture';
import type { EventResponse } from './events.store';

const BASE = '/api/v1/municipalities/naron/events/manage';
const FACILITY = '/api/v1/municipalities/naron/public/facilities/f1';
const stable = () => TestBed.inject(ApplicationRef).whenStable();
const top = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')].at(-1)!;
const FACILITIES = [{ value: 'f1', label: 'Pavillón' }];

describe('alta e edición dun evento ou dunha actividade', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/pending-count'), { count: 1 });
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function open(event: EventResponse | null, copy = false) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({ manager: 'naron' }))] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const data: EventDialogData = { slug: 'naron', event, copy, facilities: FACILITIES };
    const ref = TestBed.inject(DialogService).open<EventResponse, EventDialogData, EventDialogComponent>(
      EventDialogComponent,
      { data },
    );
    await vi.waitFor(() => expect(top()?.querySelector('[data-event-form]')).not.toBeNull());
    await stable();
    const form = (ref.componentInstance as unknown as { form: EventForm }).form;
    return { dialog: top(), form, ref };
  }

  const submit = async (dialog: HTMLElement) => {
    dialog
      .querySelector<HTMLFormElement>('[data-event-form]')!
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await stable();
  };

  it('novo: «Cando» Un día · Con horario; «Apuntarse ata» só co seu interruptor', async () => {
    const { dialog } = await open(null);
    expect(dialog.querySelector('.f-dialog__title')?.textContent).toContain('Novo evento');
    expect([...dialog.querySelectorAll('[data-event-kind]')].map((b) => b.textContent?.trim())).toEqual([
      'Un día',
      'Con horario',
    ]);
    expect(dialog.querySelector('[data-event-date]')).not.toBeNull();
    expect(dialog.querySelector('[data-event-period]')).toBeNull();
    expect(dialog.querySelector('[data-event-deadline]')).toBeNull();
    dialog.querySelector<HTMLButtonElement>('[data-event-deadline-on] button[role="switch"]')!.click();
    await stable();
    expect(dialog.querySelector('[data-event-deadline]')).not.toBeNull();
    await expectNoAxeViolations(dialog);
  });

  it('novo sen datos: «Gardar» marca os erros baixo cada campo e non manda nada', async () => {
    const { dialog } = await open(null);
    await submit(dialog);
    backend().expectNone((r) => r.method === 'POST');
    expect(dialog.querySelector('[aria-invalid="true"]')).not.toBeNull();
  });

  it('con horario: días, horas e datas, o uso, a vista previa das sesións e crear sen os días ocupados', async () => {
    const { dialog, form } = await open(null);
    dialog.querySelector<HTMLButtonElement>('[data-event-kind="SCHEDULED"]')!.click();
    await stable();
    expect(dialog.querySelector('.f-dialog__title')?.textContent).toContain('Nova actividade');
    expect(dialog.querySelector('[data-event-date]')).toBeNull();
    expect(dialog.querySelector('[data-season-days]')).not.toBeNull();
    form.patchValue({ nameGl: 'Natación', nameEs: 'Natación', facilityId: 'f1', price: '25' });
    await stable();
    (await expectRequest(FACILITY)).flush(seasonFacility());
    await stable();
    // O uso por prazas non vale; o único que queda xa vai escollido e pide a parte.
    expect(form.controls.useId.value).toBe('u1');
    await vi.waitFor(() => expect(dialog.querySelector('[data-event-part]')).not.toBeNull());
    expect(dialog.querySelector('[data-event-period]')).not.toBeNull();
    form.patchValue({
      partId: 'p1',
      weekdays: 5,
      startMinute: 17 * 60,
      endMinute: 18 * 60,
      firstDate: '2099-10-05',
      lastDate: '2100-06-16',
    });
    const ask = await expectRequest((r) => r.url === `${BASE}/sessions-preview`);
    expect(ask.request.body).toEqual({
      facilityId: 'f1',
      schedule: {
        weekdays: [1, 3],
        startMinute: 1020,
        endMinute: 1080,
        firstDate: '2099-10-05',
        lastDate: '2100-06-16',
        useId: 'u1',
        partId: 'p1',
      },
    });
    ask.flush(preview({ sessions: 68 }));
    await vi.waitFor(() => expect(dialog.querySelector('[data-event-sessions]')?.textContent).toContain('68 sesións'));
    expect(dialog.querySelector('[data-conflict="BOOKING"] [data-conflict-cancel="b9"]')).not.toBeNull();
    await expectNoAxeViolations(dialog);

    await submit(dialog);
    const post = await expectRequest((r) => r.method === 'POST' && r.url === BASE);
    const body = post.request.body as { kind: string; pricePeriod: string; schedule: { skip: string[] } };
    expect(body.kind).toBe('SCHEDULED');
    expect(body.pricePeriod).toBe('ONCE');
    expect(body.schedule.skip).toEqual(['2026-11-23', '2026-12-14']);
    post.flush(managedActivity());
    await vi.waitFor(() => expect(document.querySelector('furar-event-dialog')).toBeNull());
  });

  it('se mentres tanto cambiou algún día, o aviso e outra vista previa', async () => {
    const { dialog, form } = await open(null);
    form.patchValue({
      kind: 'SCHEDULED',
      nameGl: 'Andaina',
      nameEs: 'Andaina',
      placeKind: 'OTHER',
      place: 'Parque',
      weekdays: 1,
      startMinute: 600,
      endMinute: 660,
      firstDate: '2099-10-05',
      lastDate: '2099-11-05',
    });
    (await expectRequest((r) => r.url === `${BASE}/sessions-preview`)).flush(preview({ conflicts: [], sessions: 5 }));
    await vi.waitFor(() => expect(dialog.querySelector('[data-event-sessions]')).not.toBeNull());
    await submit(dialog);
    flushProblem(await expectRequest((r) => r.method === 'POST'), 409, 'event.sessions-changed');
    await vi.waitFor(() => expect(dialog.querySelector('furar-problem-alert')?.textContent?.trim()).toBeTruthy());
    (await expectRequest((r) => r.url === `${BASE}/sessions-preview`)).flush(preview({ conflicts: [], sessions: 4 }));
  });

  it('editar unha actividade: o horario, as datas e o lugar como texto; «Gardar» manda o mesmo horario', async () => {
    const { dialog } = await open(managedActivity());
    expect(dialog.querySelector('.f-dialog__title')?.textContent).toContain('Editar actividade');
    expect(dialog.querySelector('[data-event-kind]')).toBeNull();
    expect(dialog.querySelector('[data-season-days]')).toBeNull();
    expect(dialog.querySelector('[data-event-facility]')).toBeNull();
    const fixed = dialog.querySelector('[data-event-fixed]')?.textContent ?? '';
    expect(fixed).toContain('Luns e mércores · 17:00–18:00');
    expect(fixed).toContain('Piscina municipal');
    expect(dialog.querySelector('[data-event-preview]')).toBeNull();
    await expectNoAxeViolations(dialog);
    await submit(dialog);
    const put = await expectRequest((r) => r.method === 'PUT' && r.url === `${BASE}/a1`);
    expect(put.request.headers.get('If-Match')).toBe('"0"');
    expect((put.request.body as { schedule: unknown }).schedule).toEqual({
      weekdays: [1, 3],
      startMinute: 1020,
      endMinute: 1080,
      firstDate: '2099-10-05',
      lastDate: '2100-06-16',
      useId: 'u1',
    });
    put.flush(managedActivity({ version: 1 }));
    await vi.waitFor(() => expect(document.querySelector('furar-event-dialog')).toBeNull());
  });

  it('editar un evento dun día: o tipo non se cambia; un erro ao gardar sae no diálogo', async () => {
    const { dialog } = await open(managedEvent());
    expect(dialog.querySelector('[data-event-kind]')).toBeNull();
    expect(dialog.querySelector('[data-event-date]')).not.toBeNull();
    await submit(dialog);
    flushProblem(await expectRequest((r) => r.method === 'PUT'), 412, 'common.stale-version');
    await vi.waitFor(() => expect(dialog.querySelector('furar-problem-alert')?.textContent?.trim()).toBeTruthy());
    expect(document.querySelector('furar-event-dialog')).not.toBeNull();
  });

  it('duplicar: alta co nome « (copia)» e o tipo escollible', async () => {
    const { dialog, form } = await open(managedActivity(), true);
    expect(dialog.querySelector('.f-dialog__title')?.textContent).toContain('Nova actividade');
    expect(dialog.querySelector('[data-event-kind]')).not.toBeNull();
    expect(form.controls.nameGl.value).toBe('Natación · Iniciación (copia)');
    flushMatching((r) => r.url === '/api/v1/municipalities/naron/public/facilities/f2', seasonFacility());
    await stable();
    flushMatching((r) => r.url === `${BASE}/sessions-preview`, preview());
  });
});

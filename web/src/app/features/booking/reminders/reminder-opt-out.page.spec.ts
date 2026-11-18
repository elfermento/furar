// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ReminderOptOutPage } from './reminder-opt-out.page';

const URL = '/api/v1/municipalities/naron/public/reminder-opt-out';

describe('«Non quero recordatorios»', () => {
  afterEach(() => backend().verify());

  async function open(path: string) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(),
        provideRouter(
          [{ path: 'naron/recordatorios/baixa', component: ReminderOptOutPage }],
          withComponentInputBinding(),
        ),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(path);
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    return { el, settle };
  }

  it('manda a proba unha vez e confirma; axe', async () => {
    const { el, settle } = await open('/naron/recordatorios/baixa?t=v1.abc.1.sig');
    const post = await expectRequest(URL);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ token: 'v1.abc.1.sig' });
    post.flush(null, { status: 204, statusText: 'No Content' });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-opt-out-done]')?.textContent).toContain('Xa non che enviaremos recordatorios');
    });
    await expectNoAxeViolations(el);
  });

  it('unha ligazón caducada di o motivo da API', async () => {
    const { el, settle } = await open('/naron/recordatorios/baixa?t=v1.abc.1.sig');
    flushProblem(await expectRequest(URL), 410, 'booking.reminder-link-expired');
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-opt-out-error]')?.textContent).toContain('caducou');
    });
  });

  it('sen proba no URL non pregunta á API', async () => {
    const { el, settle } = await open('/naron/recordatorios/baixa');
    await settle();
    expect(el.querySelector('[data-opt-out-error]')?.textContent).toContain('Esta ligazón non serve');
  });
});

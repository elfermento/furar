// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { GeneralTab } from './general.tab';
import { MunicipalitySection } from './section';

const BASE = '/api/v1/municipalities/naron';
const NOTES = {
  payment: { gl: 'No mostrador', es: 'En el mostrador' },
  paymentDefault: true,
  refund: { gl: 'Transferencia', es: 'Transferencia' },
  refundDefault: true,
  scope: 'ALL' as const,
  furarPayment: { gl: 'No mostrador', es: 'En el mostrador' },
  furarRefund: { gl: 'Transferencia', es: 'Transferencia' },
};

describe('Concello › Xeral: «Buscar xente para xogar» (ADR-0040)', () => {
  afterEach(() => backend().verify());

  async function render(policy = { enabled: false, cancelHours: 12 }) {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), MunicipalitySection],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    });
    TestBed.inject(CurrentMunicipality).set(municipality({ openMatchPolicy: policy }));
    const fixture = TestBed.createComponent(GeneralTab);
    await fixture.whenStable();
    (await expectRequest(`${BASE}/custom-fields?target=MUNICIPALITY`)).flush([]);
    (await expectRequest(`${BASE}/sports-contact`)).flush({});
    (await expectRequest(`${BASE}/payment-notes`)).flush(NOTES);
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('[data-open-match-policy]')).not.toBeNull();
    });
    const settle = () => fixture.whenStable();
    const submit = async () => {
      el.querySelector('form')?.dispatchEvent(new Event('submit'));
      await settle();
    };
    const hours = () => el.querySelector<HTMLInputElement>('#settings-open-match-hours');
    const type = async (value: string) => {
      hours()!.value = value;
      hours()!.dispatchEvent(new Event('input'));
      await settle();
    };
    return { fixture, el, settle, submit, hours, type };
  }

  const toggle = (el: HTMLElement) => el.querySelector<HTMLElement>('#settings-open-match')!;

  it('apagado: só o interruptor (nome á esquerda, «?»); aceso, «Cancelar se non hai mínimo» en horas antes; garda; axe', async () => {
    const { fixture, el, submit, hours, type, settle } = await render();
    const policy = el.querySelector<HTMLElement>('[data-open-match-policy]')!;
    // Despois de «Datos abertos», na mesma tarxeta.
    expect(policy.closest('[data-extra-settings]')).not.toBeNull();
    expect(policy.textContent).toContain('Buscar xente para xogar');
    expect(toggle(el).getAttribute('aria-checked')).toBe('false');
    expect(hours()).toBeNull();

    toggle(el).click();
    await settle();
    expect(hours()?.value).toBe('12');
    expect(policy.querySelector('label[for="settings-open-match-hours"]')?.textContent).toContain(
      'Cancelar se non hai mínimo',
    );
    expect(policy.querySelector('.f-field-affix__unit')?.textContent?.trim()).toBe('horas antes');
    await expectNoAxeViolations(policy);

    // Fóra de 1–72: o erro no campo e ningún PUT.
    await type('73');
    await submit();
    expect(el.querySelector('#settings-open-match-hours-error')?.textContent).toContain(
      'O valor está fóra do rango permitido.',
    );
    backend().expectNone({ method: 'PUT', url: `${BASE}/open-match-policy` });

    await type('24');
    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${BASE}/open-match-policy` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({ enabled: true, cancelHours: 24 });
    put.flush({ enabled: true, cancelHours: 24 }, { headers: { ETag: '"4"' } });
    await vi.waitFor(() => expect(fixture.componentInstance.dirty()).toBe(false));
    const current = TestBed.inject(CurrentMunicipality).value();
    expect(current?.openMatchPolicy).toEqual({ enabled: true, cancelHours: 24 });
    expect(current?.version).toBe(4);
  });

  it('apagar con horas mal escritas: volven ás gardadas e garda só o interruptor', async () => {
    const { el, submit, type, settle } = await render({ enabled: true, cancelHours: 8 });
    await type('0');
    toggle(el).click();
    await settle();
    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${BASE}/open-match-policy` });
    expect(put.request.body).toEqual({ enabled: false, cancelHours: 8 });
    put.flush({ enabled: false, cancelHours: 8 }, { headers: { ETag: '"4"' } });
  });

  it('o erro da API (fóra de rango) sae no aviso', async () => {
    const { el, submit, type } = await render({ enabled: true, cancelHours: 8 });
    await type('10');
    await submit();
    flushProblem(
      await expectRequest({ method: 'PUT', url: `${BASE}/open-match-policy` }),
      422,
      'municipality.open-match-policy-invalid',
      { params: { min: 1, max: 72 } },
    );
    await vi.waitFor(() => expect(el.textContent).toContain('«Cancelar se non hai mínimo» vai de 1 a 72 horas antes.'));
  });
});

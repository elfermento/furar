// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { backend, expectRequest, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { definition, municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { GeneralTab } from './general.tab';
import { MunicipalitySection } from './section';

const BASE = '/api/v1/municipalities/naron';
const CONTACT = { name: 'Servizo Municipal de Deportes', phone: '981 000 100', email: 'deportes@naron.example' };
const FURAR = {
  furarPayment: { gl: 'No mostrador, ao chegar', es: 'En el mostrador, al llegar' },
  furarRefund: { gl: 'O concello dirache como cha devolve', es: 'El ayuntamiento te dirá cómo te lo devuelve' },
};
const NOTES = {
  payment: FURAR.furarPayment,
  paymentDefault: true,
  refund: { gl: 'Por transferencia', es: 'Por transferencia' },
  refundDefault: false,
  scope: 'ALL' as const,
  ...FURAR,
};

describe('Concello › Xeral › «Aviso para instalar no móbil»', () => {
  afterEach(() => backend().verify());

  async function render(overrides: Partial<ReturnType<typeof municipality>> = {}) {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), MunicipalitySection],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    });
    TestBed.inject(CurrentMunicipality).set(
      municipality({ customFields: { phone: '981' }, legal: { nif: 'P1505400J' }, ...overrides }),
    );
    const fixture = TestBed.createComponent(GeneralTab);
    await fixture.whenStable();
    (await expectRequest(`${BASE}/custom-fields?target=MUNICIPALITY`)).flush([
      definition({ required: true, rules: { maxLength: 5 } }),
    ]);
    (await expectRequest(`${BASE}/sports-contact`)).flush(CONTACT);
    (await expectRequest(`${BASE}/payment-notes`)).flush(NOTES);
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('form')).not.toBeNull();
    });
    const submit = async () => {
      el.querySelector('form')?.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    return { fixture, el, submit };
  }

  it('a última fila de «Configuración adicional», acesa por defecto, co nome e a axuda; axe', async () => {
    const { el } = await render();
    const extra = el.querySelector<HTMLElement>('[data-extra-settings]')!;
    const rows = [...extra.querySelectorAll('[data-no-show], [data-open-data], [data-install-hint-setting]')];
    expect(rows.at(-1)?.hasAttribute('data-install-hint-setting')).toBe(true);
    const toggle = extra.querySelector<HTMLButtonElement>('#settings-install-hint')!;
    expect(toggle.getAttribute('aria-checked')).toBe('true');
    expect(toggle.getAttribute('aria-label')).toContain('Aviso para instalar no móbil');
    expect(extra.querySelector('#settings-install-hint-help')?.textContent).toContain('non o volve ver');
    await expectNoAxeViolations(extra);
  });

  it('apagalo garda só o seu PUT co If-Match e deixa o concello coa versión nova', async () => {
    const { el, fixture, submit } = await render();
    el.querySelector<HTMLButtonElement>('#settings-install-hint')!.click();
    await fixture.whenStable();
    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${BASE}/install-hint` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({ enabled: false });
    put.flush({ enabled: false }, { headers: { ETag: '"4"' } });
    await vi.waitFor(() => expect(fixture.componentInstance.dirty()).toBe(false));
    expect(TestBed.inject(CurrentMunicipality).value()?.installHint).toBe(false);
    expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(4);
    backend().expectNone({ method: 'PUT', url: `${BASE}/open-data-publication` });
  });
});

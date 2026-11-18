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

describe('Concello › Xeral › «Datos abertos» (ADR-0034)', () => {
  afterEach(() => backend().verify());

  async function render(overrides: Partial<ReturnType<typeof municipality>> = {}) {
    // «Para abrir as reservas» (@defer) vai nos seus tests; aquí, só que está e non sae na carga.
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
    const value = (id: string) => el.querySelector<HTMLInputElement>(`#${id}`)?.value;
    const type = async (id: string, text: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = text;
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    const submit = async () => {
      el.querySelector('form')?.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    const scope = (option: string) => el.querySelector<HTMLButtonElement>(`[data-scope="${option}"]`)!;
    return { fixture, el, value, type, submit, scope };
  }

  it('fila na tarxeta «Configuración adicional», despois de «Penalizar as faltas»; aceso por defecto con «Vista previa»; axe', async () => {
    const { el } = await render();
    const extra = el.querySelector<HTMLElement>('[data-extra-settings]')!;
    const rows = [...extra.querySelectorAll('[data-no-show], [data-open-data]')];
    expect(rows.map((r) => r.hasAttribute('data-open-data'))).toEqual([false, true]);
    const toggle = extra.querySelector<HTMLButtonElement>('#settings-open-data')!;
    expect(toggle.getAttribute('aria-checked')).toBe('true');
    const row = toggle.closest('furar-switch')!;
    // O rótulo pode ir partido (a última palabra co «?»): o nome enteiro, no botón.
    expect(row.querySelector('#settings-open-data')?.getAttribute('aria-label')).toContain('Datos abertos');
    expect(row.querySelector('#settings-open-data-help')?.textContent).toContain('nin aparece no pé');
    // «Vista previa», debaixo do interruptor e fóra del (botón na súa liña).
    expect(row.querySelector('[data-open-data-preview]')).toBeNull();
    const preview = rows[1]!.querySelector<HTMLAnchorElement>('[data-open-data-preview]')!;
    expect(preview.getAttribute('href')).toBe('/naron/datos-abertos');
    expect(preview.getAttribute('target')).toBe('_blank');
    expect(preview.textContent).toContain('Vista previa');
    await expectNoAxeViolations(extra);
  });

  it('apagado e gardado: sen «Vista previa»; acender só no formulario aínda non a mostra; garda co If-Match', async () => {
    const { el, fixture, submit } = await render({ openData: false });
    const toggle = el.querySelector<HTMLButtonElement>('#settings-open-data')!;
    expect(el.querySelector('[data-open-data-preview]')).toBeNull();
    toggle.click();
    await fixture.whenStable();
    expect(el.querySelector('[data-open-data-preview]')).toBeNull();
    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${BASE}/open-data-publication` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({ enabled: true });
    put.flush({ enabled: true }, { headers: { ETag: '"4"' } });
    await vi.waitFor(() => expect(fixture.componentInstance.dirty()).toBe(false));
    expect(TestBed.inject(CurrentMunicipality).value()?.openData).toBe(true);
    expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(4);
    await fixture.whenStable();
    expect(el.querySelector('[data-open-data-preview]')).not.toBeNull();
    backend().expectNone({ method: 'PUT', url: `${BASE}/no-show-policy` });
  });
});

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { MunicipalitySection } from './section';
import { ServiceTab } from './service.tab';

const BASE = '/api/v1/municipalities/naron';
const CONTACT = { name: 'Servizo Municipal de Deportes', phone: '981 38 21 00', email: 'deportes@naron.example' };
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

describe('Concello › Servizo (D2-11)', () => {
  afterEach(() => backend().verify());

  async function render(notes = NOTES) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), MunicipalitySection] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(ServiceTab);
    await fixture.whenStable();
    (await expectRequest(`${BASE}/sports-contact`)).flush(CONTACT);
    (await expectRequest(`${BASE}/payment-notes`)).flush(notes);
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

  it('carga o contacto e as notas; a nota de Furar vai baleira e como axuda; axe', async () => {
    const { el, value, scope } = await render();
    expect(value('settings-sports-name')).toBe('Servizo Municipal de Deportes');
    expect(value('settings-sports-address')).toBe('');
    expect(scope('ALL').getAttribute('aria-checked')).toBe('true');
    expect(el.querySelector('[role="radiogroup"]')?.getAttribute('aria-label')).toBe(
      'Pago e devolución nas instalacións',
    );
    expect(value('settings-note-payment')).toBe('');
    expect(el.querySelector('#settings-note-payment-help')?.textContent).toContain('No mostrador, ao chegar');
    expect(value('settings-note-refund')).toBe('Por transferencia');
    await expectNoAxeViolations(el);
  });

  it('valida igual ca o servidor: teléfono, correo e nota nos dous idiomas', async () => {
    const { el, type, submit } = await render();
    await type('settings-sports-phone', 'chamar');
    await type('settings-sports-email', 'deportes@');
    await type('settings-note-payment', 'En efectivo');
    await submit();
    expect(el.querySelector('#settings-sports-phone-error')?.textContent).toContain('formato');
    expect(el.querySelector('#settings-sports-email-error')?.textContent).toContain('formato');
    expect(el.querySelector('#settings-note-payment-error')?.textContent).toContain('Castelán');
  });

  it('garda só o que cambiou, encadeando a versión do concello', async () => {
    const { type, submit } = await render();
    await type('settings-sports-address', ' Rúa Buxo, 16 ');
    await submit();
    const contact = await expectRequest({ method: 'PUT', url: `${BASE}/sports-contact` });
    expect(contact.request.headers.get('If-Match')).toBe('"3"');
    expect(contact.request.body).toEqual({ ...CONTACT, address: 'Rúa Buxo, 16' });
    contact.flush({ ...CONTACT, address: 'Rúa Buxo, 16' }, { headers: { ETag: '"4"' } });
    await vi.waitFor(() => expect(TestBed.inject(ToastService).current()?.key).toBe('settings.saved'));
    backend().expectNone({ method: 'PUT', url: `${BASE}/payment-notes` });
    expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(4);
  });

  it('«Distinto por instalación»: só unha liña; garda o modo e os textos do concello quedan', async () => {
    const { el, fixture, scope, submit } = await render();
    scope('FACILITY').click();
    await fixture.whenStable();
    expect(el.querySelector('#settings-note-refund')).toBeNull();
    expect(el.querySelector('[data-payment-by-facility]')?.textContent).toContain(
      'Indícase na ficha de cada instalación',
    );
    expect(fixture.componentInstance.dirty()).toBe(true);
    await submit();
    const notes = await expectRequest({ method: 'PUT', url: `${BASE}/payment-notes` });
    expect(notes.request.headers.get('If-Match')).toBe('"3"');
    expect(notes.request.body).toEqual({
      scope: 'FACILITY',
      payment: { gl: '', es: '' },
      refund: { gl: 'Por transferencia', es: 'Por transferencia' },
    });
    notes.flush({ ...NOTES, scope: 'FACILITY' }, { headers: { ETag: '"4"' } });
    await vi.waitFor(() => expect(fixture.componentInstance.dirty()).toBe(false));
    expect(scope('FACILITY').getAttribute('aria-checked')).toBe('true');
  });
});

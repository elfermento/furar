// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { definition, municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
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

describe('Concello › Xeral', () => {
  afterEach(() => backend().verify());

  async function render() {
    // «Para abrir as reservas» (@defer) vai nos seus tests; aquí, só que está e non sae na carga.
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), MunicipalitySection],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    });
    TestBed.inject(CurrentMunicipality).set(
      municipality({ customFields: { phone: '981' }, legal: { nif: 'P1505400J' } }),
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

  it('nome e idiomas, servizo de deportes, pago, datos propios e configuración adicional, nesa orde e cun só «Gardar»; axe', async () => {
    const { el, fixture, value, scope } = await render();
    expect(value('settings-name')).toBe('Narón');
    expect(el.querySelector('[data-general] furar-languages-fieldset')).not.toBeNull();
    expect(el.querySelector('[data-general] .f-fieldset')).toBeNull();
    // Nome e idiomas (despregable múltiple) nun grupo de campos; o idioma por defecto, fila solta.
    const group = el.querySelector('[data-general] .f-field-group')!;
    expect(group.querySelector('#settings-name')).not.toBeNull();
    const languages = group.querySelector('#settings-languages')!;
    expect(languages.getAttribute('aria-haspopup')).toBe('listbox');
    expect(languages.textContent).toContain('Galego, Castelán');
    expect(group.querySelector('label[for="settings-languages"]')?.textContent).toContain('Idiomas');
    const defaultGroup = el.querySelector('[data-general] [data-default-language-field] [role="radiogroup"]')!;
    expect(el.querySelector(`#${defaultGroup.getAttribute('aria-labelledby')}`)?.textContent).toContain(
      'Idioma por defecto',
    );
    expect(defaultGroup.querySelector('[data-default-language="gl"]')?.getAttribute('aria-checked')).toBe('true');
    // Contacto de dous en dous: nome e enderezo; teléfono e correo.
    const rows = [...el.querySelectorAll('[data-sports] [data-contact-row]')].map((row) =>
      [...row.querySelectorAll('input')].map((input) => input.id),
    );
    expect(rows).toEqual([
      ['settings-sports-name', 'settings-sports-address'],
      ['settings-sports-phone', 'settings-sports-email'],
    ]);
    expect(value('settings-sports-name')).toBe('Servizo Municipal de Deportes');
    expect(value('settings-sports-address')).toBe('');
    expect(value('settings-cf-phone')).toBe('981');
    const cards = [...el.querySelectorAll('form > furar-card')].map((card) =>
      ['data-general', 'data-sports', 'data-payment', 'data-custom-fields', 'data-extra-settings'].find((a) =>
        card.hasAttribute(a),
      ),
    );
    expect(cards).toEqual(['data-general', 'data-sports', 'data-payment', 'data-custom-fields', 'data-extra-settings']);
    expect(el.querySelectorAll('button[type="submit"]')).toHaveLength(1);
    expect(el.querySelectorAll('[data-save-status]')).toHaveLength(1);
    expect(el.querySelector('furar-theme-editor, [data-legal]')).toBeNull();
    expect(el.querySelector('a[href$="/campos"]')?.textContent).toContain('Xestionar os campos');
    expect(scope('ALL').getAttribute('aria-checked')).toBe('true');
    // Só as dúas opcións, curtas, sen texto ao carón: caben nunha liña no móbil.
    expect(scope('ALL').textContent?.trim()).toBe('Igual para todas');
    expect(scope('FACILITY').textContent?.trim()).toBe('Por instalación');
    expect(scope('ALL').closest('furar-field-line')).toBeNull();
    expect(scope('ALL').closest('[role="radiogroup"]')?.getAttribute('aria-label')).toBe(
      'Pago e devolución nas instalacións',
    );
    expect(value('settings-note-payment')).toBe('');
    expect(el.querySelector('#settings-note-payment-help')?.textContent).toContain('No mostrador, ao chegar');
    expect(value('settings-note-refund')).toBe('Por transferencia');
    expect(fixture.componentInstance.dirty()).toBe(false);
    await expectNoAxeViolations(el);
  });

  it('valida no cliente sen chamar á API: nome, datos propios, teléfono, correo e nota nos dous idiomas', async () => {
    const { el, type, submit } = await render();
    await type('settings-name', '');
    await type('settings-cf-phone', '123456');
    await type('settings-sports-phone', 'chamar');
    await type('settings-sports-email', 'deportes@');
    await type('settings-note-payment', 'En efectivo');
    await submit();
    expect(el.querySelector('#settings-name-error')).not.toBeNull();
    expect(el.querySelector('#settings-cf-phone-error')?.textContent).toContain('como máximo 5');
    expect(el.querySelector('#settings-sports-phone-error')?.textContent).toContain('formato');
    expect(el.querySelector('#settings-sports-email-error')?.textContent).toContain('formato');
    expect(el.querySelector('#settings-note-payment-error')?.textContent).toContain('Castelán');
  });

  it('só o concello: PUT co If-Match, o tema coma estaba e sen datos legais; nin contacto nin notas', async () => {
    const { el, fixture, type, submit } = await render();
    await type('settings-name', ' Concello de Narón ');
    expect(fixture.componentInstance.dirty()).toBe(true);
    await submit();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({
      name: 'Concello de Narón',
      languages: ['gl', 'es'],
      defaultLanguage: 'gl',
      theme: municipality().theme,
      customFields: { phone: '981' },
    });
    put.flush(municipality({ name: 'Concello de Narón', version: 4, customFields: { phone: '981' } }));
    await vi.waitFor(() => expect(TestBed.inject(ToastService).current()?.key).toBe('settings.saved'));
    backend().expectNone({ method: 'PUT', url: `${BASE}/sports-contact` });
    backend().expectNone({ method: 'PUT', url: `${BASE}/payment-notes` });
    expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(4);
    expect(el.querySelector<HTMLInputElement>('#settings-name')?.value).toBe('Concello de Narón');
    expect(fixture.componentInstance.dirty()).toBe(false);
  });

  it('o idioma por defecto escóllese no segmentado e vai no PUT do concello', async () => {
    const { el, fixture, submit } = await render();
    el.querySelector<HTMLButtonElement>('[data-default-language="es"]')!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.dirty()).toBe(true);
    await submit();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.body).toMatchObject({ languages: ['gl', 'es'], defaultLanguage: 'es' });
    put.flush(municipality({ version: 4, defaultLanguage: 'es', customFields: { phone: '981' } }));
    await vi.waitFor(() => expect(fixture.componentInstance.dirty()).toBe(false));
  });

  it('o concello e o contacto: un detrás doutro, encadeando a versión; as notas non', async () => {
    const { fixture, type, submit } = await render();
    await type('settings-name', 'Narón (novo)');
    await type('settings-sports-address', ' Rúa Buxo, 16 ');
    await submit();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    put.flush(municipality({ name: 'Narón (novo)', version: 4, customFields: { phone: '981' } }));
    const contact = await expectRequest({ method: 'PUT', url: `${BASE}/sports-contact` });
    expect(contact.request.headers.get('If-Match')).toBe('"4"');
    expect(contact.request.body).toEqual({ ...CONTACT, address: 'Rúa Buxo, 16' });
    contact.flush({ ...CONTACT, address: 'Rúa Buxo, 16' }, { headers: { ETag: '"5"' } });
    await vi.waitFor(() => expect(TestBed.inject(ToastService).current()?.key).toBe('settings.saved'));
    backend().expectNone({ method: 'PUT', url: `${BASE}/payment-notes` });
    expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(5);
    expect(fixture.componentInstance.dirty()).toBe(false);
  });

  it('se o contacto falla, o seu erro sae no campo; volver gardar só manda o que falta', async () => {
    const { el, fixture, type, submit } = await render();
    await type('settings-name', 'Narón (novo)');
    await type('settings-sports-phone', '981 00 00 00');
    await submit();
    (await expectRequest({ method: 'PUT', url: BASE })).flush(
      municipality({ name: 'Narón (novo)', version: 4, customFields: { phone: '981' } }),
    );
    flushProblem(await expectRequest({ method: 'PUT', url: `${BASE}/sports-contact` }), 400, 'common.validation', {
      errors: [{ field: 'sportsContact.phone', code: 'common.invalid-format', params: {} }],
    });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('#settings-sports-phone-error')).not.toBeNull();
    });
    expect(fixture.componentInstance.dirty()).toBe(true);
    await type('settings-sports-phone', '981 11 11 11');
    await submit();
    const contact = await expectRequest({ method: 'PUT', url: `${BASE}/sports-contact` });
    expect(contact.request.headers.get('If-Match')).toBe('"4"');
    contact.flush({ ...CONTACT, phone: '981 11 11 11' }, { headers: { ETag: '"5"' } });
    await vi.waitFor(() => expect(fixture.componentInstance.dirty()).toBe(false));
    backend().expectNone({ method: 'PUT', url: BASE });
  });

  it('«Configuración adicional» › «Penalizar as faltas»: apagada, sen números; acesa, os tres nunha fila; garda coa versión; axe', async () => {
    const { el, fixture, submit, type, value } = await render();
    const extra = el.querySelector<HTMLElement>('[data-extra-settings]')!;
    expect(extra.querySelector('h2')?.textContent).toContain('Configuración adicional');
    const card = extra.querySelector<HTMLElement>('[data-no-show]')!;
    const penalty = card.querySelector<HTMLButtonElement>('#settings-no-show-penalty')!;
    // Unha fila: o rótulo e o «?» á esquerda, o interruptor á dereita do todo.
    const row = penalty.closest('furar-switch')!;
    expect(row.firstElementChild?.textContent?.replace(/\s+/g, ' ')).toContain('Penalizar as faltas');
    expect(penalty.getAttribute('aria-label')).toBe('Penalizar as faltas');
    expect(row.querySelector('.f-switch__extra')?.previousElementSibling).toBe(penalty);
    expect(row.querySelector('#settings-no-show-penalty-help')?.textContent).toContain('acumula unha falta');
    expect(penalty.getAttribute('aria-describedby')).toBe('settings-no-show-penalty-help');
    expect(penalty.getAttribute('aria-checked')).toBe('false');
    expect(card.querySelector('[data-no-show-numbers]')).toBeNull();

    penalty.click();
    await fixture.whenStable();
    const numbers = card.querySelector<HTMLElement>('[data-no-show-numbers]')!;
    expect(numbers.querySelector('.f-field-group[data-cells]')).not.toBeNull();
    expect([...numbers.querySelectorAll('label')].map((label) => label.textContent?.trim())).toEqual([
      'Faltas',
      'En cantos días',
      'Días de bloqueo',
    ]);
    expect(value('settings-no-show-threshold')).toBe('3');
    expect(value('settings-no-show-windowDays')).toBe('30');
    expect(value('settings-no-show-blockDays')).toBe('15');
    await expectNoAxeViolations(card);

    // Fóra de rango (de 1 a 20 faltas): o erro no campo e ningún PUT.
    await type('settings-no-show-threshold', '21');
    await submit();
    expect(card.querySelector('#settings-no-show-threshold-error')?.textContent).toContain(
      'O valor está fóra do rango permitido.',
    );
    backend().expectNone({ method: 'PUT', url: `${BASE}/no-show-policy` });

    await type('settings-no-show-threshold', '2');
    await type('settings-no-show-blockDays', '7');
    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${BASE}/no-show-policy` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({ enabled: true, threshold: 2, windowDays: 30, blockDays: 7 });
    put.flush({ enabled: true, threshold: 2, windowDays: 30, blockDays: 7 }, { headers: { ETag: '"4"' } });
    await vi.waitFor(() => expect(fixture.componentInstance.dirty()).toBe(false));
    expect(TestBed.inject(CurrentMunicipality).value()?.noShowPolicy).toEqual({
      enabled: true,
      threshold: 2,
      windowDays: 30,
      blockDays: 7,
    });
    expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(4);
    backend().expectNone({ method: 'PUT', url: BASE });
  });

  it('«Por instalación»: só unha liña; garda o modo e os textos do concello quedan', async () => {
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

  it('versión vella → «Cargar os datos actuais» recarga todo; descartar volve ao gardado', async () => {
    const { el, fixture, type, submit, value } = await render();
    await type('settings-name', 'Outro');
    await submit();
    flushProblem(await expectRequest({ method: 'PUT', url: BASE }), 412, 'common.stale-version');
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.textContent).toContain('Cargar os datos actuais');
    });
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Cargar os datos actuais'))!.click();
    (await expectRequest(BASE)).flush(municipality({ version: 9, name: 'Narón (novo)' }));
    (await expectRequest(`${BASE}/custom-fields?target=MUNICIPALITY`)).flush([]);
    (await expectRequest(`${BASE}/sports-contact`)).flush({ ...CONTACT, address: 'Rúa Nova' });
    (await expectRequest(`${BASE}/payment-notes`)).flush(NOTES);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(value('settings-name')).toBe('Narón (novo)');
      expect(value('settings-sports-address')).toBe('Rúa Nova');
    });
    await type('settings-name', 'Outro');
    fixture.componentInstance.discard();
    await fixture.whenStable();
    expect(value('settings-name')).toBe('Narón (novo)');
  });

  it('enriba, «Para abrir as reservas», cargada á parte (@defer)', async () => {
    const { el, fixture } = await render();
    expect(el.querySelector('furar-opening-checklist')).toBeNull();
    const [block] = await fixture.getDeferBlocks();
    await block.render(DeferBlockState.Complete);
    (await expectRequest(`${BASE}/facilities?page=0&size=100`)).flush({ items: [], page: 0, size: 100, totalItems: 0 });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('[data-opening-checklist]')).not.toBeNull();
    });
    expect(el.firstElementChild?.tagName.toLowerCase()).toBe('furar-opening-checklist');
  });
});

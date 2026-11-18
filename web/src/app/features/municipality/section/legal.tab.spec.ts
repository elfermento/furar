// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { provideDeploymentSettings } from '../../../../testing/deployment';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { pickOption, selectButton, selectText } from '../../../../testing/select';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { LegalTab } from './legal.tab';
import { MunicipalitySection } from './section';

/** Os modos do despregable «Páxina de…», en galego. */
const MODES: Record<string, string> = {
  FURAR: 'Páxina desta web',
  TEXT: 'Texto propio',
  URL: 'Páxina propia',
  NONE: 'Sen páxina',
};

const BASE = '/api/v1/municipalities/naron';
const NOTICE = { activityVisible: 'P1Y', activityRetention: 'P3Y' };
const TEXTS = { accessibility: { gl: '<p>Declaración propia</p>' } };
const COMPLETE = {
  legalEntityName: 'Concello de Narón',
  nif: 'P1505400J',
  address: 'Praza de Galicia, 1',
  generalEmail: 'concello@naron.example.org',
  dpoEmail: 'dpd@naron.example.org',
};
const rect = { x: 0, y: 0, top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0, toJSON: () => ({}) };
const originals = { rects: Range.prototype.getClientRects, box: Range.prototype.getBoundingClientRect };

/** Concello › Legal (ADR-0017 punto 8). */
describe('Concello › Legal', () => {
  beforeAll(() => {
    // jsdom non mide: o editor de texto (ProseMirror) pide rectángulos ao enfocar.
    Range.prototype.getClientRects = () => [rect] as unknown as DOMRectList;
    Range.prototype.getBoundingClientRect = () => rect as DOMRect;
  });
  afterAll(() => {
    Range.prototype.getClientRects = originals.rects;
    Range.prototype.getBoundingClientRect = originals.box;
  });
  afterEach(() => backend().verify());

  async function render(page = 'privacidade', legal: object = COMPLETE) {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), provideDeploymentSettings(), MunicipalitySection],
    });
    TestBed.inject(CurrentMunicipality).set(municipality({ legal }));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LegalTab);
    fixture.componentRef.setInput('page', page);
    await fixture.whenStable();
    (await expectRequest(`${BASE}/legal-texts`)).flush(TEXTS);
    const el = fixture.nativeElement as HTMLElement;
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-legal]')).not.toBeNull();
    });
    const tab = (key: string) => el.querySelector<HTMLAnchorElement>(`[data-legal-tab="${key}"]`)!;
    const mode = async (value: string) => {
      await pickOption(el.querySelector('[data-legal-panel] [data-legal-mode]')!, MODES[value], settle);
      await settle();
    };
    const input = (key: string) => el.querySelector<HTMLInputElement>(`#settings-legal-${key}`);
    const type = async (key: string, value: string) => {
      input(key)!.value = value;
      input(key)!.dispatchEvent(new Event('input'));
      await settle();
    };
    const click = async (button: HTMLElement) => {
      button.click();
      await settle();
    };
    const go = async (path: string) => {
      fixture.componentRef.setInput('page', path);
      await settle();
    };
    const submit = async () => {
      el.querySelector('form')?.dispatchEvent(new Event('submit'));
      await settle();
    };
    return { fixture, el, tab, mode, input, type, click, go, submit, settle, navigate };
  }

  it('enriba, todos os datos da entidade; debaixo, as páxinas co seu URL; axe', async () => {
    const { el, tab, go } = await render('aviso-legal');
    const required = el.querySelector('[data-legal-required]')!;
    expect(required.querySelector('h2')?.textContent).toBe('Datos da entidade');
    expect([...required.querySelectorAll('input')].map((i) => i.id)).toEqual([
      'settings-legal-legalEntityName',
      'settings-legal-nif',
      'settings-legal-address',
      'settings-legal-generalEmail',
      'settings-legal-dpoEmail',
      'settings-legal-generalPhone',
      'settings-legal-dpoPhone',
      'settings-legal-eOfficeUrl',
      'settings-legal-processor',
    ]);
    expect(required.textContent).not.toContain('obrigatorio');
    expect(el.querySelector('[data-legal] nav')?.getAttribute('aria-label')).toBe('Páxinas legais');
    expect(tab('legalNotice').getAttribute('href')).toBe('/naron/xestion/concello/legal/aviso-legal');
    expect(el.querySelector('[data-legal-panel]')?.getAttribute('data-legal-panel')).toBe('legalNotice');
    // O modo, un despregable co nome da páxina.
    const modeField = el.querySelector('[data-legal-panel] [data-legal-mode]')!;
    expect(el.querySelector(`label[for="${selectButton(modeField).id}"]`)?.textContent?.trim()).toBe(
      'Páxina do aviso legal',
    );
    expect(selectText(modeField)).toBe('Páxina desta web');
    expect([...el.querySelectorAll('[data-legal-panel] input')].map((i) => i.id)).toEqual([
      'settings-legal-dir3Code',
      'settings-legal-useRegulationUrl',
    ]);
    expect(el.querySelector('[data-legal-view]')?.textContent).toContain('Vista previa');
    await go('privacidade');
    expect(el.querySelector('[data-legal] fieldset')).toBeNull();
    expect(el.querySelector('label[for="settings-legal-processor"]')?.textContent).toContain('Encargado do tratamento');
    expect(el.querySelector('[data-legal-missing]')).toBeNull();
    await go('accesibilidade');
    // Correo e unidade de accesibilidade, na mesma fila.
    const pair = el.querySelector('#settings-legal-accessibilityEmail')!.closest('.f-field-cells')!;
    expect([...pair.querySelectorAll('input')].map((i) => i.id)).toEqual([
      'settings-legal-accessibilityEmail',
      'settings-legal-accessibilityUnit',
    ]);
    await expectNoAxeViolations(el);
  });

  it('as axudas van nun «?» ao carón do rótulo, non debaixo', async () => {
    const { el } = await render('aviso-legal');
    expect(el.querySelector('.f-help')).toBeNull();
    const field = el.querySelector('#settings-legal-processor')!.closest('furar-field')!;
    const button = field.querySelector<HTMLButtonElement>('.f-field__head furar-help-tip button')!;
    expect(button.getAttribute('aria-label')).toBe('Axuda sobre «Encargado do tratamento»');
    expect(document.getElementById(button.getAttribute('aria-describedby')!)?.textContent).toContain(
      'Só se outra entidade aloxa',
    );
    expect(el.querySelector('#settings-legal-processor')?.getAttribute('aria-describedby')).toBe(
      'settings-legal-processor-help',
    );
  });

  it('o correo do DPD leva un «?» que explica que é; pecha con Esc e co clic fóra', async () => {
    const { el, settle } = await render();
    const help = el.querySelector<HTMLElement>('[data-about="dpoEmail"]')!;
    expect(help.closest('furar-field')?.querySelector('label')?.textContent).toContain('Correo do DPD');
    const button = help.querySelector<HTMLButtonElement>('furar-help-tip button')!;
    expect(button.closest('label')).toBeNull();
    expect(button.getAttribute('aria-label')).toBe('Que é o DPD?');
    expect(el.querySelectorAll('[data-about]')).toHaveLength(el.querySelectorAll('#settings-legal-dpoEmail').length);
    button.click();
    await settle();
    const panel = () => document.querySelector<HTMLElement>('.f-help-tip__panel');
    expect(panel()?.textContent).toContain(
      'A persoa delegada de protección de datos supervisa como trata a entidade responsable',
    );
    expect(panel()?.textContent).toContain('Furar só o mostra como contacto nos avisos de privacidade.');
    await expectNoAxeViolations(document.body);
    button.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    await settle();
    expect(panel()).toBeNull();

    button.click();
    await settle();
    expect(panel()).not.toBeNull();
    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
    document.body.click();
    await settle();
    expect(panel()).toBeNull();
  });

  it('se falta un dato, un aviso dío en vivo (non bloquea) e a pestana «Legal» o sabe', async () => {
    const { el, type } = await render();
    const section = TestBed.inject(MunicipalitySection);
    expect(section.legalWarning()).toBeNull();
    await type('nif', '');
    expect(el.querySelector('[data-legal-missing]')?.textContent).toContain(
      'Falta un dato que usan as páxinas legais por defecto: NIF.',
    );
    expect(section.legalWarning()).toBe('missing');
    await type('address', '');
    expect(el.querySelector('[data-legal-missing]')?.textContent).toContain('NIF e domicilio');
  });

  it('o NIF escríbese normalizado e compróbase ao ter 9 caracteres, sen mensaxe de correcto', async () => {
    const { el, input, type, submit } = await render();
    const error = () => el.querySelector('#settings-legal-nif-error')?.textContent ?? null;
    expect(el.querySelector('label[for="settings-legal-nif"]')?.textContent).toContain('NIF da entidade');
    expect(el.querySelector('label[for="settings-legal-legalEntityName"]')?.textContent).toContain(
      'Entidade responsable',
    );
    expect(el.querySelector('#settings-legal-legalEntityName-help')?.textContent).toContain(
      'Normalmente, o concello. Tamén pode ser o padroado, o organismo ou a empresa que xestiona o servizo.',
    );
    await type('nif', 'q-157342');
    expect(input('nif')?.value).toBe('Q157342');
    expect(error()).toBeNull();
    await type('nif', 'q-1573421 d');
    expect(input('nif')?.value).toBe('Q1573421D');
    expect(error()).toContain('O díxito de control non é correcto: revisa o NIF');
    expect(input('nif')?.getAttribute('aria-invalid')).toBe('true');
    await type('nif', '34871265E');
    expect(error()).toContain('Isto parece un NIF de persoa; indica o da entidade');
    await type('nif', 'Q1573421C');
    expect(error()).toBeNull();
    expect(input('nif')?.getAttribute('aria-describedby')).toBeNull();
    await submit();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.body.legal).toMatchObject({ nif: 'Q1573421C' });
    flushProblem(put, 400, 'validation.failed', {
      errors: [{ field: 'legal.nif', code: 'municipality.nif-check-digit', params: {} }],
    });
    await vi.waitFor(() => expect(error()).toContain('O díxito de control non é correcto'));
  });

  it('«Sen páxina» avisa da obriga legal, deixa gardar e a pestana o marca', async () => {
    const { el, mode, submit } = await render('accesibilidade');
    await mode('NONE');
    expect(el.querySelector('[data-legal-none]')?.textContent).toContain(
      'As administracións están obrigadas a publicar unha declaración de accesibilidade.',
    );
    expect(TestBed.inject(MunicipalitySection).legalWarning()).toBe('page');
    await submit();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.body.legal).toMatchObject({ accessibilityMode: 'NONE', nif: 'P1505400J' });
    put.flush(municipality({ version: 4, legal: { ...COMPLETE, accessibilityMode: 'NONE' } }));
    await vi.waitFor(() => expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(4));
  });

  it('Páxina propia pide un enderezo https: erro no campo, «!» na pestana, aviso e foco', async () => {
    const { el, tab, mode, input, type, submit } = await render();
    await mode('URL');
    expect(selectText(el.querySelector('[data-legal-panel] [data-legal-mode]')!)).toBe('Páxina propia');
    await submit();
    expect(el.querySelector('#settings-legal-privacyUrl-error')?.textContent).toContain(
      'Escribe o enderezo da vosa páxina',
    );
    expect(tab('privacy').textContent).toContain(', con erros');
    expect(el.querySelector('[data-save-status]')?.textContent).toBe('Hai campos por revisar en Privacidade.');
    await vi.waitFor(() => expect(document.activeElement).toBe(input('privacyUrl')));
    await type('privacyUrl', 'http://naron.example');
    expect(el.querySelector('#settings-legal-privacyUrl-error')?.textContent).toContain('https://');
    // O enderezo mal escrito non segue a outro modo: volve ao gardado.
    await mode('FURAR');
    await mode('URL');
    expect(input('privacyUrl')?.value).toBe('');
  });

  it('teléfono xeral, rexistro de actividades e normativa de uso: opcionais, validados e gardados', async () => {
    const { el, input, type, go, submit } = await render('aviso-legal');
    expect(input('generalPhone')?.type).toBe('tel');
    expect(input('useRegulationUrl')?.type).toBe('url');
    await type('generalPhone', '981-111');
    await type('useRegulationUrl', 'http://naron.example/normativa');
    await submit();
    expect(el.querySelector('#settings-legal-generalPhone-error')?.textContent).toContain('só números');
    expect(el.querySelector('#settings-legal-useRegulationUrl-error')?.textContent).toContain('https://');
    await type('generalPhone', '981 111 111');
    await type('useRegulationUrl', 'https://naron.example/normativa');
    await go('privacidade');
    await type('processingRegisterUrl', ' https://sede.naron.example/rat ');
    await submit();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.body.legal).toMatchObject({
      generalPhone: '981 111 111',
      processingRegisterUrl: 'https://sede.naron.example/rat',
      useRegulationUrl: 'https://naron.example/normativa',
    });
    put.flush(municipality({ version: 4, legal: { ...COMPLETE, ...put.request.body.legal } }));
    await vi.waitFor(() => expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(4));
  });

  it('un erro nun dato da entidade marca a tarxeta, non unha pestana', async () => {
    const { el, tab, type, submit } = await render('aviso-legal');
    await type('generalPhone', '981-111');
    await submit();
    expect(el.querySelector('[data-legal-required] #settings-legal-generalPhone-error')).not.toBeNull();
    expect(tab('legalNotice').textContent).not.toContain(', con erros');
    expect(el.querySelector('[data-save-status]')?.textContent).toBe('Hai campos por revisar en os datos da entidade.');
  });

  it('se o primeiro erro está noutra páxina, vai ao seu URL', async () => {
    const { el, type, go, submit, navigate } = await render('aviso-legal');
    await type('dir3Code', 'mal');
    await go('privacidade');
    await submit();
    expect(navigate).toHaveBeenCalledWith(['/', 'naron', 'xestion', 'concello', 'legal', 'aviso-legal']);
    await go('aviso-legal');
    expect(el.querySelector('#settings-legal-dir3Code-error')).not.toBeNull();
    expect(el.querySelector('[data-save-status]')?.textContent).toBe('Hai campos por revisar en Aviso legal.');
  });

  it('a primeira vez en Texto propio arranca co texto por defecto; garda os textos encadeando a versión', async () => {
    const { el, mode, submit, settle } = await render();
    await mode('TEXT');
    (await expectRequest('/api/v1/privacy-notice')).flush(NOTICE);
    await vi.waitFor(() =>
      expect(el.querySelector('#settings-legal-privacy-text-gl h2')?.textContent).toBe('Información básica'),
    );
    // O mesmo conmutador GL/ES que furar-bilingual-field, na cabeceira do campo.
    const langs = el.querySelector('furar-legal-text-editor .f-bilingual__head > [furarSegmented][role="group"]');
    expect(langs?.getAttribute('aria-label')).toBe('Idioma do texto');
    expect(langs?.querySelector('button[data-lang="gl"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(el.querySelector('furar-legal-text-editor [role="tablist"]')).toBeNull();
    langs!.querySelector<HTMLButtonElement>('button[data-lang="es"]')!.click();
    await settle();
    expect(langs?.querySelector('button[data-lang="es"]')?.getAttribute('aria-pressed')).toBe('true');
    await vi.waitFor(() => expect(el.querySelector('#settings-legal-privacy-text-es')).not.toBeNull());
    langs!.querySelector<HTMLButtonElement>('button[data-lang="gl"]')!.click();
    await settle();
    await submit();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.body.legal).toMatchObject({ privacyMode: 'TEXT' });
    put.flush(municipality({ version: 4, legal: { ...COMPLETE, privacyMode: 'TEXT' } }));
    const texts = await expectRequest({ method: 'PUT', url: `${BASE}/legal-texts` });
    expect(texts.request.headers.get('If-Match')).toBe('"4"');
    expect(texts.request.body.privacy.gl).toContain('Responsable do tratamento');
    // «Cookies» non vai no texto propio: a páxina engádea sempre.
    expect(texts.request.body.privacy.gl).not.toContain('id="cookies"');
    texts.flush(texts.request.body, { headers: { ETag: '"5"' } });
    await vi.waitFor(() => expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(5));
  });

  it('un erro da API nun dato legal vai ao seu campo', async () => {
    const { el, submit, fixture } = await render();
    await submit();
    flushProblem(await expectRequest({ method: 'PUT', url: BASE }), 400, 'validation.failed', {
      errors: [{ field: 'legal.generalEmail', code: 'validation.pattern', params: {} }],
    });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('#settings-legal-generalEmail-error')?.textContent).toContain('formato');
    });
  });
});

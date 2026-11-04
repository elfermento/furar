// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { LegalTab } from './legal.tab';
import { MunicipalitySection } from './section';

const BASE = '/api/v1/municipalities/naron';
const NOTICE = { activityVisible: 'P1Y', activityRetention: 'P3Y' };
const TEXTS = { accessibility: { gl: '<p>Declaración propia</p>' } };
const COMPLETE = {
  legalEntityName: 'Concello de Narón',
  nif: 'P1505400J',
  address: 'Praza de Galicia, 1',
  generalEmail: 'concello@naron.gal',
  dpoEmail: 'dpd@naron.gal',
};
const rect = { x: 0, y: 0, top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0, toJSON: () => ({}) };
const originals = { rects: Range.prototype.getClientRects, box: Range.prototype.getBoundingClientRect };

/** Concello › Legal (ADR-0017 punto 8; maqueta 49). */
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
    TestBed.configureTestingModule({ providers: [...provideTestApi(), MunicipalitySection] });
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
    const mode = (value: string) => el.querySelector<HTMLButtonElement>(`[data-legal-panel] [data-mode="${value}"]`)!;
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

  it('enriba, os datos que usan os textos legais; debaixo, as páxinas co seu URL; axe', async () => {
    const { el, tab, go } = await render('aviso-legal');
    const required = el.querySelector('[data-legal-required]')!;
    expect(required.querySelector('h2')?.textContent).toBe('Datos que usan os textos legais');
    expect([...required.querySelectorAll('input')].map((i) => i.id)).toEqual([
      'settings-legal-legalEntityName',
      'settings-legal-nif',
      'settings-legal-address',
      'settings-legal-generalEmail',
      'settings-legal-dpoEmail',
    ]);
    expect(required.textContent).not.toContain('obrigatorio');
    expect(el.querySelector('[data-legal] nav')?.getAttribute('aria-label')).toBe('Páxinas legais');
    expect(tab('legalNotice').getAttribute('href')).toBe('/naron/xestion/concello/legal/aviso-legal');
    expect(el.querySelector('[data-legal-panel]')?.getAttribute('data-legal-panel')).toBe('legalNotice');
    expect(el.querySelector('[data-legal-panel] [role="radiogroup"]')?.getAttribute('aria-label')).toBe(
      'Páxina do aviso legal',
    );
    expect([...el.querySelectorAll('[data-legal-panel] [data-mode]')].map((b) => b.textContent?.trim())).toEqual([
      'Páxina desta web',
      'Texto propio',
      'Páxina propia',
      'Sen páxina',
    ]);
    expect(el.querySelector('[data-legal] legend')?.textContent).toContain('Identificación do concello');
    await go('privacidade');
    expect(el.querySelector('[data-legal] legend')?.textContent).toContain('Protección de datos');
    expect(el.querySelector('label[for="settings-legal-processor"]')?.textContent).toContain('Encargado do tratamento');
    expect(el.querySelector('[data-legal-missing]')).toBeNull();
    await expectNoAxeViolations(el);
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

  it('«Sen páxina» avisa da obriga legal, deixa gardar e a pestana o marca', async () => {
    const { el, mode, click, submit } = await render('accesibilidade');
    await click(mode('NONE'));
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
    const { el, tab, mode, input, type, click, submit } = await render();
    await click(mode('URL'));
    expect(mode('URL').getAttribute('aria-checked')).toBe('true');
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
    await click(mode('FURAR'));
    await click(mode('URL'));
    expect(input('privacyUrl')?.value).toBe('');
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
    const { el, mode, click, submit } = await render();
    await click(mode('TEXT'));
    (await expectRequest('/api/v1/privacy-notice')).flush(NOTICE);
    await vi.waitFor(() =>
      expect(el.querySelector('#settings-legal-privacy-text-gl h2')?.textContent).toBe('Responsable do tratamento'),
    );
    await submit();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.body.legal).toMatchObject({ privacyMode: 'TEXT' });
    put.flush(municipality({ version: 4, legal: { ...COMPLETE, privacyMode: 'TEXT' } }));
    const texts = await expectRequest({ method: 'PUT', url: `${BASE}/legal-texts` });
    expect(texts.request.headers.get('If-Match')).toBe('"4"');
    expect(texts.request.body.privacy.gl).toContain('Responsable do tratamento');
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

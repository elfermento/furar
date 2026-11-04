// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { contrastRatio } from '../../../core/theme/contrast';
import { AppearanceTab } from './appearance.tab';
import { MunicipalitySection } from './section';

const BASE = '/api/v1/municipalities/naron';

describe('Concello › Aparencia', () => {
  afterEach(() => backend().verify());

  async function render() {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), MunicipalitySection] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(AppearanceTab);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const color = (name: string) => el.querySelector<HTMLInputElement>(`input[data-color="${name}"]`)!;
    const pick = async (name: string, value: string) => {
      color(name).value = value;
      color(name).dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    return { fixture, el, color, pick };
  }

  it('as cores son mosaicos co nome e o código; frases sen números e as razóns en «Detalles técnicos»; axe', async () => {
    const { el } = await render();
    const tiles = [...el.querySelectorAll('furar-color-tile')];
    expect(tiles.map((t) => t.querySelector('.f-color-tile__name')?.textContent?.trim())).toEqual([
      'Cor principal',
      'Texto dos botóns',
      'Cor de realce',
      'Fondo',
      'Texto',
    ]);
    expect(tiles[0]!.querySelector('.f-color-tile__hex')?.textContent).toBe('#0b5c8e');
    // A mostra branca (o fondo) leva o bordo fino; a principal, non.
    expect(tiles[3]!.querySelector('.f-color-tile__swatch')?.hasAttribute('data-pale')).toBe(true);
    expect(tiles[0]!.querySelector('.f-color-tile__swatch')?.hasAttribute('data-pale')).toBe(false);
    const checks = el.querySelector('.f-theme__checks')!;
    expect(checks.textContent).toContain('O texto dos botóns lese ben');
    expect(checks.textContent).not.toMatch(/\d:1/);
    expect(el.querySelector('details.f-theme__tech')?.hasAttribute('open')).toBe(false);
    expect(el.querySelector('details.f-theme__tech')?.textContent).toContain('correcto (mínimo 4.5:1)');
    await expectNoAxeViolations(el);
  });

  it('unha cor que non chega ao mínimo di que facer e «Axustar» cambiaa o xusto', async () => {
    const { el, fixture, color, pick } = await render();
    await pick('primary', '#3995d4');
    const failing = el.querySelector<HTMLElement>('[data-check="onPrimaryOnPrimary"]')!;
    expect(failing.getAttribute('data-ok')).toBe('false');
    expect(failing.textContent).toContain('O texto dos botóns non se le ben: escurece a cor principal');
    const fix = failing.querySelector<HTMLButtonElement>('[data-fix]')!;
    expect(fix.getAttribute('aria-label')).toBe('Axustar o texto dos botóns');
    fix.click();
    await fixture.whenStable();
    expect(el.querySelector('[data-check="onPrimaryOnPrimary"]')?.getAttribute('data-ok')).toBe('true');
    expect(contrastRatio('#ffffff', color('primary').value)).toBeGreaterThanOrEqual(4.5);
    await vi.waitFor(() => expect(document.activeElement).toBe(color('primary')));
    expect(fixture.componentInstance.dirty()).toBe(true);
  });

  it('«Vista previa» abre ao lado e segue as cores; pechar volve ao botón', async () => {
    const { el, fixture, pick } = await render();
    const toggle = el.querySelector<HTMLButtonElement>('[data-preview-toggle]')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(el.querySelector('[data-preview]')).toBeNull();
    toggle.click();
    await fixture.whenStable();
    const panel = el.querySelector<HTMLElement>('[data-preview]')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('.f-appearance')?.hasAttribute('data-detail')).toBe(true);
    expect(panel.querySelector('h2')?.textContent).toBe('Vista previa');
    expect(panel.querySelector('[role="img"]')?.getAttribute('aria-label')).toBe('Vista previa coas cores escollidas');
    await vi.waitFor(() => expect(document.activeElement).toBe(panel.querySelector('[data-preview-close]')));
    await pick('accent', '#5a2d00');
    expect(panel.querySelector<HTMLElement>('.f-theme-preview__link')?.style.color).toBe('rgb(90, 45, 0)');
    await expectNoAxeViolations(el);
    panel.querySelector<HTMLButtonElement>('[data-preview-close]')!.click();
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('[data-preview]')).toBeNull();
    });
    await vi.waitFor(() => expect(document.activeElement).toBe(toggle));
  });

  it('garda o tema co PUT do concello, sen datos legais', async () => {
    const { el, fixture, pick } = await render();
    await pick('accent', '#5a2d00');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.body.theme.accent).toBe('#5a2d00');
    expect(put.request.body.legal).toBeUndefined();
    put.flush(municipality({ version: 4, theme: { ...municipality().theme, accent: '#5a2d00' } }));
    await vi.waitFor(() => expect(fixture.componentInstance.dirty()).toBe(false));
  });

  it('Esc dentro da vista previa péchaa; fóra dela, non', async () => {
    const { el, fixture } = await render();
    el.querySelector<HTMLButtonElement>('[data-preview-toggle]')!.click();
    await fixture.whenStable();
    const escape = (target: Element) =>
      target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    escape(el.querySelector('input[data-color="primary"]')!);
    await fixture.whenStable();
    expect(el.querySelector('[data-preview]')).not.toBeNull();
    escape(el.querySelector('[data-preview-close]')!);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('[data-preview]')).toBeNull();
    });
  });

  it('o contraste baixo que rexeita a API vese no formulario; descartar volve ás cores gardadas', async () => {
    const { el, fixture, color, pick } = await render();
    await pick('accent', '#5a2d00');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    flushProblem(await expectRequest({ method: 'PUT', url: BASE }), 400, 'municipality.theme-low-contrast', {
      params: { pair: 'realce', ratio: '3.1', minimum: '4.5' },
    });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('.f-appearance__colours > .f-field-error')?.textContent?.trim()).toBe(
        'O contraste realce é 3.1:1 e debe ser polo menos 4.5:1.',
      );
    });
    expect(fixture.componentInstance.dirty()).toBe(true);
    fixture.componentInstance.discard();
    await fixture.whenStable();
    expect(color('accent').value).toBe(municipality().theme.accent);
    expect(fixture.componentInstance.dirty()).toBe(false);
    expect(fixture.componentInstance.tabName()).toBe('Aparencia');
  });

  it('versión vella → «Cargar os datos actuais» trae as cores novas', async () => {
    const { el, fixture, color, pick } = await render();
    await pick('accent', '#5a2d00');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    flushProblem(await expectRequest({ method: 'PUT', url: BASE }), 412, 'common.stale-version');
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.textContent).toContain('Cargar os datos actuais');
    });
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Cargar os datos actuais'))!.click();
    (await expectRequest(BASE)).flush(
      municipality({ version: 9, theme: { ...municipality().theme, accent: '#123456' } }),
    );
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(color('accent').value).toBe('#123456');
    });
    expect(fixture.componentInstance.dirty()).toBe(false);
  });
});

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { renderDetail } from '../../../../testing/facility-detail';
import { facility } from '../detail/facility.fixture';

const URL = '/api/v1/municipalities/naron/facilities/f1';
const DESCRIPTION = { gl: 'Céspede artificial.', es: 'Césped artificial.' };

async function render() {
  const view = await renderDetail({ facility: { description: DESCRIPTION } });
  const section = () => view.el.querySelector<HTMLElement>('#facility-description')!;
  const textarea = () => section().querySelector<HTMLTextAreaElement>('#facility-description')!;
  const lang = (code: 'gl' | 'es') => section().querySelector<HTMLButtonElement>(`button[data-lang="${code}"]`)!;
  return { ...view, section, textarea, lang };
}

describe('Sección Descrición gl/es (D1-21a)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('fóra da edición, o texto no idioma de quen mira e sen rótulos', async () => {
    const { section } = await render();
    expect(section().querySelector('[data-description]')?.textContent?.trim()).toBe('Céspede artificial.');
    expect(section().textContent).not.toContain('En galego');
    expect(section().querySelector('textarea, [data-lang]')).toBeNull();
  });

  it('en edición, un só campo co conmutador GL/ES e o idioma no campo', async () => {
    const { section, edit, textarea, lang, click } = await render();
    await edit();
    expect(textarea().value).toBe('Céspede artificial.');
    expect(textarea().getAttribute('lang')).toBe('gl');
    expect(lang('gl').getAttribute('aria-pressed')).toBe('true');
    await click(lang('es'));
    expect(textarea().value).toBe('Césped artificial.');
    expect(textarea().getAttribute('lang')).toBe('es');
    await expectNoAxeViolations(section());
  });

  it('valida igual ca o servidor: se hai texto nun idioma fai falta no outro; vai ao idioma do erro', async () => {
    const { section, edit, textarea, lang, click, type, save } = await render();
    await edit();
    await click(lang('es'));
    await type(textarea(), '   ');
    await click(lang('gl'));
    await save();
    expect(lang('es').getAttribute('aria-pressed')).toBe('true');
    expect(section().querySelector('#facility-description-error')?.textContent).toContain('Castelán');
    backend().expectNone({ method: 'PUT', url: `${URL}/description` });
  });

  it('garda co If-Match da ficha e os textos sen espazos; sae da edición', async () => {
    const { section, edit, textarea, type, save, stable, el } = await render();
    await edit();
    await type(textarea(), '  Campo de herba. ');
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/description` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({ gl: 'Campo de herba.', es: 'Césped artificial.' });
    put.flush(facility({ version: 4, description: { gl: 'Campo de herba.', es: 'Césped artificial.' } }));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-action-bar')).toBeNull();
    });
    expect(section().querySelector('[data-description]')?.textContent?.trim()).toBe('Campo de herba.');
  });

  it('412: segue en edición co escrito, ofrece recargar e o seguinte gardado vai coa versión nova', async () => {
    const { el, edit, textarea, type, save, stable, button, click } = await render();
    await edit();
    await type(textarea(), 'O meu texto');
    await save();
    flushProblem(await expectRequest({ method: 'PUT', url: `${URL}/description` }), 412, 'common.stale-version');
    await vi.waitFor(async () => {
      await stable();
      expect(el.textContent).toContain('Outra persoa cambiou esta instalación');
    });
    expect(textarea().value).toBe('O meu texto');
    await click(button('Recargar'));
    (await expectRequest(URL)).flush(
      facility({ version: 7, description: { gl: 'Doutra persoa', es: 'De otra persona' } }),
    );
    await vi.waitFor(async () => {
      await stable();
      expect(el.textContent).not.toContain('Outra persoa cambiou esta instalación');
    });
    expect(textarea().value).toBe('O meu texto');
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/description` });
    expect(put.request.headers.get('If-Match')).toBe('"7"');
    put.flush(facility({ version: 8, description: { gl: 'O meu texto', es: 'De otra persona' } }));
  });
});

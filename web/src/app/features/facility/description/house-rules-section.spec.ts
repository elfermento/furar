// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { renderDetail } from '../../../../testing/facility-detail';
import { facility } from '../detail/facility.fixture';

const URL = '/api/v1/municipalities/naron/facilities/f1';
const RULES = { gl: 'Calzado de herba artificial.', es: 'Calzado de hierba artificial.' };

async function render(houseRules?: typeof RULES) {
  const view = await renderDetail({ facility: { houseRules } });
  const section = () => view.el.querySelector<HTMLElement>('#facility-house-rules-section')!;
  const textarea = () => section().querySelector<HTMLTextAreaElement>('textarea')!;
  const lang = (code: 'gl' | 'es') => section().querySelector<HTMLButtonElement>(`button[data-lang="${code}"]`)!;
  return { ...view, section, textarea, lang };
}

describe('Sección Normas da instalación gl/es (D2-11)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('fóra da edición, o texto no idioma de quen mira; sen texto, «Sen indicar»', async () => {
    const { section } = await render(RULES);
    expect(section().querySelector('h2')?.textContent).toContain('Normas da instalación');
    expect(section().querySelector('[data-house-rules]')?.textContent?.trim()).toBe('Calzado de herba artificial.');
    TestBed.resetTestingModule();
    const empty = await render();
    expect(empty.section().querySelector('[data-house-rules]')?.textContent?.trim()).toBe('Sen indicar');
  });

  it('valida igual ca o servidor: se hai texto nun idioma fai falta no outro', async () => {
    const { section, edit, textarea, type, save } = await render();
    await edit();
    await type(textarea(), 'Sen comida no campo.');
    await save();
    expect(section().querySelector('.f-field-error')?.textContent).toContain('Castelán');
    backend().expectNone({ method: 'PUT', url: `${URL}/house-rules` });
    await expectNoAxeViolations(section());
  });

  it('garda co If-Match da ficha e os textos sen espazos; baleiros, sen normas', async () => {
    const { edit, textarea, lang, click, type, save, stable, el, section } = await render(RULES);
    await edit();
    await type(textarea(), '  Só calzado de multitaco. ');
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/house-rules` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({ gl: 'Só calzado de multitaco.', es: 'Calzado de hierba artificial.' });
    put.flush(facility({ version: 4, houseRules: { gl: 'Só calzado de multitaco.', es: RULES.es } }));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-action-bar')).toBeNull();
    });
    expect(section().querySelector('[data-house-rules]')?.textContent?.trim()).toBe('Só calzado de multitaco.');
    await edit();
    await type(textarea(), '');
    await click(lang('es'));
    await type(textarea(), '');
    await save();
    const clear = await expectRequest({ method: 'PUT', url: `${URL}/house-rules` });
    expect(clear.request.headers.get('If-Match')).toBe('"4"');
    expect(clear.request.body).toEqual({ gl: '', es: '' });
    clear.flush(facility({ version: 5 }));
  });
});

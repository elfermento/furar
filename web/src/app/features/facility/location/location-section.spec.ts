// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { renderDetail } from '../../../../testing/facility-detail';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { NO_MAP, provideDeploymentSettings } from '../../../../testing/deployment';
import type { Facility } from '../detail/facility-detail.store';
import { facility } from '../detail/facility.fixture';
import type { AddressSearch } from './address-search';
import { ADDRESS_SEARCH, geocoderUnavailable } from './address-search';
import { locationBody, locationForm } from './location-form';

const URL = '/api/v1/municipalities/naron/facilities/f1';

const PINEIROS = {
  street: 'Rúa de Piñeiros',
  streetNumber: null,
  postalCode: '15572',
  locality: 'Narón',
  ineCode: '15054',
  latitude: 43.5136,
  longitude: -8.1859,
};

describe('Localización: formulario', () => {
  it('manda os textos sen espazos e sen número se está baleiro', () => {
    const form = locationForm();
    form.setValue({
      street: ' Rúa Real ',
      streetNumber: '  ',
      postalCode: '15402',
      locality: 'Ferrol',
      ineCode: '15036',
      point: { latitude: 43.48, longitude: -8.23 },
    });
    expect(locationBody(form.getRawValue())).toEqual({
      street: 'Rúa Real',
      postalCode: '15402',
      locality: 'Ferrol',
      ineCode: '15036',
      latitude: 43.48,
      longitude: -8.23,
    });
  });

  it('valida igual ca o servidor: vía, CP de 5 díxitos, localidade e punto', () => {
    const form = locationForm();
    form.patchValue({ street: '  ', postalCode: '1557', locality: 'Narón' });
    expect(form.controls.street.errors).toEqual({ required: true });
    expect(form.controls.postalCode.hasError('pattern')).toBe(true);
    expect(form.controls.point.hasError('required')).toBe(true);
    expect(form.controls.streetNumber.valid).toBe(true);
  });
});

describe('Sección Localización (D1-21b)', () => {
  afterEach(() => {
    backend().verify();
    document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = ''));
  });

  async function render(search: AddressSearch, start: Partial<Facility> = {}, extra: unknown[] = []) {
    Element.prototype.scrollIntoView = () => undefined;
    const view = await renderDetail({
      facility: start,
      providers: [{ provide: ADDRESS_SEARCH, useValue: search }, ...extra],
    });
    const section = () => view.el.querySelector<HTMLElement>('#facility-location')!;
    const dialog = section;
    const open = () => view.edit();
    const input = (name: string) => section().querySelector<HTMLInputElement>(`#facility-location-${name}`)!;
    const type = async (name: string, value: string) => view.type(input(name), value);
    return { ...view, section, dialog, open, input, type };
  }

  const offline: AddressSearch = {
    suggest: () => Promise.reject(geocoderUnavailable()),
    resolve: () => Promise.reject(geocoderUnavailable()),
  };

  it('fóra da edición: só o mapa (o enderezo vai na cabeceira coa icona), que non é parada de tabulador', async () => {
    const { section, el } = await render(offline);
    expect(el.querySelector('.f-page-header__intro')?.textContent).toContain('Rúa do Alto, 12 · 15570 Narón');
    expect(el.querySelector('.f-page-header__intro furar-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(section().querySelector('input')).toBeNull();
    const map = section().querySelector('furar-map')!;
    expect(section().querySelector('[data-location-address]')).toBeNull();
    expect(map.querySelector('[role="group"]')).toBeNull();
    const tabbable = [...map.querySelectorAll<HTMLElement>('button, a, input, [tabindex]')].filter(
      (node) => node.tabIndex >= 0 && !node.closest('[aria-hidden="true"]') && node.tagName !== 'A',
    );
    expect(tabbable).toEqual([]);
    await expectNoAxeViolations(section());
  });

  it('sen mapa no despregue: fóra da edición o enderezo en texto; na edición, a latitude e a lonxitude', async () => {
    const { section, dialog, open, input } = await render(offline, {}, [provideDeploymentSettings(NO_MAP)]);
    expect(section().querySelector('furar-map')).toBeNull();
    expect(section().querySelector('[data-location-address]')?.textContent?.trim()).toBe(
      'Rúa do Alto, 12 · 15570 Narón',
    );
    await expectNoAxeViolations(section());
    await open();
    expect(section().querySelector('[data-location-address]')).toBeNull();
    expect(dialog()!.querySelector('.f-map__frame, .leaflet-container')).toBeNull();
    expect(input('map-latitude').value).toBe('43,500000');
    expect(dialog()!.querySelector('#facility-location-map-hint')?.textContent?.trim()).toBe(
      'Énchense ao buscar o enderezo; tamén se poden escribir.',
    );
    await expectNoAxeViolations(dialog()!);
  });

  it('busca, escolle unha suxestión e enche os campos e a chincheta; garda co If-Match', async () => {
    const search: AddressSearch = {
      suggest: vi.fn().mockResolvedValue([{ id: 'p1', label: 'Rúa de Piñeiros, Narón' }]),
      resolve: vi.fn().mockResolvedValue(PINEIROS),
    };
    const { el, dialog, open, input, type, save, stable } = await render(search);
    await open();
    expect(dialog()!.querySelector('[data-source]')?.textContent).toContain('CartoCiudad, © IGN');
    expect(input('street').value).toBe('Rúa do Alto');
    // Mapa editable: chincheta con frechas e campos de latitude e lonxitude (WCAG 2.5.7).
    expect(input('map-latitude').value).toBe('43,500000');
    await expectNoAxeViolations(dialog()!);

    await type('search', 'Piñeiros');
    let option: HTMLElement | null = null;
    await vi.waitFor(
      async () => {
        await stable();
        option = document.querySelector<HTMLElement>('[role="option"]');
        expect(option).not.toBeNull();
      },
      { timeout: 2000 },
    );
    expect(search.suggest).toHaveBeenCalledWith('Piñeiros');
    // Teclado: frecha abaixo activa a suxestión e Intro escóllea (o foco segue no campo).
    for (const key of ['ArrowDown', 'Enter']) {
      input('search').dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
      await stable();
    }
    await vi.waitFor(async () => {
      await stable();
      expect(input('street').value).toBe('Rúa de Piñeiros');
    });
    expect(search.resolve).toHaveBeenCalledWith('p1');
    expect(input('streetNumber').value).toBe('');
    expect(input('postalCode').value).toBe('15572');

    await save();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/address` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({
      street: 'Rúa de Piñeiros',
      postalCode: '15572',
      locality: 'Narón',
      ineCode: '15054',
      latitude: 43.5136,
      longitude: -8.1859,
    });
    put.flush(facility({ version: 4, address: { ...PINEIROS, streetNumber: undefined } }));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-action-bar')).toBeNull();
    });
    expect(el.querySelector('.f-page-header__intro')?.textContent).toContain('Rúa de Piñeiros · 15572 Narón');
  });

  it('503 do buscador: aviso, foco no primeiro campo e enderezo a man', async () => {
    const { el, dialog, open, input, type, save, stable } = await render(offline);
    await open();
    await type('search', 'Rúa');
    await vi.waitFor(
      async () => {
        await stable();
        expect(dialog()!.querySelector('[data-manual]')).not.toBeNull();
      },
      { timeout: 2000 },
    );
    expect(dialog()!.querySelector('[data-manual]')?.getAttribute('data-tone')).toBe('warn');
    expect(dialog()!.querySelector('#facility-location-search')).toBeNull();
    expect(document.activeElement).toBe(input('street'));

    await type('street', 'Avenida de Castela');
    await type('streetNumber', '5');
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/address` });
    expect(put.request.body).toMatchObject({ street: 'Avenida de Castela', streetNumber: '5', ineCode: '15054' });
    put.flush(facility({ version: 4 }));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-action-bar')).toBeNull();
    });
  });

  it('valida antes de enviar e pon no campo os erros da API', async () => {
    const { el, dialog, open, input, type, save, stable } = await render(offline);
    await open();
    await type('street', '   ');
    await save();
    backend().expectNone({ method: 'PUT', url: `${URL}/address` });
    expect(dialog()!.querySelector('#facility-location-street-error')?.textContent).toContain('obrigatorio');
    expect(input('street').getAttribute('aria-invalid')).toBe('true');

    await type('street', 'Rúa do Alto');
    await save();
    flushProblem(await expectRequest({ method: 'PUT', url: `${URL}/address` }), 422, 'facility.address-invalid', {
      errors: [{ field: 'address.postalCode', code: 'validation.pattern', params: {} }],
    });
    await vi.waitFor(async () => {
      await stable();
      expect(dialog()!.querySelector('#facility-location-postalCode-error')?.textContent).toContain('formato');
    });
    expect(el.querySelector('[data-edit-status]')?.textContent).toContain('Non se gardou todo');
  });
});

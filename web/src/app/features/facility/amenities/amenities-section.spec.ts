// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest } from '../../../../testing/api';
import { renderDetail } from '../../../../testing/facility-detail';
import { expectNoAxeViolations } from '../../../../testing/axe';
import type { Facility } from '../detail/facility-detail.store';
import { facility } from '../detail/facility.fixture';
import { AMENITY_SYMBOLS as SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import { AMENITIES, AMENITY_GROUPS, amenitiesBody, amenitiesForm, amenityIcon } from './amenities';

const URL = '/api/v1/municipalities/naron/facilities/f1';

describe('Catálogo de servizos e accesibilidade', () => {
  it('cada elemento ten unha icona propia, fóra da carga inicial', () => {
    const icons = AMENITIES.map(amenityIcon);
    expect(new Set(icons).size).toBe(AMENITIES.length);
    expect(icons.every((icon) => icon in SYMBOLS)).toBe(true);
  });

  it('o catálogo ten cada valor do contrato nun grupo e o corpo vai na súa orde', () => {
    expect(AMENITY_GROUPS.flatMap((g) => g.items)).toEqual(AMENITIES);
    const form = amenitiesForm(['PARKING', 'SHOWERS']);
    form.controls.HEARING_LOOP.setValue(true);
    expect(amenitiesBody(form.getRawValue())).toEqual(['SHOWERS', 'PARKING', 'HEARING_LOOP']);
  });
});

describe('Sección Servizos e accesibilidade (D1-21b)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(start: Partial<Facility> = {}) {
    const view = await renderDetail({ facility: start });
    const section = () => view.el.querySelector<HTMLElement>('#facility-amenities')!;
    const box = (name: string) =>
      [...section().querySelectorAll('label')].find((l) => l.textContent?.trim() === name)!.querySelector('input')!;
    return { ...view, section, box };
  }

  it('fóra da edición: o que ofrece na orde do catálogo, cada un coa súa icona', async () => {
    const { section } = await render({ amenities: ['STEP_FREE_ACCESS', 'SHOWERS', 'PARKING'] });
    const items = [...section().querySelectorAll('li')].map((li) => li.textContent?.trim());
    expect(items).toEqual(['Duchas', 'Aparcadoiro', 'Acceso sen chanzos']);
    const icons = [...section().querySelectorAll('li path')].map((path) => path.getAttribute('d'));
    expect(icons).toEqual([SYMBOLS.shower, SYMBOLS['local-parking'], SYMBOLS['accessible-forward']]);
    expect(section().querySelector('input')).toBeNull();
    await expectNoAxeViolations(section());
  });

  it('baleira: un texto', async () => {
    const { section } = await render();
    expect(section().querySelector('ul')).toBeNull();
    expect(section().textContent).toContain('Aínda non indicaches');
  });

  it('en edición, un fieldset por grupo; garda a lista enteira co If-Match da ficha', async () => {
    const { section, edit, box, click, save, stable, el } = await render({ amenities: ['PARKING'] });
    await edit();
    const legends = [...section().querySelectorAll('fieldset legend')].map((l) => l.textContent?.trim());
    expect(legends).toEqual(['Servizos', 'Accesibilidade']);
    expect(box('Aparcadoiro').checked).toBe(true);
    await expectNoAxeViolations(section());
    await click(box('Duchas'));
    await click(box('Bucle magnético'));
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/amenities` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({ amenities: ['SHOWERS', 'PARKING', 'HEARING_LOOP'] });
    put.flush(facility({ version: 4, amenities: ['SHOWERS', 'PARKING', 'HEARING_LOOP'] }));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-action-bar')).toBeNull();
    });
    expect(section().querySelectorAll('li')).toHaveLength(3);
  });

  it('Cancelar desfai as casillas sen ningunha petición', async () => {
    const { section, edit, box, click, cancel } = await render({ amenities: ['PARKING'] });
    await edit();
    await click(box('Duchas'));
    await cancel();
    expect([...section().querySelectorAll('li')].map((li) => li.textContent?.trim())).toEqual(['Aparcadoiro']);
  });
});

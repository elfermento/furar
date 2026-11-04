// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { PAYMENT_NOTES_URL, paymentNotes, renderDetail } from '../../../../testing/facility-detail';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { pickOption } from '../../../../testing/select';
import { definition, municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { FacilityDetailPage } from '../detail/facility-detail.page';
import type { Facility } from '../detail/facility-detail.store';
import { SUGGESTED, facility } from '../detail/facility.fixture';
import { answerBlocks } from '../blocks/blocks.fixture';

const BASE = '/api/v1/municipalities/naron';
const URL = `${BASE}/facilities/f1`;
const FOOTBALL = { code: 'FOOTBALL', label: { gl: 'Fútbol', es: 'Fútbol' } };
const PADEL = { code: 'PADEL', label: { gl: 'Pádel', es: 'Pádel' } };
const BIKES = definition({
  code: 'bike_parking',
  type: 'BOOLEAN',
  label: { gl: 'Aparcadoiro de bicis', es: 'Aparcamiento de bicis' },
  help: null as never,
  usages: [{ target: 'FACILITY', required: false, visibility: 'PUBLIC' }],
});

function details(overrides: Partial<Facility['details']> = {}): Partial<Facility> {
  return {
    details: {
      onlineBooking: true,
      showFreeSlots: false,
      sports: [FOOTBALL],
      type: 'FIELD',
      phone: '981 38 21 00',
      customFields: {},
      ...overrides,
    },
  };
}

describe('Tarxeta Datos (D1-21d)', () => {
  afterEach(() => {
    backend()
      .match({ method: 'GET', url: `${URL}/uses` })
      .forEach((request) => request.flush([]));
    backend()
      .match({ method: 'GET', url: `${URL}/schedule` })
      .forEach((request) => request.flush({ seasons: [], holidays: [] }));
    answerBlocks(backend());
    backend()
      .match(PAYMENT_NOTES_URL)
      .forEach((request) => request.flush(paymentNotes('ALL')));
    // Cambiar deportes, tipo ou cuberta pide o debuxo suxerido (próbase en photos.spec.ts).
    backend()
      .match((request) => request.url.endsWith('/drawings/suggestion'))
      .forEach((request) => request.flush(SUGGESTED));
    backend().verify();
    document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = ''));
  });

  async function render(loaded: Partial<Facility> = details(), definitions: ReturnType<typeof definition>[] = []) {
    const view = await renderDetail({ facility: loaded, customFields: definitions });
    const { stable } = view;
    const card = () => view.el.querySelector<HTMLElement>('#facility-data')!;
    await vi.waitFor(async () => {
      await stable();
      expect(card().querySelector('furar-status-state')).toBeNull();
    });
    const field = (label: string) =>
      [...card().querySelectorAll<HTMLElement>('furar-edit-field')].find((f) =>
        f.querySelector('.f-edit-field__label')?.textContent?.includes(label),
      )!;
    /** Modo edición da ficha (todos os campos á vista). */
    const open = async (label?: string) => {
      void label;
      if (!view.el.querySelector('[data-edit-save]')) await view.edit();
    };
    const type = async (id: string, value: string) => {
      const input = card().querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await stable();
    };
    const submit = view.save;
    const options = () => [...document.querySelectorAll('[role="option"]')].map((o) => o.textContent?.trim());
    /** Escribe no buscador e agarda ás suxestións; a primeira vez responde o catálogo. */
    const search = async (text: string, catalog?: unknown[]) => {
      await type('facility-data-sport', text);
      if (catalog) {
        const request = await expectRequest({ method: 'GET', url: `${BASE}/sports` });
        request.flush(catalog);
      }
      await vi.waitFor(
        async () => {
          await stable();
          expect(options().length).toBeGreaterThan(0);
        },
        { timeout: 2000 },
      );
    };
    const key = async (name: string) => {
      card()
        .querySelector('#facility-data-sport')!
        .dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));
      await stable();
    };
    return { ...view, card, field, open, type, submit, search, options, key, stable };
  }

  it('fóra da edición mostra cada dato en texto co seu valor (sen lapis) e, sen campos propios, nada máis', async () => {
    const { card, field } = await render();
    expect(card().querySelector('h2')?.textContent).toContain('Datos');
    expect(field('Deportes').textContent).toContain('Fútbol');
    expect(field('Tipo').textContent).toContain('Campo');
    expect(field('Teléfono').textContent).toContain('981 38 21 00');
    expect(field('Código RIDEGA').textContent).toContain('Sen indicar');
    expect(field('reserva por internet').textContent).toContain('Si');
    // Con reserva por internet non se ofrecen os ocos libres.
    expect(field('ocos libres')).toBeUndefined();
    expect(card().querySelector('furar-custom-field, furar-callout, furar-status-state')).toBeNull();
    expect(card().querySelectorAll('furar-edit-field')).toHaveLength(7);
    expect(card().querySelectorAll('.f-edit-field__static')).toHaveLength(7);
    expect(card().querySelector('.f-edit-field__pencil:not([hidden] *)')).toBeNull();
    await expectNoAxeViolations(card());
  });

  it('valida igual ca o servidor e non envía nada se hai erros', async () => {
    const { card, open, type, submit } = await render();
    await open('Teléfono');
    await type('facility-data-phone', '98-12');
    await open('Correo');
    await type('facility-data-email', 'sen arroba');
    await open('Código RIDEGA');
    await type('facility-data-ridegaCode', 'x'.repeat(31));
    await submit();
    expect(card().querySelector('#facility-data-phone-error')?.textContent).toContain('formato');
    expect(card().querySelector('#facility-data-email-error')?.textContent).toContain('formato');
    expect(card().querySelector('#facility-data-ridegaCode-error')?.textContent).toContain('30');
    expect(document.activeElement?.id).toBe('facility-data-ridegaCode');
    backend().expectNone({ method: 'PUT', url: `${URL}/details` });
  });

  it('garda todo xunto co If-Match da ficha: textos recortados, baleiros fóra e sen ocos con internet', async () => {
    const { el, card, field, open, type, submit, stable } = await render();
    await open('Tipo');
    await pickOption(card().querySelector('#facility-data-type')!, 'Pavillón', stable);
    await stable();
    await open('Teléfono');
    await type('facility-data-phone', '  ');
    await open('Correo');
    await type('facility-data-email', ' deportes@naron.es ');
    await open('Cuberta');
    card().querySelector<HTMLButtonElement>('[role="radio"]')!.click();
    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/details` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({
      sports: [{ code: 'FOOTBALL' }],
      type: 'SPORTS_HALL',
      email: 'deportes@naron.es',
      onlineBooking: true,
      showFreeSlots: false,
      covered: true,
      customFields: {},
    });
    put.flush(facility({ version: 4, ...details({ type: 'SPORTS_HALL', email: 'deportes@naron.es', covered: true }) }));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-action-bar')).toBeNull();
    });
    expect(field('Tipo').textContent).toContain('Pavillón');
    expect(field('Cuberta').textContent).toContain('Si');
  });

  it('en edición o nome é o primeiro campo; se cambia, garda o nome e despois os datos co If-Match novo', async () => {
    const { card, open, type, submit, stable } = await render();
    expect(card().querySelector('#facility-data-name')).toBeNull();
    await open();
    const first = card().querySelector('input, button[role="combobox"], [role="combobox"]');
    expect(first?.id).toBe('facility-data-name');
    await type('facility-data-name', '   ');
    await submit();
    expect(card().querySelector('#facility-data-name-error')).not.toBeNull();
    backend().expectNone({ method: 'PUT', url: `${URL}/name` });
    await type('facility-data-name', '  Campo de O Alto  ');
    await submit();
    const rename = await expectRequest({ method: 'PUT', url: `${URL}/name` });
    expect(rename.request.headers.get('If-Match')).toBe('"3"');
    expect(rename.request.body).toEqual({ name: 'Campo de O Alto' });
    rename.flush(facility({ version: 4, name: 'Campo de O Alto', ...details() }));
    await stable();
    const put = await vi.waitFor(() => expectRequest({ method: 'PUT', url: `${URL}/details` }));
    expect(put.request.headers.get('If-Match')).toBe('"4"');
    put.flush(facility({ version: 5, name: 'Campo de O Alto', ...details() }));
    await stable();
  });

  it('sen cambiar o nome non se envía o PUT …/name', async () => {
    const { open, type, submit } = await render();
    await open();
    await type('facility-data-phone', '981 00 00 00');
    await submit();
    backend().expectNone({ method: 'PUT', url: `${URL}/name` });
    (await expectRequest({ method: 'PUT', url: `${URL}/details` })).flush(facility({ version: 4, ...details() }));
  });

  it('sen reserva por internet aparece «Mostrar os ocos libres»; ao volver a acendela, apágase', async () => {
    const { card, field, open, submit, stable } = await render();
    await open('reserva por internet');
    card().querySelector<HTMLButtonElement>('#facility-data-online')!.click();
    await stable();
    await open('ocos libres');
    const slots = card().querySelector<HTMLButtonElement>('#facility-data-free-slots')!;
    slots.click();
    await stable();
    expect(slots.getAttribute('aria-checked')).toBe('true');
    expect(field('ocos libres')).toBeDefined();
    card().querySelector<HTMLButtonElement>('#facility-data-online')!.click();
    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/details` });
    expect(put.request.body).toMatchObject({ onlineBooking: true, showFreeSlots: false });
    put.flush(facility({ version: 4, ...details() }));
  });

  it('deportes: busca no catálogo sen os xa escollidos, engade, quita e crea un novo en gl e es', async () => {
    const { card, open, search, options, key, submit, stable } = await render();
    await open('Deportes');
    await expectNoAxeViolations(card());
    // O catálogo pídese unha vez, ao buscar por primeira vez; o xa escollido non sae.
    await search('pad', [FOOTBALL, PADEL]);
    expect(options()).toEqual([expect.stringContaining('Pádel'), expect.stringContaining('Crear deporte')]);
    await key('ArrowDown');
    await key('Enter');
    expect([...card().querySelectorAll('li')].map((li) => li.textContent?.trim())).toEqual(['Fútbol', 'Pádel']);

    card().querySelector<HTMLButtonElement>('[data-remove="0"] button')!.click();
    await stable();
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Eliminar Pádel');

    await search('Bolos celtas');
    await key('ArrowUp');
    await key('Enter');
    await vi.waitFor(() => expect(document.querySelector('#facility-new-sport')).not.toBeNull());
    const name = document.querySelector<HTMLInputElement>('#facility-new-sport')!;
    expect(name.value).toBe('Bolos celtas');
    document.querySelector<HTMLButtonElement>('furar-facility-create-sport-dialog [data-lang="es"]')!.click();
    await vi.waitFor(() => expect(name.getAttribute('lang')).toBe('es'));
    expect(name.value).toBe('Bolos celtas');
    document.querySelector<HTMLButtonElement>('[data-save]')!.click();
    const post = await expectRequest({ method: 'POST', url: `${BASE}/sports` });
    expect(post.request.body).toEqual({ gl: 'Bolos celtas', es: 'Bolos celtas' });
    post.flush({ customSportId: 'c1', label: { gl: 'Bolos celtas', es: 'Bolos celtas' } });
    await vi.waitFor(async () => {
      await stable();
      expect(card().textContent).toContain('Bolos celtas');
    });

    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/details` });
    expect(put.request.body.sports).toEqual([{ code: 'PADEL' }, { customSportId: 'c1' }]);
    flushProblem(put, 409, 'facility.sport-duplicated');
    await vi.waitFor(async () => {
      await stable();
      expect(card().textContent).toContain('A instalación xa ten ese deporte.');
    });
  });

  it('campos propios: saen cos demais, editan no sitio e gardan os demais valores como están', async () => {
    const { card, field, open, submit, stable } = await render(
      details({ customFields: { bike_parking: true, old: 'x' } }),
      [BIKES],
    );
    expect(field('Aparcadoiro de bicis').textContent).toContain('Si');
    await open('Aparcadoiro de bicis');
    card().querySelectorAll<HTMLInputElement>('furar-custom-field input[type="radio"]')[1]!.click();
    await stable();
    await expectNoAxeViolations(card());
    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/details` });
    expect(put.request.body.customFields).toEqual({ old: 'x', bike_parking: false });
    flushProblem(put, 422, 'validation.failed', {
      errors: [
        { field: 'customFields.bike_parking', code: 'customfield.invalid-type', params: {} },
        { field: 'details.phone', code: 'validation.pattern', params: {} },
      ],
    });
    await vi.waitFor(async () => {
      await stable();
      expect(card().querySelector('#facility-data-phone-error')?.textContent).toContain('formato');
    });
    expect(field('Aparcadoiro de bicis').textContent).toContain('tipo esperado');
  });

  it('se os campos propios non cargan, avisa con «Tentar de novo» e o resto edítase igual', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(FacilityDetailPage);
    fixture.componentRef.setInput('id', 'f1');
    await fixture.whenStable();
    (await expectRequest(URL)).flush(facility(details()));
    flushProblem(
      await expectRequest({ method: 'GET', url: `${BASE}/custom-fields?target=FACILITY` }),
      500,
      'common.unexpected',
    );
    const card = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('#facility-data')!;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(card.querySelector('furar-callout')?.textContent).toContain('campos propios');
    });
    [...card.querySelectorAll('button')].find((b) => b.textContent?.includes('Tentar de novo'))!.click();
    (await expectRequest({ method: 'GET', url: `${BASE}/custom-fields?target=FACILITY` })).flush([]);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(card.querySelector('furar-callout')).toBeNull();
    });
  });
});

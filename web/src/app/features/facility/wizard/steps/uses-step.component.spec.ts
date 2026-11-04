// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend, expectRequest, flushProblem } from '../../../../../testing/api';
import { WIZARD_BASE, openWizard, renderWizardStep, setupWizard } from '../../../../../testing/facility-wizard';
import { facility } from '../../detail/facility.fixture';
import type { Use } from '../../uses/uses.store';
import { use } from '../../uses/uses.fixture';
import { STEP } from '../wizard';
import type { WizardStore } from '../wizard.store';
import { UsesStepComponent } from './uses-step.component';

const F = `${WIZARD_BASE}/facilities/f2`;
const sport = (code: string, gl: string) => ({ code, label: { gl, es: gl } });

/** Borrador de pádel e baloncesto, no paso «Usos». */
const draft = () =>
  facility({
    id: 'f2',
    published: false,
    details: {
      onlineBooking: true,
      showFreeSlots: false,
      sports: [sport('padel', 'Pádel'), sport('basketball', 'Baloncesto')],
      customFields: {},
    },
  });

describe('Paso «Usos» do asistente', () => {
  let store: WizardStore;

  afterEach(() => backend().verify());

  async function render(uses: Use[] = []) {
    store = setupWizard();
    await openWizard(store, draft(), { uses });
    store.goTo(STEP.uses);
    const view = await renderWizardStep(UsesStepComponent);
    const button = (text: string) =>
      [...view.el.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === text);
    const presets = () => [...view.el.querySelectorAll('[data-preset]')].map((b) => b.textContent?.trim());
    const editor = () => view.el.querySelector<HTMLElement>('furar-wizard-use-editor');
    const radio = (text: string) =>
      [...(editor()?.querySelectorAll<HTMLButtonElement>('[role="radio"]') ?? [])].find(
        (b) => b.textContent?.trim() === text,
      )!;
    const parts = () => [...(editor()?.querySelectorAll<HTMLInputElement>('[data-part] input') ?? [])];
    return { ...view, button, presets, editor, radio, parts };
  }

  it('sen usos: os atallos saen dos deportes; un atallo abre o formulario cheo e «Cancelar» pécha', async () => {
    const { el, presets, button, click, editor, parts } = await render();
    expect(presets()).toEqual(['Pista de pádel', 'Pista enteira', 'Media pista']);
    expect(el.querySelector('[data-use-other]')?.textContent?.trim()).toBe('Outro');
    expect(el.querySelector('#wizard-uses-error')).toBeNull();

    await click(button('Media pista')!);
    expect(editor()?.querySelector('h3')?.textContent?.trim()).toBe('Novo uso');
    expect((editor()?.querySelector('#wizard-use-name') as HTMLInputElement).value).toBe('Media pista');
    expect(parts().map((input) => input.value)).toEqual(['Media pista norte', 'Media pista sur']);
    // Co formulario aberto, os atallos non se ven.
    expect(presets()).toEqual([]);

    await click(el.querySelector('[data-use-cancel]'));
    expect(editor()).toBeNull();
    expect(presets()).toHaveLength(3);
  });

  it('«Seguinte» sen ningún uso avisa no paso', async () => {
    const { el, stable } = await render();
    expect(await store.next()).toBe(false);
    await stable();
    expect(el.querySelector('#wizard-uses-error')?.textContent?.trim()).toBe('Engade polo menos un uso.');
  });

  it('engadir un uso desde un atallo vai á API ao momento; o atallo xa non se ofrece', async () => {
    const { el, presets, button, click, editor } = await render();
    await click(button('Pista de pádel')!);
    await click(el.querySelector('[data-use-apply]'));
    const created = await expectRequest({ method: 'POST', url: `${F}/uses` });
    expect(created.request.body).toMatchObject({ name: 'Pista de pádel', rentalMode: 'WHOLE', slotMinutes: 60 });
    created.flush(use({ id: 'u7', facilityId: 'f2', name: 'Pista de pádel', slotMinutes: 60 }));
    (await expectRequest({ method: 'GET', url: F })).flush(draft());
    await vi.waitFor(() => expect(editor()).toBeNull());
    expect(el.querySelector('[data-use="Pista de pádel"]')?.textContent).toContain('Sen prezo');
    expect(presets()).toEqual(['Pista enteira', 'Media pista']);
  });

  it('o formulario: por partes engade e quita partes; por prazas, a capacidade; de pago, o importe', async () => {
    const { el, click, editor, radio, parts, button } = await render();
    await click(el.querySelector('[data-use-other]'));
    await click(radio('Por partes'));
    expect(parts()).toHaveLength(2);
    // Co mínimo de partes, non se poden quitar.
    expect(editor()?.querySelectorAll('[data-part] furar-delete-button')).toHaveLength(0);
    await click(button('Parte')!);
    expect(parts()).toHaveLength(3);
    expect(editor()?.querySelectorAll('[data-part] furar-delete-button')).toHaveLength(3);
    await click(editor()!.querySelector('[data-part] furar-delete-button button'));
    expect(parts()).toHaveLength(2);

    await click(radio('Por prazas'));
    expect(editor()?.querySelector('#wizard-use-capacity')).not.toBeNull();
    expect(parts()).toHaveLength(0);
    await click(radio('De pago'));
    expect(editor()?.querySelector('#wizard-use-amount-per')?.textContent?.trim()).toBe('por persoa');
    await click(radio('Enteira'));
    await click(radio('90 min'));
    expect(editor()?.querySelector('#wizard-use-amount-per')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'por reserva de 90 min',
    );

    // Sen nome nin importe, «Engadir uso» queda cos erros e non vai á API.
    await click(el.querySelector('[data-use-apply]'));
    expect(editor()?.querySelector('#wizard-use-name')?.getAttribute('aria-invalid')).toBe('true');
    expect(editor()?.querySelector('#wizard-use-amount')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('os usos gardados: prezo por reserva ou por persoa; editar no sitio e eliminar', async () => {
    const paid = use({
      id: 'u1',
      facilityId: 'f2',
      name: 'Pista',
      price: { kind: 'PAID', amountCents: 1200, per: 'BOOKING' },
    });
    const seats = use({
      id: 'u2',
      facilityId: 'f2',
      name: 'Clase',
      rentalMode: 'SEATS',
      capacity: 20,
      price: { kind: 'PAID', amountCents: 300, per: 'PERSON' },
    });
    const { el, click, editor } = await render([paid, seats]);
    const row = (name: string) => el.querySelector<HTMLElement>(`[data-use="${name}"]`);
    expect(row('Pista')?.querySelector('.f-price')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      '12,00 € por reserva de 90 min',
    );
    expect(row('Clase')?.querySelector('small')?.textContent?.trim()).toBe('por persoa');

    await click(row('Pista')!.querySelector('[aria-label="Editar Pista"]'));
    expect(row('Pista')).toBeNull();
    expect(editor()?.closest('li')).not.toBeNull();
    expect(editor()?.querySelector('h3')?.textContent?.trim()).toBe('Editar uso');
    expect(el.querySelector('[data-use-other]')).toBeNull();
    const name = editor()!.querySelector<HTMLInputElement>('#wizard-use-name')!;
    name.value = 'Clase';
    name.dispatchEvent(new Event('input'));
    await click(el.querySelector('[data-use-apply]'));
    flushProblem(await expectRequest({ method: 'PUT', url: `${F}/uses/u1` }), 409, 'facility.use-name-taken', {
      params: { name: 'Clase' },
    });
    await vi.waitFor(() => expect(editor()?.textContent).toContain('Esta instalación xa ten un uso chamado «Clase».'));
    await click(el.querySelector('[data-use-cancel]'));

    await click(row('Clase')!.querySelector('[aria-label="Eliminar o uso Clase"]'));
    (await expectRequest({ method: 'POST', url: `${F}/uses/u2/archive` })).flush(null);
    (await expectRequest({ method: 'GET', url: F })).flush(draft());
    await vi.waitFor(() => expect(row('Clase')).toBeNull());
  });

  it('«Quen fai as reservas?»: só o persoal ensina se se ven os ocos libres', async () => {
    const { el, click, stable } = await render([use({ facilityId: 'f2' })]);
    expect(el.querySelector('furar-switch')).toBeNull();
    el.querySelectorAll('furar-radio-cards input[type="radio"]')[1].dispatchEvent(new Event('change'));
    await stable();
    expect(store.data.controls.onlineBooking.value).toBe(false);
    expect(el.querySelector('furar-switch')).not.toBeNull();
    await click(el.querySelector('#wizard-free-slots'));
    expect(store.data.controls.showFreeSlots.value).toBe(true);
  });
});

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend, expectRequest } from '../../../../../testing/api';
import { NO_MAP } from '../../../../../testing/deployment';
import {
  WIZARD_BASE,
  WIZARD_NOTES,
  drawing,
  libraryPhoto,
  openWizard,
  renderWizardStep,
  setupWizard,
  startWizard,
} from '../../../../../testing/facility-wizard';
import { definition } from '../../../../../testing/fixtures';
import { facility } from '../../detail/facility.fixture';
import { fromLibrary } from '../../photos/photos';
import { FUTBOL_7, use } from '../../uses/uses.fixture';
import { STEP } from '../wizard';
import type { WizardStore } from '../wizard.store';
import { ReviewStepComponent } from './review-step.component';

const PADEL = { code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } };

/** Borrador enchido en todos os pasos, co pago por instalación. */
const complete = () =>
  facility({
    id: 'f2',
    name: 'Pista de pádel 3',
    published: false,
    missing: [],
    amenities: ['PARKING'],
    description: { gl: 'Pista cuberta', es: 'Pista cubierta' },
    houseRules: { gl: 'Calzado limpo', es: 'Calzado limpio' },
    details: {
      onlineBooking: false,
      showFreeSlots: true,
      sports: [PADEL],
      type: 'SPORTS_HALL',
      covered: true,
      phone: '981 000 000',
      email: 'pistas@naron.example',
      ridegaCode: 'R-15',
      customFields: { pitch: 'Herba', legacy: 'Vello' },
    },
  });

describe('Paso «Revisar» do asistente', () => {
  let store: WizardStore;

  afterEach(() => backend().verify());

  async function render(drawings: unknown[] = []) {
    const view = await renderWizardStep(ReviewStepComponent);
    (await expectRequest(`${WIZARD_BASE}/drawings`)).flush(drawings);
    await view.stable();
    const edits: number[] = [];
    view.fixture.componentInstance.edit.subscribe((step) => edits.push(step));
    /** O texto dunha fila do resumo (o valor, «Sen indicar» ou o que falta). */
    const row = (id: string) => view.el.querySelector(`[data-row="${id}"] dd`)?.textContent?.trim();
    const problem = (id: string) => view.el.querySelector(`[data-row="${id}"] [data-problem]`)?.textContent?.trim();
    return { ...view, edits, row, problem };
  }

  it('baleiro: «Para publicala falta» cada cousa co seu paso, e no resumo, «Falta» ou «Sen indicar»', async () => {
    store = setupWizard();
    await startWizard(store);
    const { el, edits, row, problem, click } = await render();
    const missing = [...el.querySelectorAll('[data-missing]')].map((item) => item.textContent?.trim());
    expect(missing).toEqual(['O nome', 'Os deportes', 'O enderezo', 'Polo menos un uso']);
    await click(el.querySelector('[data-missing="address"]'));
    expect(edits).toEqual([STEP.location]);

    expect(problem('name')).toBe('Falta');
    expect(problem('sports')).toBe('Falta');
    expect(problem('address')).toBe('Falta');
    expect(row('type')).toBe('Sen indicar');
    expect(row('covered')).toBe('Sen indicar');
    expect(row('phone')).toBe('Sen indicar');
    expect(row('who')).toBe('A veciñanza, desde esta web');
    expect(el.querySelector(`[aria-labelledby="wizard-review-${STEP.uses}"] .f-field-error`)?.textContent?.trim()).toBe(
      'Falta',
    );
    // O horario por defecto xa vale: «Todo o ano», de 8:00 a 23:00 e os festivos pechados.
    expect(row('season')).toBe('Todo o ano');
    expect(row('MONDAY')).toContain('8:00');
    expect(row('HOLIDAYS')).toBe('Pechado');
    // Co pago igual para todas, o paso 7 só leva as normas.
    expect(el.querySelector('[data-row="payment"]')).toBeNull();
    expect(el.querySelector('furar-map')).toBeNull();

    await click(el.querySelector(`[data-edit-step="${STEP.schedule}"]`));
    expect(edits).toEqual([STEP.location, STEP.schedule]);
    expect(el.querySelector(`[data-edit-step="${STEP.schedule}"]`)?.getAttribute('aria-label')).toBe('Editar Horario');
  });

  it('completo: sen avisos, cada paso co escrito, os usos co prezo, as fotos e o mapa', async () => {
    store = setupWizard();
    const pitch = definition({
      code: 'pitch',
      label: { gl: 'Céspede', es: 'Césped' },
      usages: [{ target: 'FACILITY', required: false, visibility: 'PUBLIC' }],
    });
    const lit = definition({
      id: '019a0b6e-0000-7000-8000-0000000000ab',
      code: 'lit',
      type: 'BOOLEAN',
      label: { gl: 'Iluminada', es: 'Iluminada' },
      usages: [{ target: 'FACILITY', required: false, visibility: 'PUBLIC' }],
    });
    const paid = use({
      id: 'u5',
      facilityId: 'f2',
      name: 'Pista',
      price: { kind: 'PAID', amountCents: 1200, per: 'BOOKING' },
    });
    await openWizard(store, complete(), {
      uses: [paid, { ...FUTBOL_7, facilityId: 'f2' }],
      definitions: [pitch, lit],
      notes: { ...WIZARD_NOTES, scope: 'FACILITY' },
    });
    store.forms.setPhotos([fromLibrary(libraryPhoto('p1', { gl: 'A pista', es: 'La pista' }))]);
    const { el, row, problem } = await render([drawing('football_field', 'Campo de fútbol')]);

    expect(el.querySelector('#wizard-missing')).toBeNull();
    expect(row('name')).toBe('Pista de pádel 3');
    expect(row('sports')).toBe('Pádel');
    expect(row('type')).toBe('Pavillón');
    expect(row('covered')).toBe('Si');
    expect(row('description')).toBe('Pista cuberta');
    expect(row('address')).toContain('Rúa do Alto');
    expect(row('phone')).toBe('981 000 000');
    expect(row('email')).toBe('pistas@naron.example');
    expect(row('amenities')).toBe('Aparcadoiro');
    expect(row('ridega')).toBe('R-15');
    expect(row('cf-pitch')).toBe('Herba');
    expect(row('cf-lit')).toBe('Sen indicar');
    expect(row('who')).toBe('Só o persoal do concello · Mostrar os ocos libres');
    expect(row('rules')).toBe('Calzado limpo');
    // Pago por instalación sen texto propio: o do concello por defecto.
    expect(row('payment')).toBe('Por defecto: «Furar paga»');
    expect(row('refund')).toBe('Por defecto: «Furar devolve»');
    expect(problem('name')).toBeUndefined();

    const uses = [...el.querySelectorAll('.f-wizard-uses__row')].map((item) => item.textContent?.replace(/\s+/g, ' '));
    expect(uses).toHaveLength(2);
    expect(uses[0]).toContain('12,00 €');
    expect(uses[1]).toContain('Sen prezo');
    const thumbs = [...el.querySelectorAll<HTMLImageElement>('.f-wizard-review__thumbs img')];
    expect(thumbs.map((img) => img.alt)).toEqual(['A pista']);
    expect(el.textContent).not.toContain('Sen fotos');
    expect(el.querySelector('furar-map')).not.toBeNull();

    // Co pago escrito na instalación, vese ese.
    store.payment.controls.payment.setValue({ gl: 'Na oficina', es: 'En la oficina' });
    await vi.waitFor(() => expect(row('payment')).toBe('Na oficina'));
  });

  it('o que está mal escrito vese en vermello e o horario sen tramo válido, como que falta', async () => {
    store = setupWizard();
    await openWizard(store, complete(), { uses: [use({ facilityId: 'f2' })] });
    store.data.controls.phone.setValue('abc');
    store.data.controls.email.setValue('sen-arroba');
    store.data.controls.ridegaCode.setValue('x'.repeat(31));
    store.description.controls.es.setValue('');
    store.houseRules.controls.gl.setValue('');
    store.sameHours.setValue({ startMinute: 900, endMinute: 600 });
    const { el, problem } = await render();
    expect(problem('phone')).toBe('Formato non válido');
    expect(problem('email')).toBe('Formato non válido');
    expect(problem('ridega')).toBe('Formato non válido');
    expect(problem('description')).toBe('Nun só idioma');
    expect(problem('rules')).toBe('Nun só idioma');
    expect(problem('schedule')).toBe('Falta');
    expect(el.querySelector('[data-row="MONDAY"]')).toBeNull();
    const missing = [...el.querySelectorAll('[data-missing]')].map((item) => item.getAttribute('data-missing'));
    expect(missing).toEqual(['description', 'contact', 'schedule', 'rules']);
  });

  it('sen fotos, o debuxo que se verá (o suxerido) e sen mapa no despregue, sen mapa', async () => {
    store = setupWizard(NO_MAP);
    await openWizard(store, complete());
    const { el } = await render([drawing('pool', 'Piscina'), drawing('football_field', 'Campo de fútbol')]);
    const thumbs = [...el.querySelectorAll<HTMLImageElement>('.f-wizard-review__thumbs img')];
    expect(thumbs.map((img) => img.alt)).toEqual(['Debuxo: Campo de fútbol']);
    expect(el.textContent).toContain('Sen fotos: vese o debuxo «Campo de fútbol».');
    expect(el.querySelector('furar-map')).toBeNull();
  });
});

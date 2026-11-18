// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend, expectRequest } from '../../../../../testing/api';
import {
  WIZARD_BASE,
  WIZARD_NOTES,
  renderWizardStep,
  setupWizard,
  startWizard,
} from '../../../../../testing/facility-wizard';
import { definition } from '../../../../../testing/fixtures';
import { STEP } from '../wizard';
import type { WizardStore } from '../wizard.store';
import { ContactStepComponent } from './contact-step.component';
import { LocationStepComponent } from './location-step.component';
import { RulesStepComponent } from './rules-step.component';

const facilityField = (required: boolean) =>
  definition({
    code: 'pitch',
    label: { gl: 'Céspede', es: 'Césped' },
    usages: [{ target: 'FACILITY', required, visibility: 'PUBLIC' }],
  });

describe('Pasos «Ubicación», «Contacto e servizos» e «Normas» do asistente', () => {
  let store: WizardStore;

  afterEach(() => backend().verify());

  it('ubicación: os erros do enderezo vense ao pulsar «Seguinte»', async () => {
    store = setupWizard();
    await startWizard(store);
    store.goTo(STEP.location);
    const { el, stable } = await renderWizardStep(LocationStepComponent);
    // Sen chincheta, as instalacións do concello centran o mapa.
    (await expectRequest(`${WIZARD_BASE}/facilities?page=0&size=100`)).flush({ items: [], totalItems: 0 });
    expect(el.textContent).not.toContain('Campo obrigatorio');
    expect(el.querySelector('furar-required-mark')).not.toBeNull();
    expect(el.querySelectorAll('[aria-invalid="true"]')).toHaveLength(0);
    expect(await store.next()).toBe(false);
    await stable();
    expect(el.querySelectorAll('[aria-invalid="true"]').length).toBeGreaterThan(0);
  });

  it('contacto: todo opcional; o mal escrito avisa ao seguir e os servizos marcan o formulario', async () => {
    store = setupWizard();
    await startWizard(store);
    store.goTo(STEP.contact);
    const { el, type, click, stable } = await renderWizardStep(ContactStepComponent);
    expect(el.querySelector('[data-optional]')?.textContent?.trim()).toBe(
      'Todo opcional: podes enchelo agora ou despois, na ficha.',
    );
    expect(el.querySelector('#wizard-own')).toBeNull();
    // Sen liñas de axuda nin «?»: o que se admite dío o erro.
    expect(el.querySelectorAll('.f-help, furar-help-tip')).toHaveLength(0);
    // «Servizos» e «Accesibilidade», cada un co seu título (sen un título común).
    expect([...el.querySelectorAll('[data-group] h3')].map((h) => h.textContent?.trim())).toEqual([
      'Servizos',
      'Accesibilidade',
    ]);
    expect(el.querySelector('legend')).toBeNull();
    await type(el.querySelector('#wizard-phone'), 'abc');
    await type(el.querySelector('#wizard-email'), 'sen-arroba');
    expect(el.querySelector('#wizard-phone')?.getAttribute('aria-invalid')).toBeNull();
    expect(await store.next()).toBe(false);
    await stable();
    expect(el.querySelector('#wizard-phone')?.getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector('#wizard-email')?.getAttribute('aria-invalid')).toBe('true');
    // Teléfono e correo nunha cápsula: cada erro, debaixo do seu campo.
    const text = (id: string) => el.querySelector(id)?.textContent?.replace(/\s+/g, ' ').trim();
    expect(text('#wizard-phone-error')).toBe('Só cifras e espazos (de 6 a 20); pode empezar por +.');
    expect(text('#wizard-email-error')).toBe('Escribe un correo completo, coma nome@example.org.');
    expect(el.querySelector('#wizard-ridegaCode')?.getAttribute('aria-invalid')).toBeNull();
    await type(el.querySelector('#wizard-email'), `${'a'.repeat(250)}@x.gal`);
    await type(el.querySelector('#wizard-ridegaCode'), 'x'.repeat(31));
    expect(text('#wizard-email-error')).toBe('Como máximo 254 caracteres.');
    expect(el.querySelector('#wizard-ridegaCode-error')?.textContent?.trim()).toBe('Como máximo 30 caracteres.');

    await click(el.querySelector('[data-group] [role="switch"]'));
    expect(Object.values(store.amenities.getRawValue()).filter(Boolean)).toHaveLength(1);
    expect(store.amenities.dirty).toBe(true);
  });

  it('contacto cun campo propio obrigatorio: xa non é opcional e o campo vai en «Datos propios»', async () => {
    store = setupWizard();
    await startWizard(store, { definitions: [facilityField(true)] });
    store.goTo(STEP.contact);
    const { el } = await renderWizardStep(ContactStepComponent);
    expect(el.querySelector('[data-optional]')).toBeNull();
    expect(el.textContent).not.toContain('Campo obrigatorio');
    expect(el.querySelector('#wizard-own')?.textContent?.trim()).toBe('Datos propios');
    expect(el.querySelector('furar-custom-fields-form')?.textContent).toContain('Céspede');
  });

  it('normas: co pago igual para todas, só as normas', async () => {
    store = setupWizard();
    await startWizard(store);
    store.goTo(STEP.rules);
    const { el } = await renderWizardStep(RulesStepComponent);
    expect(el.querySelector('#wizard-house-rules')).not.toBeNull();
    expect(el.querySelector('label[for="wizard-house-rules"]')?.classList.contains('sr-only')).toBe(false);
    expect(el.querySelector('[role="tablist"]')).toBeNull();
    expect(el.querySelector('#wizard-payment')).toBeNull();
  });

  it('normas e pago: dúas pestanas, cada unha co seu; a do pago co texto por defecto do concello', async () => {
    store = setupWizard();
    await startWizard(store, { notes: { ...WIZARD_NOTES, scope: 'FACILITY' } });
    store.goTo(STEP.rules);
    const { el, click, stable } = await renderWizardStep(RulesStepComponent);
    const tabs = () => [...el.querySelectorAll<HTMLElement>('[role="tab"]')];
    expect(el.querySelector('[role="tablist"]')?.getAttribute('aria-label')).toBe('Normas e pago');
    expect(tabs().map((tab) => tab.textContent?.trim())).toEqual(['Normas', 'Pago']);
    expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
    // Na de normas, só as normas (a pestana xa di o título: o rótulo, só para lectores).
    expect(el.querySelector('#wizard-house-rules')).not.toBeNull();
    expect(el.querySelector('label[for="wizard-house-rules"]')?.classList.contains('sr-only')).toBe(true);
    expect(el.querySelector('#wizard-payment')).toBeNull();
    expect(el.querySelector('h3')).toBeNull();

    await click(tabs()[1]);
    expect(el.querySelector('#wizard-house-rules')).toBeNull();
    // A axuda vai no «?» do rótulo, co texto por defecto do concello.
    expect(el.querySelector('[data-rules-panel="payment"] .f-help')).toBeNull();
    expect(el.querySelector('#wizard-payment-help')?.closest('furar-help-tip')).not.toBeNull();
    expect(el.querySelector('#wizard-payment-help')?.textContent?.trim()).toBe(
      'Nos dous idiomas ou en ningún. Baleiro, vese o texto por defecto: «Furar paga».',
    );
    expect(el.querySelector('#wizard-refund-help')?.textContent?.trim()).toBe(
      'Nos dous idiomas ou en ningún. Baleiro, vese o texto por defecto: «Furar devolve».',
    );
    expect(el.querySelector('.f-tabs__error')).toBeNull();
    await stable();
  });

  it('normas e pago: o erro marca a súa pestana e «Seguinte» leva a ela', async () => {
    store = setupWizard();
    await startWizard(store, { notes: { ...WIZARD_NOTES, scope: 'FACILITY' } });
    store.goTo(STEP.rules);
    const { el, type, click, stable } = await renderWizardStep(RulesStepComponent);
    const tab = (key: string) => el.querySelector<HTMLElement>(`[data-rules-tab="${key}"]`);
    await click(tab('payment'));
    await type(el.querySelector('textarea#wizard-payment'), 'Na oficina');
    expect(store.payment.controls.payment.controls.gl.value).toBe('Na oficina');
    // Desde a pestana de normas (ben), «Seguinte» pasa á do pago, que ten o erro.
    await click(tab('rules'));
    expect(await store.next()).toBe(false);
    await stable();
    expect(tab('payment')?.getAttribute('aria-selected')).toBe('true');
    expect(tab('payment')?.querySelector('.f-tabs__error')).not.toBeNull();
    expect(tab('payment')?.textContent).toContain(', con erros');
    expect(tab('rules')?.querySelector('.f-tabs__error')).toBeNull();
    // Nun só idioma: o erro vese no campo.
    expect(el.querySelector('#wizard-payment-error')).not.toBeNull();
    // Pódese volver ás normas: non se salta á outra pestana sen outro «Seguinte».
    await click(tab('rules'));
    expect(tab('rules')?.getAttribute('aria-selected')).toBe('true');
  });
});

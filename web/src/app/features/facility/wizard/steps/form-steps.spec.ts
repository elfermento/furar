// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend } from '../../../../../testing/api';
import { WIZARD_NOTES, renderWizardStep, setupWizard, startWizard } from '../../../../../testing/facility-wizard';
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
    expect(el.querySelector('furar-required-note')).not.toBeNull();
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
    await type(el.querySelector('#wizard-phone'), 'abc');
    await type(el.querySelector('#wizard-email'), 'sen-arroba');
    expect(el.querySelector('#wizard-phone')?.getAttribute('aria-invalid')).toBeNull();
    expect(await store.next()).toBe(false);
    await stable();
    expect(el.querySelector('#wizard-phone')?.getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector('#wizard-email')?.getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector('#wizard-ridegaCode')?.getAttribute('aria-invalid')).toBeNull();

    await click(el.querySelector('[data-group] input[type="checkbox"]'));
    expect(Object.values(store.amenities.getRawValue()).filter(Boolean)).toHaveLength(1);
    expect(store.amenities.dirty).toBe(true);
  });

  it('contacto cun campo propio obrigatorio: xa non é opcional e o campo vai en «Datos propios»', async () => {
    store = setupWizard();
    await startWizard(store, { definitions: [facilityField(true)] });
    store.goTo(STEP.contact);
    const { el } = await renderWizardStep(ContactStepComponent);
    expect(el.querySelector('[data-optional]')).toBeNull();
    expect(el.querySelector('furar-required-note')).not.toBeNull();
    expect(el.querySelector('#wizard-own')?.textContent?.trim()).toBe('Datos propios');
    expect(el.querySelector('furar-custom-fields-form')?.textContent).toContain('Céspede');
  });

  it('normas: co pago igual para todas, só as normas', async () => {
    store = setupWizard();
    await startWizard(store);
    store.goTo(STEP.rules);
    const { el } = await renderWizardStep(RulesStepComponent);
    expect(el.querySelector('#wizard-house-rules')).not.toBeNull();
    expect(el.querySelector('h3#wizard-payment-title')).toBeNull();
  });

  it('normas e pago: co pago por instalación, o pago e a devolución co texto por defecto do concello', async () => {
    store = setupWizard();
    await startWizard(store, { notes: { ...WIZARD_NOTES, scope: 'FACILITY' } });
    store.goTo(STEP.rules);
    const { el, type, stable } = await renderWizardStep(RulesStepComponent);
    expect(el.querySelector('h3#wizard-payment-title')?.textContent?.trim()).toBe('Pago e devolución');
    expect(el.querySelector('#wizard-payment-help')?.textContent?.trim()).toBe('Por defecto: «Furar paga»');
    expect(el.querySelector('#wizard-refund-help')?.textContent?.trim()).toBe('Por defecto: «Furar devolve»');

    await type(el.querySelector('textarea#wizard-payment'), 'Na oficina');
    expect(store.payment.controls.payment.controls.gl.value).toBe('Na oficina');
    expect(await store.next()).toBe(false);
    await stable();
    // Nun só idioma: o erro vese tras «Seguinte».
    expect(el.querySelector('#wizard-payment-error')).not.toBeNull();
  });
});

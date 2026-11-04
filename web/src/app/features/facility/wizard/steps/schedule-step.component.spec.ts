// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend } from '../../../../../testing/api';
import { renderWizardStep, setupWizard, startWizard } from '../../../../../testing/facility-wizard';
import { STEP } from '../wizard';
import type { WizardStore } from '../wizard.store';
import { ScheduleStepComponent } from './schedule-step.component';

describe('Paso «Horario» do asistente', () => {
  let store: WizardStore;

  beforeEach(async () => {
    store = setupWizard();
    await startWizard(store);
    store.goTo(STEP.schedule);
  });

  afterEach(() => backend().verify());

  async function render() {
    const view = await renderWizardStep(ScheduleStepComponent);
    const mode = (text: string) =>
      [...view.el.querySelectorAll<HTMLButtonElement>('[role="radio"]')].find((b) => b.textContent?.trim() === text)!;
    const error = () => view.el.querySelector('.f-field-error')?.textContent?.trim();
    return { ...view, mode, error };
  }

  it('«Todo o ano» co mesmo horario todos os días; «Engadir tempada» explica que vai na ficha', async () => {
    const { el, click, mode } = await render();
    expect(el.textContent).toContain('Todo o ano');
    expect(mode('Mesmo horario todos os días').getAttribute('aria-checked')).toBe('true');
    expect(el.querySelectorAll('furar-time-picker')).toHaveLength(2);
    const add = el.querySelector<HTMLButtonElement>('[aria-controls="wizard-season-hint"]')!;
    const hint = el.querySelector<HTMLElement>('#wizard-season-hint')!;
    expect(hint.hidden).toBe(true);
    await click(add);
    expect(add.getAttribute('aria-expanded')).toBe('true');
    expect(hint.hidden).toBe(false);
  });

  it('o tramo único que remata antes de empezar avisa ao tocalo', async () => {
    const { error, stable } = await render();
    store.sameHours.setValue({ startMinute: 900, endMinute: 600 });
    await stable();
    expect(error()).toBeUndefined();
    store.sameHours.markAsTouched();
    store.forms.touch();
    await stable();
    expect(error()).toBe('O tramo 1 ten que rematar despois de empezar.');
  });

  it('«Por días»: a semana coas filas da ficha; sen ningún día aberto, avisa ao seguir', async () => {
    const { el, click, mode, error, stable } = await render();
    await click(mode('Por días'));
    expect(store.scheduleMode.value).toBe('DAYS');
    const rows = el.querySelectorAll('.f-wizard-week furar-facility-day-row');
    expect(rows).toHaveLength(8);
    expect(rows[7].textContent).toContain('Festivos');
    // O tramo único xa non se ve: as horas van en cada día.
    expect([...el.querySelectorAll('furar-time-picker')].every((picker) => picker.closest('.f-wizard-week'))).toBe(
      true,
    );

    store.week().controls.week.controls.forEach((day) => day.controls.open.setValue(false));
    await stable();
    expect(error()).toBeUndefined();
    expect(await store.next()).toBe(false);
    await stable();
    expect(error()).toBe('Abre polo menos un día.');
  });
});

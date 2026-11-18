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
    const cards = () => [...view.el.querySelectorAll<HTMLElement>('[data-season]')];
    const mode = (card: HTMLElement, text: string) =>
      [...card.querySelectorAll<HTMLButtonElement>('[role="radio"]')].find((b) => b.textContent?.trim() === text)!;
    const errors = (card: HTMLElement) =>
      [...card.querySelectorAll('.f-field-error')].map((error) => error.textContent?.replace(/\s+/g, ' ').trim());
    return { ...view, cards, mode, errors };
  }

  it('unha tempada «Nova tempada» co nome baleiro, «Ata» apagado e o mesmo horario todos os días', async () => {
    const { el, cards, mode } = await render();
    const [card] = cards();
    expect(cards()).toHaveLength(1);
    expect(card.querySelector('h3')?.textContent?.trim()).toBe('Nova tempada');
    expect(card.querySelector<HTMLInputElement>('[data-season-name]')?.value).toBe('');
    const hasEnd = card.querySelector('[data-season-has-end] [role="switch"]')!;
    expect(hasEnd.getAttribute('aria-checked')).toBe('false');
    expect(hasEnd.getAttribute('aria-label') ?? card.textContent).toContain('Con data de fin');
    // Co interruptor apagado, a data de fin non está (nin desactivada).
    expect(card.querySelector('[data-season-end]')).toBeNull();
    expect(mode(card, 'Igual cada día').getAttribute('aria-checked')).toBe('true');
    expect(card.querySelectorAll('furar-time-picker')).toHaveLength(2);
    expect(card.textContent).toContain('Abre');
    expect(card.textContent).toContain('Pecha');
    // Unha soa tempada non se pode quitar; «Engadir tempada» é un botón normal.
    expect(card.querySelector('[data-season-delete]')).toBeNull();
    expect(el.querySelector('[data-season-add]')?.textContent?.trim()).toBe('Engadir tempada');
  });

  it('o nome é obrigatorio: ao seguir sen el, o erro vai no campo; ao escribilo, é o título', async () => {
    const { cards, errors, stable, type } = await render();
    expect(await store.next()).toBe(false);
    await stable();
    const [card] = cards();
    expect(errors(card)).toEqual(['Este campo é obrigatorio.']);
    expect(card.querySelector('[data-season-name]')?.getAttribute('aria-invalid')).toBe('true');
    await type(card.querySelector('[data-season-name]'), 'Inverno');
    expect(card.querySelector('h3')?.textContent?.trim()).toBe('Inverno');
    expect(errors(card)).toEqual([]);
  });

  it('acender «Ata» pide a data de fin', async () => {
    const { cards, click, errors, stable } = await render();
    const [card] = cards();
    await click(card.querySelector('[data-season-has-end] [role="switch"]'));
    expect(store.seasons()[0].form.controls.noEnd.value).toBe(false);
    // Ao acender aparece baleira, obrigatoria e despois do interruptor (o seguinte no tabulador).
    const end = card.querySelector<HTMLButtonElement>('[data-season-end] button')!;
    expect(end.disabled).toBe(false);
    expect(end.compareDocumentPosition(card.querySelector('[data-season-has-end] [role="switch"]')!)).toBe(
      Node.DOCUMENT_POSITION_PRECEDING,
    );
    store.seasons()[0].form.controls.name.setValue('Todo o ano');
    expect(await store.next()).toBe(false);
    await stable();
    expect(errors(card)).toEqual(['Este campo é obrigatorio.']);
  });

  it('«Engadir tempada» pon outra tarxeta co foco no nome; se as datas pisan a anterior, o erro vai no seu «Desde»', async () => {
    const { el, cards, click, errors, stable } = await render();
    store.seasons()[0].form.controls.name.setValue('Todo o ano');
    await click(el.querySelector('[data-season-add]'));
    expect(cards()).toHaveLength(2);
    const second = cards()[1];
    expect(second.querySelector('h3')?.textContent?.trim()).toBe('Nova tempada');
    expect(document.activeElement).toBe(second.querySelector('[data-season-name]'));
    expect(second.querySelector('[data-season-has-end] [role="switch"]')?.getAttribute('aria-checked')).toBe('true');
    expect(second.querySelector('[data-season-copy]')?.textContent).toContain('Todo o ano');
    // Con dúas, cada unha pódese quitar.
    expect(cards().every((card) => card.querySelector('[data-season-delete]'))).toBe(true);

    const summer = store.seasons()[1];
    summer.form.controls.name.setValue('Verán');
    summer.form.controls.startDate.setValue('2026-07-01');
    summer.form.controls.endDate.setValue('2026-08-31');
    await stable();
    expect(errors(second)).toEqual([
      'Estas datas pisan a tempada «Todo o ano». Para cambiar só uns días soltos, usa unha excepción.',
    ]);
    expect(second.querySelector('[data-season-start] button')?.getAttribute('aria-invalid')).toBe('true');
    expect(store.ok().schedule).toBe(false);

    await click(second.querySelector('[data-season-delete] button'));
    expect(cards()).toHaveLength(1);
    expect(store.ok().schedule).toBe(true);
  });

  it('o tramo único que remata antes de empezar avisa ao tocalo', async () => {
    const { cards, errors, stable } = await render();
    const season = store.seasons()[0];
    season.sameHours.setValue({ startMinute: 900, endMinute: 600 });
    await stable();
    expect(errors(cards()[0])).toEqual([]);
    season.sameHours.markAsTouched();
    store.forms.touch();
    await stable();
    expect(errors(cards()[0])).toEqual(['O tramo 1 ten que rematar despois de empezar.']);
  });

  it('«Por días»: a semana coas filas da ficha; sen ningún día aberto, avisa ao seguir', async () => {
    const { cards, click, mode, errors, stable } = await render();
    const season = store.seasons()[0];
    season.form.controls.name.setValue('Todo o ano');
    const [card] = cards();
    await click(mode(card, 'Por días'));
    expect(season.mode.value).toBe('DAYS');
    const rows = card.querySelectorAll('.f-season-week furar-facility-day-row');
    expect(rows).toHaveLength(8);
    expect(rows[7].textContent).toContain('Festivos');
    expect(card.querySelector('.f-season-week')?.getAttribute('aria-label')).toBe('Semana de Todo o ano');
    // O tramo único xa non se ve: as horas van en cada día.
    expect([...card.querySelectorAll('furar-time-picker')].every((picker) => picker.closest('.f-season-week'))).toBe(
      true,
    );

    season.week.controls.forEach((day) => day.controls.open.setValue(false));
    await stable();
    expect(errors(card)).toEqual([]);
    expect(await store.next()).toBe(false);
    await stable();
    expect(errors(card)).toEqual(['Abre polo menos un día.']);
  });
});

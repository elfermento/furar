// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { ApiProblem } from '../../../core/errors/api-problem';
import { SELECT_OPTIONS, definition } from '../../../../testing/fixtures';
import { provideTestI18n } from '../../../../testing/i18n';
import type { CustomFieldDefinitionDraft } from '../custom-field.types';
import { CustomFieldEditorComponent } from './custom-field-editor.component';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { pickOption, selectText } from '../../../../testing/select';

describe('CustomFieldEditorComponent', () => {
  async function render(def: ReturnType<typeof definition> | null = null) {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(CustomFieldEditorComponent);
    fixture.componentRef.setInput('definition', def);
    const saved: CustomFieldDefinitionDraft[] = [];
    fixture.componentInstance.save.subscribe((draft) => saved.push(draft));
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const type = async (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`);
      if (!input) throw new Error(`sen #${id}`);
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    const stable = () => fixture.whenStable();
    /** Escolle no despregable `#id` a opción co texto `label`. */
    const pick = async (id: string, label: string) => {
      await pickOption(el.querySelector(`#${id}`)!, label, stable);
      await stable();
    };
    /** Abre o despregable, mira se a opción está desactivada e pécheo. */
    const optionDisabled = async (id: string, label: string) => {
      el.querySelector<HTMLButtonElement>(`#${id}`)!.click();
      await stable();
      const option = [...document.querySelectorAll('.f-select__option')].find((o) => o.textContent?.trim() === label);
      const disabled = option?.getAttribute('aria-disabled') === 'true';
      el.querySelector<HTMLButtonElement>(`#${id}`)!.click();
      await stable();
      return disabled;
    };
    const submit = async () => {
      el.querySelector('form')?.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve));
    };
    const click = async (selector: string) => {
      const target = el.querySelector<HTMLElement>(selector);
      if (!target) throw new Error(`sen ${selector}`);
      target.click();
      await fixture.whenStable();
    };
    /** Escolle o sitio no selector «Onde se usa». */
    const place = async (label: string) => {
      const option = [...el.querySelectorAll<HTMLButtonElement>('[role="radio"]')].find(
        (b) => b.textContent?.trim() === label,
      );
      if (!option) throw new Error(`sen o sitio ${label}`);
      option.click();
      await fixture.whenStable();
    };
    return { fixture, el, saved, type, pick, optionDisabled, submit, click, place };
  }

  it('non envía se falta algo: resumo anunciado e foco no primeiro erro', async () => {
    const { el, saved, submit } = await render();
    await submit();
    expect(saved).toHaveLength(0);
    expect(el.querySelector('[role="alert"] p')?.textContent).toContain('erro');
    expect(document.activeElement?.id).toBe('def-labelGl');
    expect(el.querySelector('#def-labelGl')?.getAttribute('aria-describedby')).toBe('def-labelGl-error');
  });

  it('a vista previa pinta o campo en gl e es mentres se escribe', async () => {
    const { el, type, pick } = await render();
    await type('def-labelGl', 'Número de federado');
    await type('def-labelEs', 'Número de federado (es)');
    const previews = el.querySelectorAll('furar-custom-field-preview [lang]');
    expect(previews[0]?.textContent).toContain('Número de federado');
    expect(previews[1]?.textContent).toContain('Número de federado (es)');
    // Coa etiqueta escrita (baleira, o campo da vista previa queda sen nome: rexistrado aparte).
    await expectNoAxeViolations(el);
    await pick('def-type', 'Si ou non');
    expect(el.querySelectorAll('furar-custom-field-preview input[type="radio"]').length).toBe(4);
  });

  it('as regras de data escóllense no calendario propio e a máxima non pode ir antes da mínima', async () => {
    const { el, pick, fixture } = await render();
    await pick('def-type', 'Data');
    expect(el.querySelector('input[type="date"]')).toBeNull();
    expect(el.querySelector('label[for="rule-minDate"]')?.textContent).toContain('Data mínima');
    const rules = fixture.componentInstance['form'].controls.rules.controls;
    rules.minDate.setValue('2026-06-10');
    rules.maxDate.setValue('2026-06-15');
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('furar-date-field button#rule-maxDate')!.click();
    await fixture.whenStable();
    expect(document.querySelector('td[data-date="2026-06-09"]')?.getAttribute('aria-disabled')).toBe('true');
    document.querySelector<HTMLElement>('td[data-date="2026-06-20"]')!.click();
    await fixture.whenStable();
    expect(rules.maxDate.value).toBe('2026-06-20');
  });

  it('envía o borrador válido; as opcións aparecen para as seleccións', async () => {
    const { el, saved, type, pick, submit, fixture, place } = await render();
    await place('Instalación');
    await type('def-labelGl', 'Deporte');
    await type('def-labelEs', 'Deporte');
    await type('def-code', 'sport');
    await pick('def-type', 'Unha opción da lista');
    expect(el.querySelector('furar-options-editor')).not.toBeNull();
    el.querySelector<HTMLButtonElement>('#option-add')?.click();
    await fixture.whenStable();
    await type('option-0-code', 'tennis');
    await type('option-0-labelGl', 'Tenis');
    await type('option-0-labelEs', 'Tenis');
    await submit();
    expect(saved).toHaveLength(1);
    expect(saved[0]).toMatchObject({
      code: 'sport',
      type: 'SELECT',
      options: [{ code: 'tennis', inactive: false }],
      usages: [{ target: 'FACILITY', required: false, visibility: 'STAFF' }],
    });
  });

  it('onde se usa: un sitio, co seu obrigatorio e quen o ve; na reserva sen «Público»', async () => {
    const { el, saved, type, pick, submit, click, place, optionDisabled } = await render();
    await type('def-labelGl', 'Federación');
    await type('def-labelEs', 'Federación');
    await type('def-code', 'federation');
    const group = el.querySelector('[role="radiogroup"]');
    expect(group?.getAttribute('aria-labelledby')).toBe('def-place');
    expect([...el.querySelectorAll('[role="radio"]')].map((b) => b.textContent?.trim())).toEqual([
      'Concello',
      'Instalación',
      'Reserva',
    ]);
    expect(el.querySelector('[role="radio"][aria-checked="true"]')?.textContent?.trim()).toBe('Concello');
    await click('#def-required');
    await pick('def-visibility', 'Titular e persoal');
    await expectNoAxeViolations(el);
    await submit();
    expect(saved.at(-1)?.usages).toEqual([{ target: 'MUNICIPALITY', required: true, visibility: 'OWNER' }]);

    await pick('def-visibility', 'Público');
    await place('Reserva');
    expect(selectText(el.querySelector('#def-visibility')!)).toBe('A persoa que reserva e o persoal');
    expect(await optionDisabled('def-visibility', 'Só o persoal')).toBe(false);
    await pick('def-visibility', 'Só o persoal');
    await submit();
    expect(saved.at(-1)?.usages).toEqual([{ target: 'BOOKING', required: true, visibility: 'STAFF' }]);
    // axe sobre o editor enteiro tarda co equipo cargado: marxe sobre os 5 s por defecto.
  }, 15_000);

  it('en edición marca o sitio gardado co seu obrigatorio e visibilidade; con valores, bloqueado', async () => {
    const { el } = await render(
      definition({ hasValues: true, usages: [{ target: 'FACILITY', required: true, visibility: 'PUBLIC' }] }),
    );
    expect(el.querySelector('[role="radio"][aria-checked="true"]')?.textContent?.trim()).toBe('Instalación');
    expect(el.querySelector('[role="radiogroup"]')?.getAttribute('aria-disabled')).toBe('true');
    expect(el.querySelector('[role="radiogroup"]')?.getAttribute('aria-describedby')).toBe('def-place-lock');
    expect(el.querySelector('#def-place-lock')?.textContent).toContain('non se pode cambiar de sitio');
    expect(el.querySelector<HTMLInputElement>('#def-required')?.checked).toBe(true);
    expect(selectText(el.querySelector('#def-visibility')!)).toBe('Público');
  });

  it('en edición código e tipo están bloqueados; as opcións, táboa con desactivar e eliminar', async () => {
    const options = SELECT_OPTIONS.map((o, i) => ({ ...o, hasValues: i === 0 }));
    const { el, fixture } = await render(definition({ type: 'SELECT', options }));
    expect(el.querySelector<HTMLInputElement>('#def-code')?.disabled).toBe(true);
    expect(el.querySelector<HTMLButtonElement>('#def-type')?.disabled).toBe(true);
    expect(el.querySelector('#def-type')?.getAttribute('aria-describedby')).toBe('def-type-help');
    expect(el.querySelector('#option-0-code')?.hasAttribute('readonly')).toBe(true);
    expect(el.querySelector('#option-0-labelGl')?.getAttribute('aria-label')).toBe('Opción 1 en galego');
    const named = (name: string) =>
      [...el.querySelectorAll<HTMLButtonElement>('furar-options-editor button')].find(
        (b) => b.getAttribute('aria-label') === name,
      );
    // A desactivada leva «Activar»; as outras, «Desactivar».
    expect(named('Activar a opción «Vella»')).toBeDefined();
    named('Desactivar a opción «Ximnasio»')?.click();
    await fixture.whenStable();
    expect(named('Activar a opción «Ximnasio»')).toBeDefined();
    // Eliminar unha con valores: o diálogo di que non e ofrece desactivala.
    named('Eliminar a opción «Piscina»')?.click();
    const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    expect(dialog()?.textContent).toContain('Non se pode eliminar «Piscina»');
    dialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    await vi.waitFor(() => expect(named('Activar a opción «Piscina»')).toBeDefined());
    // Unha nova quítase sen preguntar.
    el.querySelector<HTMLButtonElement>('#option-add')?.click();
    await fixture.whenStable();
    expect(el.querySelectorAll('#option-3-code').length).toBe(1);
    named('Eliminar a opción «4»')?.click();
    await fixture.whenStable();
    expect(el.querySelector('#option-3-code')).toBeNull();
  });

  it('pinta os erros da API no seu campo', async () => {
    const { fixture, el } = await render();
    fixture.componentRef.setInput(
      'problem',
      new ApiProblem(409, 'customfield.code-taken', { code: 'phone' }, [], 't-1', 'x'),
    );
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    expect(el.querySelector('#def-code-error')?.textContent).toContain('«phone»');
    expect(el.textContent).toContain('t-1');
  });

  it('dato persoal: aviso do art. 9 ligado á caixa, PUBLIC desactivado e paso a STAFF', async () => {
    const { el, pick, optionDisabled, click, place } = await render();
    await place('Instalación');
    await pick('def-visibility', 'Público');
    const checkbox = el.querySelector<HTMLInputElement>('#def-personalData');
    const select = () => el.querySelector<HTMLButtonElement>('#def-visibility');
    expect(el.querySelector('#def-personal-warning')?.textContent?.trim()).toBe('');
    await click('#def-personalData');
    expect(checkbox?.getAttribute('aria-describedby')).toBe('def-personal-help def-personal-warning');
    expect(el.querySelector('#def-personal-warning')?.textContent).toContain('saúde');
    expect(await optionDisabled('def-visibility', 'Público')).toBe(true);
    expect(select()?.getAttribute('aria-describedby')).toBe('def-visibility-note');
    expect(selectText(select()!)).toBe('Só persoal');
    await click('#def-personalData');
    expect(await optionDisabled('def-visibility', 'Público')).toBe(false);
  });

  it('o 422 customfield.personal-data-public vai a «Quen o ve», con aria-describedby e foco', async () => {
    const { fixture, el, type, pick } = await render();
    await type('def-labelGl', 'Teléfono');
    await type('def-labelEs', 'Teléfono');
    await type('def-code', 'phone');
    await pick('def-visibility', 'Público');
    fixture.componentRef.setInput(
      'problem',
      new ApiProblem(422, 'customfield.personal-data-public', {}, [], 't-2', 'x'),
    );
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    const select = el.querySelector('#def-visibility');
    expect(select?.getAttribute('aria-invalid')).toBe('true');
    expect(select?.getAttribute('aria-describedby')).toBe('def-visibility-error');
    expect(el.querySelector('#def-visibility-error')?.textContent).toContain('non pode ser público');
    expect(document.activeElement).toBe(select);
  });

  it('cancelar avisa ao pai', async () => {
    const { fixture, el } = await render();
    let cancelled = false;
    fixture.componentInstance.cancelled.subscribe(() => (cancelled = true));
    [...el.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes('Cancelar'))?.click();
    expect(cancelled).toBe(true);
  });
});

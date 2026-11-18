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
    /** Escribe nos dous idiomas dun `furar-bilingual-field` (conmutador GL/ES e un só campo). */
    const typeBoth = async (id: string, gl: string, es: string) => {
      const field = el.querySelector(`#${id}`)?.closest('furar-bilingual-field');
      if (!field) throw new Error(`sen #${id}`);
      for (const [lang, value] of [
        ['gl', gl],
        ['es', es],
      ] as const) {
        field.querySelector<HTMLButtonElement>(`button[data-lang="${lang}"]`)!.click();
        await fixture.whenStable();
        await type(id, value);
      }
    };
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
    /** As opcións do despregable, na orde en que se ven. */
    const optionLabels = async (id: string) => {
      el.querySelector<HTMLButtonElement>(`#${id}`)!.click();
      await stable();
      const labels = [...document.querySelectorAll('.f-select__option')].map((o) => o.textContent?.trim());
      el.querySelector<HTMLButtonElement>(`#${id}`)!.click();
      await stable();
      return labels;
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
    const place = (label: string) => pick('def-target', label);
    return { fixture, el, saved, type, typeBoth, pick, optionDisabled, optionLabels, submit, click, place };
  }

  it('non envía se falta algo: resumo anunciado e foco no primeiro erro', async () => {
    const { el, saved, submit } = await render();
    await submit();
    expect(saved).toHaveLength(0);
    expect(el.querySelector('[role="alert"] p')?.textContent).toContain('erro');
    expect(document.activeElement?.id).toBe('def-label');
    expect(el.querySelector('#def-label')?.getAttribute('aria-describedby')).toBe('def-label-error');
  });

  it('coa etiqueta baleira, a vista previa usa «Etiqueta» no ton dun placeholder; nunca un campo sen nome', async () => {
    const { el, typeBoth, pick } = await render();
    for (const type of [null, 'Número', 'Si ou non', 'Unha opción da lista', 'Data']) {
      if (type) await pick('def-type', type);
      const fields = [...el.querySelectorAll<HTMLElement>('furar-custom-field-preview furar-custom-field')];
      expect(fields).toHaveLength(2);
      for (const field of fields) {
        expect(field.hasAttribute('data-label-empty')).toBe(true);
        expect(field.querySelector('.f-label, .label, .f-switch__label')?.textContent?.trim()).toBe('Etiqueta');
      }
      await expectNoAxeViolations(el.querySelector<HTMLElement>('furar-custom-field-preview')!);
    }
    await typeBoth('def-label', 'Número de federado', 'Número de federado (es)');
    const first = el.querySelector<HTMLElement>('furar-custom-field-preview furar-custom-field')!;
    expect(first.hasAttribute('data-label-empty')).toBe(false);
    expect(first.textContent).toContain('Número de federado');
  });

  it('a vista previa pinta o campo en gl e es mentres se escribe', async () => {
    const { el, typeBoth, pick } = await render();
    await typeBoth('def-label', 'Número de federado', 'Número de federado (es)');
    const previews = el.querySelectorAll('furar-custom-field-preview [lang]');
    expect(previews[0]?.textContent).toContain('Número de federado');
    expect(previews[1]?.textContent).toContain('Número de federado (es)');
    // Coa etiqueta escrita; baleira, o rótulo provisional «Etiqueta» (caso de arriba).
    await expectNoAxeViolations(el);
    await pick('def-type', 'Si ou non');
    // Si/non opcional: un despregable por idioma (obrigatorio, sería un interruptor).
    expect(el.querySelectorAll('furar-custom-field-preview furar-select').length).toBe(2);
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
    const { el, saved, type, typeBoth, pick, submit, fixture, place } = await render();
    await place('Instalación');
    await typeBoth('def-label', 'Deporte', 'Deporte');
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

  it('onde se usa: un sitio, co seu obrigatorio e quen o ve; na reserva sen «Todo o mundo»', async () => {
    const { el, saved, type, typeBoth, pick, submit, click, place, optionDisabled, optionLabels } = await render();
    await typeBoth('def-label', 'Federación', 'Federación');
    await type('def-code', 'federation');
    // Onde se usa: un despregable (Concello · Instalación · Reserva), no grupo con «Quen o ve».
    expect(el.querySelector('label[for="def-target"]')?.textContent?.trim()).toBe('Onde se usa');
    expect(await optionLabels('def-target')).toEqual(['Concello', 'Instalación', 'Reserva']);
    expect(selectText(el.querySelector('#def-target')!)).toBe('Concello');
    expect(el.querySelector('#def-target')?.closest('.f-field-group')?.querySelector('#def-visibility')).not.toBeNull();
    // No concello o «titular» é o persoal: só «Todo o mundo» e «Só o persoal».
    expect(await optionLabels('def-visibility')).toEqual(['Todo o mundo (web pública)', 'Só o persoal']);
    // Obrigatorio é un interruptor, xunto ao dato persoal.
    expect(el.querySelector('#def-required')?.getAttribute('role')).toBe('switch');
    expect(el.querySelector('[data-def-switches] #def-personalData')).not.toBeNull();
    // Cada interruptor na súa fila (grella dunha columna): o rótulo primeiro e o interruptor ao final.
    const switches = el.querySelector<HTMLElement>('[data-def-switches]')!;
    expect(switches.classList).toContain('grid');
    expect(switches.classList).not.toContain('flex');
    for (const row of switches.querySelectorAll('furar-switch')) {
      expect(row.firstElementChild?.classList).toContain('f-switch__text');
      expect(row.querySelector('.f-switch__extra')?.previousElementSibling?.getAttribute('role')).toBe('switch');
    }
    await click('#def-required');
    expect(el.querySelector('#def-required')?.getAttribute('aria-checked')).toBe('true');
    await expectNoAxeViolations(el);
    await submit();
    expect(saved.at(-1)?.usages).toEqual([{ target: 'MUNICIPALITY', required: true, visibility: 'STAFF' }]);

    await pick('def-visibility', 'Todo o mundo (web pública)');
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
    expect(selectText(el.querySelector('#def-target')!)).toBe('Instalación');
    expect(el.querySelector<HTMLButtonElement>('#def-target')?.disabled).toBe(true);
    expect(el.querySelector('#def-target')?.getAttribute('aria-describedby')).toBe('def-place-lock');
    expect(el.querySelector('#def-place-lock')?.textContent).toContain('non se pode cambiar de sitio');
    expect(el.querySelector('#def-required')?.getAttribute('aria-checked')).toBe('true');
    expect(selectText(el.querySelector('#def-visibility')!)).toBe('Todo o mundo (web pública)');
  });

  it('instalación e «Todo o mundo»: aviso de que se ve na web e nos datos abertos; noutros, non', async () => {
    const { el, pick, place } = await render();
    const tip = () => el.querySelector('[data-def-public-tip]');
    await pick('def-visibility', 'Todo o mundo (web pública)');
    expect(tip()).toBeNull();
    await place('Instalación');
    await pick('def-visibility', 'Todo o mundo (web pública)');
    expect(tip()?.textContent?.trim()).toBe('Vese na web e nos datos abertos; non poñas nomes de persoas.');
    expect(el.querySelector('#def-visibility')?.getAttribute('aria-describedby')).toContain(tip()!.id);
    await pick('def-visibility', 'Só o persoal');
    expect(tip()).toBeNull();
  });

  it('un campo do concello gardado como «titular» segue á vista como «O persoal»', async () => {
    const { el, optionLabels } = await render(
      definition({ usages: [{ target: 'MUNICIPALITY', required: false, visibility: 'OWNER' }] }),
    );
    expect(selectText(el.querySelector('#def-visibility')!)).toBe('O persoal');
    expect(await optionLabels('def-visibility')).toEqual(['Todo o mundo (web pública)', 'O persoal', 'Só o persoal']);
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

  it('dato persoal: un aviso ligado ao interruptor, PUBLIC desactivado e paso a STAFF', async () => {
    const { el, pick, optionDisabled, click, place } = await render();
    await place('Instalación');
    await pick('def-visibility', 'Todo o mundo (web pública)');
    const toggle = el.querySelector<HTMLButtonElement>('#def-personalData');
    const select = () => el.querySelector<HTMLButtonElement>('#def-visibility');
    const warning = () => el.querySelector<HTMLElement>('#def-personal-warning')!;
    expect(toggle?.getAttribute('role')).toBe('switch');
    expect(warning().textContent?.trim()).toBe('');
    await click('#def-personalData');
    expect(toggle?.getAttribute('aria-describedby')).toBe('def-personal-warning');
    expect(warning().querySelectorAll('furar-callout')).toHaveLength(1);
    expect(warning().querySelector('.f-callout__title')?.textContent).toContain('Ao ser un dato persoal');
    expect([...warning().querySelectorAll('li')].map((li) => li.textContent?.trim())).toEqual([
      'Non se mostra nunca na web pública.',
      'Entra na exportación do concello.',
      'Non pidas datos de saúde nin de discapacidade salvo que unha lei o permita, e explica na axuda para que se usa.',
    ]);
    expect(await optionDisabled('def-visibility', 'Todo o mundo (web pública)')).toBe(true);
    expect(select()?.getAttribute('aria-describedby')).toBe('def-visibility-note');
    expect(selectText(select()!)).toBe('Só o persoal');
    await place('Reserva');
    expect(warning().textContent).toContain('Nas reservas bórrase ao anonimizalas.');
    await place('Instalación');
    await click('#def-personalData');
    expect(warning().textContent?.trim()).toBe('');
    expect(await optionDisabled('def-visibility', 'Todo o mundo (web pública)')).toBe(false);
  });

  it('o 422 customfield.personal-data-public vai a «Quen o ve», con aria-describedby e foco', async () => {
    const { fixture, el, type, typeBoth, pick } = await render();
    await typeBoth('def-label', 'Teléfono', 'Teléfono');
    await type('def-code', 'phone');
    await pick('def-visibility', 'Todo o mundo (web pública)');
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

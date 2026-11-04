// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { facility } from '../detail/facility.fixture';
import { renderUses } from '../../../../testing/facility-uses';
import { FACILITY_URL, USES_URL, use } from './uses.fixture';

const input = (root: HTMLElement, selector: string) => root.querySelector<HTMLInputElement>(selector)!;
const errorText = (root: HTMLElement) => [...root.querySelectorAll('.f-field-error')].map((e) => e.textContent?.trim());

describe('Modal dun uso: cada modo valida igual ca o servidor (D1-22)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function addNew() {
    const ui = await renderUses([]);
    await ui.open(null);
    return {
      ...ui,
      submit: ui.apply,
      pickDate: (index: number, day: number) => ui.pickDate(ui.when()!, index, day),
    };
  }

  it('enteira: «Aceptar» deixa o novo pendente; «Gardar» crea co corpo mínimo e recarga a ficha', async () => {
    const { section, form, type, submit, save, stable, dialog } = await addNew();
    expect(dialog()?.querySelector('h2')?.textContent).toContain('Novo uso');
    expect(form().querySelector('.f-help')).toBeNull();
    await type(input(form(), 'input'), '  Pista completa ');
    await submit();
    expect(dialog()).toBeNull();
    backend().expectNone({ method: 'POST', url: USES_URL });
    const tile = section().querySelector<HTMLButtonElement>('[data-use-id="new-0"]')!;
    expect(tile.textContent).toContain('Pista completa');
    expect(tile.textContent).toContain('Sen gardar');
    expect(document.activeElement).toBe(tile);
    await save();
    const post = await expectRequest({ method: 'POST', url: USES_URL });
    expect(post.request.body).toEqual({
      name: 'Pista completa',
      rentalMode: 'WHOLE',
      slotMinutes: 60,
      availability: 'ALWAYS',
      blocksOthers: false,
      weekdays: 127,
      price: { kind: 'NONE' },
      rules: {},
    });
    post.flush(use({ id: 'u9', name: 'Pista completa', slotMinutes: 60 }));
    (await expectRequest(FACILITY_URL)).flush(facility({ missing: ['SCHEDULE'] }));
    await vi.waitFor(async () => {
      await stable();
      expect(section().querySelector('[data-use-id]')).toBeNull();
      expect(section().querySelector('furar-row')?.textContent).toContain('Pista completa');
    });
  });

  it('nome baleiro: erro no campo e o modal segue aberto; Cancelar non deixa nada', async () => {
    const { section, form, submit, dialog, button, click } = await addNew();
    await submit();
    expect(errorText(form())).toContain('Este campo é obrigatorio.');
    expect(errorText(form())).not.toContain('A lonxitude debe estar entre 1 e 100.');
    expect(document.activeElement).toBe(input(form(), 'input'));
    await click(button('Cancelar', dialog()!));
    expect(dialog()).toBeNull();
    expect(section().querySelectorAll('[data-use-id]')).toHaveLength(0);
  });

  it('por partes: dúas partes de saída, nomes obrigatorios e sen repetir; garda as partes en orde', async () => {
    const { form, radio, click, type, submit, save, button } = await addNew();
    await type(input(form(), 'input'), 'Fútbol 7');
    await click(radio('Por partes'));
    const parts = () => [...form().querySelectorAll<HTMLInputElement>('[data-part] input')];
    expect(parts().map((p) => p.getAttribute('aria-label'))).toEqual(['Parte 1', 'Parte 2']);
    await submit();
    expect(errorText(form()).filter((t) => t === 'Este campo é obrigatorio.')).toHaveLength(2);
    await type(parts()[0], 'Campo A');
    await type(parts()[1], 'campo a');
    await submit();
    expect(errorText(form())).toContain('Dúas partes deste uso teñen o mesmo nome: cambia un deles.');
    await type(parts()[1], 'Campo B');
    await click(button('Parte', form()));
    expect(document.activeElement).toBe(parts()[2]);
    await click(form().querySelector<HTMLButtonElement>('[aria-label="Eliminar a parte 3"]')!);
    await expectNoAxeViolations(form());
    await submit();
    await save();
    const post = await expectRequest({ method: 'POST', url: USES_URL });
    expect(post.request.body).toMatchObject({ rentalMode: 'PARTS', parts: [{ name: 'Campo A' }, { name: 'Campo B' }] });
    expect(post.request.body.capacity).toBeUndefined();
    post.flush(use({ id: 'u9', name: 'Fútbol 7', rentalMode: 'PARTS' }));
    (await expectRequest(FACILITY_URL)).flush(facility());
  });

  it('por prazas: obrigatorias e de 2 a 10 000; a duración co segmentado radio', async () => {
    const { form, radio, click, type, submit, save } = await addNew();
    await type(input(form(), 'input'), 'Natación libre');
    await click(radio('Por prazas'));
    await submit();
    expect(errorText(form())).toContain('Indica cantas persoas caben en cada franxa.');
    await type(input(form(), 'input[type="number"]'), '1');
    await submit();
    expect(errorText(form())).toContain('O valor está fóra do rango permitido.');
    await type(input(form(), 'input[type="number"]'), '25');
    await click(radio('30 min'));
    expect(radio('30 min').getAttribute('aria-checked')).toBe('true');
    await expectNoAxeViolations(form());
    await submit();
    await save();
    const post = await expectRequest({ method: 'POST', url: USES_URL });
    expect(post.request.body).toMatchObject({ rentalMode: 'SEATS', capacity: 25, slotMinutes: 30 });
    expect(post.request.body.parts).toBeUndefined();
    post.flush(use({ id: 'u9', rentalMode: 'SEATS', capacity: 25 }));
    (await expectRequest(FACILITY_URL)).flush(facility());
  });

  it('só unhas datas: períodos con dous campos de data, «bloquea» só con datas, ata non antes de desde', async () => {
    const { form, radio, click, type, submit, save, pickDate, when, openWhen, button } = await addNew();
    await type(input(form(), 'input'), 'Xornadas de Samaín');
    await openWhen();
    expect(when()?.querySelector('furar-switch')).toBeNull();
    await click(radio('Só unhas datas'));
    expect(when()?.querySelector('furar-switch')?.textContent).toContain('Bloquea os demais usos');
    await click(button('Listo', when()!));
    expect(when()).toBeNull();
    await submit();
    // Os erros do «Cando» saen baixo o resumo e o panel ábrese para amosalos.
    expect(errorText(form())).toContain('Revisa os campos marcados do uso.');
    expect(errorText(when()!)).toContain('Este campo é obrigatorio.');
    backend().expectNone({ method: 'POST', url: USES_URL });
    const from = await pickDate(0, 10);
    const to = await pickDate(1, 2);
    expect(to >= from).toBe(true);
    await click(when()!.querySelector<HTMLButtonElement>('furar-switch [role="switch"]')!);
    await expectNoAxeViolations(when()!);
    await click(button('Listo', when()!));
    await submit();
    await save();
    const post = await expectRequest({ method: 'POST', url: USES_URL });
    expect(post.request.body).toMatchObject({ availability: 'DATES', blocksOthers: true, periods: [{ from, to }] });
    post.flush(use({ id: 'u9', availability: 'DATES', blocksOthers: true, periods: [{ from, to }] }));
    (await expectRequest(FACILITY_URL)).flush(facility());
  });

  it('dous períodos que se pisan: erro na fila do segundo, igual ca o servidor', async () => {
    const { form, radio, click, type, submit, pickDate, button, when, openWhen } = await addNew();
    await type(input(form(), 'input'), 'Campus');
    await openWhen();
    await click(radio('Só unhas datas'));
    await pickDate(0, 3);
    await pickDate(1, 5);
    await click(button('Período', when()!));
    await pickDate(2, 4);
    await pickDate(3, 4);
    await click(button('Listo', when()!));
    await submit();
    expect(errorText(when()!)).toEqual(['Os períodos deste uso non se poden pisar.']);
    backend().expectNone({ method: 'POST', url: USES_URL });
  });
});

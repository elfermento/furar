// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { facility } from '../detail/facility.fixture';
import { renderUses } from '../../../../testing/facility-uses';
import { FACILITY_URL, USES_URL, use } from './uses.fixture';
import { useBody, useForm, useSports } from './use-form';

const input = (root: HTMLElement, selector: string) => root.querySelector<HTMLInputElement>(selector)!;
const errorText = (root: HTMLElement) =>
  [...root.querySelectorAll('.f-field-error')].map((e) => e.textContent?.replace(/\s+/g, ' ').trim());

describe('Modal dun uso: cada modo valida igual ca o servidor', () => {
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
    // Nome, «Alúgase» (despregable) e «Cando» na mesma cápsula.
    const top = form().querySelector('[data-use-top]')!;
    expect(top.matches('furar-field-group')).toBe(true);
    expect(top.querySelector('input')?.id).toMatch(/-name$/);
    expect(top.querySelector('[data-use-mode] [aria-haspopup="listbox"]')).not.toBeNull();
    expect(top.querySelector('[data-use-when]')).not.toBeNull();
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
      sports: [],
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
    expect(errorText(form()).join()).not.toContain('A lonxitude debe estar entre 1 e 100.');
    expect(document.activeElement).toBe(input(form(), 'input'));
    await click(button('Cancelar', dialog()!));
    expect(dialog()).toBeNull();
    expect(section().querySelectorAll('[data-use-id]')).toHaveLength(0);
  });

  it('por partes: dúas partes de saída, nomes obrigatorios e sen repetir; garda as partes en orde', async () => {
    const { form, choose, click, type, submit, save, button } = await addNew();
    await type(input(form(), 'input'), 'Fútbol 7');
    await choose('mode', 'Por partes');
    const parts = () => [...form().querySelectorAll<HTMLInputElement>('[data-part] input')];
    // Cada parte, un campo co seu rótulo no grupo; co mínimo (2), sen papeleira.
    expect(parts().map((p) => form().querySelector(`label[for="${p.id}"]`)?.textContent?.trim())).toEqual([
      'Parte 1',
      'Parte 2',
    ]);
    expect(form().querySelector('[data-part-remove]')).toBeNull();
    await submit();
    // Os erros de cada parte, debaixo do grupo, co seu rótulo diante.
    // Cada erro debaixo da súa parte, sen «Parte N:» diante (o rótulo xa se ve enriba).
    expect(
      [...form().querySelectorAll('[data-part] furar-field > .f-field-error')].map((e) => e.textContent?.trim()),
    ).toEqual(['Este campo é obrigatorio.', 'Este campo é obrigatorio.']);
    await type(parts()[0], 'Campo A');
    await type(parts()[1], 'campo a');
    await submit();
    expect(
      errorText(form()).some((t) => t.endsWith('Dúas partes deste uso teñen o mesmo nome: cambia un deles.')),
    ).toBe(true);
    await type(parts()[1], 'Campo B');
    await click(button('Engadir parte', form()));
    expect(document.activeElement).toBe(parts()[2]);
    // Con tres, a papeleira de cada unha, co seu nome; quitar a terceira leva o foco á anterior.
    expect(
      [...form().querySelectorAll('furar-field-action [data-part-remove] button')].map((b) =>
        b.getAttribute('aria-label'),
      ),
    ).toEqual(['Quitar a parte «Campo A»', 'Quitar a parte «Campo B»', 'Quitar a parte 3']);
    await click(form().querySelector<HTMLButtonElement>('[aria-label="Quitar a parte 3"]')!);
    expect(document.activeElement).toBe(parts()[1]);
    await expectNoAxeViolations(form());
    await submit();
    await save();
    const post = await expectRequest({ method: 'POST', url: USES_URL });
    expect(post.request.body).toMatchObject({ rentalMode: 'PARTS', parts: [{ name: 'Campo A' }, { name: 'Campo B' }] });
    expect(post.request.body.capacity).toBeUndefined();
    post.flush(use({ id: 'u9', name: 'Fútbol 7', rentalMode: 'PARTS' }));
    (await expectRequest(FACILITY_URL)).flush(facility());
  });

  it('por prazas: obrigatorias e de 2 a 10 000; a duración co despregable', async () => {
    const { form, choose, chosen, type, submit, save } = await addNew();
    await type(input(form(), 'input'), 'Natación libre');
    await choose('mode', 'Por prazas');
    await submit();
    expect(errorText(form())).toContain('Indica cantas persoas caben en cada franxa.');
    await type(input(form(), 'input[type="number"]'), '1');
    await submit();
    expect(errorText(form())).toContain('O valor está fóra do rango permitido.');
    await type(input(form(), 'input[type="number"]'), '25');
    await choose('slot', '30 min');
    expect(chosen('slot')).toBe('30 min');
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
    // O erro da fila, debaixo do grupo, co rótulo «Ata» diante.
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
    await click(button('Engadir período', when()!));
    await pickDate(2, 4);
    await pickDate(3, 4);
    await click(button('Listo', when()!));
    await submit();
    expect(errorText(when()!)).toEqual(['Os períodos deste uso non se poden pisar.']);
    backend().expectNone({ method: 'POST', url: USES_URL });
  });
});

describe('Deportes dun uso (ADR-0025, revisión do 2026-11-18)', () => {
  const facilitySports = ['football', 'football_7', 'c-1'];

  it('un uso novo leva todos os da instalación; un gardado, os seus que aínda ten ou, se non queda ningún, todos', () => {
    expect(useForm(undefined, facilitySports).controls.sports.value).toEqual(facilitySports);
    expect(useSports(use({ sports: ['c-1', 'football_7', 'tennis'] }), facilitySports)).toEqual(['football_7', 'c-1']);
    expect(useSports(use({ sports: ['tennis'] }), facilitySports)).toEqual(facilitySports);
    expect(useSports(use({ sports: undefined }), facilitySports)).toEqual(facilitySports);
  });

  it('con varios na instalación fai falta un; van no corpo na orde da instalación', () => {
    const form = useForm(use({ name: 'Fútbol 7', sports: ['football_7'] }), facilitySports);
    expect(useBody(form).sports).toEqual(['football_7']);
    form.controls.sports.setValue([]);
    expect(form.controls.sports.errors).toEqual({ 'validation.required': {} });
    // Cun só deporte, o campo non sae e non se pide.
    const single = useForm(undefined, ['padel']);
    single.controls.sports.setValue([]);
    expect(single.controls.sports.valid).toBe(true);
  });
});

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { DialogService } from '../ui/dialog/dialog.service';
import { DependentsStore } from './dependents.store';
import { addDependent, openDependents } from './open-dependents';

const URL = '/api/v1/me/dependents';
const UXIA = { id: 'd1', name: 'Uxía Vilar', birthYear: 2015, version: 0 };
const BRAIS = { id: 'd2', name: 'Brais Vilar', birthYear: 2018, version: 0 };

const stable = () => TestBed.inject(ApplicationRef).whenStable();
const layers = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')];
const top = () => layers().at(-1)!;
const type = (input: HTMLInputElement, value: string) => {
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
};

describe('«Persoas ao meu cargo» e «Persoa ao meu cargo»', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({}))] }));
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function open(people: object[] = [UXIA, BRAIS]) {
    await openDependents(TestBed.inject(DialogService));
    (await expectRequest(URL)).flush(people);
    await stable();
  }

  it('lista as persoas co seu ano, «Editar» discreto e eliminar vermello; «Engadir persoa» no pé', async () => {
    await open();
    const dialog = top();
    expect(dialog.querySelector('.f-dialog__title')?.textContent).toBe('Persoas ao meu cargo');
    const rows = [...dialog.querySelectorAll('[data-dependents-list] li')];
    expect(rows.map((row) => row.querySelector('b')?.textContent)).toEqual(['Uxía Vilar', 'Brais Vilar']);
    expect(rows[0].querySelector('small')?.textContent).toBe('2015');
    const edit = rows[0].querySelector<HTMLButtonElement>('[data-dependent-edit]')!;
    expect(edit.getAttribute('data-variant')).toBe('quiet');
    expect(edit.getAttribute('data-size')).toBe('sm');
    expect(edit.getAttribute('aria-label')).toBe('Editar a Uxía Vilar');
    const remove = rows[0].querySelector<HTMLButtonElement>('[data-dependent-delete] button')!;
    expect(remove.getAttribute('data-variant')).toBe('danger');
    expect(remove.getAttribute('aria-label')).toBe('Eliminar a Uxía Vilar');
    expect(dialog.querySelector('[data-dependents-add]')?.textContent?.trim()).toBe('Engadir persoa');
    await expectNoAxeViolations(dialog);
  });

  it('sen ningunha, dio; con dez, «Engadir persoa» queda desactivado', async () => {
    await open([]);
    expect(top().querySelector('[data-dependents-empty]')?.textContent).toBe('Aínda non gardaches ningunha persoa.');
    TestBed.inject(DependentsStore).items.set(
      Array.from({ length: 10 }, (_, i) => ({ ...UXIA, id: `d${i}`, name: `Persoa ${i}` })),
    );
    await stable();
    expect(top().querySelector<HTMLButtonElement>('[data-dependents-add]')!.disabled).toBe(true);
  });

  it('engadir pide o nome, un ano válido e a declaración; gardada, queda na lista', async () => {
    await open([UXIA]);
    top().querySelector<HTMLButtonElement>('[data-dependents-add]')!.click();
    await stable();
    const dialog = top();
    expect(dialog.querySelector('.f-dialog__title')?.textContent).toBe('Persoa ao meu cargo');
    const save = dialog.querySelector<HTMLButtonElement>('[data-dependent-save]')!;
    expect(save.textContent?.trim()).toBe('Engadir');
    const name = dialog.querySelector<HTMLInputElement>('[data-dependent-name]')!;
    const year = dialog.querySelector<HTMLInputElement>('[data-dependent-year]')!;
    const care = dialog.querySelector<HTMLInputElement>('[data-dependent-care]')!;
    expect(care.checked).toBe(false);

    type(year, '1800');
    dialog.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    await stable();
    expect(name.getAttribute('aria-invalid')).toBe('true');
    expect(dialog.textContent).toContain('Escribe un ano de nacemento válido.');
    expect(dialog.textContent).toContain('Marca que esta persoa está ao teu cargo.');
    await expectNoAxeViolations(dialog);

    type(name, ' Brais Vilar ');
    type(year, '2018');
    care.click();
    dialog.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    const post = await expectRequest(URL);
    expect(post.request.body).toEqual({ name: 'Brais Vilar', birthYear: 2018, inMyCare: true });
    post.flush(BRAIS);
    await stable();
    expect(layers()).toHaveLength(1);
    expect([...top().querySelectorAll('[data-dependents-list] b')].map((b) => b.textContent)).toEqual([
      'Brais Vilar',
      'Uxía Vilar',
    ]);
  });

  it('editar abre a ficha rechea, coa declaración sen marcar, e garda co If-Match; o erro da API queda na capa', async () => {
    await open([UXIA]);
    top().querySelector<HTMLButtonElement>('[data-dependent-edit]')!.click();
    await stable();
    const dialog = top();
    expect(dialog.querySelector<HTMLInputElement>('[data-dependent-name]')!.value).toBe('Uxía Vilar');
    expect(dialog.querySelector<HTMLInputElement>('[data-dependent-year]')!.value).toBe('2015');
    expect(dialog.querySelector('[data-dependent-save]')?.textContent?.trim()).toBe('Gardar');
    dialog.querySelector<HTMLInputElement>('[data-dependent-care]')!.click();
    dialog.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    const put = await expectRequest(`${URL}/d1`);
    expect(put.request.headers.get('If-Match')).toBe('"0"');
    flushProblem(put, 409, 'people.dependent-duplicate');
    await stable();
    expect(top().querySelector('[data-dependent-error]')?.textContent).toContain(
      'Xa tes gardada unha persoa con ese nome e ano.',
    );
    top().querySelector<HTMLButtonElement>('[data-dependent-cancel]')!.click();
    await stable();
    expect(layers()).toHaveLength(1);
  });

  it('eliminar pregunta antes e di o que pasa; confirmado, a persoa sae da lista', async () => {
    await open();
    top().querySelector<HTMLButtonElement>('[data-dependent-delete] button')!.click();
    await stable();
    const confirm = top();
    expect(confirm.getAttribute('role')).toBe('alertdialog');
    expect(confirm.textContent).toContain('Eliminar a Uxía Vilar?');
    expect(confirm.textContent).toContain('Xa non poderás reservar para esta persoa. As reservas feitas consérvanse.');
    confirm.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    const del = await expectRequest(`${URL}/d1`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null, { status: 204, statusText: 'No Content' });
    await vi.waitFor(async () => {
      await stable();
      expect(layers()).toHaveLength(1);
      expect([...top().querySelectorAll('[data-dependents-list] b')].map((b) => b.textContent)).toEqual([
        'Brais Vilar',
      ]);
    });
  });

  it('«Engadir persoa» desde «Para quen» devolve a ficha gardada, ou nada se se pecha', async () => {
    const adding = addDependent(TestBed.inject(DialogService));
    await vi.waitFor(async () => {
      await stable();
      expect(layers()).toHaveLength(1);
    });
    top().querySelector<HTMLButtonElement>('[data-dependent-cancel]')!.click();
    expect(await adding).toBeNull();
  });
});

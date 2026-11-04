// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { definition, municipality } from '../../../testing/fixtures';
import { expectNoAxeViolations } from '../../../testing/axe';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { CustomFieldEditPage } from './custom-field-edit.page';
import { CustomFieldsAdminPage } from './custom-fields-admin.page';

const LIST = '/api/v1/municipalities/naron/custom-fields?includeInactive=true';

describe('xestión de campos personalizados', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
  });
  afterEach(() => backend().verify());

  const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');
  const button = (el: HTMLElement, name: string) =>
    [...el.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.getAttribute('aria-label') === name);

  it('lista activos e desactivados de todos os sitios; desactivar e activar con If-Match', async () => {
    const fixture = TestBed.createComponent(CustomFieldsAdminPage);
    (await expectRequest(LIST)).flush([
      definition({
        id: 'a',
        code: 'phone',
        usages: [{ target: 'FACILITY', required: true, visibility: 'PUBLIC' }],
      }),
      definition({
        id: 'c',
        code: 'level',
        label: { gl: 'Nivel', es: 'Nivel' },
        usages: [{ target: 'BOOKING', required: false, visibility: 'OWNER' }],
      }),
      definition({ id: 'b', code: 'old', status: 'INACTIVE', label: { gl: 'Vello', es: 'Viejo' } }),
    ]);
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.textContent).toContain('Campos activos: 2. Cada sitio admite ata 50.');
    });
    // Onde se usa, co obrigatorio e quen o ve; na reserva, cos textos da reserva.
    const tags = [...el.querySelectorAll('furar-tag')].map((t) => t.textContent?.trim());
    expect(tags).toContain('Instalación: Público, obrigatorio');
    expect(tags).toContain('Reserva: A persoa que reserva e o persoal');
    expect([...el.querySelectorAll('h2')].map((h) => h.textContent?.trim())).toEqual(['Activos', 'Desactivados']);
    await expectNoAxeViolations(el);
    const deactivate = button(el, 'Desactivar o campo «Teléfono»');
    expect(deactivate?.getAttribute('title')).toBe('Desactivar');
    deactivate?.click();
    const post = await expectRequest('/api/v1/municipalities/naron/custom-fields/a/deactivate');
    expect(post.request.headers.get('If-Match')).toBe('"1"');
    post.flush(definition({ id: 'a', status: 'INACTIVE', version: 2 }));
    await vi.waitFor(() =>
      expect(TestBed.inject(ToastService).current()?.key).toBe('customFields.admin.deactivatedNotice'),
    );
    button(el, 'Activar o campo «Vello»')?.click();
    flushProblem(
      await expectRequest('/api/v1/municipalities/naron/custom-fields/b/activate'),
      412,
      'common.stale-version',
    );
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('[role="alert"]')?.textContent).toContain('cambiaron');
    });
  });

  it('eliminar pregunta sempre: sen valores borra; con valores ofrece desactivar', async () => {
    const fixture = TestBed.createComponent(CustomFieldsAdminPage);
    (await expectRequest(LIST)).flush([
      definition({ id: 'a', code: 'phone', hasValues: true }),
      definition({ id: 'c', code: 'level', label: { gl: 'Nivel', es: 'Nivel' }, hasValues: false }),
    ]);
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(button(el, 'Eliminar o campo «Nivel»')).toBeDefined();
    });
    button(el, 'Eliminar o campo «Nivel»')?.click();
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    expect(dialog()?.textContent).toContain('Eliminar o campo «Nivel»?');
    dialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    const del = await expectRequest({ method: 'DELETE', url: '/api/v1/municipalities/naron/custom-fields/c' });
    expect(del.request.headers.get('If-Match')).toBe('"1"');
    del.flush('', { status: 204, statusText: 'No Content' });
    await vi.waitFor(() =>
      expect(TestBed.inject(ToastService).current()?.key).toBe('customFields.admin.deletedNotice'),
    );
    await vi.waitFor(() => expect(dialog()).toBeNull());

    button(el, 'Eliminar o campo «Teléfono»')?.click();
    await vi.waitFor(() => expect(dialog()).not.toBeNull());
    expect(dialog()?.textContent).toContain('Non se pode eliminar «Teléfono»');
    const offer = dialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!;
    expect(offer.textContent?.trim()).toBe('Desactivar');
    offer.click();
    (await expectRequest('/api/v1/municipalities/naron/custom-fields/a/deactivate')).flush(
      definition({ id: 'a', status: 'INACTIVE', version: 2, hasValues: true }),
    );
    await vi.waitFor(() =>
      expect(TestBed.inject(ToastService).current()?.key).toBe('customFields.admin.deactivatedNotice'),
    );
  });

  it('alta: envía os usos do editor, avisa e volve á lista', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CustomFieldEditPage);
    await fixture.whenStable();
    const page = fixture.componentInstance as unknown as { save(d: unknown): Promise<void> };
    const saving = page.save({
      code: 'phone',
      type: 'TEXT',
      label: { gl: 'T', es: 'T' },
      rules: {},
      options: [],
      personalData: false,
      usages: [{ target: 'MUNICIPALITY', required: false, visibility: 'PUBLIC' }],
    });
    const post = await expectRequest({ method: 'POST', url: '/api/v1/municipalities/naron/custom-fields' });
    expect(post.request.body).toMatchObject({
      code: 'phone',
      usages: [{ target: 'MUNICIPALITY', required: false, visibility: 'PUBLIC' }],
    });
    post.flush(definition());
    await saving;
    expect(TestBed.inject(ToastService).current()?.key).toBe('customFields.editor.saved');
    expect(navigate).toHaveBeenCalledWith(['/', 'naron', 'xestion', 'concello', 'campos']);
  });

  it('edición: carga a definición co seu sitio, garda con If-Match sen código nin tipo; erro da API ao editor', async () => {
    const fixture = TestBed.createComponent(CustomFieldEditPage);
    fixture.componentRef.setInput('id', 'a');
    await fixture.whenStable();
    (await expectRequest('/api/v1/municipalities/naron/custom-fields/a')).flush(
      definition({
        id: 'a',
        hasValues: true,
        usages: [{ target: 'FACILITY', required: true, visibility: 'PUBLIC' }],
      }),
    );
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect((fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#def-code')?.value).toBe('phone');
    });
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector<HTMLInputElement>('#def-required')?.checked).toBe(true);
    // Xa ten valores: o sitio non cambia e unha liña di por que.
    const place = el.querySelector('[role="radiogroup"]');
    expect(place?.getAttribute('aria-disabled')).toBe('true');
    expect(place?.querySelector('[aria-checked="true"]')?.textContent?.trim()).toBe('Instalación');
    expect(el.querySelector('#def-place-lock')?.textContent).toContain('non se pode cambiar de sitio');
    const page = fixture.componentInstance as unknown as { save(d: unknown): Promise<void> };
    const saving = page.save({
      code: 'phone',
      type: 'TEXT',
      label: { gl: 'T', es: 'T' },
      rules: {},
      options: [],
      personalData: true,
      usages: [{ target: 'MUNICIPALITY', required: true, visibility: 'STAFF' }],
    });
    const put = await expectRequest({ method: 'PUT', url: '/api/v1/municipalities/naron/custom-fields/a' });
    expect(put.request.headers.get('If-Match')).toBe('"1"');
    expect(put.request.body).not.toHaveProperty('code');
    expect(put.request.body).not.toHaveProperty('type');
    expect(put.request.body).toMatchObject({
      personalData: true,
      usages: [{ target: 'MUNICIPALITY', required: true, visibility: 'STAFF' }],
    });
    flushProblem(put, 409, 'customfield.option-has-values', { params: { option: 'x' } });
    await saving;
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('«x»');
  });

  it('definición inexistente → erro traducido', async () => {
    const fixture = TestBed.createComponent(CustomFieldEditPage);
    fixture.componentRef.setInput('id', 'zz');
    await fixture.whenStable();
    flushProblem(
      await expectRequest('/api/v1/municipalities/naron/custom-fields/zz'),
      404,
      'customfield.definition-not-found',
    );
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(fixture.nativeElement.textContent).toContain('Non existe ese campo');
    });
  });
});

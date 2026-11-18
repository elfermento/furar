// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { definition, municipality } from '../../../testing/fixtures'; // @cf
import { municipality } from '../../../testing/fixtures'; // @nocf
import { ToastService } from '../../shared/ui/toast/toast.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { @Name@EditPage } from './@name-kebab@-edit.page';

const BASE = '/api/v1/municipalities/naron/@names@';
const DEFINITIONS = '/api/v1/municipalities/naron/custom-fields?target=@TARGET@'; // @cf
const ITEM = {
  id: 'a',
  name: 'Primeiro',
  customFields: {}, // @cf
  version: 3,
  createdAt: '2026-10-22T10:00:00Z',
  updatedAt: '2026-10-22T10:00:00Z',
};

describe('@Name@EditPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    TestBed.inject(CurrentMunicipality).set(municipality());
  });
  afterEach(() => backend().verify());

  async function render(id?: string) {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(@Name@EditPage);
    if (id) {
      fixture.componentRef.setInput('id', id);
    }
    // @cf{
    (await expectRequest(DEFINITIONS)).flush([
      definition({
        code: 'phone',
        required: true,
        usages: [{ target: '@TARGET@', required: true, visibility: 'PUBLIC' }],
        rules: { maxLength: 9 },
      }),
    ]);
    // @cf}
    if (id) {
      (await expectRequest(`${BASE}/${id}`)).flush(ITEM);
    }
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('form')).not.toBeNull();
    });
    const input = (selector: string) => el.querySelector<HTMLInputElement>(selector);
    const type = async (selector: string, value: string) => {
      const target = input(selector);
      if (!target) throw new Error(`sen ${selector}`);
      target.value = value;
      target.dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    const submit = async () => {
      el.querySelector('form')?.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve));
    };
    return { fixture, el, navigate, input, type, submit };
  }

  it('non envía baleiro: erro ligado ao campo (aria-describedby) e foco nel', async () => {
    const { el, input, submit } = await render();
    await submit();
    const name = input('#@name-kebab@-name');
    expect(name?.getAttribute('aria-invalid')).toBe('true');
    expect(name?.getAttribute('aria-describedby')).toBe('@name-kebab@-name-error');
    expect(document.activeElement).toBe(name);
    expect(el.querySelector('#@name-kebab@-name-error')?.textContent?.trim()).not.toBe('');
  });

  it('alta: POST, aviso e volta á lista', async () => {
    const { navigate, type, submit } = await render();
    await type('#@name-kebab@-name', '  Novo  ');
    await type('#@name-kebab@-cf-phone', '981000000'); // @cf
    await submit();
    const post = await expectRequest({ method: 'POST', url: BASE });
    expect(post.request.body).toEqual({
      name: 'Novo',
      customFields: { phone: '981000000' }, // @cf
    });
    post.flush({ ...ITEM, name: 'Novo' });
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(['/', 'naron', '@route@']));
    expect(TestBed.inject(ToastService).current()?.key).toBe('@name@.saved');
  });

  it('edición: carga e garda con If-Match da versión lida', async () => {
    const { input, type, submit } = await render('a');
    expect(input('#@name-kebab@-name')?.value).toBe('Primeiro');
    await type('#@name-kebab@-name', 'Cambiado');
    await type('#@name-kebab@-cf-phone', '981000000'); // @cf
    await submit();
    const put = await expectRequest({ method: 'PUT', url: `${BASE}/a` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toMatchObject({ name: 'Cambiado' });
    put.flush({ ...ITEM, name: 'Cambiado', version: 4 });
  });

  it('412: avisa e ofrece recargar os datos actuais', async () => {
    const { el, fixture, input, submit } = await render('a');
    await submit(); // @nocf
    // @cf{
    input('#@name-kebab@-cf-phone')!.value = '981000000';
    input('#@name-kebab@-cf-phone')!.dispatchEvent(new Event('input'));
    await submit();
    // @cf}
    flushProblem(await expectRequest({ method: 'PUT', url: `${BASE}/a` }), 412, 'common.stale-version');
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('[role="alert"]')?.textContent?.trim()).not.toBe('');
    });
    const reload = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Cargar'));
    reload?.click();
    (await expectRequest(DEFINITIONS)).flush([]); // @cf
    (await expectRequest(`${BASE}/a`)).flush({ ...ITEM, name: 'Doutra persoa', version: 4 });
    await vi.waitFor(() => expect(input('#@name-kebab@-name')?.value).toBe('Doutra persoa'));
  });

  it('erros da API no seu campo, tamén nos campos personalizados', async () => {
    const { el, type, submit, input } = await render();
    await type('#@name-kebab@-name', 'Novo');
    await type('#@name-kebab@-cf-phone', '981000000'); // @cf
    await submit();
    flushProblem(await expectRequest({ method: 'POST', url: BASE }), 400, 'validation.failed', {
      errors: [
        { field: 'name', code: 'validation.size', params: { max: 100 } },
        { field: 'customFields.phone', code: 'customfield.invalid-format', params: {} }, // @cf
      ],
    });
    await vi.waitFor(() => expect(input('#@name-kebab@-name')?.getAttribute('aria-invalid')).toBe('true'));
    expect(input('#@name-kebab@-cf-phone')?.getAttribute('aria-invalid')).toBe('true'); // @cf
    expect(el.querySelector('#@name-kebab@-name-error')).not.toBeNull();
  });
});

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { definition, municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { GeneralTab } from './general.tab';
import { MunicipalitySection } from './section';

const BASE = '/api/v1/municipalities/naron';

describe('Concello › Xeral', () => {
  afterEach(() => backend().verify());

  async function render() {
    // «Para abrir as reservas» (@defer) vai nos seus tests; aquí, só que está e non sae na carga.
    TestBed.configureTestingModule({
      providers: [...provideTestApi(), MunicipalitySection],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    });
    TestBed.inject(CurrentMunicipality).set(
      municipality({ customFields: { phone: '981' }, legal: { nif: 'P1505400J' } }),
    );
    const fixture = TestBed.createComponent(GeneralTab);
    await fixture.whenStable();
    (await expectRequest(`${BASE}/custom-fields?target=MUNICIPALITY`)).flush([
      definition({ required: true, rules: { maxLength: 5 } }),
    ]);
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('form')).not.toBeNull();
    });
    const type = async (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    const submit = async () => {
      el.querySelector('form')?.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    return { fixture, el, type, submit };
  }

  it('nome, idiomas e os datos propios co seu valor; sen cores nin datos legais; axe', async () => {
    const { el, fixture } = await render();
    expect(el.querySelector<HTMLInputElement>('#settings-name')?.value).toBe('Narón');
    expect(el.querySelector<HTMLInputElement>('#settings-cf-phone')?.value).toBe('981');
    expect(el.querySelector('furar-languages-fieldset')).not.toBeNull();
    expect(el.querySelector('furar-theme-editor, [data-legal]')).toBeNull();
    expect(el.querySelector('a[href$="/campos"]')?.textContent).toContain('Xestionar os campos');
    expect(fixture.componentInstance.dirty()).toBe(false);
    await expectNoAxeViolations(el);
  });

  it('valida no cliente sen chamar á API', async () => {
    const { el, type, submit } = await render();
    await type('settings-name', '');
    await type('settings-cf-phone', '123456');
    await submit();
    expect(el.querySelector('#settings-cf-phone-error')?.textContent).toContain('como máximo 5');
    expect(el.querySelector('#settings-name-error')).not.toBeNull();
  });

  it('garda co If-Match: o tema coma estaba e sen datos legais (non cambian); avisa', async () => {
    const { el, fixture, type, submit } = await render();
    await type('settings-name', ' Concello de Narón ');
    expect(fixture.componentInstance.dirty()).toBe(true);
    await submit();
    const put = await expectRequest({ method: 'PUT', url: BASE });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({
      name: 'Concello de Narón',
      languages: ['gl', 'es'],
      defaultLanguage: 'gl',
      theme: municipality().theme,
      customFields: { phone: '981' },
    });
    put.flush(municipality({ name: 'Concello de Narón', version: 4, customFields: { phone: '981' } }));
    await vi.waitFor(() => expect(TestBed.inject(ToastService).current()?.key).toBe('settings.saved'));
    expect(TestBed.inject(CurrentMunicipality).value()?.version).toBe(4);
    expect(el.querySelector<HTMLInputElement>('#settings-name')?.value).toBe('Concello de Narón');
    expect(fixture.componentInstance.dirty()).toBe(false);
  });

  it('versión vella → «Cargar os datos actuais»; descartar volve ao gardado', async () => {
    const { el, fixture, type, submit } = await render();
    await type('settings-name', 'Outro');
    await submit();
    flushProblem(await expectRequest({ method: 'PUT', url: BASE }), 412, 'common.stale-version');
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.textContent).toContain('Cargar os datos actuais');
    });
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Cargar os datos actuais'))!.click();
    (await expectRequest(BASE)).flush(municipality({ version: 9, name: 'Narón (novo)' }));
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector<HTMLInputElement>('#settings-name')?.value).toBe('Narón (novo)');
    });
    await type('settings-name', 'Outro');
    fixture.componentInstance.discard();
    await fixture.whenStable();
    expect(el.querySelector<HTMLInputElement>('#settings-name')?.value).toBe('Narón (novo)');
  });

  it('enriba, «Para abrir as reservas», cargada á parte (@defer)', async () => {
    const { el, fixture } = await render();
    expect(el.querySelector('furar-opening-checklist')).toBeNull();
    const [block] = await fixture.getDeferBlocks();
    await block.render(DeferBlockState.Complete);
    (await expectRequest(`${BASE}/facilities?page=0&size=100`)).flush({ items: [], page: 0, size: 100, totalItems: 0 });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('[data-opening-checklist]')).not.toBeNull();
    });
    expect(el.firstElementChild?.tagName.toLowerCase()).toBe('furar-opening-checklist');
  });
});

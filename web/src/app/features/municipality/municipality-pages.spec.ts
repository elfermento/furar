// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { municipality } from '../../../testing/fixtures';
import { expectNoAxeViolations } from '../../../testing/axe';
import { FirstRun } from '../../core/municipality/first-run';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { MunicipalityCreatePage } from './municipality-create.page';
import { MunicipalityPickerPage } from './municipality-picker.page';

describe('páxinas de concello', () => {
  afterEach(() => backend().verify());

  it('portada: lista os concellos (todas as páxinas) e enlaza cada un', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi(fakeSession({ platform: true })) });
    const fixture = TestBed.createComponent(MunicipalityPickerPage);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Cargando');
    (await expectRequest('/api/v1/municipalities?page=0&size=100')).flush({
      items: [{ id: '1', slug: 'naron', name: 'Narón' }],
      page: 0,
      size: 1,
      totalItems: 2,
    });
    (await expectRequest('/api/v1/municipalities?page=1&size=100')).flush({
      items: [{ id: '2', slug: 'ferrol', name: 'Ferrol' }],
      page: 1,
      size: 1,
      totalItems: 2,
    });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect([...fixture.nativeElement.querySelectorAll('li a')].map((a: Element) => a.getAttribute('href'))).toEqual([
        '/naron',
        '/ferrol',
      ]);
    });
    expect(fixture.nativeElement.textContent).toContain('Dar de alta un concello');
    await expectNoAxeViolations(fixture.nativeElement);
  });

  const page = (items: { id: string; slug: string; name: string }[]) => ({
    items,
    page: 0,
    size: 100,
    totalItems: items.length,
  });

  it('raíz cun só concello: vai á súa portada, sen lista que escoller (ADR-0028)', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi(fakeSession({ platform: true })) });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(MunicipalityPickerPage);
    (await expectRequest('/api/v1/municipalities?page=0&size=100')).flush(
      page([{ id: '1', slug: 'naron', name: 'Narón' }]),
    );
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(['/', 'naron'], { replaceUrl: true }));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('ul')).toBeNull();
  });

  it('raíz sen concellos: portada de exemplo, buscador desactivado e sen o nome dun concello (maqueta 46)', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi(fakeSession({})) });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(MunicipalityPickerPage);
    (await expectRequest('/api/v1/municipalities?page=0&size=100')).flush(page([]));
    const el = fixture.nativeElement as HTMLElement;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelectorAll('furar-example-card')).toHaveLength(4);
    });
    expect(el.querySelector('h1')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Reserva a túa pista');
    expect(el.querySelector<HTMLButtonElement>('.f-search__go')?.disabled).toBe(true);
    expect(el.querySelector('.f-search')?.hasAttribute('data-disabled')).toBe(true);
    expect(el.querySelector('.f-shot__chrome span')?.textContent).not.toContain('/');
    expect(navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(FirstRun).noMunicipality()).toBe(true);
    await expectNoAxeViolations(el);
    fixture.destroy();
    expect(TestBed.inject(FirstRun).noMunicipality()).toBe(false);
  });

  it('raíz sen concellos e coa administración da plataforma: á alta do concello', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi(fakeSession({ platform: true })) });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    TestBed.createComponent(MunicipalityPickerPage);
    (await expectRequest('/api/v1/municipalities?page=0&size=100')).flush(page([]));
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith('/plataforma/concellos/novo', { replaceUrl: true }));
  });

  it('portada: erro da API traducido', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const fixture = TestBed.createComponent(MunicipalityPickerPage);
    flushProblem(await expectRequest('/api/v1/municipalities?page=0&size=100'), 500, 'common.internal');
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Algo fallou no servidor');
    });
  });

  it('alta de concello: valida, crea e vai á xestión; slug en uso marcado no campo', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi(fakeSession({ platform: true })) });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(MunicipalityCreatePage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const type = async (id: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`#${id}`);
      if (!input) throw new Error(id);
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    const submit = async () => {
      el.querySelector('form')?.dispatchEvent(new Event('submit'));
      await fixture.whenStable();
    };
    await submit();
    expect(el.querySelector('#create-slug')?.getAttribute('aria-invalid')).toBe('true');
    await type('create-slug', 'as-pontes');
    await type('create-name', 'As Pontes');
    await submit();
    const create = await expectRequest('/api/v1/municipalities');
    expect(create.request.body).toEqual({
      slug: 'as-pontes',
      name: 'As Pontes',
      languages: ['gl', 'es'],
      defaultLanguage: 'gl',
    });
    flushProblem(create, 409, 'municipality.slug-taken', { params: { slug: 'as-pontes' } });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('#create-slug-error')?.textContent).toContain('«as-pontes»');
    });
    // O erro da API queda no campo ata cambiar o valor.
    await type('create-slug', 'pontes');
    await submit();
    (await expectRequest('/api/v1/municipalities')).flush(municipality({ slug: 'pontes', name: 'As Pontes' }));
    // A plataforma vai a «Persoal» do concello novo, para invitar a primeira persoa xestora.
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith(['/', 'pontes', 'xestion', 'concello', 'persoal']));
    // «Concello das Pontes creado», sen punto final, nun toast que pasa a navegación.
    expect(TestBed.inject(ToastService).current()).toMatchObject({
      key: 'platform.created',
      params: { ofName: 'das Pontes' },
    });
  });

  it('alta de concello: primeiro o nome; o identificador séguelle ata que se escribe a man', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi(fakeSession({ platform: true })) });
    const fixture = TestBed.createComponent(MunicipalityCreatePage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const input = (id: string) => el.querySelector<HTMLInputElement>(`#${id}`)!;
    const type = async (id: string, value: string) => {
      input(id).value = value;
      input(id).dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    const ids = [...el.querySelectorAll('input')].map((i) => i.id);
    expect(ids.indexOf('create-name')).toBeLessThan(ids.indexOf('create-slug'));
    expect(el.querySelector('#create-slug-help')?.textContent).toContain('naron, a-coruna ou vilagarcia-de-arousa');
    await type('create-name', 'A Coruña');
    expect(input('create-slug').value).toBe('a-coruna');
    await type('create-name', 'Vilagarcía de Arousa');
    expect(input('create-slug').value).toBe('vilagarcia-de-arousa');
    // Escrito a man: o nome xa non o cambia.
    await type('create-slug', 'vilagarcia');
    await type('create-name', 'Vilagarcía');
    expect(input('create-slug').value).toBe('vilagarcia');
    // Baleiro outra vez: volve seguir o nome.
    await type('create-slug', '');
    await type('create-name', 'O Porriño');
    expect(input('create-slug').value).toBe('o-porrino');
    await expectNoAxeViolations(el);
  });
});

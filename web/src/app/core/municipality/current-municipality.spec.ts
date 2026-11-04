// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import type { RedirectCommand } from '@angular/router';
import { type ActivatedRouteSnapshot, type RouterStateSnapshot, convertToParamMap } from '@angular/router';
import { provideApiConfiguration } from '../../api/api-configuration';
import { municipality } from '../../../testing/fixtures';
import { provideTestI18n } from '../../../testing/i18n';
import { problemInterceptor } from '../errors/problem.interceptor';
import { CurrentMunicipality } from './current-municipality';
import { leaveMunicipalityResolver, municipalityResolver } from './municipality.resolver';

describe('CurrentMunicipality e resolver', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideTestI18n(),
        provideHttpClient(withInterceptors([problemInterceptor])),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  const resolve = (slug: string) =>
    TestBed.runInInjectionContext(
      () =>
        municipalityResolver(
          { paramMap: convertToParamMap({ municipality: slug }) } as ActivatedRouteSnapshot,
          {} as RouterStateSnapshot,
        ) as Promise<unknown>,
    );

  it('carga o concello e aplica o seu tema antes de pintar; a segunda vez non pide', async () => {
    const pending = resolve('naron');
    backend
      .expectOne('/api/v1/municipalities/naron')
      .flush(municipality({ theme: { ...municipality().theme, primary: '#123456' } }));
    expect(await pending).toMatchObject({ slug: 'naron' });
    expect(document.documentElement.style.getPropertyValue('--furar-primary')).toBe('#123456');
    expect(TestBed.inject(CurrentMunicipality).name()).toBe('Narón');
    expect(await resolve('naron')).toMatchObject({ slug: 'naron' });
    backend.verify();
  });

  it('concello inexistente → 404 sen cambiar o URL; API caída → 500', async () => {
    const missing = resolve('non-existe');
    backend
      .expectOne('/api/v1/municipalities/non-existe')
      .flush({ code: 'municipality.not-found', status: 404, params: {} }, { status: 404, statusText: 'Not Found' });
    const redirect = (await missing) as RedirectCommand;
    expect(redirect.redirectTo.toString()).toBe('/erro/404');
    expect(redirect.navigationBehaviorOptions?.skipLocationChange).toBe(true);
    const broken = resolve('ferrol');
    backend.expectOne('/api/v1/municipalities/ferrol').flush('x', { status: 503, statusText: 'Down' });
    expect(((await broken) as RedirectCommand).redirectTo.toString()).toBe('/erro/500');
  });

  it('fóra dun concello volve o tema por defecto', () => {
    const current = TestBed.inject(CurrentMunicipality);
    current.set(municipality());
    const result = TestBed.runInInjectionContext(() =>
      leaveMunicipalityResolver({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    expect(result).toBe(true);
    expect(current.value()).toBeNull();
    expect(document.documentElement.style.getPropertyValue('--furar-primary')).toBe('');
  });
});

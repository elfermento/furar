// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { provideTestI18n } from '../../../testing/i18n';
import { acceptLanguageInterceptor } from '../i18n/accept-language.interceptor';
import { LanguageService } from '../i18n/language.service';
import { ApiProblem } from './api-problem';
import { asProblem, problemInterceptor } from './problem.interceptor';

describe('problemInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideTestI18n(),
        provideHttpClient(withInterceptors([acceptLanguageInterceptor, problemInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  async function fail(url: string, respond: (req: ReturnType<HttpTestingController['expectOne']>) => void) {
    const result = firstValueFrom(http.get(url)).catch((error: unknown) => error);
    respond(backend.expectOne(url));
    return result;
  }

  it('traduce o code RFC 9457 co seus parámetros e conserva os erros por campo', async () => {
    const error = await fail('/api/v1/municipalities/naron', (req) =>
      req.flush(
        {
          type: 'about:blank',
          title: 'x',
          status: 422,
          code: 'customfield.too-long',
          params: { max: 10 },
          traceId: 'abc',
          affected: [{ bookingId: 'b1' }],
          errors: [{ field: 'customFields.phone', code: 'customfield.too-long', params: { max: 10 } }, 'lixo'],
        },
        { status: 422, statusText: 'Unprocessable' },
      ),
    );
    expect(error).toBeInstanceOf(ApiProblem);
    const problem = error as ApiProblem;
    expect(problem.code).toBe('customfield.too-long');
    expect(problem.translated).toBe('Escribe como máximo 10 caracteres.');
    expect(problem.traceId).toBe('abc');
    expect(problem.messageKey).toBe('errors.customfield.too-long');
    expect(problem.errorsFor('customFields.phone')).toHaveLength(1);
    expect(problem.fieldErrors).toHaveLength(1); // o elemento non obxecto descártase
    // As extensións RFC 9457 (p. ex. affected[]) chegan á feature; os membros comúns non.
    expect(problem.extensions).toEqual({ affected: [{ bookingId: 'b1' }] });
  });

  it('429 con Retry-After en segundos → extensión retryAfter; sen ela ou noutra forma, nada', async () => {
    const body = { type: 'about:blank', title: 'x', status: 429, code: 'staff.invitation-rate-limited' };
    const limited = (await fail('/api/v1/a', (req) =>
      req.flush(body, { status: 429, statusText: 'x', headers: { 'Retry-After': '540' } }),
    )) as ApiProblem;
    expect(limited.extensions).toEqual({ retryAfter: 540 });
    const spent = (await fail('/api/v1/b', (req) => req.flush(body, { status: 429, statusText: 'x' }))) as ApiProblem;
    expect(spent.extensions).toEqual({});
    const date = (await fail('/api/v1/c', (req) =>
      req.flush(body, { status: 429, statusText: 'x', headers: { 'Retry-After': 'Wed, 21 Oct 2026 07:28:00 GMT' } }),
    )) as ApiProblem;
    expect(date.extensions).toEqual({});
  });

  it('sen corpo problem+json → erro xenérico; sen rede → common.network', async () => {
    const generic = (await fail('/api/v1/x', (req) =>
      req.flush('boom', { status: 502, statusText: 'Bad' }),
    )) as ApiProblem;
    expect(generic.code).toBe('common.unexpected');
    const offline = (await fail('/api/v1/y', (req) => req.error(new ProgressEvent('error')))) as ApiProblem;
    expect(offline.code).toBe('common.network');
    expect(offline.status).toBe(0);
  });

  it('código descoñecido → texto xenérico, nunca a clave en bruto', async () => {
    const problem = (await fail('/api/v1/z', (req) =>
      req.flush({ code: 'novo.codigo', params: {}, status: 400 }, { status: 400, statusText: 'Bad' }),
    )) as ApiProblem;
    expect(problem.translated).toBe('Algo fallou. Téntao de novo.');
  });

  it('manda Accept-Language co idioma activo só á API', async () => {
    await TestBed.inject(LanguageService).use('es');
    void firstValueFrom(http.get('/api/v1/municipalities'));
    expect(backend.expectOne('/api/v1/municipalities').request.headers.get('Accept-Language')).toBe('es');
    void firstValueFrom(http.get('/i18n/x.json'));
    expect(backend.expectOne('/i18n/x.json').request.headers.has('Accept-Language')).toBe(false);
  });

  it('asProblem envolve calquera erro', () => {
    expect(asProblem(new Error('x')).code).toBe('common.unexpected');
    const original = new ApiProblem(404, 'common.not-found', {}, [], undefined, 'x');
    expect(asProblem(original)).toBe(original);
  });
});

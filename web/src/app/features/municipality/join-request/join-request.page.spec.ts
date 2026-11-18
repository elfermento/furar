// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { JoinRequestPage } from './join-request.page';

describe('«¿Es un concello?» (/alta-concello)', () => {
  afterEach(() => backend().verify());

  async function render() {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), provideRouter([])] });
    const fixture = TestBed.createComponent(JoinRequestPage);
    const el = fixture.nativeElement as HTMLElement;
    document.body.appendChild(el);
    await fixture.whenStable();
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    const field = (name: string) => el.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#join-request-${name}`)!;
    const type = async (name: string, value: string) => {
      const input = field(name);
      input.value = value;
      input.dispatchEvent(new Event('input'));
      await settle();
    };
    const send = async () => {
      el.querySelector<HTMLButtonElement>('[data-join-send]')!.click();
      await settle();
    };
    const errorOf = (name: string) => el.querySelector(`#join-request-${name}-error`)?.textContent?.trim();
    return { fixture, el, settle, field, type, send, errorOf };
  }

  async function fillValid(type: (name: string, value: string) => Promise<void>) {
    await type('municipality', ' Concello de Exemplo ');
    await type('name', 'Ana Rey');
    await type('email', 'ana@example.org');
    await type('phone', '981 000 000');
  }

  it('os campos, na orde acordada, coa introdución e a frase de privacidade; pasa axe', async () => {
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Alta dun concello');
    expect(el.querySelector('.f-page-header__intro')?.textContent?.trim()).toBe(
      'Para que as instalacións deportivas do teu concello se reserven nesta web. Cóntanos quen es e escribímosche.',
    );
    const labels = [...el.querySelectorAll('[data-join-form] furar-field label')].map((l) =>
      l.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(labels).toEqual([
      'Concello ou entidade * (obrigatorio)',
      'Nome e apelidos * (obrigatorio)',
      'Cargo',
      'Correo * (obrigatorio)',
      'Teléfono * (obrigatorio)',
      'Mensaxe',
    ]);
    // Correo e teléfono, nunha fila.
    expect(el.querySelector('.f-field-cells #join-request-email')).not.toBeNull();
    expect(el.querySelector('.f-field-cells #join-request-phone')).not.toBeNull();
    expect(el.querySelector<HTMLTextAreaElement>('#join-request-message')?.placeholder).toBe(
      'Por exemplo, cantas instalacións tedes',
    );
    // A privacidade, nunha páxina propia: a frase leva a ligazón.
    expect(el.querySelector('[data-join-privacy]')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Usamos estes datos só para responder á solicitude. Máis en Privacidade.',
    );
    const privacy = el.querySelector<HTMLAnchorElement>('[data-join-privacy] a');
    expect(privacy?.textContent?.trim()).toBe('Privacidade');
    expect(privacy?.getAttribute('href')).toBe('/alta-concello/privacidade');
    expect(el.querySelector('furar-disclosure')).toBeNull();
    // O campo trampa non se ve nin se alcanza co teclado.
    const trap = el.querySelector<HTMLInputElement>('#join-request-website')!;
    expect(trap.closest('[aria-hidden="true"]')).not.toBeNull();
    expect(trap.tabIndex).toBe(-1);
    expect(el.querySelector('[data-join-send]')?.textContent?.trim()).toBe('Enviar solicitude');
    await expectNoAxeViolations(el);
  });

  it('ao enviar baleiro: o erro debaixo de cada obrigatorio, o foco ao primeiro e ningunha petición', async () => {
    const { el, send, errorOf, field } = await render();
    await send();
    expect(errorOf('municipality')).toBe('Este campo é obrigatorio.');
    expect(errorOf('name')).toBe('Este campo é obrigatorio.');
    expect(errorOf('email')).toBe('Este campo é obrigatorio.');
    expect(errorOf('phone')).toBe('Este campo é obrigatorio.');
    expect(errorOf('role')).toBeUndefined();
    expect(errorOf('message')).toBeUndefined();
    expect(field('municipality').getAttribute('aria-invalid')).toBe('true');
    expect(field('municipality').getAttribute('aria-describedby')).toBe('join-request-municipality-error');
    await vi.waitFor(() => expect(document.activeElement).toBe(field('municipality')));
    backend().expectNone('/api/v1/municipality-join-requests');
    el.remove();
  });

  it('un correo incompleto leva o texto propio; o foco vai a el', async () => {
    const { el, type, send, errorOf, field } = await render();
    await fillValid(type);
    await type('email', 'ana@example');
    await send();
    expect(errorOf('email')).toBe('Escribe un correo completo, coma nome@example.org.');
    expect(errorOf('municipality')).toBeUndefined();
    await vi.waitFor(() => expect(document.activeElement).toBe(field('email')));
    backend().expectNone('/api/v1/municipality-join-requests');
    el.remove();
  });

  it('enviada: o corpo recortado, sen opcionais baleiros; despois só a confirmación co correo, sen a introdución', async () => {
    const { el, type, send, settle } = await render();
    await fillValid(type);
    await type('message', 'Temos seis');
    await send();
    const request = await expectRequest('/api/v1/municipality-join-requests');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      municipality: 'Concello de Exemplo',
      name: 'Ana Rey',
      email: 'ana@example.org',
      phone: '981 000 000',
      message: 'Temos seis',
      website: '',
    });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-join-sent]')).not.toBeNull();
    });
    expect(el.querySelector('[data-join-form]')).toBeNull();
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Alta dun concello');
    expect(el.querySelector('.f-page-header__intro')).toBeNull();
    const done = el.querySelector('[data-join-sent]')!;
    expect(done.querySelector('.f-callout__title')?.textContent?.trim()).toBe('Recibimos a solicitude');
    expect(done.textContent?.replace(/\s+/g, ' ')).toContain('Escribirémosche a ana@example.org.');
    expect(done.querySelector('[role="status"]')).not.toBeNull();
    await vi.waitFor(() => expect(document.activeElement).toBe(done));
    await expectNoAxeViolations(el);
    el.remove();
  });

  it('os erros por campo da API van debaixo do seu campo; o límite, nun aviso, e a solicitude segue escrita', async () => {
    const { el, type, send, settle, errorOf, field } = await render();
    await fillValid(type);
    await send();
    flushProblem(await expectRequest('/api/v1/municipality-join-requests'), 400, 'validation.failed', {
      errors: [{ field: 'phone', code: 'validation.pattern', params: {} }],
    });
    await vi.waitFor(async () => {
      await settle();
      expect(errorOf('phone')).toBe('O formato non é válido.');
    });

    await type('phone', '981 000 001');
    await send();
    flushProblem(
      await expectRequest('/api/v1/municipality-join-requests'),
      429,
      'notification.join-request-rate-limited',
    );
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('furar-problem-alert')?.textContent).toContain(
        'Enviáronse moitas solicitudes desde a túa conexión. Volve tentalo mañá.',
      );
    });
    expect(el.querySelector('[data-join-sent]')).toBeNull();
    expect(field('municipality').value).toBe(' Concello de Exemplo ');
    el.remove();
  });
});

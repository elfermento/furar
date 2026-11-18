// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import {
  backend,
  expectRequest,
  fakeSession,
  flushMatching,
  flushProblem,
  provideTestApi,
} from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import {
  AVAILABILITY_URL,
  BLOCK_URL,
  CONTACT_URL,
  DAY,
  FACILITY_URL,
  FIELDS_URL,
  MY_SIMILAR_URL,
  NOTES,
  NOTES_URL,
  NOT_BLOCKED,
  SIMILAR_URL,
  WAITS_URL,
  availability,
  busy,
  facility,
  slot,
  waitFor,
} from './reserve.fixture';
import { ReservePage } from './reserve.page';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

/** Fútbol 7: 17:00, 18:00 e 19:00 libres; 20:00 e 21:00 ocupadas. */
const withBusy = () => ({ ...availability(), busy: [busy(1200), busy(1260)] });

const SIMILAR = {
  items: [
    {
      reason: 'SAME_FACILITY_NEAR',
      facilityId: 'f1',
      facilityName: 'Campo de fútbol 1 de O Alto',
      useId: 'u1',
      date: DAY,
      start: busy(1140).start,
      end: busy(1200).start,
      startMinute: 1140,
    },
    {
      reason: 'OTHER_FACILITY_SAME_TIME',
      facilityId: 'f2',
      facilityName: 'Campo de fútbol 2',
      facilityAddress: 'Rúa dos Carballos, 14',
      useId: 'u7',
      date: DAY,
      start: busy(1200).start,
      end: busy(1260).start,
      startMinute: 1200,
    },
  ],
};

interface Options {
  readonly session?: ReturnType<typeof fakeSession>;
  readonly block?: object;
  readonly waits?: object[];
  /** A resposta de «Ocos parecidos»; `null`: a proba respóndea. */
  readonly similar?: object | null;
}

describe('Páxina de reservar: horas ocupadas, espera e ocos parecidos', () => {
  afterEach(() => {
    // As entidades das que é responsable (ADR-0039): «Eu · ‹Entidade›» e «Reservar como».
    flushMatching((r) => r.url.endsWith('/me/organizations'), []);
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const anonymous = () => ({ ...fakeSession({}), authenticated: () => false }) as never;
  const isSimilar = (r: { url: string }) => r.url === SIMILAR_URL || r.url === MY_SIMILAR_URL;
  const BUSY_URL = `/naron/instalacions/f1/reservar?uso=u1&data=${DAY}&hora=20:00`;

  async function render(url: string, options: Options = {}) {
    const session = options.session ?? fakeSession({});
    const signedIn = session.authenticated();
    const block = options.block ?? NOT_BLOCKED;
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(session),
        provideRouter(
          [
            { path: 'naron/instalacions/:id/reservar', component: ReservePage },
            { path: 'naron/reservas', component: StubPage },
          ],
          withComponentInputBinding(),
        ),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    (await expectRequest(NOTES_URL)).flush(NOTES);
    if (signedIn) {
      (await expectRequest(BLOCK_URL)).flush(block);
      if (block !== NOT_BLOCKED) (await expectRequest(CONTACT_URL)).flush({ phone: '981 000 000' });
      flushMatching((r) => r.url === FIELDS_URL, []);
    }
    (await expectRequest(FACILITY_URL)).flush(facility());
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(withBusy());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-reserve-field="time"]')).not.toBeNull();
    });
    const panel = url.includes('hora=20:00') && block === NOT_BLOCKED && !session.roles.workAccount;
    if (panel && signedIn)
      (await expectRequest((r) => r.url === WAITS_URL && r.method === 'GET')).flush(options.waits ?? []);
    if (panel && options.similar !== null) (await expectRequest(isSimilar)).flush(options.similar ?? { items: [] });
    await settle();
    return { harness, el, settle, session };
  }

  const timeValue = (el: HTMLElement) =>
    el.querySelector('[data-reserve-field="time"] .f-field-popover__value')?.textContent?.trim();
  const query = () => new URLSearchParams(TestBed.inject(Router).url.split('?')[1] ?? '');
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

  it('o selector mestura libres e ocupadas; a ocupada é gris, di «Ocupada» e pódese escoller', async () => {
    const { el, settle } = await render(`/naron/instalacions/f1/reservar?uso=u1&data=${DAY}`);
    el.querySelector<HTMLButtonElement>('[data-reserve-field="time"] .f-field-popover__trigger')!.click();
    await settle();
    const hours = [...document.querySelectorAll<HTMLButtonElement>('.f-reserve__slot')];
    expect(hours.map((hour) => [hour.querySelector('b')?.textContent, hour.hasAttribute('data-busy')])).toEqual([
      ['17:00', false],
      ['18:00', false],
      ['19:00', false],
      ['20:00', true],
      ['21:00', true],
    ]);
    const occupied = hours[3];
    expect(occupied.querySelector('small')?.textContent?.trim()).toBe('Ocupada');
    // O nome accesible é o texto que se ve (WCAG 2.5.3): sen aria-label.
    expect(occupied.hasAttribute('aria-label')).toBe(false);
    expect(occupied.disabled).toBe(false);
    occupied.click();
    (await expectRequest((r) => r.url === WAITS_URL)).flush([]);
    (await expectRequest(isSimilar)).flush({ items: [] });
    await vi.waitFor(async () => {
      await settle();
      expect(timeValue(el)).toBe('20:00–21:00 · ocupada');
    });
    expect(query().get('hora')).toBe('20:00');
    expect(el.querySelector('[data-wait-add]')?.textContent?.trim()).toBe('Avísame se se libera');
  });

  it('cunha ocupada escollida: «Avísame» no canto do total e «Continuar», e os ocos parecidos; axe', async () => {
    const { el } = await render(BUSY_URL, { similar: SIMILAR });
    expect(timeValue(el)).toBe('20:00–21:00 · ocupada');
    expect(el.querySelector('[data-reserve-go]')).toBeNull();
    expect(el.querySelector('[data-reserve-total]')).toBeNull();
    const add = el.querySelector<HTMLButtonElement>('[data-wait-add]')!;
    expect(add.getAttribute('data-variant')).toBe('primary');
    expect(el.querySelector('[data-wait-sign-in]')).toBeNull();
    expect(el.querySelector('h2')?.textContent?.trim()).toBe('Ocos parecidos');
    const rows = [...el.querySelectorAll<HTMLElement>('[data-similar-slot]')];
    expect(rows.map((row) => text(row.querySelector('.f-row__label')))).toEqual([
      'Mércores 28, 19:00–20:00',
      'Mércores 28, 20:00–21:00',
    ]);
    expect(rows.map((row) => text(row.querySelector('.f-row__detail')))).toEqual([
      'Esta mesma instalación',
      'Campo de fútbol 2 · Rúa dos Carballos, 14',
    ]);
    expect(rows.map((row) => row.querySelector('a')?.getAttribute('href'))).toEqual([
      `/naron/instalacions/f1/reservar?uso=u1&data=${DAY}&hora=19:00`,
      `/naron/instalacions/f2/reservar?uso=u7&data=${DAY}&hora=20:00`,
    ]);
    await expectNoAxeViolations(el);
  });

  it('pulsar un oco parecido desta instalación déixao escollido e volve «Confirmar reserva»', async () => {
    const { el, settle } = await render(BUSY_URL, { similar: SIMILAR });
    el.querySelector<HTMLAnchorElement>('[data-similar-slot] a')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(timeValue(el)).toBe('19:00–20:00');
    });
    expect(el.querySelector('[data-wait-add]')).toBeNull();
    expect(el.querySelector('[data-similar-slots]')).toBeNull();
    expect(el.querySelector<HTMLButtonElement>('[data-reserve-go]')?.disabled).toBe(false);
  });

  it('sen sesión: os ocos parecidos son os públicos e «Avísame» leva a entrar co mesmo URL', async () => {
    const { el, session } = await render(BUSY_URL, { session: anonymous(), similar: null });
    (await expectRequest((r) => r.url === SIMILAR_URL)).flush({ items: [] });
    expect(el.querySelector('[data-wait-sign-in]')?.textContent?.trim()).toBe(
      'Para avisarte tes que entrar coa túa conta.',
    );
    el.querySelector<HTMLButtonElement>('[data-wait-add]')!.click();
    await vi.waitFor(() => expect(session.login).toHaveBeenCalledWith(TestBed.inject(Router).url));
    expect(TestBed.inject(Router).url).toContain('hora=20:00');
    backend().expectNone(WAITS_URL);
  });

  it('«Avísame» garda a espera: aviso verde e «Deixar de esperar» (perigo); deixala, sen preguntar, tamén cun 404', async () => {
    const { el, settle } = await render(BUSY_URL);
    el.querySelector<HTMLButtonElement>('[data-wait-add]')!.click();
    const post = await expectRequest((r) => r.url === WAITS_URL && r.method === 'POST');
    expect(post.request.body).toEqual({ facilityId: 'f1', useId: 'u1', start: busy(1200).start });
    post.flush(waitFor(), { status: 201, statusText: 'Created' });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-wait-on]')).not.toBeNull();
    });
    const notice = el.querySelector<HTMLElement>('[data-wait-on]')!;
    expect(notice.getAttribute('data-tone')).toBe('success');
    expect(text(notice)).toContain('Avisarémoste por correo se se libera');
    expect(text(notice)).toContain('Reserva a primeira persoa que entre.');
    const remove = el.querySelector<HTMLButtonElement>('[data-wait-remove]')!;
    expect(remove.getAttribute('data-variant')).toBe('danger');
    expect(remove.textContent?.trim()).toBe('Deixar de esperar');
    expect(el.querySelector('[data-wait-add]')).toBeNull();
    await expectNoAxeViolations(el);

    remove.click();
    expect(document.querySelector('furar-dialog-container')).toBeNull();
    flushProblem(
      await expectRequest((r) => r.url === `${WAITS_URL}/w1` && r.method === 'DELETE'),
      404,
      'booking.wait-not-found',
    );
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-wait-add]')).not.toBeNull();
    });
    expect(el.querySelector('[data-wait-error] furar-callout')).toBeNull();
  });

  it('se xa espera por esa hora (ao chegar desde o correo), o aviso verde e «Deixar de esperar»', async () => {
    const { el } = await render(BUSY_URL, { waits: [waitFor()] });
    expect(el.querySelector('[data-wait-on]')).not.toBeNull();
    expect(el.querySelector('[data-wait-remove]')).not.toBeNull();
    expect(el.querySelector('[data-wait-add]')).toBeNull();
  });

  it('co límite de esperas: o erro no panel, coa ligazón «Ver as miñas reservas»', async () => {
    const { el, settle } = await render(BUSY_URL);
    el.querySelector<HTMLButtonElement>('[data-wait-add]')!.click();
    flushProblem(await expectRequest((r) => r.url === WAITS_URL && r.method === 'POST'), 422, 'booking.wait-limit', {
      params: { max: 3 },
    });
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-wait-error] furar-callout')).not.toBeNull();
    });
    expect(text(el.querySelector('[data-wait-error]'))).toContain(
      'Podes esperar por 3 horas á vez. Deixa de esperar por unha para engadir outra.',
    );
    const mine = el.querySelector<HTMLAnchorElement>('[data-wait-mine]')!;
    expect(mine.textContent?.trim()).toBe('Ver as miñas reservas');
    expect(mine.getAttribute('href')).toBe('/naron/reservas');
  });

  it('outro 4xx (xa ten reserva a esa hora): o erro no panel, sen ligazón', async () => {
    const { el, settle } = await render(BUSY_URL);
    el.querySelector<HTMLButtonElement>('[data-wait-add]')!.click();
    flushProblem(
      await expectRequest((r) => r.url === WAITS_URL && r.method === 'POST'),
      409,
      'booking.wait-own-booking',
    );
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[data-wait-error]'))).toContain('Xa tes unha reserva a esa hora.');
    });
    expect(el.querySelector('[data-wait-mine]')).toBeNull();
    expect(el.querySelector('[data-wait-add]')).not.toBeNull();
  });

  it('se a hora xa quedou libre: recárganse as horas, queda escollida como libre e volve «Confirmar reserva»', async () => {
    const { el, settle } = await render(BUSY_URL);
    el.querySelector<HTMLButtonElement>('[data-wait-add]')!.click();
    flushProblem(await expectRequest((r) => r.url === WAITS_URL && r.method === 'POST'), 409, 'booking.wait-slot-free');
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush({
      ...availability([slot(1140, 1, ['p2']), slot(1200, 1, ['p1', 'p2'])]),
      busy: [busy(1260)],
    });
    await vi.waitFor(async () => {
      await settle();
      expect(timeValue(el)).toBe('20:00–21:00');
    });
    expect(el.querySelector('[data-reserve-freed]')?.textContent).toContain('Esta hora xa está libre: resérvaa.');
    expect(el.querySelector('[data-wait-add]')).toBeNull();
    expect(el.querySelector<HTMLButtonElement>('[data-reserve-go]')?.disabled).toBe(false);
  });

  it('«Ocos parecidos»: cargando, dúas filas de esqueleto; se falla, unha liña e «Avísame» segue; baleiro, sen sección', async () => {
    const { el, settle } = await render(BUSY_URL, { similar: null });
    const skeleton = el.querySelector('furar-similar-slots furar-status-state')!;
    expect(skeleton.getAttribute('data-mode')).toBe('skeleton');
    flushProblem(await expectRequest((r) => r.url === MY_SIMILAR_URL), 500, 'common.unexpected');
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-similar-error]')).not.toBeNull();
    });
    expect(el.querySelector('[data-similar-error]')?.textContent?.trim()).toBe('Non puidemos buscar ocos parecidos.');
    expect(el.querySelector<HTMLButtonElement>('[data-wait-add]')?.disabled).toBe(false);
  });

  it('sen ocos parecidos a sección non sae', async () => {
    const { el } = await render(BUSY_URL);
    expect(el.querySelector('furar-similar-slots h2')).toBeNull();
    expect(el.querySelector('[data-similar-slots]')).toBeNull();
  });

  it('bloqueada por faltas: a ocupada vese escollida, pero sen «Avísame» nin «Ocos parecidos»', async () => {
    const { el } = await render(BUSY_URL, { block: { blocked: true, until: '2026-11-24', faults: 3, windowDays: 30 } });
    expect(timeValue(el)).toBe('20:00–21:00 · ocupada');
    expect(el.querySelector('[data-reserve-blocked]')).not.toBeNull();
    expect(el.querySelector('furar-wait-panel, furar-similar-slots')).toBeNull();
  });

  it('conta de traballo: sen «Avísame» nin «Ocos parecidos»', async () => {
    const { el } = await render(BUSY_URL, { session: fakeSession({ workAccount: true }) });
    expect(el.querySelector('[data-reserve-work-account]')).not.toBeNull();
    expect(el.querySelector('furar-wait-panel, furar-similar-slots')).toBeNull();
    expect(el.querySelector('[data-reserve-total]')).toBeNull();
    expect(el.querySelector<HTMLButtonElement>('[data-reserve-go]')?.disabled).toBe(true);
  });
});

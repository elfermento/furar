// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Meta } from '@angular/platform-browser';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { NO_MAP, provideDeploymentSettings } from '../../../../testing/deployment';
import { municipality } from '../../../../testing/fixtures';
import { bookingMatch, myMatch, publicMatch } from '../../../../testing/open-matches';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { BOOKINGS_URL, CONTACT_URL, NOTES, NOTES_URL, booking } from '../reserve/reserve.fixture';
import { OpenMatchPage } from './open-match.page';

const PUBLIC = '/api/v1/municipalities/naron/public/open-matches/m1';
const MINE = '/api/v1/municipalities/naron/me/open-matches/m1';

/** A reserva da partida que organiza: pista enteira, para dentro de moito, que aínda se cancela de balde. */
const organizerBooking = (overrides: Partial<MyBookingResponse> = {}) =>
  booking({
    rentalMode: 'WHOLE',
    partId: undefined,
    partName: undefined,
    useName: 'Fútbol sala',
    start: '2099-11-20T19:00:00Z',
    end: '2099-11-20T20:30:00Z',
    cancelUntil: '2099-11-19T19:00:00Z',
    paymentStatus: 'NONE',
    price: { kind: 'FREE' },
    ...overrides,
  });

describe('OpenMatchPage: a páxina dunha partida (ADR-0040)', () => {
  afterEach(() => {
    sessionStorage.clear();
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  /** A ficha pública da instalación (só o punto, para «Como chegar»), ou o seu erro. */
  const FACILITY = '/api/v1/municipalities/naron/public/facilities/f1';
  const WITH_POINT = { address: { latitude: 43.51, longitude: -8.19 } };

  async function render(
    response: object | 404 | 500,
    session = fakeSession({}),
    facility: object | 500 = WITH_POINT,
    providers: unknown[] = [],
  ) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(session), ...providers] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(OpenMatchPage);
    fixture.componentRef.setInput('id', 'm1');
    const el = fixture.nativeElement as HTMLElement;
    const url = session.authenticated() ? MINE : PUBLIC;
    const request = await expectRequest(url);
    if (typeof response === 'number') flushProblem(request, response, 'booking.match-not-found');
    else request.flush(response);
    await settle(fixture);
    for (const read of backend().match(FACILITY)) {
      if (facility === 500) flushProblem(read, 500, 'common.unexpected');
      else read.flush(facility);
    }
    await settle(fixture);
    return { fixture, el };
  }

  const settle = async (fixture: { whenStable: () => Promise<unknown> }) => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const anonymous = () => {
    const session = fakeSession({});
    session.authenticated.set(false);
    return session;
  };

  it('sen sesión: título, descrición en texto plano, a tarxeta (instalación, cando, prazas co prazo, organiza) e «Unirme», que leva a entrar; noindex; axe', async () => {
    const session = anonymous();
    const { el } = await render(publicMatch({ description: '<b>Ola</b> https://exemplo.org' }), session);
    expect(document.querySelector('meta[name="robots"]')?.getAttribute('content')).toBe('noindex');
    expect(text(el.querySelector('h1'))).toBe('Pachanga de venres');
    expect(text(el.querySelector('[data-match-description]'))).toBe('<b>Ola</b> https://exemplo.org');
    expect(el.querySelector('[data-match-description] a, [data-match-description] b')).toBeNull();
    expect([...el.querySelectorAll('[data-match-tags] furar-tag')].map(text)).toEqual([
      'Fútbol sala',
      'Pista enteira',
      'Nivel medio',
    ]);
    expect(el.querySelector('[data-match-facility]')?.getAttribute('href')).toBe('/naron/instalacions/f1');
    expect(text(el.querySelector('[data-match-when]'))).toBe('venres, 20 de novembro · 20:00–21:30');
    expect(text(el.querySelector('[data-match-counts]'))).toBe('4 de 10 · faltan 2');
    expect(text(el.querySelector('[data-match-rule]'))).toBe(
      'Xógase se hai 6 persoas antes do venres, 20 de novembro ás 08:00. Máximo 10.',
    );
    expect(text(el.querySelector('[data-match-organizer]'))).toBe('Uxía Pereira');
    // Ningún outro nome sen estar dentro.
    expect(el.querySelector('[data-match-people]')).toBeNull();
    // «Como chegar», co punto da ficha da instalación.
    expect(el.querySelector('[data-match-map] furar-map')).not.toBeNull();
    await expectNoAxeViolations(el);

    el.querySelector<HTMLButtonElement>('[data-match-join]')!.click();
    await vi.waitFor(() => expect(session.login).toHaveBeenCalled());
    // Recórdase «Unirme» para esta partida (ao volver con sesión, únese soa).
    expect(JSON.parse(sessionStorage.getItem('furar.openMatchJoin')!)).toMatchObject({ slug: 'naron', id: 'm1' });
  });

  describe('«Unirme» sen sesión: ao volver xa con sesión, únese soa (unha vez)', () => {
    const intent = (at = Date.now(), id = 'm1') =>
      sessionStorage.setItem('furar.openMatchJoin', JSON.stringify({ slug: 'naron', id, at }));

    it('sen aprobación: únese, «Xa estás na partida.» e a intención xa non está', async () => {
      intent();
      const { fixture, el } = await render(myMatch());
      const toast = vi.spyOn(TestBed.inject(ToastService), 'show');
      (await expectRequest({ method: 'POST', url: MINE + '/players' })).flush(
        myMatch({ me: { role: 'CONFIRMED', playerId: 'p2' }, total: 5 }),
      );
      await settle(fixture);
      expect(el.querySelector('[data-match-action]')?.getAttribute('data-match-action')).toBe('joined');
      expect(toast).toHaveBeenCalledWith('openMatch.page.joinedToast');
      expect(sessionStorage.getItem('furar.openMatchJoin')).toBeNull();
    });

    it('con aprobación: manda a solicitude, «Solicitude enviada.»', async () => {
      intent();
      const { fixture } = await render(myMatch({ approval: true }));
      const toast = vi.spyOn(TestBed.inject(ToastService), 'show');
      (await expectRequest({ method: 'POST', url: MINE + '/players' })).flush(
        myMatch({ approval: true, me: { role: 'PENDING', playerId: 'p3' } }),
      );
      await settle(fixture);
      expect(toast).toHaveBeenCalledWith('openMatch.page.askedToast');
    });

    it('completa entrementres: o motivo da API e a partida no seu estado', async () => {
      intent();
      const { fixture, el } = await render(myMatch());
      flushProblem(await expectRequest({ method: 'POST', url: MINE + '/players' }), 409, 'booking.match-full');
      (await expectRequest({ method: 'GET', url: MINE })).flush(myMatch({ total: 10 }));
      await settle(fixture);
      expect(el.textContent).toContain('A partida xa está completa.');
      expect(el.querySelector('[data-match-join]')).toBeNull();
    });

    it('caducada, doutra partida ou vista antes sen sesión: non se une e esquécese', async () => {
      intent(Date.now() - 11 * 60 * 1000);
      await render(myMatch());
      backend().expectNone({ method: 'POST', url: MINE + '/players' });
      expect(sessionStorage.getItem('furar.openMatchJoin')).toBeNull();
      TestBed.resetTestingModule();
      intent(Date.now(), 'm2');
      await render(myMatch());
      backend().expectNone({ method: 'POST', url: MINE + '/players' });
      TestBed.resetTestingModule();
      // Volveu sen sesión (cancelou o acceso): esquécese, e despois xa non se une.
      intent();
      await render(publicMatch(), anonymous());
      expect(sessionStorage.getItem('furar.openMatchJoin')).toBeNull();
    });
  });

  it('«Como chegar»: sen o punto da instalación (falla a ficha) ou sen mapa no despregue, non sae', async () => {
    const failed = await render(publicMatch(), anonymous(), 500);
    expect(failed.el.querySelector('[data-match-map]')).toBeNull();
    expect(failed.el.querySelector('[data-match-card]')).not.toBeNull();
  });

  it('sen mapa no despregue nin se pide a ficha da instalación', async () => {
    const { el } = await render(publicMatch(), anonymous(), WITH_POINT, [provideDeploymentSettings(NO_MAP)]);
    backend().expectNone(FACILITY);
    expect(el.querySelector('[data-match-map]')).toBeNull();
  });

  it('ao saír da páxina quítase o noindex', async () => {
    const { fixture } = await render(publicMatch(), anonymous());
    expect(TestBed.inject(Meta).getTag('name="robots"')).not.toBeNull();
    fixture.destroy();
    expect(TestBed.inject(Meta).getTag('name="robots"')).toBeNull();
  });

  it('con sesión, sen aprobación: «Unirme» → dentro, coas persoas e «Saír»; ao saír, volve «Unirme»', async () => {
    const { fixture, el } = await render(myMatch());
    el.querySelector<HTMLButtonElement>('[data-match-join]')!.click();
    (await expectRequest({ method: 'POST', url: MINE + '/players' })).flush(
      myMatch({
        me: { role: 'CONFIRMED', playerId: 'p2' },
        total: 5,
        knownCount: 2,
        players: [
          { id: 'p1', name: 'Brais Castro' },
          { id: 'p2', name: 'Marta Vilar' },
        ],
      }),
    );
    await settle(fixture);
    expect(el.querySelector('[data-match-action]')?.getAttribute('data-match-action')).toBe('joined');
    expect(el.textContent).toContain('Estás dentro');
    const people = [...el.querySelectorAll('[data-match-people] li')];
    expect(people.map((li) => text(li.querySelector('span')))).toEqual([
      'Uxía Pereira + 1',
      'Brais Castro',
      'Marta Vilar',
    ]);
    expect(people.map((li) => text(li.querySelector('furar-tag')) ?? null)).toEqual(['Organiza', null, 'Ti']);
    expect(text(el.querySelector('#match-people'))).toBe('Persoas 5');

    el.querySelector<HTMLButtonElement>('[data-match-leave]')!.click();
    (await expectRequest({ method: 'DELETE', url: MINE + '/players/me' })).flush(null);
    (await expectRequest({ method: 'GET', url: MINE })).flush(myMatch());
    await settle(fixture);
    expect(text(el.querySelector('[data-match-join]'))).toBe('Unirme');
  });

  it('con aprobación: «Pedir unirme» → «Solicitude enviada» e «Retirar a solicitude»', async () => {
    const { fixture, el } = await render(myMatch({ approval: true, total: 3, minimum: 4, maximum: 4 }));
    expect(text(el.querySelector('[data-match-join]'))).toBe('Pedir unirme');
    el.querySelector<HTMLButtonElement>('[data-match-join]')!.click();
    (await expectRequest({ method: 'POST', url: MINE + '/players' })).flush(
      myMatch({ approval: true, me: { role: 'PENDING', playerId: 'p3' } }),
    );
    await settle(fixture);
    expect(el.textContent).toContain('Solicitude enviada');
    expect(el.textContent).toContain('Avisámoste por correo cando Uxía Pereira responda.');
    expect(text(el.querySelector('[data-match-leave]'))).toBe('Retirar a solicitude');
    expect(el.querySelector('[data-match-people]')).toBeNull();
  });

  it('quen organiza: as solicitudes como tarxetas, «Rexeitar» e «Aceptar» cheos; aceptar e rexeitar; axe', async () => {
    const requests = [
      { id: 'r1', name: 'Xoán Iglesias', requestedAt: new Date(Date.now() - 2 * 3_600_000).toISOString() },
      { id: 'r2', name: 'Carme Pena', requestedAt: new Date(Date.now() - 20 * 60_000).toISOString() },
    ];
    const organizer = myMatch({ me: { role: 'ORGANIZER' }, approval: true, requests, bookingId: 'b1' });
    const { fixture, el } = await render(organizer);
    expect(text(el.querySelector('#match-requests'))).toBe('Solicitudes 2');
    const first = el.querySelector('[data-request="r1"]')!;
    expect(text(first)).toContain('Xoán Iglesias');
    expect(text(first)).toContain('pediu unirse hai 2 h');
    const reject = first.querySelector<HTMLButtonElement>('[data-request-reject]')!;
    const accept = first.querySelector<HTMLButtonElement>('[data-request-accept]')!;
    expect(reject.getAttribute('data-variant')).toBe('danger-fill');
    expect(accept.getAttribute('data-variant')).toBe('primary');
    expect(accept.getAttribute('aria-label')).toBe('Aceptar a Xoán Iglesias');
    expect(text(el.querySelector('[data-match-organizer]'))).toBe('Ti');
    await organizerBookingLoaded(fixture);
    const stop = el.querySelector<HTMLButtonElement>('[data-match-stop]')!;
    const cancel = el.querySelector<HTMLButtonElement>('[data-match-cancel]')!;
    expect(text(stop)).toBe('Deixar de buscar');
    expect(stop.getAttribute('data-variant')).toBe('secondary');
    expect(text(cancel)).toBe('Cancelar a reserva');
    expect(cancel.getAttribute('data-variant')).toBe('danger');
    expect(el.querySelector('[data-match-manage]')).toBeNull();
    await expectNoAxeViolations(el);

    accept.click();
    (await expectRequest({ method: 'POST', url: MINE + '/players/r1/accept' })).flush({
      ...organizer,
      total: 5,
      requests: [requests[1]],
      players: [{ id: 'r1', name: 'Xoán Iglesias' }],
    });
    await settle(fixture);
    expect(el.querySelector('[data-request="r1"]')).toBeNull();
    expect(text(el.querySelector('[data-match-people]'))).toContain('Xoán Iglesias');

    el.querySelector<HTMLButtonElement>('[data-request="r2"] [data-request-reject]')!.click();
    (await expectRequest({ method: 'POST', url: MINE + '/players/r2/reject' })).flush({ ...organizer, requests: [] });
    await settle(fixture);
    expect(el.querySelector('[data-match-requests]')).toBeNull();
  });

  /** Quen organiza: a súa reserva e os textos da devolución do concello. */
  async function organizerBookingLoaded(
    fixture: { whenStable: () => Promise<unknown> },
    reservation: MyBookingResponse = organizerBooking(),
  ) {
    (await expectRequest(`${BOOKINGS_URL}/b1`)).flush(reservation);
    (await expectRequest(NOTES_URL)).flush(NOTES);
    (await expectRequest(CONTACT_URL)).flush({});
    await settle(fixture);
  }
  const dialog = () => [...document.querySelectorAll<HTMLElement>('furar-dialog-container')].at(-1) ?? null;
  const organizer = (overrides: object = {}) =>
    myMatch({
      me: { role: 'ORGANIZER' },
      bookingId: 'b1',
      players: [{ id: 'p1', name: 'Brais Castro' }],
      ...overrides,
    });

  it('quen organiza, «Deixar de buscar»: pregunta e pecha; queda o estado e «Cancelar a reserva»', async () => {
    const { fixture, el } = await render(organizer());
    await organizerBookingLoaded(fixture);
    el.querySelector<HTMLButtonElement>('[data-match-stop]')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-confirm-accept]')).toBeTruthy();
    });
    expect(dialog()!.textContent).toContain('Deixar de buscar xente?');
    expect(dialog()!.textContent).toContain('A reserva e a persoa que se uniu seguen.');
    // Botón principal (non vermello) e as liñas de detalle, coma en «As miñas reservas».
    expect(dialog()!.querySelectorAll('[data-confirm-detail]')).toHaveLength(2);
    expect(dialog()!.querySelector('[data-confirm-accept]')?.getAttribute('data-variant')).toBe('primary');
    dialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    const put = await expectRequest({ method: 'PUT', url: `${BOOKINGS_URL}/b1/open-match/status` });
    expect(put.request.body).toEqual({ status: 'CLOSED' });
    put.flush(organizer({ status: 'CLOSED' }));
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(text(el.querySelector('[data-match-closed]'))).toBe('Xa non se une máis xente. A reserva segue.');
    });
    expect(el.querySelector('[data-match-stop]')).toBeNull();
    expect(text(el.querySelector('[data-match-cancel]'))).toBe('Cancelar a reserva');
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'openMatch.stop.done' });
  });

  it('quen organiza, «Cancelar a reserva»: o aviso ás apuntadas, cancela e a partida queda cancelada', async () => {
    const { fixture, el } = await render(organizer());
    await organizerBookingLoaded(fixture, organizerBooking({ openMatch: bookingMatch({ confirmed: 3 }) }));
    el.querySelector<HTMLButtonElement>('[data-match-cancel]')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-confirm-detail]')).toBeTruthy();
    });
    expect(dialog()!.textContent).toContain('Cancelar a reserva?');
    const lines = [...dialog()!.querySelectorAll('[data-confirm-detail]')].map(text);
    expect(lines.at(-1)).toBe('Cancélase tamén a partida e avisamos por correo ás 3 persoas apuntadas.');
    dialog()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    (await expectRequest({ method: 'POST', url: `${BOOKINGS_URL}/b1/cancel` })).flush(
      organizerBooking({ status: 'CANCELLED_BY_PERSON' }),
    );
    (await expectRequest({ method: 'GET', url: MINE })).flush(organizer({ status: 'CANCELLED' }));
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(text(el.querySelector('[data-match-cancelled]'))).toBe('Esta partida cancelouse.');
    });
    expect(el.querySelector('[data-match-stop], [data-match-cancel]')).toBeNull();
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'booking.mine.cancel.done' });
  });

  it('quen organiza, xa sen poder cancelar: só «Deixar de buscar»', async () => {
    const { fixture, el } = await render(organizer());
    await organizerBookingLoaded(fixture, organizerBooking({ canCancel: false }));
    expect(el.querySelector('[data-match-stop]')).not.toBeNull();
    expect(el.querySelector('[data-match-cancel]')).toBeNull();
  });

  it('un erro ao unirse (completa entrementres): o aviso e a tarxeta co estado de agora', async () => {
    const { fixture, el } = await render(myMatch());
    el.querySelector<HTMLButtonElement>('[data-match-join]')!.click();
    flushProblem(await expectRequest({ method: 'POST', url: MINE + '/players' }), 409, 'booking.match-full');
    (await expectRequest({ method: 'GET', url: MINE })).flush(myMatch({ total: 10 }));
    await settle(fixture);
    expect(el.textContent).toContain('A partida xa está completa.');
    expect(text(el.querySelector('[data-match-full]'))).toBe('Completa');
    expect(el.querySelector('[data-match-join]')).toBeNull();
  });

  it('dentro dunha oculta, pechada ou cancelada: o aviso do concello e o estado', async () => {
    const hidden = await render(myMatch({ me: { role: 'CONFIRMED', playerId: 'p1' }, hidden: true }));
    expect(text(hidden.el.querySelector('[data-match-hidden]'))).toContain('O concello ocultou esta partida');
    TestBed.resetTestingModule();
    const closed = await render(myMatch({ status: 'CLOSED' }));
    expect(text(closed.el.querySelector('[data-match-closed]'))).toBe('Xa non se busca máis xente.');
    TestBed.resetTestingModule();
    const cancelled = await render(myMatch({ status: 'CANCELLED' }));
    expect(text(cancelled.el.querySelector('[data-match-cancelled]'))).toBe('Esta partida cancelouse.');
    TestBed.resetTestingModule();
    const rejected = await render(myMatch({ me: { role: 'REJECTED', playerId: 'p1' } }));
    expect(rejected.el.textContent).toContain('Non entraches nesta partida');
  });

  it('xa non está (404): «non está dispoñible» como h1; outro erro, «Tentar de novo» volve pedir', async () => {
    const gone = await render(404, anonymous());
    expect(text(gone.el.querySelector('h1'))).toBe('Esta partida xa non está dispoñible');
    TestBed.resetTestingModule();
    const { fixture, el } = await render(500, anonymous());
    expect(el.textContent).toContain('Non se puido cargar a partida');
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Tentar de novo'))!.click();
    (await expectRequest(PUBLIC)).flush(publicMatch());
    (await expectRequest(FACILITY)).flush(WITH_POINT);
    await settle(fixture);
    expect(text(el.querySelector('h1'))).toBe('Pachanga de venres');
  });
});

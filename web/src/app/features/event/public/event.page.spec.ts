// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { MyEventRegistrationResponse, PublicEventResponse } from '../../../shared/events/public-events.store';
import { myRegistration, publicActivity } from '../../../shared/events/public-event.fixture';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { publicEvent } from '../event.fixture';
import { EventJoinComponent, anyoneLeft } from './event-join.component';
import { EventPage, eventAction } from './event.page';

const BASE = '/api/v1/municipalities/naron/events/e1';
const ACTIVITY = '/api/v1/municipalities/naron/events/a1';
const DEPENDENTS = '/api/v1/me/dependents';

const LUA = { id: 'd1', name: 'Lúa Souto Pena', birthYear: 2015, version: 0 };
const BRAIS = { id: 'd2', name: 'Brais Souto Pena', birthYear: 2011, version: 0 };

describe('eventAction: que se pode facer para apuntar a alguén', () => {
  it('segundo o evento e se queda alguén da conta por apuntar', () => {
    expect(eventAction(publicEvent())).toBe('join');
    expect(eventAction(publicEvent({ approval: true }))).toBe('ask');
    expect(eventAction(publicEvent({ free: 0, open: false, waitlist: true }))).toBe('wait');
    expect(eventAction(publicEvent({ free: 1, open: false, waitlist: true }))).toBe('wait');
    expect(eventAction(publicEvent({ free: 0, open: false }))).toBe('wait');
    expect(eventAction(publicEvent({ free: 0, approval: true }))).toBe('ask');
    expect(eventAction(publicEvent({ free: 0, open: false, approval: true }))).toBe('closed');
    expect(eventAction(publicEvent({ open: false }))).toBe('closed');
    expect(eventAction(publicEvent(), false)).toBe('none');
  });

  it('queda alguén por apuntar: eu ou unha persoa a cargo sen inscrición', () => {
    const lua = { id: 'd1', name: 'Lúa' };
    expect(anyoneLeft([], [])).toBe(true);
    expect(anyoneLeft([myRegistration()], [])).toBe(false);
    expect(anyoneLeft([myRegistration()], [{ id: 'd1' }])).toBe(true);
    expect(anyoneLeft([myRegistration(), myRegistration({ dependent: lua })], [{ id: 'd1' }])).toBe(false);
  });
});

describe('EventPage: a ficha dun evento ou dunha actividade', () => {
  afterEach(() => {
    vi.useRealTimers();
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

  const settle = async (fixture: { whenStable: () => Promise<unknown> }) => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };

  /** Con sesión: o evento, as inscricións da conta e as persoas a cargo. */
  async function render(
    event: PublicEventResponse,
    registrations: MyEventRegistrationResponse[] = [],
    dependents: object[] = [],
  ) {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({})), provideRouter([])] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(EventPage);
    fixture.componentRef.setInput('id', event.id);
    const el = fixture.nativeElement as HTMLElement;
    const base = `/api/v1/municipalities/naron/events/${event.id}`;
    (await expectRequest(DEPENDENTS)).flush(dependents);
    (await expectRequest((r) => r.method === 'GET' && r.url === base)).flush(event);
    (await expectRequest(base + '/registrations/mine')).flush(registrations);
    await settle(fixture);
    return { fixture, el };
  }

  /** Despois dunha acción, a ficha volve pedir o evento e as inscricións. */
  async function refresh(base: string, event: PublicEventResponse, registrations: MyEventRegistrationResponse[]) {
    (await expectRequest((r) => r.method === 'GET' && r.url === base)).flush(event);
    (await expectRequest(base + '/registrations/mine')).flush(registrations);
  }

  it('un evento dun día: cando, onde, prazas, prezo co pago no mostrador e «Apuntarme»; axe', async () => {
    const { el } = await render(publicEvent({ priceCents: 500 }));
    expect(el.querySelector('h1')?.textContent).toContain('Carreira popular de Nadal');
    expect(text(el.querySelector('[data-event-when]'))).toBe('domingo, 20 de decembro · 11:00');
    expect(el.querySelector('[data-event-dates]')).toBeNull();
    expect(el.querySelector('[data-event-age]')).toBeNull();
    expect(el.querySelector('[data-event-facility]')?.getAttribute('href')).toBe('/naron/instalacions/f1');
    expect(text(el.querySelector('[data-event-seats]'))).toBe('38 libres de 200');
    expect(text(el.querySelector('[data-event-price]'))?.replace(/\s/g, ' ')).toBe('5,00 €');
    expect(el.textContent).toContain('Págase no mostrador');
    // Sen persoas a cargo nin límite de idade, sen «Para quen».
    expect(el.querySelector('furar-person-picker')).toBeNull();
    expect(text(el.querySelector('[data-event-join]'))).toBe('Apuntarme');
    await expectNoAxeViolations(el);
  });

  it('unha actividade: horario e datas, idade, prazas e prezo co período', async () => {
    const { el } = await render(publicActivity({ partName: 'Vaso pequeno' }));
    expect(text(el.querySelector('[data-event-when]'))).toBe('Luns e mércores · 17:00–18:00');
    expect(text(el.querySelector('[data-event-part]'))).toBe('Vaso pequeno');
    expect(text(el.querySelector('[data-event-dates]'))).toBe('Do 6 de outubro ao 24 de xuño');
    expect(text(el.querySelector('[data-event-age]'))).toBe('Nacidos entre 2014 e 2016');
    expect(text(el.querySelector('[data-event-seats]'))).toBe('5 libres de 20');
    expect(text(el.querySelector('[data-event-price]'))?.replace(/\s/g, ' ')).toBe('25,00 € ao trimestre');
  });

  it('«Para quen»: eu e as persoas a cargo; a que non entra pola idade, atenuada co motivo; apunta á escollida', async () => {
    const { fixture, el } = await render(publicActivity(), [], [BRAIS, LUA]);
    const options = [...el.querySelectorAll('[data-event-who] [role="option"]')];
    expect(
      options.map((o) => [
        text(o.querySelector('.f-person-picker__avatar')),
        text(o.querySelector('.f-person-picker__who')),
      ]),
    ).toEqual([
      ['XN', 'Eu'],
      ['BS', 'Brais Souto PenaNon entra: é para nacidos entre 2014 e 2016'],
      ['LS', 'Lúa Souto PenaAo meu cargo · 2015'],
    ]);
    expect(el.querySelector('[data-event-who] [role="listbox"]')?.getAttribute('aria-labelledby')).toBe('event-who');
    expect(options[1].getAttribute('aria-disabled')).toBe('true');
    // De primeiras, a persoa a cargo que entra; a que non entra non se escolle.
    expect(options[2].getAttribute('aria-selected')).toBe('true');
    (options[1] as HTMLElement).click();
    await settle(fixture);
    expect(options[1].getAttribute('aria-selected')).toBe('false');
    expect(el.querySelector('[data-event-birth]')).toBeNull();
    expect(el.querySelector('[data-event-add-person]')).not.toBeNull();
    await expectNoAxeViolations(el);

    el.querySelector<HTMLButtonElement>('[data-event-join]')!.click();
    const post = await expectRequest((r) => r.method === 'POST' && r.url === ACTIVITY + '/registration');
    expect(post.request.body).toEqual({ dependentId: 'd1' });
    post.flush(myRegistration({ dependent: { id: 'd1', name: LUA.name }, event: publicActivity({ free: 4 }) }));
    await refresh(ACTIVITY, publicActivity({ free: 4 }), [
      myRegistration({
        dependent: { id: 'd1', name: LUA.name },
        payment: 'PENDING',
        nextSessions: [{ date: '2098-10-06', startsAt: '2098-10-06T15:00:00Z', endsAt: '2098-10-06T16:00:00Z' }],
        event: publicActivity({ free: 4 }),
      }),
    ]);
    await settle(fixture);
    const band = el.querySelector('[data-band="joined"]');
    expect(text(band?.querySelector('.f-status-band__heading'))).toBe('Praza confirmada');
    expect(text(band?.querySelector('[data-band-person]'))).toBe('Lúa Souto Pena');
    expect(text(band?.querySelector('[data-payment]'))).toBe('Pendente de pago');
    expect(text(band?.querySelector('[data-event-leave]'))).toBe('Dar de baixa');
    expect(band?.querySelector('[data-band-sessions]')).not.toBeNull();
    // Lúa xa está: quedan eu e Brais (que non entra).
    expect([...el.querySelectorAll('[data-event-who] [role="option"]')]).toHaveLength(2);
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'event.public.joinedToast' });
  });

  it('«Eu» cunha actividade con límite de idade: a data de nacemento, obrigatoria, co erro debaixo', async () => {
    const { fixture, el } = await render(publicActivity());
    expect(el.querySelectorAll('[data-event-who] [role="option"]')).toHaveLength(1);
    const field = el.querySelector('[data-event-birth]');
    expect(field).not.toBeNull();
    el.querySelector<HTMLButtonElement>('[data-event-join]')!.click();
    await settle(fixture);
    backend().expectNone((r) => r.method === 'POST');
    expect(text(el.querySelector('#event-birth-date-error'))).toBe('Indica a data de nacemento.');
    expect(el.querySelector('#event-birth-date')?.getAttribute('aria-invalid')).toBe('true');
    await vi.waitFor(() => expect(document.activeElement?.id).toBe('event-birth-date'));
  });

  it('a data que non entra: o erro da API debaixo do campo', async () => {
    const { fixture, el } = await render(publicActivity());
    const join = fixture.debugElement.query(By.directive(EventJoinComponent)).componentInstance as unknown as {
      birthDate: { setValue: (v: string) => void };
    };
    join.birthDate.setValue('1984-05-12');
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('[data-event-join]')!.click();
    const post = await expectRequest((r) => r.method === 'POST');
    expect(post.request.body).toEqual({ birthDate: '1984-05-12' });
    flushProblem(post, 422, 'event.age-not-allowed');
    await refresh(ACTIVITY, publicActivity(), []);
    await settle(fixture);
    expect(text(el.querySelector('#event-birth-date-error'))).toBe(
      'Ese ano de nacemento non entra na idade deste evento.',
    );
    expect(text(el.querySelector('furar-problem-alert'))).toBeFalsy();
  });

  it('completa sen aprobación: a banda «Non quedan prazas» con «Avísame se queda sitio»; entra na lista', async () => {
    const full = publicActivity({
      free: 0,
      open: false,
      waitlist: true,
      waiting: 2,
      birthYearFrom: undefined,
      birthYearTo: undefined,
    });
    const { fixture, el } = await render(full);
    const band = el.querySelector('[data-band="full"]');
    expect(text(band?.querySelector('.f-status-band__heading'))).toBe('Non quedan prazas');
    expect(band?.querySelector('[data-band-how] button')?.getAttribute('aria-expanded')).toBe('false');
    expect(el.querySelector('[data-event-join]')).toBeNull();
    expect(el.querySelector('[data-event-seats] [data-seats="full"]')?.textContent?.trim()).toBe('Completa');
    await expectNoAxeViolations(el);

    band!.querySelector<HTMLButtonElement>('[data-event-wait]')!.click();
    const waiting = myRegistration({ status: 'WAITING', position: 3, event: { ...full, waiting: 3 } });
    (await expectRequest((r) => r.method === 'POST')).flush(waiting);
    await refresh(ACTIVITY, { ...full, waiting: 3 }, [waiting]);
    await settle(fixture);
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'event.band.waitToast' });
    const mine = el.querySelector('[data-band="waiting"]');
    expect(text(mine?.querySelector('.f-status-band__heading'))).toBe('Estás na lista de espera · 3.ª');
    expect(text(mine?.querySelector('[data-band-line]'))).toBe('Avisámoste por correo se queda sitio.');
    expect(mine?.querySelector('[data-event-leave]')?.getAttribute('data-variant')).toBe('quiet');
    expect(mine?.querySelector('[role="status"]')).not.toBeNull();
    // Xa non queda ninguén da conta: sen banda «Non quedan prazas».
    expect(el.querySelector('[data-band="full"]')).toBeNull();

    mine!.querySelector<HTMLButtonElement>('[data-event-leave]')!.click();
    (await expectRequest((r) => r.method === 'DELETE' && r.url === ACTIVITY + '/registration')).flush(null);
    await refresh(ACTIVITY, full, []);
    await settle(fixture);
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'event.band.leftListToast' });
    expect(el.querySelector('[data-band="full"]')).not.toBeNull();
  });

  it('praza libre para ti: ata cando é só túa, «Confirmar a praza»; Prazas non di «Completa»', async () => {
    // A media mañá: «Ata as HH:MM», o mesmo día (á noite, a hora sería mañá e levaría o día).
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-16T10:00:00Z'));
    const until = new Date(Date.now() + 30 * 60_000).toISOString();
    const event = publicActivity({ free: 1, open: false, waitlist: true, waiting: 3 });
    const offer = myRegistration({ status: 'NOTIFIED', position: 1, exclusiveUntil: until, event });
    const { fixture, el } = await render(event, [offer]);
    const band = el.querySelector('[data-band="offer"]');
    expect(band?.getAttribute('data-tone')).toBe('warn');
    expect(text(band?.querySelector('.f-status-band__heading'))).toBe('Hai unha praza libre para ti');
    expect(text(band?.querySelector('[data-band-line]'))).toMatch(/^Ata as \d\d:\d\d só o sabes ti\.$/);
    expect(text(band?.querySelector('[data-event-confirm]'))).toBe('Confirmar a praza');
    expect(text(band?.querySelector('[data-event-leave]'))).toBe('Non a quero');
    expect(text(el.querySelector('[data-event-seats]'))).toBe('1 libre, para a lista de espera');
    expect(el.querySelector('[data-band="full"]')).toBeNull();
    await expectNoAxeViolations(el);

    band!.querySelector<HTMLButtonElement>('[data-event-confirm]')!.click();
    const confirm = await expectRequest((r) => r.method === 'POST' && r.url === ACTIVITY + '/registration/confirm');
    confirm.flush(myRegistration({ event: publicActivity({ free: 0 }) }));
    await refresh(ACTIVITY, publicActivity({ free: 0 }), [myRegistration({ event: publicActivity({ free: 0 }) })]);
    await settle(fixture);
    expect(el.querySelector('[data-band="joined"]')).not.toBeNull();
    expect(text(el.querySelector('[data-band="joined"] [data-event-leave]'))).toBe('Darme de baixa');
  });

  it('pasada a hora só para ti: avisamos a máis persoas e pódese confirmar igual', async () => {
    const event = publicActivity({ free: 1, open: false, waitlist: true });
    const offer = myRegistration({
      status: 'NOTIFIED',
      exclusiveUntil: new Date(Date.now() - 60_000).toISOString(),
      event,
    });
    const { el } = await render(event, [offer]);
    expect(text(el.querySelector('[data-band="offer"] [data-band-line]'))).toBe(
      'Xa avisamos tamén a máis persoas: é túa se a confirmas mentres siga libre.',
    );
    expect(el.querySelector('[data-band="offer"] [data-event-confirm]')).not.toBeNull();
  });

  it('a praza xa se cubriu: segue na lista co seu posto', async () => {
    const event = publicActivity({ free: 0, open: false, waitlist: true });
    const { el } = await render(event, [myRegistration({ status: 'WAITING', position: 3, seatCovered: true, event })]);
    const band = el.querySelector('[data-band="covered"]');
    expect(text(band?.querySelector('.f-status-band__heading'))).toBe('A praza xa se cubriu');
    expect(text(band?.querySelector('[data-band-line]'))).toBe('Segues na lista, 3.ª.');
    expect(text(band?.querySelector('[data-event-leave]'))).toBe('Saír da lista');
  });

  it('confirmar cando xa non queda: o aviso de erro e a ficha ao día', async () => {
    const event = publicActivity({ free: 1, open: false, waitlist: true });
    const offer = myRegistration({ status: 'NOTIFIED', event });
    const { fixture, el } = await render(event, [offer]);
    el.querySelector<HTMLButtonElement>('[data-event-confirm]')!.click();
    flushProblem(await expectRequest((r) => r.method === 'POST'), 409, 'event.full');
    const covered = myRegistration({ status: 'WAITING', position: 1, seatCovered: true, event: { ...event, free: 0 } });
    await refresh(ACTIVITY, { ...event, free: 0 }, [covered]);
    await settle(fixture);
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'event.band.confirmError', tone: 'danger' });
    expect(el.querySelector('[data-band="covered"]')).not.toBeNull();
  });

  it('«Pedir praza» con aprobación: queda pendente coa súa banda', async () => {
    const { fixture, el } = await render(publicEvent({ approval: true }));
    const ask = el.querySelector<HTMLButtonElement>('[data-event-join]')!;
    expect(text(ask)).toBe('Pedir praza');
    ask.click();
    const pending = myRegistration({ status: 'PENDING', event: publicEvent({ approval: true }) });
    (await expectRequest((r) => r.method === 'POST' && r.url === BASE + '/registration')).flush(pending);
    await refresh(BASE, publicEvent({ approval: true }), [pending]);
    await settle(fixture);
    expect(el.querySelector('[data-event-action]')?.getAttribute('data-event-action')).toBe('none');
    expect(text(el.querySelector('[data-band="pending"] .f-status-band__heading'))).toBe('Pediches praza');
    expect(text(el.querySelector('[data-band="pending"] [data-event-leave]'))).toBe('Retirar a solicitude');
  });

  it('completa con aprobación: o chip en «Prazas» e «Pedir praza» activo', async () => {
    const { el } = await render(publicEvent({ free: 0, approval: true }));
    expect(el.querySelector('[data-event-seats] [data-seats="full"]')).not.toBeNull();
    const ask = el.querySelector<HTMLButtonElement>('[data-event-join]')!;
    expect(text(ask)).toBe('Pedir praza');
    expect(ask.disabled).toBe(false);
    expect(el.querySelector('[data-band="full"]')).toBeNull();
  });

  it('rexeitada: a banda sen botóns', async () => {
    const event = publicEvent({ approval: true });
    const { el } = await render(event, [myRegistration({ status: 'REJECTED', event })]);
    expect(el.querySelector('[data-band="rejected"]')).not.toBeNull();
    expect(el.querySelector('[data-event-leave]')).toBeNull();
    expect(el.querySelector('[data-event-join]')).toBeNull();
  });

  it('pechado: a frase sen botón', async () => {
    const { el } = await render(publicEvent({ open: false }));
    expect(el.querySelector('[data-event-closed]')).not.toBeNull();
  });

  it('sen sesión: pide só o evento; «Avísame se queda sitio» leva a entrar e volver á ficha', async () => {
    const session = fakeSession({});
    session.authenticated.set(false);
    TestBed.configureTestingModule({ providers: [...provideTestApi(session), provideRouter([])] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(EventPage);
    fixture.componentRef.setInput('id', 'a1');
    const el = fixture.nativeElement as HTMLElement;
    (await expectRequest(ACTIVITY)).flush(publicActivity({ free: 0, open: false, waitlist: true }));
    await settle(fixture);
    expect(el.querySelector('furar-person-picker')).toBeNull();
    el.querySelector<HTMLButtonElement>('[data-event-wait]')!.click();
    await vi.waitFor(() => expect(session.login).toHaveBeenCalled());
    backend().expectNone((r) => r.method === 'POST');
  });

  it('non existe: «non atopado» sen reintentar', async () => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({})), provideRouter([])] });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(EventPage);
    fixture.componentRef.setInput('id', 'e1');
    const el = fixture.nativeElement as HTMLElement;
    (await expectRequest(DEPENDENTS)).flush([]);
    flushProblem(await expectRequest((r) => r.url === BASE), 404, 'event.not-found');
    backend()
      .match((r) => r.url.startsWith(BASE))
      .forEach((r) => r.flush([]));
    await settle(fixture);
    expect(el.querySelector('furar-status-state')).not.toBeNull();
    expect(el.querySelector('furar-status-state button')).toBeNull();
  });
});

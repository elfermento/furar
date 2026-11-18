// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { ToastService } from '../ui/toast/toast.service';
import { registrationChips } from './my-event-chips';
import { myRegistration, publicActivity, publicEvent } from './public-event.fixture';
import { MyEventsComponent } from './my-events.component';

const BASE = '/api/v1/municipalities/naron/events';

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class StubPage {}

describe('MyEventsComponent: os eventos en «As miñas reservas»', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');

  async function render(items: unknown[]) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({})),
        provideRouter([{ path: 'naron/eventos/:id', component: StubPage }]),
      ],
    });
    const fixture = TestBed.createComponent(MyEventsComponent);
    fixture.componentRef.setInput('slug', 'naron');
    const counts: number[] = [];
    fixture.componentInstance.loaded.subscribe((n) => counts.push(n));
    (await expectRequest(BASE + '/mine')).flush(items);
    const settle = async () => {
      await new Promise((resolve) => setTimeout(resolve));
      await fixture.whenStable();
    };
    await settle();
    return { el: fixture.nativeElement as HTMLElement, counts, settle };
  }

  async function openDetail(card: Element, settle: () => Promise<void>) {
    card.querySelector<HTMLButtonElement>('.f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-my-event-detail]')).toBeTruthy();
    });
    return dialog()!;
  }

  it('a mesma tarxeta ca as reservas: pulsable enteira, sen botóns, co prezo, a data, o lugar e os chips; axe', async () => {
    const { el, counts } = await render([
      { status: 'CONFIRMED', payment: 'PENDING', event: publicEvent({ priceCents: 500 }) },
      { status: 'PENDING', event: publicEvent({ id: 'e2', facility: undefined, place: 'Praza do concello' }) },
    ]);
    const cards = [...el.querySelectorAll('[data-my-event]')];
    expect(cards).toHaveLength(2);
    const [first, second] = cards;
    expect(first.tagName.toLowerCase()).toBe('furar-status-card');
    expect(first.querySelectorAll('button, a')).toHaveLength(1);
    expect(text(first.querySelector('h2'))).toBe('Carreira popular de Nadal');
    expect(text(first.querySelector('[data-card-amount]'))).toBe('5,00 €');
    expect(text(first.querySelector('[data-line="when"]'))).toBe('domingo, 20 de decembro · 11:00');
    expect(first.querySelectorAll('[data-line="when"] .f-status-card__part')).toHaveLength(2);
    expect(text(first.querySelector('[data-line="place"]'))).toBe('Pista de atletismo');
    expect([...first.querySelectorAll('[data-card-chips] furar-tag')].map(text)).toEqual([
      'Apuntada',
      'Pendente de pago',
    ]);
    expect(second.querySelector('[data-card-amount]')).toBeNull();
    expect(text(second.querySelector('[data-line="place"]'))).toBe('Praza do concello');
    expect(second.querySelector('[data-chip="PENDING"]')?.getAttribute('data-tone')).toBe('warn');
    expect(second.querySelector('[data-payment]')).toBeNull();
    expect(counts).toEqual([2]);
    await expectNoAxeViolations(el);
  });

  it('o detalle: a banda de estado, lugar, para quen e prezo; «Ver o evento»', async () => {
    const { el, settle } = await render([
      { status: 'CONFIRMED', payment: 'PAID', seatCovered: false, event: publicEvent({ priceCents: 500 }) },
    ]);
    const detail = await openDetail(el.querySelector('[data-my-event="e1"]')!, settle);
    expect(text(detail.querySelector('h2'))).toBe('Carreira popular de Nadal');
    expect(text(detail.querySelector('.f-dialog__subheading'))).toBe('domingo, 20 de decembro · 11:00');
    const band = detail.querySelector('[data-band="joined"]')!;
    expect(text(band.querySelector('.f-status-band__heading'))).toBe('Praza confirmada');
    expect(text(band.querySelector('[data-payment]'))).toBe('Pagada');
    expect(text(band.querySelector('[data-event-leave]'))).toBe('Darme de baixa');
    // Un evento dun día non ten sesións.
    expect(band.querySelector('[data-band-sessions]')).toBeNull();
    const rows = [...detail.querySelectorAll('.f-value-list__row')].map((row) => [
      row.getAttribute('data-value'),
      text(row.querySelector('dt')),
      text(row.querySelector('dd'))?.replace(/\s/g, ' '),
    ]);
    expect(rows).toEqual([
      ['place', 'Lugar', 'Pista de atletismo'],
      ['person', 'Para', 'Xestora Narón'],
      ['price', 'Prezo', '5,00 €'],
    ]);
    const actions = [...detail.querySelectorAll('furar-action-item')];
    expect(actions.map(text)).toEqual(['Ver o evento']);
    await expectNoAxeViolations(detail);
    detail.querySelector<HTMLAnchorElement>('[data-my-event-open]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
    });
    expect(TestBed.inject(Router).url).toBe('/naron/eventos/e1');
  });

  it('unha actividade: a próxima sesión na tarxeta; no detalle, o horario e «Próximas sesións» pregadas', async () => {
    const lua = { id: 'd1', name: 'Lúa Souto Pena' };
    const nextSessions = [
      { date: '2098-10-08', startsAt: '2098-10-08T15:00:00Z', endsAt: '2098-10-08T16:00:00Z' },
      { date: '2098-10-15', startsAt: '2098-10-15T15:00:00Z', endsAt: '2098-10-15T16:00:00Z' },
    ];
    const { el, settle } = await render([
      myRegistration({
        dependent: lua,
        payment: 'PENDING',
        nextSessions,
        event: publicActivity({ partName: 'Vaso pequeno' }),
      }),
    ]);
    const card = el.querySelector('[data-my-event="a1"]')!;
    expect(card.getAttribute('data-dependent')).toBe('d1');
    expect(text(card.querySelector('[data-line="when"]'))).toBe('Próxima: mércores, 8 de outubro · 17:00');
    expect(text(card.querySelector('[data-line="place"]'))).toBe('Piscina municipal · Lúa Souto Pena');
    expect([...card.querySelectorAll('[data-card-chips] furar-tag')].map(text)).toEqual([
      'Apuntada',
      'Pendente de pago',
    ]);
    expect(text(card.querySelector('[data-card-amount]'))?.replace(/\s/g, ' ')).toBe('25,00 €');
    const detail = await openDetail(card, settle);
    expect(text(detail.querySelector('.f-dialog__subheading'))).toBe('Luns e mércores · 17:00–18:00');
    expect(text(detail.querySelector('[data-band-person]'))).toBe('Lúa Souto Pena');
    expect(text(detail.querySelector('[data-value="price"] dd'))?.replace(/\s/g, ' ')).toBe('25,00 € ao trimestre');
    expect(text(detail.querySelector('[data-value="person"] dd'))).toBe('Lúa Souto Pena');
    const sessions = detail.querySelector('[data-band-sessions]')!;
    expect(sessions.querySelector('button')?.getAttribute('aria-expanded')).toBe('false');
    // As sesións reais da API (o 13 cancelouse: non vén).
    expect([...sessions.querySelectorAll('li')].map(text)).toEqual([
      'Mércores, 8 de outubro · 17:00',
      'Mércores, 15 de outubro · 17:00',
    ]);
    expect(text(detail.querySelector('[data-value="place"] dd'))).toBe('Piscina municipal · Vaso pequeno');
    expect(text(detail.querySelector('[data-my-event-open]'))).toBe('Ver a actividade');
  });

  it('na lista e avisada: as súas tarxetas (a avisada primeiro) e, no detalle, «Confirmar a praza»', async () => {
    const until = new Date(Date.now() + 30 * 60_000).toISOString();
    const { el, settle } = await render([
      myRegistration({ status: 'WAITING', position: 3, event: publicActivity({ id: 'a2', free: 0 }) }),
      myRegistration({ status: 'NOTIFIED', position: 1, exclusiveUntil: until, event: publicActivity({ free: 1 }) }),
    ]);
    const cards = [...el.querySelectorAll('[data-my-event]')];
    expect(cards.map((c) => c.getAttribute('data-registration'))).toEqual(['NOTIFIED', 'WAITING']);
    const [offer, waiting] = cards;
    expect(text(offer.querySelector('[data-line="when"]'))).toMatch(/^Ata as \d\d:\d\d só o sabes ti$/);
    expect(text(offer.querySelector('[data-chip="NOTIFIED"]'))).toBe('Praza libre para ti');
    expect(offer.querySelector('[data-chip="NOTIFIED"]')?.getAttribute('data-tone')).toBe('warn');
    expect(offer.querySelector('[data-card-amount]')).toBeNull();
    expect(text(waiting.querySelector('[data-line="when"]'))).toBe('Luns e mércores · 17:00–18:00');
    expect(text(waiting.querySelector('[data-chip="WAITING"]'))).toBe('Na lista · 3.ª');
    expect(waiting.querySelector('[data-chip="WAITING"]')?.getAttribute('data-tone')).toBe('muted');
    await expectNoAxeViolations(el);

    const detail = await openDetail(offer, settle);
    expect(detail.querySelector('[data-band="offer"]')).not.toBeNull();
    await expectNoAxeViolations(detail);
    detail.querySelector<HTMLButtonElement>('[data-event-confirm]')!.click();
    (await expectRequest((r) => r.method === 'POST' && r.url === BASE + '/a1/registration/confirm')).flush(
      myRegistration({ event: publicActivity({ free: 0 }) }),
    );
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-my-event="a1"]')?.getAttribute('data-registration')).toBe('CONFIRMED');
    });
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'event.public.joinedToast' });
  });

  it('saír da lista quita a tarxeta e o foco vai ao título', async () => {
    const { el, settle, counts } = await render([
      myRegistration({ status: 'WAITING', position: 2, dependent: { id: 'd1', name: 'Lúa' }, event: publicActivity() }),
    ]);
    const heading = document.createElement('h1');
    heading.textContent = 'As miñas reservas';
    document.body.prepend(heading);
    try {
      const detail = await openDetail(el.querySelector('[data-my-event="a1"]')!, settle);
      detail.querySelector<HTMLButtonElement>('[data-event-leave]')!.click();
      const request = await expectRequest((r) => r.method === 'DELETE' && r.url.startsWith(BASE + '/a1/registration'));
      expect(request.request.params.get('dependentId')).toBe('d1');
      request.flush(null);
      await vi.waitFor(async () => {
        await settle();
        expect(el.querySelector('[data-my-event]')).toBeNull();
      });
      expect(counts).toEqual([1, 0]);
      expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'event.band.leftListToast' });
      await vi.waitFor(() => expect(document.activeElement).toBe(heading));
    } finally {
      heading.remove();
    }
  });

  it('«Desapuntarme» no detalle quita a tarxeta; se falla, o aviso e a tarxeta queda', async () => {
    const { el, counts, settle } = await render([
      { status: 'CONFIRMED', event: publicEvent() },
      { status: 'CONFIRMED', event: publicEvent({ id: 'e2' }) },
    ]);
    let detail = await openDetail(el.querySelector('[data-my-event="e1"]')!, settle);
    detail.querySelector<HTMLButtonElement>('[data-event-leave]')!.click();
    (await expectRequest((r) => r.method === 'DELETE' && r.url === BASE + '/e1/registration')).flush(null);
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelectorAll('[data-my-event]')).toHaveLength(1);
    });
    expect(counts).toEqual([2, 1]);
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'event.public.leftToast' });

    detail = await openDetail(el.querySelector('[data-my-event="e2"]')!, settle);
    detail.querySelector<HTMLButtonElement>('[data-event-leave]')!.click();
    flushProblem(
      await expectRequest((r) => r.method === 'DELETE' && r.url === BASE + '/e2/registration'),
      500,
      'common.unexpected',
    );
    await vi.waitFor(async () => {
      await settle();
      // O erro, no aviso flotante de erro (non marcha só); sen motivo concreto, «Téntao de novo.».
      expect(TestBed.inject(ToastService).current()).toMatchObject({
        key: 'event.public.leaveError',
        tone: 'danger',
        reason: { key: 'ui.toast.retry' },
      });
    });
    expect(el.querySelectorAll('[data-my-event]')).toHaveLength(1);
  });

  it('sen lugar nin prezo; pechar o detalle sen escoller non fai nada', async () => {
    const { el, settle } = await render([
      { status: 'PENDING', seatCovered: false, event: publicEvent({ facility: undefined }) },
    ]);
    expect(el.querySelector('[data-line="place"]')).toBeNull();
    const detail = await openDetail(el.querySelector('[data-my-event="e1"]')!, settle);
    expect([...detail.querySelectorAll('.f-value-list__row')].map((row) => row.getAttribute('data-value'))).toEqual([
      'person',
    ]);
    expect(text(detail.querySelector('[data-band="pending"] [data-event-leave]'))).toBe('Retirar a solicitude');
    detail.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
    await vi.waitFor(() => expect(dialog()).toBeNull());
    backend().expectNone((r) => r.method === 'DELETE');
  });

  it('se a lista falla, non pinta nada e di que hai cero', async () => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(fakeSession({})), provideRouter([])] });
    const fixture = TestBed.createComponent(MyEventsComponent);
    fixture.componentRef.setInput('slug', 'naron');
    const counts: number[] = [];
    fixture.componentInstance.loaded.subscribe((n) => counts.push(n));
    flushProblem(await expectRequest(BASE + '/mine'), 500, 'common.unexpected');
    await vi.waitFor(() => expect(counts).toEqual([0]));
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-my-events]')).toBeNull();
  });

  it('as etiquetas: inscrición coa paleta de estados e o pago só nun evento de pago aceptado', () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const paid = publicEvent({ priceCents: 500 });
    const keys = (item: Parameters<typeof registrationChips>[0]) => {
      const chips = registrationChips(item);
      return [
        `${chips.status.status}:${chips.status.tone}`,
        chips.payment && `${chips.payment.key}:${chips.payment.tone}`,
      ];
    };
    expect(keys({ event: paid })).toEqual(['CONFIRMED:success', null]);
    expect(keys({ status: 'REJECTED', payment: 'PENDING', event: paid })).toEqual(['REJECTED:danger', null]);
    expect(keys({ status: 'CONFIRMED', payment: 'WAIVED', event: paid })).toEqual(['CONFIRMED:success', 'WAIVED:info']);
    expect(keys({ status: 'CONFIRMED', payment: 'PAID', event: publicEvent() })).toEqual(['CONFIRMED:success', null]);
    expect(keys({ status: 'WAITING', event: paid })).toEqual(['WAITING:muted', null]);
    expect(keys({ status: 'NOTIFIED', event: paid })).toEqual(['NOTIFIED:warn', null]);
    expect(registrationChips({ status: 'WAITING', position: 2, event: paid }).status).toMatchObject({
      key: 'event.mine.waitingAt',
      params: { position: '2.ª' },
    });
  });
});

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { ToastService } from '../ui/toast/toast.service';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { myMatch } from '../../../testing/open-matches';
import { MyOpenMatchesComponent } from './my-open-matches.component';

const MINE = '/api/v1/municipalities/naron/me/open-matches';

describe('«As miñas reservas»: as partidas ás que me unín (ADR-0040)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(items: object[] | 'error') {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const fixture = TestBed.createComponent(MyOpenMatchesComponent);
    const loaded = vi.fn();
    fixture.componentInstance.loaded.subscribe(loaded);
    fixture.componentRef.setInput('slug', 'naron');
    const request = await expectRequest(MINE);
    if (items === 'error') flushProblem(request, 500, 'common.unexpected');
    else request.flush(items);
    await settle(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement, loaded };
  }

  const settle = async (fixture: { whenStable: () => Promise<unknown> }) => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();
  const dialog = () => document.querySelector<HTMLElement>('furar-dialog-container');

  it('as que estou ou pedín (non as que organizo): tarxeta pulsable co chip «Apuntada» ou «Pendente»; axe', async () => {
    const { el, loaded } = await render([
      myMatch({ me: { role: 'CONFIRMED', playerId: 'p1' } }),
      myMatch({ id: 'm2', me: { role: 'PENDING', playerId: 'p2' }, title: undefined }),
      myMatch({ id: 'm3', me: { role: 'ORGANIZER' } }),
      myMatch({ id: 'm4', me: { role: 'REJECTED', playerId: 'p4' } }),
    ]);
    expect(loaded).toHaveBeenLastCalledWith(2);
    expect(el.querySelectorAll('[data-my-open-match]')).toHaveLength(2);
    const first = el.querySelector('[data-my-open-match="m1"]')!;
    expect(text(first.querySelector('.f-status-card__name'))).toBe('Pachanga de venres');
    expect(text(first.querySelector('[data-line="place"]'))).toBe('Pavillón da Gándara · Fútbol sala');
    expect(text(first.querySelector('[data-line="organizer"]'))).toBe('Organiza: Uxía Pereira');
    expect(text(first.querySelector('[data-chip]'))).toBe('Apuntada');
    expect(first.querySelector('[data-chip]')?.getAttribute('data-tone')).toBe('success');
    const second = el.querySelector('[data-my-open-match="m2"] [data-chip]')!;
    expect(text(second)).toBe('Pendente');
    expect(second.getAttribute('data-tone')).toBe('warn');
    await expectNoAxeViolations(el);
  });

  it('o detalle: os datos, «Ver a partida» e «Saír»; saír quítaa e avisa', async () => {
    const { fixture, el, loaded } = await render([myMatch({ me: { role: 'CONFIRMED', playerId: 'p1' } })]);
    el.querySelector<HTMLButtonElement>('.f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-my-open-match-detail]')).toBeTruthy();
    });
    const detail = dialog()!;
    expect(text(detail.querySelector('[data-value="seats"] dd'))).toBe('4 de 10 · faltan 2');
    expect(text(detail.querySelector('[data-value="organizer"] dd'))).toBe('Uxía Pereira');
    expect(detail.querySelector('[data-my-open-match-open]')?.getAttribute('href')).toBe('/naron/partidas/m1');
    detail.querySelector<HTMLButtonElement>('[data-my-open-match-leave]')!.click();
    (await expectRequest({ method: 'DELETE', url: MINE + '/m1/players/me' })).flush(null);
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(el.querySelector('[data-my-open-match]')).toBeNull();
    });
    expect(loaded).toHaveBeenLastCalledWith(0);
  });

  it('pendente: «Retirar a solicitude»; se falla, a tarxeta queda', async () => {
    const { fixture, el } = await render([myMatch({ me: { role: 'PENDING', playerId: 'p1' } })]);
    el.querySelector<HTMLButtonElement>('.f-status-card__open')!.click();
    await vi.waitFor(async () => {
      await settle(fixture);
      expect(dialog()?.querySelector('[data-my-open-match-leave]')).toBeTruthy();
    });
    const leave = dialog()!.querySelector<HTMLButtonElement>('[data-my-open-match-leave]')!;
    expect(text(leave)).toBe('Retirar a solicitude');
    leave.click();
    flushProblem(await expectRequest({ method: 'DELETE', url: MINE + '/m1/players/me' }), 409, 'booking.match-started');
    await settle(fixture);
    expect(el.querySelector('[data-my-open-match]')).not.toBeNull();
    // O aviso flotante de erro co motivo da API («Non se puido saír da partida. A partida xa empezou.»).
    expect(TestBed.inject(ToastService).current()).toMatchObject({
      key: 'openMatch.mine.leaveError',
      tone: 'danger',
      reason: { key: 'errors.booking.match-started' },
    });
  });

  it('se falla a lectura, nada (e di que hai 0)', async () => {
    const { el, loaded } = await render('error');
    expect(el.querySelector('ul')).toBeNull();
    expect(loaded).toHaveBeenLastCalledWith(0);
  });
});

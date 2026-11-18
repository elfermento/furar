// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ViewportScroller } from '@angular/common';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { Event as RouterEvent, Navigation } from '@angular/router';
import { NavigationEnd, NavigationSkipped, Router, Scroll } from '@angular/router';
import { Subject } from 'rxjs';
import { KEEP_SCROLL_STATE, providePageScroll } from './page-scroll';

/** O router de verdade só emite `Scroll` tras arrancar a app: aquí emítense á man. */
describe('providePageScroll', () => {
  function setup() {
    const events = new Subject<RouterEvent>();
    const last = signal<Navigation | null>(null);
    const scroller = {
      setHistoryScrollRestoration: vi.fn(),
      scrollToPosition: vi.fn(),
      scrollToAnchor: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: { events, lastSuccessfulNavigation: last } },
        { provide: ViewportScroller, useValue: scroller },
        providePageScroll(),
      ],
    });
    TestBed.inject(Router);
    const navigate = (id: number, state?: Record<string, unknown>, position: [number, number] | null = null) => {
      last.set({ id, extras: { state } } as unknown as Navigation);
      events.next(new Scroll(new NavigationEnd(id, '/x', '/x'), position, null));
    };
    return { events, scroller, navigate };
  }

  it('o historial do navegador non restaura só: faino o router', () => {
    const { scroller } = setup();
    expect(scroller.setHistoryScrollRestoration).toHaveBeenCalledWith('manual');
  });

  it('unha navegación nova vai arriba; a dunha pestana (KEEP_SCROLL_STATE) non move a páxina', () => {
    const { scroller, navigate } = setup();
    navigate(1);
    expect(scroller.scrollToPosition).toHaveBeenLastCalledWith([0, 0]);
    scroller.scrollToPosition.mockClear();
    navigate(2, { [KEEP_SCROLL_STATE]: true });
    expect(scroller.scrollToPosition).not.toHaveBeenCalled();
    navigate(3, {});
    expect(scroller.scrollToPosition).toHaveBeenLastCalledWith([0, 0]);
  });

  it('atrás e adiante volven á posición gardada, tamén nas pestanas', () => {
    const { scroller, navigate } = setup();
    navigate(4, { [KEEP_SCROLL_STATE]: true }, [0, 640]);
    expect(scroller.scrollToPosition).toHaveBeenLastCalledWith([0, 640], { behavior: 'instant' });
  });

  it('cunha áncora ou con scroll manual, non fai nada (a áncora é do router)', () => {
    const { events, scroller } = setup();
    events.next(new Scroll(new NavigationEnd(5, '/x#a', '/x#a'), null, 'a'));
    events.next(new Scroll(new NavigationEnd(6, '/y', '/y'), null, null, 'manual'));
    expect(scroller.scrollToPosition).not.toHaveBeenCalled();
  });

  it('a mesma URL outra vez (navegación omitida) vai arriba', () => {
    const { events, scroller } = setup();
    events.next(new Scroll(new NavigationSkipped(7, '/x', ''), null, null));
    expect(scroller.scrollToPosition).toHaveBeenLastCalledWith([0, 0]);
  });
});

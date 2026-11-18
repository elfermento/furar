// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DeckStepper, fitDeck } from './how-it-works.component';
import {
  NARROW_QUERY,
  STATIC_QUERY,
  STEP_DWELL_FAST_MS,
  STEP_DWELL_MS,
  STEP_MOVE_MS,
  deckFits,
  pinnedDeck,
  sectionProgress,
  stepDelay,
  stepToward,
  targetStep,
} from './step-deck';

/**
 * Unha xanela: `pinned` falso é unha pantalla ancha e baixa (`STATIC_QUERY`); `narrow`, o móbil;
 * e, se se pide, `data-motion` en <html>.
 */
function fakeView(pinned: boolean, motion: string | null = null, narrow = false, innerHeight = 800): Window {
  const root = document.createElement('html');
  if (motion) root.setAttribute('data-motion', motion);
  return {
    innerHeight,
    document: { documentElement: root, querySelector: () => null },
    getComputedStyle: () => ({ getPropertyValue: () => '64px', marginBottom: '24px' }),
    matchMedia: (query: string) => ({
      matches: query === STATIC_QUERY ? !pinned : query === NARROW_QUERY ? narrow : false,
    }),
  } as unknown as Window;
}

/** Unha sección coas tres tarxetas e o título, cos altos dados. */
function deck(title: number, cards: readonly number[]): HTMLElement {
  const section = document.createElement('section');
  section.innerHTML = '<h2 class="f-steps__title"></h2>' + cards.map(() => '<li class="f-step"></li>').join('');
  const height = (el: Element, value: number) => Object.defineProperty(el, 'offsetHeight', { value });
  height(section.querySelector('.f-steps__title')!, title);
  section.querySelectorAll('.f-step').forEach((card, i) => height(card, cards[i]));
  return section;
}

describe('baralla «Así de fácil»', () => {
  it('o scroll escolle o paso: un terzo da sección cada un', () => {
    expect([0, 0.2, 0.34, 0.5, 0.67, 1, 2, -1].map((p) => targetStep(p, 3))).toEqual([0, 0, 1, 1, 2, 2, 2, 0]);
  });

  it('cada paso queda polo menos o seu tempo; con scroll rápido, algo menos; dun en dun', () => {
    expect(stepDelay(1, 1, 0)).toBe(Infinity);
    expect(stepDelay(0, 1, 0)).toBe(STEP_DWELL_MS);
    expect(stepDelay(0, 1, 400)).toBe(STEP_DWELL_MS - 400);
    expect(stepDelay(0, 1, 5000)).toBe(0);
    expect(stepDelay(0, 2, 0)).toBe(STEP_DWELL_FAST_MS);
    expect(STEP_DWELL_FAST_MS).toBeGreaterThan(STEP_MOVE_MS);
    expect(stepToward(0, 2)).toBe(1);
    expect(stepToward(2, 0)).toBe(1);
  });

  it('un scroll brusco ata o final: os pasos van dun en dun, co seu tempo', () => {
    vi.useFakeTimers();
    try {
      let now = 0;
      const section = deck(80, [500, 500, 500]);
      let top = 0;
      section.getBoundingClientRect = () => ({ top, height: 2400 }) as DOMRect;
      const stepper = new DeckStepper(section, fakeView(true), () => now);
      stepper.update();
      expect(section.dataset['active']).toBe('0');
      top = -1600; // de golpe ao final (percorrido 2400 − 800)
      stepper.update();
      expect(section.dataset['active']).toBe('0');
      now += STEP_DWELL_FAST_MS;
      vi.advanceTimersByTime(STEP_DWELL_FAST_MS);
      expect(section.dataset['active']).toBe('1');
      now += STEP_DWELL_MS - 1;
      vi.advanceTimersByTime(STEP_DWELL_MS - 1);
      expect(section.dataset['active']).toBe('1');
      now += 1;
      vi.advanceTimersByTime(1);
      expect(section.dataset['active']).toBe('2');
      stepper.stop();
    } finally {
      vi.useRealTimers();
    }
  });

  it('sen baralla fixa (movemento reducido), o paso vai directo', () => {
    const section = deck(80, [500, 500, 500]);
    let top = 0;
    section.getBoundingClientRect = () => ({ top, height: 2400 }) as DOMRect;
    const stepper = new DeckStepper(section, fakeView(true, 'reduce'));
    stepper.update();
    top = -1600;
    stepper.update();
    expect(section.dataset['active']).toBe('2');
  });

  it('progreso da sección fixa', () => {
    expect(sectionProgress(0, 3000, 1000)).toBe(0);
    expect(sectionProgress(-1000, 3000, 1000)).toBe(0.5);
    expect(sectionProgress(-5000, 3000, 1000)).toBe(1);
    expect(sectionProgress(-10, 800, 1000)).toBe(0);
  });

  it('a baralla vai fixa tamén no móbil; non nunha pantalla ancha e baixa nin con movemento reducido', () => {
    expect(pinnedDeck(fakeView(true))).toBe(true);
    expect(pinnedDeck(fakeView(true, null, true))).toBe(true);
    expect(pinnedDeck(fakeView(false))).toBe(false);
    expect(pinnedDeck(fakeView(true, 'reduce'))).toBe(false);
  });

  it('no móbil, fixa só se o título e a tarxeta máis alta caben entre as dúas barras', () => {
    expect(deckFits(600, 100, [400, 500, 450])).toBe(true);
    expect(deckFits(600, 100, [400, 501, 450])).toBe(false);
    // 844 de alto − 64 da barra superior: 780 de sitio; título 80 + 24 de marxe.
    const fits = deck(80, [500, 620, 560]);
    fitDeck(fits, fakeView(true, null, true, 844));
    expect(fits.dataset['deck']).toBeUndefined();
    // Unha pantalla pequena (ou o texto moi grande): unha debaixo doutra; ao caber, outra vez fixa.
    const small = deck(80, [500, 620, 560]);
    fitDeck(small, fakeView(true, null, true, 568));
    expect(small.dataset['deck']).toBe('static');
    fitDeck(small, fakeView(true, null, true, 900));
    expect(small.dataset['deck']).toBeUndefined();
    // No escritorio non se mide: a baralla ten o seu alto.
    const wide = deck(80, [900, 900, 900]);
    fitDeck(wide, fakeView(true, null, false, 568));
    expect(wide.dataset['deck']).toBeUndefined();
  });
});

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Page } from '@playwright/test';
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, test, text } from './fixtures';

/**
 * Preferencias de accesibilidade: o botón da barra, o panel e os tres axustes, cada
 * un e os tres á vez, no escritorio (gl, es) e no móbil (gl-mobile, 320 px), con axe e sen scroll
 * horizontal nas páxinas públicas. Barra do móbil: unha fila co escudo, a
 * accesibilidade e a conta.
 */

interface Settings {
  textSize?: 'normal' | 'large' | 'x-large';
  highContrast?: boolean;
  reducedMotion?: boolean;
}

const PAGES = ['/', '/naron', '/naron/instalacions', '/naron/accesibilidade'];

const CASES: [string, Settings][] = [
  ['texto grande', { textSize: 'large' }],
  ['texto moi grande', { textSize: 'x-large' }],
  ['alto contraste', { highContrast: true }],
  ['menos movemento', { reducedMotion: true }],
  ['os tres á vez', { textSize: 'x-large', highContrast: true, reducedMotion: true }],
];

/** Garda os axustes antes de cargar a páxina, coma se a persoa os escollese noutra visita. */
async function remember(page: Page, settings: Settings): Promise<void> {
  await page.addInitScript((value) => {
    localStorage.setItem('furar.display', JSON.stringify({ origin: 'device', ...value }));
  }, settings);
}

function a11yButton(page: Page, lang: 'gl' | 'es') {
  return page.locator('furar-site-bar').getByRole('button', { name: text(lang, 'ui.displayPrefs.button') });
}

async function openPanel(page: Page, lang: 'gl' | 'es') {
  await a11yButton(page, lang).click();
  const panel = page.getByRole('dialog', { name: text(lang, 'ui.displayPrefs.title') });
  await expect(panel).toBeVisible();
  return panel;
}

const rootState = (page: Page) =>
  page.evaluate(() => {
    const root = document.documentElement;
    const style = getComputedStyle(root);
    return {
      textSize: root.getAttribute('data-text-size'),
      contrast: root.getAttribute('data-contrast'),
      motion: root.getAttribute('data-motion'),
      fontSize: Number.parseFloat(style.fontSize),
      line: style.getPropertyValue('--f-line').trim(),
      ink: style.getPropertyValue('--f-ink').trim(),
      border: style.getPropertyValue('--f-border').trim(),
    };
  });

test.describe('preferencias de accesibilidade', () => {
  test('o botón e o panel: nome, tres tamaños e dous interruptores; Esc e clic fóra pechan', async ({ page, lang }) => {
    await page.goto('/naron');
    const button = a11yButton(page, lang);
    await expect(button).toHaveAttribute('title', text(lang, 'ui.displayPrefs.button'));
    const panel = await openPanel(page, lang);
    await expect(panel.getByRole('radio')).toHaveText([
      text(lang, 'ui.displayPrefs.size.normal'),
      text(lang, 'ui.displayPrefs.size.large'),
      text(lang, 'ui.displayPrefs.size.x-large'),
    ]);
    await expect(
      panel.getByRole('radio', { name: text(lang, 'ui.displayPrefs.size.normal'), exact: true }),
    ).toBeFocused();
    await expect(panel.getByRole('switch')).toHaveCount(2);
    await expect(panel.locator('furar-icon')).toHaveCount(0);
    // O panel cabe na pantalla (tamén no móbil).
    const box = await panel.boundingBox();
    const viewport = page.viewportSize();
    expect(box && viewport && box.y + box.height <= viewport.height && box.x >= 0).toBeTruthy();
    await expectNoA11yViolations(page);
    await page.keyboard.press('Escape');
    await expect(panel).toBeHidden();
    await expect(button).toBeFocused();
    await openPanel(page, lang);
    // Clic fóra: na esquina da barra (no móbil o panel tapa o contido de debaixo).
    await page.mouse.click(2, 2);
    await expect(page.getByRole('dialog', { name: text(lang, 'ui.displayPrefs.title') })).toBeHidden();
  });

  test('cada axuste aplícase ao momento, en toda a web e lémbrase ao recargar', async ({ page, lang }) => {
    await page.goto('/naron');
    const base = (await rootState(page)).fontSize;
    const panel = await openPanel(page, lang);
    await panel.getByRole('radio', { name: text(lang, 'ui.displayPrefs.size.large'), exact: true }).click();
    expect((await rootState(page)).fontSize).toBeCloseTo(base * 1.125, 1);
    await panel.getByRole('radio', { name: text(lang, 'ui.displayPrefs.size.x-large'), exact: true }).click();
    expect((await rootState(page)).fontSize).toBeCloseTo(base * 1.25, 1);
    await panel.getByRole('switch', { name: text(lang, 'ui.displayPrefs.contrast') }).click();
    await panel.getByRole('switch', { name: text(lang, 'ui.displayPrefs.motion') }).click();
    let state = await rootState(page);
    expect([state.textSize, state.contrast, state.motion]).toEqual(['x-large', 'more', 'reduce']);
    expect(state.line).toBe(state.ink);
    expect(state.border).toBe('2px');
    await page.reload();
    state = await rootState(page);
    expect([state.textSize, state.contrast, state.motion]).toEqual(['x-large', 'more', 'reduce']);
    // Tamén na páxina doutra ruta, sen volver escoller nada.
    await page.goto('/naron/instalacions');
    expect((await rootState(page)).contrast).toBe('more');
  });

  test('sen parpadeo: os axustes gardados xa están en <html> antes de pintar a app', async ({ page }) => {
    await remember(page, { textSize: 'large', highContrast: true, reducedMotion: true });
    await page.goto('/naron', { waitUntil: 'commit' });
    await page.waitForFunction(() => document.documentElement.hasAttribute('data-text-size'));
    const early = await page.evaluate(() => ({
      app: document.querySelector('furar-site-bar') !== null,
      size: document.documentElement.getAttribute('data-text-size'),
      contrast: document.documentElement.getAttribute('data-contrast'),
      motion: document.documentElement.getAttribute('data-motion'),
    }));
    expect([early.size, early.contrast, early.motion]).toEqual(['large', 'more', 'reduce']);
  });

  test('o sistema pide contraste e menos movemento: os interruptores saen acendidos e a persoa gaña ao apagalos', async ({
    page,
    lang,
  }) => {
    await page.emulateMedia({ contrast: 'more', reducedMotion: 'reduce' });
    await page.goto('/naron');
    expect((await rootState(page)).contrast).toBe('more');
    const panel = await openPanel(page, lang);
    const contrast = panel.getByRole('switch', { name: text(lang, 'ui.displayPrefs.contrast') });
    const motion = panel.getByRole('switch', { name: text(lang, 'ui.displayPrefs.motion') });
    await expect(contrast).toHaveAttribute('aria-checked', 'true');
    await expect(motion).toHaveAttribute('aria-checked', 'true');
    await contrast.click();
    await motion.click();
    let state = await rootState(page);
    expect([state.contrast, state.motion]).toEqual(['no-preference', 'no-preference']);
    expect(state.line).not.toBe(state.ink);
    await page.reload();
    state = await rootState(page);
    expect([state.contrast, state.motion]).toEqual(['no-preference', 'no-preference']);
  });

  for (const [name, settings] of CASES) {
    test(`${name}: as páxinas públicas pasan axe e sen scroll horizontal`, async ({ page, lang }) => {
      await remember(page, settings);
      for (const path of PAGES) {
        await page.goto(path);
        await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
        await expectNoA11yViolations(page);
        await expectNoHorizontalScroll(page);
      }
      // O panel tamén, co axuste posto.
      await openPanel(page, lang);
      await expectNoA11yViolations(page);
    });
  }

  test('móbil: unha soa fila co escudo, a accesibilidade e a conta, tamén co texto moi grande', async ({
    page,
    lang,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await remember(page, { textSize: 'x-large' });
    for (const path of ['/', '/naron']) {
      await page.goto(path);
      const bar = page.locator('furar-site-bar .f-topbar__row');
      await expect(bar.locator('furar-language-switcher')).toHaveCount(0);
      await expect(a11yButton(page, lang)).toBeVisible();
      const guest = bar.locator('button.f-usermenu__guest');
      await expect(guest).toBeVisible();
      // No móbil, redondo e só coa icona (no escritorio, «Entrar» co texto).
      await expect(guest).toHaveAttribute('data-round', 'true');
      // Unha fila: o escudo, a accesibilidade e a conta á mesma altura.
      const tops = await Promise.all(
        [bar.locator('.f-topbar__shield'), a11yButton(page, lang), guest].map(
          async (l) => (await l.boundingBox())?.y ?? 0,
        ),
      );
      expect(Math.max(...tops) - Math.min(...tops)).toBeLessThan(16);
      await expect(bar.locator('.f-topbar__name')).not.toBeInViewport();
      await expectNoHorizontalScroll(page);
    }
    await page.locator('furar-site-bar button.f-usermenu__guest').click();
    await expect(page.getByRole('menuitem', { name: text(lang, 'session.login'), exact: true })).toBeVisible();
    await expect(page.locator('[data-item="language"]')).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('móbil co texto moi grande: a portada común non ten scroll horizontal en ningún momento da carga', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await remember(page, { textSize: 'x-large' });
    // Equipo lento: entre crear a páxina e a súa primeira detección de cambios pasa tempo abondo para medir.
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 6 });
    // Mide en cada fotograma desde o principio: o peor momento, non só o final.
    await page.addInitScript(() => {
      const state = { frames: 0, overflow: 0 };
      (window as unknown as { furarOverflow: typeof state }).furarOverflow = state;
      const measure = () => {
        const root = document.documentElement;
        state.frames += 1;
        state.overflow = Math.max(state.overflow, root.scrollWidth - root.clientWidth);
        requestAnimationFrame(measure);
      };
      requestAnimationFrame(measure);
    });
    await page.goto('/');
    await expect(page.locator('[data-open-search]')).toBeVisible();
    await expect(page.locator('[data-rail]').first()).toBeVisible();
    const measured = await page.evaluate(
      () => (window as unknown as { furarOverflow: { frames: number; overflow: number } }).furarOverflow,
    );
    expect(measured.frames).toBeGreaterThan(5);
    expect(measured.overflow, 'scroll horizontal durante a carga').toBeLessThanOrEqual(0);
  });

  test('marca: na portada común unha liña; nun concello, o nome e «Deportes municipais»', async ({ page, lang }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
    await expect(page.locator('.f-topbar__brand')).toHaveText(text(lang, 'app.name'));
    await page.goto('/naron');
    await expect(page.locator('.f-topbar__tagline')).toHaveText(text(lang, 'site.tagline'));
  });
});

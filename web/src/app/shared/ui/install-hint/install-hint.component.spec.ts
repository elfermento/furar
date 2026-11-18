// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { InstallHintComponent } from './install-hint.component';
import { INSTALL_HINT_DISMISSED_KEY } from './install-hint';

const ANDROID =
  'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36';
const IPHONE =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1';
const IPHONE_CHROME =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/130.0.6723.90 Mobile/15E148 Safari/604.1';
const DESKTOP = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36';

const stable = () => TestBed.inject(ApplicationRef).whenStable();

/** O navegador da proba: o UA e as consultas de medios que casan (o resto, non). */
function device(ua: string, media: readonly string[]): () => void {
  const originalUa = Object.getOwnPropertyDescriptor(navigator, 'userAgent');
  const originalMedia = Object.getOwnPropertyDescriptor(window, 'matchMedia');
  Object.defineProperty(navigator, 'userAgent', { configurable: true, get: () => ua });
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) => ({
      matches: media.includes(query),
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }),
  });
  return () => {
    if (originalUa) Object.defineProperty(navigator, 'userAgent', originalUa);
    else Reflect.deleteProperty(navigator, 'userAgent');
    if (originalMedia) Object.defineProperty(window, 'matchMedia', originalMedia);
    else Reflect.deleteProperty(window, 'matchMedia');
  };
}

/** `beforeinstallprompt` de Chromium, coa resposta da persoa á xanela do navegador. */
function installPromptEvent(outcome: 'accepted' | 'dismissed') {
  const event = new Event('beforeinstallprompt', { cancelable: true });
  const prompt = vi.fn().mockResolvedValue(undefined);
  Object.assign(event, { prompt, userChoice: Promise.resolve({ outcome, platform: 'web' }) });
  return { event, prompt };
}

describe('furar-install-hint: aviso para instalar no móbil', () => {
  let restore: () => void = () => undefined;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    localStorage.removeItem(INSTALL_HINT_DISMISSED_KEY);
  });

  afterEach(() => {
    restore();
    localStorage.removeItem(INSTALL_HINT_DISMISSED_KEY);
    document.getElementById('furar-booking-css')?.remove();
    document.querySelectorAll('.cdk-overlay-container').forEach((el) => (el.innerHTML = ''));
  });

  async function render(enabled = true) {
    const fixture = TestBed.createComponent(InstallHintComponent);
    fixture.componentRef.setInput('enabled', enabled);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const capsule = () => el.querySelector<HTMLElement>('[data-install-hint]');
    return { fixture, el, capsule };
  }

  it('Android: aparece co evento do navegador; «Instalar» abre a xanela e, aceptada, non volve', async () => {
    restore = device(ANDROID, ['(pointer: coarse)']);
    const { fixture, capsule } = await render();
    expect(capsule(), 'sen o evento, nada').toBeNull();

    const { event, prompt } = installPromptEvent('accepted');
    window.dispatchEvent(event);
    expect(event.defaultPrevented, 'sen a barriña propia do navegador').toBe(true);
    await fixture.whenStable();
    expect(capsule()?.dataset['installHint']).toBe('android');
    expect(capsule()?.textContent).toContain('Ten Furar no móbil, coma unha app');
    expect(capsule()?.classList).toContain('f-glass');
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);

    capsule()!.querySelector<HTMLButtonElement>('[data-install-hint-install]')!.click();
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(capsule()).toBeNull();
      expect(localStorage.getItem(INSTALL_HINT_DISMISSED_KEY)).toBe('1');
    });
    expect(prompt).toHaveBeenCalledOnce();
  });

  it('Android: cancelar a xanela esconde o aviso nesta visita sen lembralo; `appinstalled` lémbrao', async () => {
    restore = device(ANDROID, ['(pointer: coarse)']);
    const { fixture, capsule } = await render();
    window.dispatchEvent(installPromptEvent('dismissed').event);
    await fixture.whenStable();
    capsule()!.querySelector<HTMLButtonElement>('[data-install-hint-install]')!.click();
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(capsule()).toBeNull();
    });
    expect(localStorage.getItem(INSTALL_HINT_DISMISSED_KEY)).toBeNull();

    window.dispatchEvent(installPromptEvent('dismissed').event);
    await fixture.whenStable();
    expect(capsule(), 'o navegador volve ofrecelo').not.toBeNull();
    window.dispatchEvent(new Event('appinstalled'));
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(capsule()).toBeNull();
      expect(localStorage.getItem(INSTALL_HINT_DISMISSED_KEY)).toBe('1');
    });
  });

  it('iPhone (Safari): aparece sen evento; «Instalar» abre a folla cos dous pasos e «Entendido» pecha', async () => {
    // Un iPhone: dedo e menos de 640 px (a capa sae como folla).
    restore = device(IPHONE, ['(pointer: coarse)', '(max-width: 39.99rem)']);
    const { capsule } = await render();
    expect(capsule()?.dataset['installHint']).toBe('ios');
    // A folla de estilos das capas xa está: a proba non agarda por un <link> que jsdom non carga.
    const link = document.createElement('link');
    link.id = 'furar-booking-css';
    document.head.append(link);

    capsule()!.querySelector<HTMLButtonElement>('[data-install-hint-install]')!.click();
    let steps: HTMLElement | null = null;
    await vi.waitFor(async () => {
      await stable();
      steps = document.querySelector<HTMLElement>('[data-install-steps]');
      expect(steps).not.toBeNull();
    });
    expect(steps!.dataset['installSteps']).toBe('safari');
    const pane = steps!.closest('.f-dialog-pane')!;
    expect(pane.classList, 'no móbil, folla pegada abaixo').toContain('f-dialog-pane--sheet');
    expect(pane.querySelector('h2')?.textContent).toContain('Engadir á pantalla de inicio');
    // O número vai só para a vista (a lista xa numera para os lectores de pantalla).
    const items = [...steps!.querySelectorAll('li')].map((li) =>
      li.lastElementChild?.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(items).toEqual(['Toca Compartir na barra de Safari', 'Escolle Engadir á pantalla de inicio']);
    await expectNoAxeViolations(pane as HTMLElement);

    pane.querySelector<HTMLButtonElement>('[data-install-steps-done]')!.click();
    await vi.waitFor(async () => {
      await stable();
      expect(document.querySelector('[data-install-steps]')).toBeNull();
    });
    expect(capsule(), 'viu os pasos: o aviso marcha').toBeNull();
    expect(localStorage.getItem(INSTALL_HINT_DISMISSED_KEY), 'e non volve neste navegador').toBe('1');
  });

  it('iPhone (Chrome): a mesma folla, cos pasos de Chrome (Compartir na barra de enderezos)', async () => {
    restore = device(IPHONE_CHROME, ['(pointer: coarse)', '(max-width: 39.99rem)']);
    const { capsule } = await render();
    expect(capsule()?.dataset['installHint']).toBe('ios-chrome');
    const link = document.createElement('link');
    link.id = 'furar-booking-css';
    document.head.append(link);

    capsule()!.querySelector<HTMLButtonElement>('[data-install-hint-install]')!.click();
    let steps: HTMLElement | null = null;
    await vi.waitFor(async () => {
      await stable();
      steps = document.querySelector<HTMLElement>('[data-install-steps]');
      expect(steps).not.toBeNull();
    });
    expect(steps!.dataset['installSteps']).toBe('chrome');
    const pane = steps!.closest('.f-dialog-pane')!;
    expect(pane.querySelector('h2')?.textContent).toContain('Engadir á pantalla de inicio');
    const items = [...steps!.querySelectorAll('li')].map((li) =>
      li.lastElementChild?.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(items).toEqual(['Toca Compartir na barra de enderezos de Chrome', 'Escolle Engadir á pantalla de inicio']);
    expect(steps!.querySelector('furar-icon'), 'a mesma icona de Compartir de iOS').not.toBeNull();
    await expectNoAxeViolations(pane as HTMLElement);
  });

  it('pechar (✕) esconde o aviso e lémbrao neste navegador: ao volver, xa non sae', async () => {
    restore = device(IPHONE, ['(pointer: coarse)']);
    const first = await render();
    const close = first.capsule()!.querySelector<HTMLButtonElement>('[data-install-hint-close]')!;
    expect(close.getAttribute('aria-label')).toBe('Pechar e non volver mostrar');
    close.click();
    await first.fixture.whenStable();
    expect(first.capsule()).toBeNull();
    expect(localStorage.getItem('furar.installHint.dismissed')).toBe('1');

    const again = await render();
    expect(again.capsule()).toBeNull();
  });

  it('o concello apagouno: non se ve, nin co evento', async () => {
    restore = device(ANDROID, ['(pointer: coarse)']);
    const { fixture, capsule } = await render(false);
    window.dispatchEvent(installPromptEvent('accepted').event);
    await fixture.whenStable();
    expect(capsule()).toBeNull();
  });

  it('escritorio: nunca, nin co evento de Chrome nin cun UA de iPhone sen dedo', async () => {
    restore = device(DESKTOP, []);
    const desktop = await render();
    window.dispatchEvent(installPromptEvent('accepted').event);
    await desktop.fixture.whenStable();
    expect(desktop.capsule()).toBeNull();
    restore();

    restore = device(IPHONE, []);
    const noTouch = await render();
    expect(noTouch.capsule()).toBeNull();
  });

  it('xa aberta coma app: non se ve', async () => {
    restore = device(IPHONE, ['(pointer: coarse)', '(display-mode: standalone)']);
    const { capsule } = await render();
    expect(capsule()).toBeNull();
    expect(localStorage.getItem(INSTALL_HINT_DISMISSED_KEY), 'lémbrao para o navegador').toBe('1');
  });
});

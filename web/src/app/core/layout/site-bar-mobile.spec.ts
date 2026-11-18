// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { fakeSession, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { LanguageService } from '../i18n/language.service';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { SiteBarComponent } from './site-bar.component';

@Component({
  imports: [SiteBarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<furar-site-bar /><main><h1>{{ "x" }}</h1></main>',
})
class HostComponent {}

/** `matchMedia` co ancho do móbil (< 640 px) ou do escritorio. */
function viewport(narrow: boolean) {
  const original = Object.getOwnPropertyDescriptor(window, 'matchMedia');
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) => ({
      matches: query.includes('max-width') ? narrow : false,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }),
  });
  return () => {
    if (original) Object.defineProperty(window, 'matchMedia', original);
    else Reflect.deleteProperty(window, 'matchMedia');
  };
}

describe('barra superior: accesibilidade, marca, conta e idioma', () => {
  let restore: () => void = () => undefined;
  afterEach(() => {
    restore();
    document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = ''));
  });

  async function render(options: { narrow: boolean; session?: ReturnType<typeof fakeSession>; place?: boolean }) {
    restore = viewport(options.narrow);
    TestBed.configureTestingModule({
      providers: [...provideTestApi(options.session ?? anonymous()), provideRouter([])],
    });
    if (options.place !== false) TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const anonymous = () => ({ ...fakeSession({}), authenticated: signal(false) });
  const menuRows = () =>
    [...document.querySelectorAll<HTMLElement>('.f-usermenu__panel [role^="menuitem"]')].map((row) =>
      row.textContent?.replace(/\s+/g, ' ').trim(),
    );

  it('escritorio: botón «Accesibilidade» e, xunto a el, o botón da persoa (sen idioma solto na barra)', async () => {
    const { fixture, el } = await render({ narrow: false });
    const tools = el.querySelector('.f-topbar__tools')!;
    const a11y = tools.querySelector('button[aria-label="Accesibilidade"]');
    expect(a11y?.getAttribute('title')).toBe('Accesibilidade');
    expect(tools.querySelector('furar-language-switcher, [aria-pressed]')).toBeNull();
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(tools.querySelector('button.f-usermenu__guest')).not.toBeNull();
    });
    expect(a11y?.closest('furar-display-preferences')?.nextElementSibling?.tagName.toLowerCase()).toBe(
      'furar-user-menu',
    );
    await expectNoAxeViolations(el);
  });

  it('na portada común (sen concello), a marca é unha soa liña', async () => {
    const { el } = await render({ narrow: false, place: false });
    const brand = el.querySelector('.f-topbar__brand');
    expect(brand?.getAttribute('href')).toBe('/');
    expect(brand?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Reservas deportivas');
    expect(el.querySelector('.f-topbar__tagline')).toBeNull();
  });

  it.each([true, false])(
    'sen sesión (estreito: %s): escudo, accesibilidade e un botón da persoa con «Entrar» e «Idioma»',
    async (narrow) => {
      const { fixture, el } = await render({ narrow });
      expect(el.querySelector('furar-language-switcher')).toBeNull();
      // O nome do concello segue sendo o nome da ligazón (só se esconde á vista).
      expect(el.querySelector('.f-topbar__brand')?.textContent).toContain('Narón');
      let trigger: HTMLButtonElement | null = null;
      await vi.waitFor(async () => {
        await fixture.whenStable();
        trigger = el.querySelector<HTMLButtonElement>('button.f-usermenu__guest');
        expect(trigger).not.toBeNull();
      });
      expect(trigger!.getAttribute('aria-label')).toBe('Entrar e idioma');
      // No móbil, redondo e só coa icona; no escritorio, pastilla con icona e «Entrar».
      expect(trigger!.getAttribute('data-variant')).toBe('primary');
      expect(trigger!.getAttribute('data-size')).toBe('sm');
      expect(trigger!.getAttribute('data-round')).toBe(narrow ? 'true' : null);
      expect(trigger!.textContent?.trim()).toBe(narrow ? '' : 'Entrar');
      trigger!.click();
      await fixture.whenStable();
      expect(menuRows()).toEqual(['Entrar', 'Idioma Galego']);
      document.querySelector<HTMLElement>('[data-item="language"]')!.click();
      await fixture.whenStable();
      // Cada idioma co seu nome e o seu `lang`; o actual, con ✓ (aria-checked), sen fondo.
      expect(menuRows()).toEqual(['Idioma', 'Galego', 'Castellano']);
      const option = (id: string) => document.querySelector<HTMLElement>(`[data-option="${id}"]`)!;
      expect(option('gl').getAttribute('lang')).toBe('gl');
      expect(option('es').getAttribute('lang')).toBe('es');
      expect(option('gl').getAttribute('aria-checked')).toBe('true');
      expect(option('es').getAttribute('aria-checked')).toBe('false');
      const use = vi.spyOn(TestBed.inject(LanguageService), 'use').mockResolvedValue(undefined);
      option('es').click();
      expect(use).toHaveBeenCalledWith('es', true);
    },
  );

  it('móbil sen sesión: «Entrar» do menú inicia a sesión', async () => {
    const session = anonymous();
    const { fixture, el } = await render({ narrow: true, session });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('button.f-usermenu__guest')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('button.f-usermenu__guest')!.click();
    await fixture.whenStable();
    document.querySelector<HTMLElement>('[data-item="login"]')!.click();
    expect(session.login).toHaveBeenCalled();
  });

  it.each([true, false])(
    'con sesión (estreito: %s): o idioma vai no menú da persoa, antes de «Pechar sesión»',
    async (narrow) => {
      const { fixture, el } = await render({ narrow, session: fakeSession({}) });
      expect(el.querySelector('furar-language-switcher')).toBeNull();
      await vi.waitFor(async () => {
        await fixture.whenStable();
        expect(el.querySelector('button.f-usermenu__trigger')).not.toBeNull();
      });
      el.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!.click();
      await fixture.whenStable();
      expect(menuRows()).toEqual([
        'A miña conta',
        'As miñas reservas',
        'Persoas ao meu cargo',
        'Idioma Galego',
        'Pechar sesión',
      ]);
    },
  );

  it('móbil: «Concello ▾» do persoal de varios concellos sen concello abre unha folla, non un menú', async () => {
    const session = fakeSession({
      memberships: [
        { slug: 'ares', name: 'Ares', role: 'STAFF' },
        { slug: 'naron', name: 'Narón', role: 'MANAGER' },
      ],
    });
    const { fixture, el } = await render({ narrow: true, session, place: false });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('furar-pick-menu button')).not.toBeNull();
    });
    const button = el.querySelector<HTMLButtonElement>('furar-pick-menu button')!;
    expect(button.textContent?.replace(/\s+/g, ' ').trim()).toBe('Concello');
    expect(button.getAttribute('aria-haspopup')).toBe('dialog');
    await expectNoAxeViolations(el);
  });
});

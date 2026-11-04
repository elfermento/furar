// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';
import { provideApiConfiguration } from '../../api/api-configuration';
import { municipality } from '../../../testing/fixtures';
import { provideTestI18n } from '../../../testing/i18n';
import { fakeSession, fakeStaffAccess } from '../../../testing/api';
import { SessionService } from '../auth/session.service';
import { StaffAccessService } from '../auth/staff-access.service';
import { LanguageService } from '../i18n/language.service';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { ErrorPage } from './error.page';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { ShellComponent } from './shell.component';
import { TranslatedTitleStrategy } from './translated-title.strategy';
import { provideHttpClient } from '@angular/common/http';

@Component({ changeDetection: ChangeDetectionStrategy.OnPush, template: '<h1>{{ "x" }}</h1>' })
class PageComponent {}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<h1>{{ "x" }}</h1><h2 id="instalacions">{{ "y" }}</h2>',
})
class SectionPageComponent {}

describe('ShellComponent', () => {
  const authenticated = signal(false);
  const session = {
    ...fakeSession({ manager: 'naron', previousSignInAt: '2026-10-27T17:05:00Z' }),
    authenticated,
    name: signal('Xestora'),
  };

  async function render() {
    TestBed.configureTestingModule({
      providers: [
        provideTestI18n(),
        provideHttpClient(),
        provideApiConfiguration(''),
        provideRouter(
          [
            { path: '', component: PageComponent, title: 'titles.home' },
            { path: 'erro/:status', component: ErrorPage, title: 'titles.error' },
            { path: 'naron', component: PageComponent, title: 'titles.home' },
            { path: 'naron/xestion/instalacions', component: PageComponent, title: 'titles.home' },
            { path: 'portada', component: SectionPageComponent, title: 'titles.home', data: { layout: 'bleed' } },
          ],
          withComponentInputBinding(),
        ),
        { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
        { provide: SessionService, useValue: session },
        { provide: StaffAccessService, useFactory: () => fakeStaffAccess(session, inject(CurrentMunicipality)) },
      ],
    });
    const fixture = TestBed.createComponent(ShellComponent);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/');
    await fixture.whenStable();
    return { fixture, router, el: fixture.nativeElement as HTMLElement };
  }

  it('ten salto ao contido, barra superior e pé; anónimo ve «Entrar»', async () => {
    const { el } = await render();
    expect(el.querySelector('a[href="#contido"]')?.textContent).toContain('Saltar ao contido');
    expect(el.querySelector('main#contido')).not.toBeNull();
    expect(el.querySelector('furar-site-bar header')).not.toBeNull();
    const login = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Entrar'));
    login?.click();
    expect(session.login).toHaveBeenCalledWith('/');
    // Sen concello: nin menú público nin barra inferior; sen sesión, nin xestión.
    expect(el.querySelector('header nav')).toBeNull();
    expect(el.querySelector('.f-bottomnav')).toBeNull();
  });

  it('co concello e rol de xestor: nome, navegación de xestión e pechar sesión', async () => {
    const { fixture, el } = await render();
    authenticated.set(true);
    TestBed.inject(CurrentMunicipality).set(municipality());
    await fixture.whenStable();
    expect(el.querySelector('header a')?.textContent).toContain('Narón');
    expect(el.querySelector('header a')?.textContent).toContain('Deportes municipais');
    const manage = el.querySelector('nav[aria-label="Xestión do concello"]');
    expect(manage).not.toBeNull();
    const facilities = manage?.querySelector<HTMLAnchorElement>('a');
    expect(facilities?.textContent?.trim()).toBe('Instalacións');
    expect(facilities?.getAttribute('href')).toBe('/naron/xestion/instalacions');
    // Unha soa barra: sen segunda fila de xestión.
    expect(el.querySelectorAll('header nav')).toHaveLength(1);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('button.f-usermenu__trigger')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!.click();
    await fixture.whenStable();
    document.querySelector<HTMLButtonElement>('[data-item="logout"]')!.click();
    expect(session.logout).toHaveBeenCalled();
    authenticated.set(false);
  });

  it('na xestión, o acceso anterior como texto (non alerta), en Europe/Madrid; fóra dela, non', async () => {
    const { fixture, router, el } = await render();
    authenticated.set(true);
    TestBed.inject(CurrentMunicipality).set(municipality());
    await router.navigateByUrl('/naron');
    await fixture.whenStable();
    expect(el.querySelector('[data-last-sign-in]')).toBeNull();
    await router.navigateByUrl('/naron/xestion/instalacions');
    await fixture.whenStable();
    const line = el.querySelector('[data-last-sign-in]');
    expect(line?.textContent?.trim()).toBe('O teu último acceso foi o 27/10/2026, 18:05.');
    expect(line?.closest('[role="alert"], [role="status"]')).toBeNull();
    authenticated.set(false);
    await fixture.whenStable();
    expect(el.querySelector('[data-last-sign-in]')).toBeNull();
  });

  it('pé: código fonte e licenzas sempre; accesibilidade, privacidade e aviso legal co concello', async () => {
    const { fixture, el } = await render();
    const footerLinks = () =>
      [...el.querySelectorAll<HTMLAnchorElement>('footer nav a')].map((a) => [
        a.textContent?.trim(),
        a.getAttribute('href'),
      ]);
    // Tarxeta de cristal coma a barra superior: a marca curta e as ligazóns en pastilla.
    expect(el.querySelector('footer > .f-footer__bar.f-glass')).not.toBeNull();
    expect(el.querySelector('.f-footer__brand')?.textContent?.trim()).toBe('Reservas deportivas · software libre');
    expect(el.querySelector('.f-footer__brand a')?.getAttribute('href')).toBe('/codigo-fonte');
    expect([...el.querySelectorAll('footer nav a')].every((a) => a.classList.contains('f-footer__link'))).toBe(true);
    expect(el.querySelector('footer nav')?.getAttribute('aria-label')).toBe('Información legal e código fonte');
    expect(footerLinks()).toEqual([
      ['Código fonte', '/codigo-fonte'],
      ['Licenza (EUPL-1.2)', '/LICENSE.txt'],
      ['Licenzas de terceiros', '/3rdpartylicenses.txt'],
    ]);
    TestBed.inject(CurrentMunicipality).set(municipality());
    await fixture.whenStable();
    expect(footerLinks().slice(0, 3)).toEqual([
      ['Accesibilidade', '/naron/accesibilidade'],
      ['Privacidade', '/naron/privacidade'],
      ['Aviso legal', '/naron/aviso-legal'],
    ]);
    // Co aviso legal de Furar, a marca leva á súa sección «Software» (a atribución da EUPL).
    expect(el.querySelector('.f-footer__brand a')?.getAttribute('href')).toBe('/naron/aviso-legal#software');
    // Páxina propia (ADR-0017 punto 8): a ligazón vai á web do concello e dío.
    TestBed.inject(CurrentMunicipality).set(
      municipality({ legal: { privacyMode: 'URL', privacyUrl: 'https://naron.example/privacidade' } }),
    );
    await fixture.whenStable();
    expect(footerLinks()[1]).toEqual(['Privacidade(páxina do concello)', 'https://naron.example/privacidade']);
    expect(el.querySelectorAll('footer nav a')[1]?.getAttribute('rel')).toBe('noopener noreferrer');
  });

  it('o salto ao contido e a navegación levan o foco ao título; o toast pasa a navegación e o novo substitúe ao vello', async () => {
    const { fixture, router, el } = await render();
    el.querySelector<HTMLAnchorElement>('a[href="#contido"]')?.click();
    expect(document.activeElement?.tagName).toBe('H1');
    const toasts = TestBed.inject(ToastService);
    toasts.show('settings.saved');
    await router.navigateByUrl('/naron');
    await fixture.whenStable();
    expect(el.querySelector('furar-toast[role="status"] [data-toast]')?.textContent).toContain('Cambios gardados');
    expect(document.activeElement?.tagName).toBe('H1');
    toasts.show('customFields.editor.saved', { label: 'Aforo' });
    await fixture.whenStable();
    expect(el.querySelectorAll('[data-toast]')).toHaveLength(1);
    expect(el.querySelector('[data-toast]')?.textContent).not.toContain('Cambios gardados');
  });

  it('se só cambian os parámetros da busca, o foco non salta ao título', async () => {
    // Regresión (D2-17): escoller a data na páxina de reservar (`?data=`) levaba o foco ao h1.
    const { fixture, router, el } = await render();
    await router.navigateByUrl('/naron');
    await fixture.whenStable();
    const button = document.createElement('button');
    el.querySelector('main')?.append(button);
    button.focus();
    await router.navigateByUrl('/naron?data=2026-10-29');
    await fixture.whenStable();
    expect(document.activeElement).toBe(button);
    await router.navigateByUrl('/');
    await fixture.whenStable();
    expect(document.activeElement?.tagName).toBe('H1');
  });

  it('a portada vai a todo o ancho e un fragmento leva o foco ao seu destino', async () => {
    const { fixture, router, el } = await render();
    expect(el.querySelector('main')?.getAttribute('data-layout')).toBe('page');
    await router.navigateByUrl('/portada');
    await fixture.whenStable();
    expect(el.querySelector('main')?.getAttribute('data-layout')).toBe('bleed');
    expect(document.activeElement?.tagName).toBe('H1');
    await router.navigateByUrl('/portada#instalacions');
    await fixture.whenStable();
    await vi.waitFor(() => expect(document.activeElement?.id).toBe('instalacions'));
  });

  it('o título da páxina tradúcese e cambia co idioma; a páxina de erro coñece 403/404/500', async () => {
    const { fixture, router, el } = await render();
    expect(TestBed.inject(Title).getTitle()).toBe('Inicio · Reservas deportivas');
    await TestBed.inject(LanguageService).use('es');
    await fixture.whenStable();
    expect(TestBed.inject(Title).getTitle()).toBe('Inicio · Reservas deportivas');
    await router.navigateByUrl('/erro/403');
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toContain('Sin permiso');
    await router.navigateByUrl('/erro/999');
    await fixture.whenStable();
    expect(el.querySelector('h1')?.textContent).toContain('No existe');
    await TestBed.inject(LanguageService).use('gl');
  });

  it('o selector de idioma marca o activo con aria-pressed e o nome no propio idioma', async () => {
    const { fixture, el } = await render();
    const buttons = [...el.querySelectorAll<HTMLButtonElement>('[role="group"] button')];
    expect(
      buttons.map((b) => [b.lang, b.textContent?.replace(/\s+/g, ' ').trim(), b.getAttribute('aria-pressed')]),
    ).toEqual([
      ['gl', 'GL Galego', 'true'],
      ['es', 'ES Castellano', 'false'],
    ]);
    buttons[1]?.click();
    await fixture.whenStable();
    await vi.waitFor(() => expect(document.documentElement.lang).toBe('es'));
    expect(localStorage.getItem('furar.lang')).toBe('es');
    buttons[0]?.click();
    localStorage.clear();
  });

  it('avisa cando non hai conexión', async () => {
    const { fixture, el } = await render();
    const online = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    window.dispatchEvent(new Event('offline'));
    await fixture.whenStable();
    expect(el.textContent).toContain('Sen conexión');
    online.mockReturnValue(true);
    window.dispatchEvent(new Event('online'));
    await fixture.whenStable();
    expect(el.textContent).not.toContain('Sen conexión');
    online.mockRestore();
  });

  it('o pé queda abaixo nas páxinas curtas: o marco é unha columna da altura da pantalla (site.css)', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/styles/components/site.css'), 'utf8');
    const rule = (selector: string) =>
      css.slice(css.indexOf(`${selector} {`), css.indexOf('}', css.indexOf(`${selector} {`)));
    expect(rule('  .f-shell')).toContain('min-height: 100dvh;');
    expect(rule('  .f-shell')).toContain('flex-direction: column;');
    expect(rule('.f-shell > .f-main')).toContain('flex: 1 0 auto;');
  });

  it('barra superior, contido e pé comparten contedor e bordos (docs/deseno.md §7)', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/styles/components/site.css'), 'utf8');
    const rule = (selector: string) =>
      css.slice(css.indexOf(`${selector} {`), css.indexOf('}', css.indexOf(`${selector} {`)));
    expect(rule('  .f-topbar__bar')).toContain('max-width: var(--container-bar);');
    expect(rule('  .f-footer__bar')).toContain('max-width: var(--container-bar);');
    // O contido leva a marxe dentro: o seu bordo cae onde o da barra (1rem desde 640 px).
    expect(rule('  .f-main')).toContain('max-width: calc(var(--container-bar) + 2 * var(--spacing-gutter));');
    expect(rule('  .f-main')).toContain('padding: var(--spacing-section) var(--spacing-gutter);');
    expect(css).not.toContain("data-layout='wide'");
  });
});

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
import { NO_MAP, provideDeploymentSettings } from '../../../testing/deployment';
import { SessionService } from '../auth/session.service';
import { StaffAccessService } from '../auth/staff-access.service';
import { LanguageService } from '../i18n/language.service';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { MunicipalityDirectory } from '../municipality/municipality-directory';
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
    ...fakeSession({ manager: 'naron' }),
    authenticated,
    name: signal('Xestora'),
  };

  async function render(extra: unknown[] = []) {
    TestBed.configureTestingModule({
      providers: [
        // Despregue falso, sen correo de soporte; unha proba pode dar outro en `extra` (gaña o último).
        provideDeploymentSettings(),
        ...(extra as []),
        provideTestI18n(),
        provideHttpClient(),
        provideApiConfiguration(''),
        provideRouter(
          [
            { path: '', component: PageComponent, title: 'titles.home', data: { footer: 'full' } },
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

  it('ten salto ao contido, barra superior e pé; anónimo ve «Entrar» no menú da persoa', async () => {
    const { fixture, el } = await render();
    expect(el.querySelector('a[href="#contido"]')?.textContent).toContain('Saltar ao contido');
    expect(el.querySelector('main#contido')).not.toBeNull();
    expect(el.querySelector('furar-site-bar header')).not.toBeNull();
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('button.f-usermenu__guest')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('button.f-usermenu__guest')!.click();
    await fixture.whenStable();
    document.querySelector<HTMLButtonElement>('[data-item="login"]')!.click();
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

  it('pé enteiro: columnas «Legal» e «Servizo» con título; o nome do servizo como texto', async () => {
    const { fixture, el } = await render();
    const footerLinks = () =>
      [...el.querySelectorAll<HTMLAnchorElement>('footer nav a')].map((a) => [
        a.textContent?.trim(),
        a.getAttribute('href'),
      ]);
    const titles = () => [...el.querySelectorAll('.f-footer__title')].map((t) => t.textContent?.trim());
    expect(el.querySelector('footer')?.getAttribute('data-variant')).toBe('full');
    // Franxa coa cor da páxina, sen tarxeta: sen cristal.
    expect(el.querySelector('footer .f-glass')).toBeNull();
    // O nome do servizo, como texto (xa non é ligazón).
    expect([...el.querySelectorAll('.f-footer__brand > span')].map((p) => p.textContent?.trim())).toEqual([
      'Reservas deportivas',
      'software libre',
    ]);
    expect(el.querySelector('.f-footer__brand a')).toBeNull();
    expect([...el.querySelectorAll('footer nav a')].every((a) => a.classList.contains('f-footer__link'))).toBe(true);
    expect(el.querySelector('footer nav')?.getAttribute('aria-label')).toBe('Información legal e do servizo');
    // Sen concello, só «Servizo»: «Código e licenzas» vai ao código fonte.
    expect(titles()).toEqual(['Servizo']);
    expect(footerLinks()).toEqual([['Código e licenzas', '/codigo-fonte']]);
    TestBed.inject(CurrentMunicipality).set(municipality());
    await fixture.whenStable();
    expect(titles()).toEqual(['Legal', 'Servizo']);
    // Cada lista, nomeada polo seu título.
    for (const list of el.querySelectorAll('.f-footer__list')) {
      expect(el.querySelector(`#${list.getAttribute('aria-labelledby')}`)?.classList).toContain('f-footer__title');
    }
    expect(footerLinks()).toEqual([
      ['Aviso legal', '/naron/aviso-legal'],
      ['Privacidade', '/naron/privacidade'],
      // «Cookies»: a sección de Privacidade desta web, que sae sempre (sen banner: só técnicas).
      ['Cookies', '/naron/privacidade#cookies'],
      ['Accesibilidade', '/naron/accesibilidade'],
      ['Datos abertos', '/naron/datos-abertos'],
      // Co aviso legal de Furar, «Código e licenzas» leva á súa sección «Software».
      ['Código e licenzas', '/naron/aviso-legal#software'],
    ]);
    // Páxina propia (ADR-0017 punto 8): a ligazón vai á web do concello e dío.
    TestBed.inject(CurrentMunicipality).set(
      municipality({ legal: { privacyMode: 'URL', privacyUrl: 'https://naron.example/privacidade' } }),
    );
    await fixture.whenStable();
    expect(footerLinks()[1]).toEqual(['Privacidade(páxina do concello)', 'https://naron.example/privacidade']);
    expect(el.querySelectorAll('footer nav a')[1]?.getAttribute('rel')).toBe('noopener noreferrer');
    expect(footerLinks()[2]).toEqual(['Cookies', '/naron/privacidade#cookies']);
  });

  it('pé: «Datos abertos» co do concello aceso; na raíz, só con varios concellos, algún cos datos abertos acesos, e sen petición nova', async () => {
    const listed = signal<{ id: string; name: string; slug: string; openData: boolean }[] | null>(null);
    const { fixture, el } = await render([{ provide: MunicipalityDirectory, useValue: { municipalities: listed } }]);
    const link = () => el.querySelector<HTMLAnchorElement>('[data-footer-open-data]');
    expect(link()).toBeNull();
    listed.set([{ id: '1', name: 'Narón', slug: 'naron', openData: true }]);
    await fixture.whenStable();
    expect(link()).toBeNull();
    listed.set([
      { id: '1', name: 'Narón', slug: 'naron', openData: false },
      { id: '2', name: 'Cambre', slug: 'cambre', openData: false },
    ]);
    await fixture.whenStable();
    expect(link()).toBeNull();
    listed.set([
      { id: '1', name: 'Narón', slug: 'naron', openData: false },
      { id: '2', name: 'Cambre', slug: 'cambre', openData: true },
    ]);
    await fixture.whenStable();
    expect(link()?.getAttribute('href')).toBe('/datos-abertos');
    expect(link()?.textContent?.trim()).toBe('Datos abertos');
    TestBed.inject(CurrentMunicipality).set(municipality({ openData: false }));
    await fixture.whenStable();
    expect(link()).toBeNull();
    TestBed.inject(CurrentMunicipality).set(municipality({ openData: true }));
    await fixture.whenStable();
    expect(link()?.getAttribute('href')).toBe('/naron/datos-abertos');
    // Abre a columna «Servizo», antes de «Código e licenzas».
    expect(link()?.closest('[data-footer-column]')?.getAttribute('data-footer-column')).toBe('service');
    expect(link()?.closest('li')?.previousElementSibling).toBeNull();
    expect(link()?.closest('li')?.nextElementSibling?.textContent?.trim()).toBe('Código e licenzas');
  });

  it('pé: a columna «Contacto» co correo de soporte do despregue, como ligazón mailto; sen el, a columna non existe', async () => {
    const none = await render();
    expect(none.el.querySelector('[data-footer-support]')).toBeNull();
    expect(none.el.querySelector('[data-footer-column="contact"]')).toBeNull();
    TestBed.resetTestingModule();

    const { fixture, router, el } = await render([provideDeploymentSettings(NO_MAP, 'soporte@example.org')]);
    const support = () => el.querySelector<HTMLAnchorElement>('footer nav a[data-footer-support]');
    expect(el.querySelector('footer')?.getAttribute('data-variant')).toBe('full');
    expect(support()?.getAttribute('href')).toBe('mailto:soporte@example.org');
    expect(support()?.textContent?.trim()).toBe('soporte@example.org');
    expect(support()?.getAttribute('aria-label')).toBe('Correo de soporte: soporte@example.org');
    expect(support()?.closest('[data-footer-column]')?.getAttribute('data-footer-column')).toBe('contact');
    expect(el.querySelector('[data-footer-column="contact"] .f-footer__title')?.textContent?.trim()).toBe('Contacto');
    // Na liña das páxinas interiores: os catro legais e «Soporte: correo», sen código nin licenza.
    TestBed.inject(CurrentMunicipality).set(municipality());
    await router.navigateByUrl('/naron/xestion/instalacions');
    await fixture.whenStable();
    expect(el.querySelector('footer')?.getAttribute('data-variant')).toBe('line');
    expect([...el.querySelectorAll<HTMLAnchorElement>('footer nav a')].map((a) => a.getAttribute('href'))).toEqual([
      '/naron/aviso-legal',
      '/naron/privacidade',
      '/naron/privacidade#cookies',
      '/naron/accesibilidade',
      'mailto:soporte@example.org',
    ]);
    expect(support()?.closest('li')?.textContent).toContain('Soporte:');
  });

  it('pé: «¿Es un concello?» na columna «Servizo», antes de «Código e licenzas», só se o despregue ten a función e só na portada común', async () => {
    const several = [
      { id: '1', name: 'Narón', slug: 'naron', openData: false },
      { id: '2', name: 'Cambre', slug: 'cambre', openData: false },
    ];
    const none = await render([{ provide: MunicipalityDirectory, useValue: { municipalities: signal(several) } }]);
    expect(none.el.querySelector('[data-footer-join-request]')).toBeNull();
    TestBed.resetTestingModule();

    const listed = signal<typeof several | null>(several);
    const { fixture, el } = await render([
      provideDeploymentSettings(NO_MAP, null, true),
      { provide: MunicipalityDirectory, useValue: { municipalities: listed } },
    ]);
    const link = () => el.querySelector<HTMLAnchorElement>('footer a[data-footer-join-request]');
    expect(link()?.getAttribute('href')).toBe('/alta-concello');
    expect(link()?.textContent?.trim()).toBe('¿Es un concello?');
    expect(link()?.closest('[data-footer-column]')?.getAttribute('data-footer-column')).toBe('service');
    expect(link()?.closest('li')?.nextElementSibling?.textContent?.trim()).toBe('Código e licenzas');
    // Cun só concello na instalación, a raíz é a súa portada: sen ligazón.
    listed.set([several[0]]);
    await fixture.whenStable();
    expect(link()).toBeNull();
    // Na portada e nas páxinas dun concello, tampouco.
    listed.set(several);
    TestBed.inject(CurrentMunicipality).set(municipality());
    await fixture.whenStable();
    expect(el.querySelector('footer')?.getAttribute('data-variant')).toBe('full');
    expect(el.querySelector('[data-footer-column="service"]')).not.toBeNull();
    expect(link()).toBeNull();
  });

  it('pé nas páxinas interiores: unha liña cos catro legais, sen código nin licenzas', async () => {
    const { fixture, router, el } = await render();
    TestBed.inject(CurrentMunicipality).set(municipality({ openData: true }));
    await router.navigateByUrl('/naron/xestion/instalacions');
    await fixture.whenStable();
    expect(el.querySelector('footer')?.getAttribute('data-variant')).toBe('line');
    expect(el.querySelector('.f-footer__brand')).toBeNull();
    expect(el.querySelectorAll('footer nav ul')).toHaveLength(1);
    expect([...el.querySelectorAll<HTMLAnchorElement>('footer nav a')].map((a) => a.textContent?.trim())).toEqual([
      'Aviso legal',
      'Privacidade',
      'Cookies',
      'Accesibilidade',
    ]);
    // A portada volve ao pé enteiro.
    await router.navigateByUrl('/portada');
    await fixture.whenStable();
    expect(el.querySelector('footer')?.getAttribute('data-variant')).toBe('full');
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
    // Regresión: escoller a data na páxina de reservar (`?data=`) levaba o foco ao h1.
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

  it('o idioma, no menú da persoa: o activo con ✓, cada nome no seu idioma e a ruta non cambia', async () => {
    const { fixture, router, el } = await render();
    await router.navigateByUrl('/naron');
    await fixture.whenStable();
    expect(el.querySelector('[role="group"] button[aria-pressed]')).toBeNull();
    const openLanguage = async () => {
      await vi.waitFor(async () => {
        await fixture.whenStable();
        expect(el.querySelector('button.f-usermenu__guest')).not.toBeNull();
      });
      el.querySelector<HTMLButtonElement>('button.f-usermenu__guest')!.click();
      await fixture.whenStable();
      document.querySelector<HTMLElement>('[data-item="language"]')!.click();
      await fixture.whenStable();
      return [...document.querySelectorAll<HTMLElement>('[data-option]')];
    };
    const options = await openLanguage();
    expect(options.map((o) => [o.lang, o.textContent?.trim(), o.getAttribute('aria-checked')])).toEqual([
      ['gl', 'Galego', 'true'],
      ['es', 'Castellano', 'false'],
    ]);
    options[1].click();
    await fixture.whenStable();
    await vi.waitFor(() => expect(document.documentElement.lang).toBe('es'));
    expect(localStorage.getItem('furar.lang')).toBe('es');
    expect(router.url).toBe('/naron');
    (await openLanguage())[0].click();
    await vi.waitFor(() => expect(document.documentElement.lang).toBe('gl'));
    document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = ''));
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
    // O pé é unha franxa a todo o ancho; o seu contido, no mesmo contedor ca o contido.
    expect(rule('  .f-footer__in')).toContain('max-width: calc(var(--container-bar) + 2 * var(--spacing-gutter));');
    // O contido leva a marxe dentro: o seu bordo cae onde o da barra (1rem desde 640 px).
    expect(rule('  .f-main')).toContain('max-width: calc(var(--container-bar) + 2 * var(--spacing-gutter));');
    expect(rule('  .f-main')).toContain('padding: var(--spacing-section) var(--spacing-gutter);');
    expect(css).not.toContain("data-layout='wide'");
  });
});

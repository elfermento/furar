// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { expectRequest, fakeSession, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { BottomNavComponent } from './bottom-nav.component';
import { manageLinks } from './manage-links';
import { SiteBarComponent } from './site-bar.component';

@Component({
  imports: [SiteBarComponent, BottomNavComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<furar-site-bar /><main><h1>{{ "x" }}</h1></main><furar-bottom-nav />',
})
class HostComponent {}

describe('barras superior e inferior', () => {
  async function render(session = fakeSession({})) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(session),
        provideRouter([
          { path: 'naron', component: HostComponent },
          { path: 'naron/instalacions', component: HostComponent },
          { path: 'naron/xestion', component: HostComponent },
          { path: 'naron/xestion/:page', component: HostComponent },
          { path: 'naron/xestion/concello/:tab', component: HostComponent },
        ]),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const anonymous = () => ({ ...fakeSession({}), authenticated: signal(false) });

  it('marca unha vez, menú público e barra inferior cos mesmos destinos; pasa axe', async () => {
    const { el } = await render(anonymous());
    const brand = el.querySelector<HTMLAnchorElement>('.f-topbar__brand');
    expect(brand?.getAttribute('href')).toBe('/naron');
    expect(brand?.textContent?.replace(/\s+/g, ' ').trim()).toBe('Narón Deportes municipais');
    const main = el.querySelector('nav[aria-label="Principal"]');
    expect(
      [...(main?.querySelectorAll('a') ?? [])].map((a) => [a.textContent?.trim(), a.getAttribute('href')]),
    ).toEqual([['Instalacións', '/naron/instalacions']]);
    const bottom = el.querySelector('nav.f-bottomnav');
    expect(bottom?.getAttribute('aria-label')).toBe('Navegación');
    expect(
      [...(bottom?.querySelectorAll('a') ?? [])].map((a) => [a.textContent?.trim(), a.getAttribute('href')]),
    ).toEqual([
      ['Inicio', '/naron'],
      ['Instalacións', '/naron/instalacions'],
    ]);
    // Sen sesión, «As miñas reservas» non sae.
    expect(el.textContent).not.toContain('reservas');
    expect(el.querySelector('nav[aria-label="Xestión do concello"]')).toBeNull();
    await expectNoAxeViolations(el);
  });

  it('con sesión (veciñanza): «As miñas reservas» na barra e no menú da persoa', async () => {
    const { fixture, el } = await render();
    const main = el.querySelector('nav[aria-label="Principal"]');
    expect(
      [...(main?.querySelectorAll('a') ?? [])].map((a) => [a.textContent?.trim(), a.getAttribute('href')]),
    ).toEqual([
      ['Instalacións', '/naron/instalacions'],
      ['As miñas reservas', '/naron/reservas'],
    ]);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('button.f-usermenu__trigger')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!.click();
    await fixture.whenStable();
    const panel = document.querySelector<HTMLElement>('.f-usermenu__panel')!;
    expect(
      [...panel.querySelectorAll('[role="menuitem"]')].map((i) => [i.textContent?.trim(), i.getAttribute('href')]),
    ).toEqual([
      ['A miña conta', 'http://localhost/auth/realms/furar/account'],
      ['As miñas reservas', '/naron/reservas'],
      ['Pechar sesión', null],
    ]);
    await expectNoAxeViolations(el);
  });

  it('Recepción (STAFF): «Reservas» arriba e abaixo; o menú sen «As miñas reservas» nin a web pública', async () => {
    const { fixture, el } = await render(fakeSession({ staff: 'naron' }));
    const links = (nav: Element | null) =>
      [...(nav?.querySelectorAll('a') ?? [])].map((a) => [a.textContent?.trim(), a.getAttribute('href')]);
    expect(links(el.querySelector('furar-site-bar nav[aria-label="Xestión do concello"]'))).toEqual([
      ['Reservas', '/naron/xestion/reservas'],
    ]);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(links(el.querySelector('nav.f-bottomnav'))).toEqual([['Reservas', '/naron/xestion/reservas']]);
    });
    // Os indicadores son só da xestión (P1 do dominio 3): nin na barra nin no menú.
    expect(el.textContent).not.toContain('Indicadores');
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('button.f-usermenu__trigger')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!.click();
    await fixture.whenStable();
    const panel = document.querySelector<HTMLElement>('.f-usermenu__panel')!;
    expect(
      [...panel.querySelectorAll('[role="menuitem"]')].map((i) => [i.textContent?.trim(), i.getAttribute('href')]),
    ).toEqual([
      // Fóra da xestión, a súa entrada primeiro (maqueta 56): as reservas do concello.
      ['Xestión do concello', '/naron/xestion/reservas'],
      ['A miña conta', 'http://localhost/auth/realms/furar/account'],
      ['Seguridade da conta', 'http://localhost/auth/realms/furar/account/account-security/signing-in'],
      ['Pechar sesión', null],
    ]);
    expect(panel.textContent).not.toContain('Indicadores');
  });

  it('persoal de varios concellos: «Concello» no menú da persoa leva á xestión do escollido', async () => {
    const session = fakeSession({
      manager: 'naron',
      memberships: [
        { slug: 'ares', name: 'Ares', role: 'STAFF' },
        { slug: 'naron', name: 'Narón', role: 'MANAGER' },
      ],
    });
    const { fixture, el } = await render(session);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('button.f-usermenu__trigger')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!.click();
    await fixture.whenStable();
    const panel = document.querySelector<HTMLElement>('.f-usermenu__panel')!;
    const row = panel.querySelector<HTMLElement>('[data-item="switch"]')!;
    expect(row.textContent?.replace(/\s+/g, ' ').trim()).toBe('Concello Narón');
    row.click();
    await fixture.whenStable();
    const options = [...panel.querySelectorAll<HTMLElement>('[role="menuitemradio"]')];
    expect(options.map((o) => [o.textContent?.replace(/\s+/g, ' ').trim(), o.getAttribute('aria-checked')])).toEqual([
      ['Ares Recepción', 'false'],
      ['Narón Xestión', 'true'],
    ]);
    options[0]!.click();
    expect(navigate).toHaveBeenCalledWith('/ares/xestion/reservas');
  });

  it('cun só concello como persoal, o menú da persoa non cambia', async () => {
    const session = fakeSession({ manager: 'naron', memberships: [{ slug: 'naron', name: 'Narón', role: 'MANAGER' }] });
    const { fixture, el } = await render(session);
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('button.f-usermenu__trigger')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!.click();
    await fixture.whenStable();
    expect(document.querySelector('.f-usermenu__panel [data-item="switch"]')).toBeNull();
  });

  it('a activa vai con aria-current, tamén cos filtros do listado no URL', async () => {
    const { fixture, el } = await render(anonymous());
    await TestBed.inject(Router).navigateByUrl('/naron/instalacions?deporte=padel');
    await fixture.whenStable();
    const current = [...el.querySelectorAll('a[aria-current="page"]')].map((a) => a.textContent?.trim());
    expect(current).toEqual(['Instalacións', 'Instalacións']);
  });

  it('escóndese ao baixar e volve co foco dentro', async () => {
    const { fixture, el } = await render();
    const bar = el.querySelector<HTMLElement>('furar-site-bar header')!;
    // Un fotograma = unha volta do bucle (o planificador de Angular tamén os usa: nunca síncrono).
    const frames = vi
      .spyOn(window, 'requestAnimationFrame')
      .mockImplementation((cb) => window.setTimeout(() => cb(0), 0) as unknown as number);
    const scrollTo = async (y: number) => {
      Object.defineProperty(window, 'scrollY', { value: y, configurable: true });
      window.dispatchEvent(new Event('scroll'));
      await new Promise((resolve) => setTimeout(resolve, 5));
      await fixture.whenStable();
    };
    await scrollTo(200);
    await scrollTo(400);
    await fixture.whenStable();

    expect(bar.hasAttribute('data-hidden')).toBe(true);
    bar
      .querySelector<HTMLAnchorElement>('.f-topbar__brand')
      ?.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    await fixture.whenStable();
    expect(bar.hasAttribute('data-hidden')).toBe(false);
    bar.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
    await scrollTo(100);
    await fixture.whenStable();
    expect(bar.hasAttribute('data-hidden')).toBe(false);
    frames.mockRestore();
    Object.defineProperty(window, 'scrollY', { value: 0, configurable: true });
  });

  it('co persoal: unha soa barra coa xestión como menú e o menú da persoa', async () => {
    const session = fakeSession({ manager: 'naron' });
    const { fixture, el } = await render(session);
    const navs = [...el.querySelectorAll('furar-site-bar nav')];
    expect(navs.map((n) => n.getAttribute('aria-label'))).toEqual(['Xestión do concello']);
    const links = (nav: Element | null) =>
      [...(nav?.querySelectorAll('a') ?? [])].map((a) => [a.textContent?.trim(), a.getAttribute('href')]);
    // Arriba, catro ligazóns: «Concello» é unha máis (maqueta 49), sen despregable.
    expect(links(navs[0])).toEqual([
      ['Instalacións', '/naron/xestion/instalacions'],
      ['Reservas', '/naron/xestion/reservas'],
      ['Indicadores', '/naron/xestion/indicadores'],
      ['Concello', '/naron/xestion/concello'],
    ]);
    expect(navs[0].querySelector('button')).toBeNull();
    // Abaixo, os mesmos catro destinos, sen «Máis» (bottom-nav.spec.ts).
    const bottom = el.querySelector('nav.f-bottomnav')!;
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(links(bottom)).toEqual(links(navs[0]));
    });
    expect(bottom.querySelector('[data-more]')).toBeNull();
    expect(el.querySelector('.f-topbar__brand')?.getAttribute('href')).toBe('/naron');

    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('.f-usermenu__trigger')?.textContent).toContain('Xestora Narón');
    });
    const trigger = el.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!;
    // Na pastilla, só o nome (maqueta 50); o rol vai na cabeceira do menú.
    expect(trigger.textContent?.replace(/\s+/g, ' ').trim()).toBe('Xestora Narón XN');
    expect(trigger.querySelector('.f-usermenu__avatar')?.textContent?.trim()).toBe('XN');
    expect(trigger.querySelector('furar-icon')).toBeNull();
    trigger.click();
    await fixture.whenStable();
    const panel = document.querySelector<HTMLElement>('.f-usermenu__panel')!;
    expect(panel.textContent).toContain('Xestión · Concello de Narón');
    expect(panel.textContent).toContain('xestora@naron.example');
    expect(
      [...panel.querySelectorAll('[role="menuitem"]')].map((i) => [i.textContent?.trim(), i.getAttribute('href')]),
    ).toEqual([
      // Na web pública, a xestión primeiro e en negra (maqueta 56), sen «Ver a web pública».
      ['Xestión do concello', '/naron/xestion/concello'],
      ['A miña conta', 'http://localhost/auth/realms/furar/account'],
      ['Seguridade da conta', 'http://localhost/auth/realms/furar/account/account-security/signing-in'],
      ['Pechar sesión', null],
    ]);
    expect(panel.querySelector('[data-item="manage"]')?.classList).toContain('f-usermenu__strong');
    await expectNoAxeViolations(panel);
    document.querySelector<HTMLButtonElement>('[data-item="logout"]')!.click();
    expect(session.logout).toHaveBeenCalled();
  });

  it('Xestión na xestión: sen «Xestión do concello» e con «Ver a web pública»', async () => {
    const { fixture } = await render(fakeSession({ manager: 'naron' }));
    await TestBed.inject(Router).navigateByUrl('/naron/xestion/instalacions');
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(document.querySelector('button.f-usermenu__trigger')).not.toBeNull();
    });
    document.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!.click();
    await fixture.whenStable();
    const ids = [...document.querySelectorAll('.f-usermenu__panel [data-item]')].map((i) =>
      i.getAttribute('data-item'),
    );
    expect(ids).toEqual(['account', 'public', 'security', 'logout']);
  });

  it('«Concello» é activa en calquera das súas pestanas, con aria-current; axe', async () => {
    const { fixture, el } = await render(fakeSession({ manager: 'naron' }));
    const concello = () => el.querySelector<HTMLAnchorElement>('furar-site-bar a[href="/naron/xestion/concello"]')!;
    expect(concello().hasAttribute('aria-current')).toBe(false);
    await TestBed.inject(Router).navigateByUrl('/naron/xestion/concello/persoal');
    await fixture.whenStable();
    expect(concello().getAttribute('aria-current')).toBe('page');
    expect(concello().classList).toContain('f-topbar__link--active');
    await expectNoAxeViolations(el.querySelector('furar-site-bar')!);
    await TestBed.inject(Router).navigateByUrl('/naron/xestion/indicadores');
    await fixture.whenStable();
    expect(concello().hasAttribute('aria-current')).toBe(false);
    expect(el.querySelector('furar-site-bar a[aria-current="page"]')?.textContent?.trim()).toBe('Indicadores');
  });

  it('a plataforma nun concello: Instalacións e «Concello», sen Reservas nin Indicadores nin «As miñas reservas»', async () => {
    const { fixture, el } = await render(fakeSession({ platform: true }));
    // Configura o concello enteiro (ADR-0028, revisión do 2026-11-03), sen datos da veciñanza.
    const nav = el.querySelector('nav[aria-label="Xestión do concello"]')!;
    const hrefs = [...nav.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['/naron/xestion/instalacions', '/naron/xestion/concello']);
    expect(el.querySelector('a[href="/naron/xestion/reservas"]')).toBeNull();
    expect(el.querySelector('a[href="/naron/xestion/indicadores"]')).toBeNull();
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(el.querySelector('.f-usermenu__trigger')?.textContent).toContain('Xestora Narón');
    });
    el.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!.click();
    await fixture.whenStable();
    const ids = [...document.querySelectorAll('.f-usermenu__panel [data-item]')].map((i) =>
      i.getAttribute('data-item'),
    );
    // Co menú da xestión, tamén «Ver a web pública»; e o rexistro da plataforma. Sen «As miñas reservas».
    expect(ids).toEqual(['account', 'public', 'platform-activity', 'security', 'logout']);
    expect(document.querySelector('.f-usermenu__panel [data-item="platform-activity"]')?.getAttribute('href')).toBe(
      '/plataforma/rexistro',
    );
  });

  describe('a plataforma: a súa entrada no menú da persoa (maqueta 56)', () => {
    const MUNICIPALITIES = '/api/v1/municipalities?page=0&size=100';
    const summaries = (...names: [string, string][]) => ({
      items: names.map(([slug, name]) => ({ id: slug, slug, name })),
      page: 0,
      size: 100,
      totalItems: names.length,
    });

    async function openMenu(fixture: Awaited<ReturnType<typeof render>>['fixture']) {
      await vi.waitFor(async () => {
        await fixture.whenStable();
        expect(document.querySelector('button.f-usermenu__trigger')).not.toBeNull();
      });
      document.querySelector<HTMLButtonElement>('button.f-usermenu__trigger')!.click();
      await fixture.whenStable();
      return document.querySelector<HTMLElement>('.f-usermenu__panel')!;
    }

    it('sen ningún concello: «Novo concello», primeiro e en negra', async () => {
      const { fixture } = await render(fakeSession({ platform: true }));
      (await expectRequest(MUNICIPALITIES)).flush(summaries());
      const panel = await openMenu(fixture);
      const first = panel.querySelector('[role="menuitem"]')!;
      expect(first.getAttribute('data-item')).toBe('new-municipality');
      expect(first.textContent?.trim()).toBe('Novo concello');
      expect(first.getAttribute('href')).toBe('/plataforma/concellos/novo');
      expect(first.classList).toContain('f-usermenu__strong');
      expect(panel.querySelector('[data-item="switch"]')).toBeNull();
      await expectNoAxeViolations(panel);
    });

    it('con concellos: «Concellos ›» co actual; leva á sección «Concello» do escollido; «Novo concello» ao final', async () => {
      const { fixture } = await render(fakeSession({ platform: true }));
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      (await expectRequest(MUNICIPALITIES)).flush(summaries(['naron', 'Narón'], ['ferrol', 'Ferrol']));
      const panel = await openMenu(fixture);
      const row = panel.querySelector<HTMLElement>('[data-item="switch"]')!;
      expect(row.textContent?.replace(/\s+/g, ' ').trim()).toBe('Concellos Narón');
      expect(panel.querySelector('[data-item="new-municipality"]')).toBeNull();
      row.click();
      await fixture.whenStable();
      const options = [...panel.querySelectorAll<HTMLElement>('[role="menuitemradio"]')];
      expect(options.map((o) => [o.textContent?.trim(), o.getAttribute('aria-checked')])).toEqual([
        ['Ferrol', 'false'],
        ['Narón', 'true'],
      ]);
      expect(panel.querySelector('[data-item="new-municipality"]')?.getAttribute('href')).toBe(
        '/plataforma/concellos/novo',
      );
      await expectNoAxeViolations(panel);
      options[0]!.click();
      expect(navigate).toHaveBeenCalledWith('/ferrol/xestion/concello');
    });
  });
});

describe('destinos da xestión', () => {
  const keys = (links: ReturnType<typeof manageLinks>) => links.map((link) => link.path.join('/'));

  it('xestor: Instalacións · Reservas · Indicadores · Concello (unha sección con pestanas)', () => {
    expect(keys(manageLinks('naron', 'manager'))).toEqual([
      '//naron/xestion/instalacions',
      '//naron/xestion/reservas',
      '//naron/xestion/indicadores',
      '//naron/xestion/concello',
    ]);
    expect(manageLinks('naron', 'manager').at(-1)?.key).toBe('nav.municipality');
  });

  it('Recepción: só Reservas, a súa entrada (maqueta 50)', () => {
    expect(keys(manageLinks('naron', 'staff'))).toEqual(['//naron/xestion/reservas']);
  });

  it('plataforma: Instalacións e «Concello» (ADR-0028, revisión do 2026-11-03), sen Reservas nin Indicadores', () => {
    expect(keys(manageLinks('naron', 'platform'))).toEqual([
      '//naron/xestion/instalacions',
      '//naron/xestion/concello',
    ]);
  });
});

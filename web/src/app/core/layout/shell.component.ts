// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NgTemplateOutlet } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { ActivatedRouteSnapshot } from '@angular/router';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { filter } from 'rxjs';
import { ActionScopeDirective } from '../../shared/ui/action/action-scope';
import { DeploymentSettings } from '../deployment/deployment-settings';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { SOFTWARE_LEGAL_ANCHOR } from '../municipality/legal-anchors';
import { MunicipalityDirectory } from '../municipality/municipality-directory';
import type { LegalPageKey } from '../municipality/legal-pages';
import { LEGAL_PAGE_PATHS, externalLegalUrl, legalPageMode, legalPagePublished } from '../municipality/legal-pages';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ToastComponent } from '../../shared/ui/toast/toast.component';
import { BottomNavComponent } from './bottom-nav.component';
import { ManageTabsComponent } from './manage-tabs.component';
import { ExternalHrefDirective } from './external-href.directive';
import { OfflineBannerComponent } from './offline-banner.component';
import { SiteBarComponent } from './site-bar.component';

/**
 * Como ocupa o contido o ancho: o contedor da barra superior e do pé (por defecto) ou ancho
 * enteiro (`data.layout = 'bleed'`, portada: cada sección pon o seu contedor).
 */
export type ShellLayout = 'page' | 'bleed';

function layoutOf(route: ActivatedRouteSnapshot): ShellLayout {
  let current: ActivatedRouteSnapshot | null = route;
  let layout: ShellLayout = 'page';
  while (current) {
    layout = (current.data['layout'] as ShellLayout | undefined) ?? layout;
    current = current.firstChild;
  }
  return layout;
}

/**
 * Pé enteiro (`full`) só nas páxinas principais públicas: as portadas (`layout: 'bleed'`) e as
 * que o piden (`data.footer = 'full'`, as legais); no resto, unha liña discreta (`line`).
 */
export type ShellFooter = 'full' | 'line';

export function footerOf(route: ActivatedRouteSnapshot): ShellFooter {
  for (let current: ActivatedRouteSnapshot | null = route; current; current = current.firstChild) {
    if (current.data['footer'] === 'full' || current.data['layout'] === 'bleed') return 'full';
  }
  return 'line';
}

/**
 * Unha ligazón do pé: ruta da app (`link`), ficheiro servido á parte (`href`), web do concello
 * (`external`) ou un correo (`email`: o rótulo vai diante, como texto, e a ligazón é o enderezo).
 */
export interface FooterLink {
  readonly key: string;
  readonly label: string;
  readonly link?: readonly string[];
  readonly fragment?: string;
  readonly href?: string;
  readonly external?: string;
  readonly email?: string;
}

/** Unha columna do pé enteiro: o seu título e as súas ligazóns. */
export interface FooterColumn {
  readonly key: 'legal' | 'service' | 'contact';
  readonly title: string;
  readonly links: readonly FooterLink[];
}

/**
 * Estrutura común: salto ao contido, barra superior de cristal (marca, menú, idioma, sesión e
 * xestión), contido, pé e, no móbil, barra inferior. O acceso anterior do persoal (ENS op.acc.6
 * R5) vese en «A miña conta», non nas páxinas.
 */
const FOOTER_ORDER: readonly LegalPageKey[] = ['legalNotice', 'privacy', 'accessibility'];
const FOOTER_LABELS = {
  accessibility: 'footer.accessibility',
  privacy: 'footer.privacy',
  legalNotice: 'footer.legalNotice',
} as const;

@Component({
  selector: 'furar-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    NgTemplateOutlet,
    TranslocoPipe,
    SiteBarComponent,
    BottomNavComponent,
    ManageTabsComponent,
    OfflineBannerComponent,
    ToastComponent,
    IconComponent,
    ExternalHrefDirective,
    ActionScopeDirective,
  ],
  host: { class: 'f-shell' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
})
export class ShellComponent {
  protected readonly municipality = inject(CurrentMunicipality);
  /**
   * Accesibilidade, Privacidade e Aviso legal no pé: a ruta de Furar ou, se o concello publica a
   * páxina na súa web (modo «Páxina propia»), o seu enderezo (ADR-0017 punto 8). Sen páxina (modo
   * «Sen páxina»), sen ligazón.
   */
  private readonly legalLinks = computed((): FooterLink[] => {
    const slug = this.municipality.slug();
    if (!slug) return [];
    const legal = this.municipality.value()?.legal;
    return FOOTER_ORDER.filter((page) => legalPagePublished(legal, page)).map((page) => ({
      key: page,
      label: FOOTER_LABELS[page],
      link: ['/', slug, LEGAL_PAGE_PATHS[page]],
      external: externalLegalUrl(legal, page) ?? undefined,
    }));
  });
  /**
   * «Soporte: correo» no pé, se o despregue ten correo de soporte (`FURAR_SUPPORT_EMAIL`); é do
   * despregue, non do concello. Vai despois das ligazóns legais, no pé enteiro e na liña.
   */
  private readonly deployment = inject(DeploymentSettings);
  private readonly supportLinks = computed((): FooterLink[] => {
    const email = this.deployment.supportEmail();
    return email ? [{ key: 'support', label: 'footer.support', email }] : [];
  });
  /** Pé enteiro (portadas e páxinas legais) ou unha liña (o resto). */
  protected readonly footer = signal<ShellFooter>('line');
  /** «Cookies»: a sección de Privacidade desta web, que sae sempre (só técnicas, sen banner). */
  private readonly cookiesLinks = computed((): FooterLink[] => {
    const slug = this.municipality.slug();
    return slug
      ? [{ key: 'cookies', label: 'footer.cookies', link: ['/', slug, LEGAL_PAGE_PATHS.privacy], fragment: 'cookies' }]
      : [];
  });
  /** Os legais na orde do pé: Aviso legal, Privacidade, Cookies e Accesibilidade. */
  private readonly legalInOrder = computed((): FooterLink[] => {
    const legal = this.legalLinks();
    const by = (key: string) => legal.filter((link) => link.key === key);
    return [...by('legalNotice'), ...by('privacy'), ...this.cookiesLinks(), ...by('accessibility')];
  });
  /**
   * Pé enteiro, por columnas: «Legal»; «Servizo» (datos abertos, se os hai, «¿Es un concello?», se
   * o despregue a ten e só na portada común, e «Código e licenzas», a sección «Software» do aviso legal); «Contacto», só co correo de soporte
   * (sen el, a columna non existe).
   */
  protected readonly footerColumns = computed((): FooterColumn[] => {
    const slug = this.municipality.slug();
    const openData: FooterLink[] = (slug ? this.municipality.value()?.openData : this.commonOpenData())
      ? [{ key: 'openData', label: 'footer.openData', link: slug ? ['/', slug, 'datos-abertos'] : ['/datos-abertos'] }]
      : [];
    const route = this.softwareRoute();
    const software: FooterLink = route
      ? { key: 'software', label: 'footer.software', link: route, fragment: SOFTWARE_LEGAL_ANCHOR }
      : { key: 'software', label: 'footer.software', href: '/codigo-fonte' };
    // «¿Es un concello?» (ADR-0042, revisión do 2026-11-17): só se o despregue ten a función e só
    // na portada común; nin nas portadas nin nas páxinas de cada concello.
    const joinRequest: FooterLink[] =
      this.deployment.joinRequests() && this.commonHome()
        ? [{ key: 'joinRequest', label: 'footer.joinRequest', link: ['/alta-concello'] }]
        : [];
    const columns: FooterColumn[] = [
      { key: 'legal', title: 'footer.legalTitle', links: this.legalInOrder() },
      { key: 'service', title: 'footer.serviceTitle', links: [...openData, ...joinRequest, software] },
      { key: 'contact', title: 'footer.contactTitle', links: this.supportLinks() },
    ];
    return columns.filter((column) => column.links.length > 0);
  });
  /** Liña das páxinas interiores: os catro legais e o soporte. */
  protected readonly footerLine = computed((): FooterLink[] => [...this.legalInOrder(), ...this.supportLinks()]);
  /**
   * «Código e licenzas» leva á sección «Software» do aviso legal de Furar (o programa, a súa
   * licenza, o código e as licenzas de terceiros); se o concello usa outro aviso legal ou non hai
   * concello, ao código fonte.
   */
  private readonly softwareRoute = computed(() => {
    const slug = this.municipality.slug();
    return slug && legalPageMode(this.municipality.value()?.legal, 'legalNotice') === 'FURAR'
      ? ['/', slug, LEGAL_PAGE_PATHS.legalNotice]
      : null;
  });
  /**
   * «Datos abertos» de toda a instalación no pé da raíz (ADR-0034, punto 24): con varios concellos
   * e só se algún publica os seus datos abertos. Usa a lista que xa leu a raíz
   * (`MunicipalityDirectory`), sen petición nova; mentres non se leu, sen ligazón.
   */
  private readonly directory = inject(MunicipalityDirectory);
  /** Sen concello e con varios na instalación: a portada común (o único pé enteiro sen concello). */
  private readonly commonHome = computed(
    () => !this.municipality.slug() && (this.directory.municipalities() ?? []).length > 1,
  );
  private readonly commonOpenData = computed(
    () => this.commonHome() && (this.directory.municipalities() ?? []).some((m) => m.openData),
  );
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');
  protected readonly layout = signal<ShellLayout>('page');

  constructor() {
    let first = true;
    let path = '';
    // Ao cambiar de páxina, o foco vai ao título novo (WCAG 2.4.3), ou ao destino do fragmento
    // (#instalacions); non na primeira carga nin se se navegou desde unhas pestanas. Se só cambian os parámetros da busca (`?data=` ao
    // escoller nun campo, os filtros dun listado), é a mesma páxina e o foco queda onde estaba.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => {
        this.layout.set(layoutOf(this.router.routerState.snapshot.root));
        this.footer.set(footerOf(this.router.routerState.snapshot.root));
        const fragment = this.router.parseUrl(this.router.url).fragment;
        const previous = path;
        path = this.router.url.split(/[?#]/)[0];
        if (first) {
          first = false;
          return;
        }
        if (path === previous && !fragment) return;
        afterNextRender(() => this.focusContent(fragment), { injector: this.injector });
      });
  }

  protected skipToContent(event: Event): void {
    event.preventDefault();
    this.focusContent(null);
  }

  private focusContent(fragment: string | null): void {
    const main = this.main().nativeElement;
    // Pestanas que son páxinas (`furar-tab-nav`): o foco queda na pestana escollida.
    if (!fragment && main.ownerDocument.activeElement?.closest('[data-keep-focus]')) return;
    const anchor = fragment ? main.ownerDocument.getElementById(fragment) : null;
    const target = (anchor && main.contains(anchor) ? anchor : null) ?? main.querySelector<HTMLElement>('h1');
    if (target) {
      target.tabIndex = -1;
      target.focus();
    } else {
      main.focus();
    }
  }
}

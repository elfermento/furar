// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
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
import { formatInstant } from '../../shared/ui/calendar/madrid-time';
import { StaffAccessService } from '../auth/staff-access.service';
import { CurrentMunicipality } from '../municipality/current-municipality';
import type { LegalPageKey } from '../municipality/legal-pages';
import { LEGAL_PAGE_PATHS, externalLegalUrl, legalPageMode, legalPagePublished } from '../municipality/legal-pages';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { ToastComponent } from '../../shared/ui/toast/toast.component';
import { BottomNavComponent } from './bottom-nav.component';
import { ExternalHrefDirective } from './external-href.directive';
import { isManagementUrl } from './last-sign-in';
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
 * Estrutura común: salto ao contido, barra superior de cristal (marca, menú, idioma, sesión e
 * xestión), contido, pé e, no móbil, barra inferior. Nas páxinas da xestión, o persoal ve o seu
 * acceso anterior (ENS op.acc.6 R5) como texto, sen interromper: non é unha alerta.
 */
const FOOTER_ORDER: readonly LegalPageKey[] = ['accessibility', 'privacy', 'legalNotice'];
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
    TranslocoPipe,
    SiteBarComponent,
    BottomNavComponent,
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
   * páxina na súa web (modo «Páxina propia»), o seu enderezo. Sen páxina (modo «Sen páxina»), sen
   * ligazón.
   */
  protected readonly legalLinks = computed(() => {
    const legal = this.municipality.value()?.legal;
    return FOOTER_ORDER.filter((page) => legalPagePublished(legal, page)).map((page) => ({
      page,
      path: LEGAL_PAGE_PATHS[page],
      label: FOOTER_LABELS[page],
      external: externalLegalUrl(legal, page),
    }));
  });
  /**
   * A marca do pé («Reservas deportivas · software libre») leva á sección «Software» do aviso legal
   * de Furar, que nomea o programa e a súa licenza (EUPL-1.2); se o concello usa outro aviso legal
   * ou non hai concello, ao código fonte.
   */
  protected readonly brandRoute = computed(() => {
    const slug = this.municipality.slug();
    return slug && legalPageMode(this.municipality.value()?.legal, 'legalNotice') === 'FURAR'
      ? ['/', slug, LEGAL_PAGE_PATHS.legalNotice]
      : null;
  });
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');
  protected readonly layout = signal<ShellLayout>('page');
  private readonly staff = inject(StaffAccessService);
  private readonly management = signal(false);
  /** «27/10/2026, 18:05» (Europe/Madrid) só na xestión e se a API devolve un acceso anterior. */
  protected readonly lastSignIn = computed(() => {
    const previous = this.staff.previousSignInAt();
    return this.management() && previous ? formatInstant(previous) : null;
  });

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
        this.management.set(isManagementUrl(this.router.url));
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

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  runInInjectionContext,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { DisplayPreferencesComponent } from '../../shared/ui/display-preferences/display-preferences.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { initials } from '../../shared/ui/user-menu/initials';
import { PickMenuComponent } from '../../shared/ui/pick-menu/pick-menu.component';
import type { PickMenuOption, PickSheetRequest } from '../../shared/ui/pick-menu/pick-menu.component';
import { UserMenuComponent } from '../../shared/ui/user-menu/user-menu.component';
import { PlatformAccessService } from '../auth/platform-access.service';
import { SessionService } from '../auth/session.service';
import { managementHome } from '../auth/roles';
import { StaffAccessService } from '../auth/staff-access.service';
import type { StaffMembershipsService } from '../auth/staff-memberships.service';
import { LanguageService } from '../i18n/language.service';
import { isLanguage } from '../i18n/languages';
import { CurrentMunicipality } from '../municipality/current-municipality';
import type { MunicipalityDirectory } from '../municipality/municipality-directory';
import { COMPACT_QUERY, guestMenuItems, languageMenu, mediaMatches } from './language-menu';
import { isManagementUrl } from './management-url';
import { EXACT_WITH_FRAGMENT } from './manage-links';
import { ManageNav } from './manage-nav';
import type * as Switcher from './municipality-switcher';
import { BOOKING_STYLESHEET } from './booking-styles';
import { lazyStylesheet } from './lazy-stylesheet';
import { userMenuItems } from './user-menu-items';
import { HideOnScrollDirective } from './hide-on-scroll.directive';
import { placeParams } from '../../shared/i18n/place-name';

/**
 * Barra superior de cristal, unha soa para todo (docs/deseno.md §6): marca do concello (nome e
 * «Deportes municipais», unha soa vez; leva á portada pública), menú, idioma e sesión. Sen sesión
 * da xestión, o menú público («Instalacións»); coa xestión, o seu (Instalacións · Reservas ·
 * Indicadores · Concello, todas ligazóns, coa activa marcada con `aria-current`); Recepción (STAFF),
 * só «Reservas». O rol sae da API (`StaffAccessService`). Con sesión, o menú da persoa
 * (`furar-user-menu`), co nome no botón (sen o rol): «A miña conta» (a pantalla da app), «As miñas
 * reservas» e «Persoas ao meu cargo» (só a veciñanza) e pechar sesión. Primeiro, a entrada á
 * xestión: a plataforma, «Concellos ›» (ou «Novo concello» se non hai ningún); Xestión e
 * Recepción, «Xestión do concello» fóra dela. O cambio de concello do persoal, só con dous ou máis
 * concellos; sen ningún escollido, ademais, o botón «Concello ▾» na barra.
 * Á dereita, o botón de accesibilidade e a conta. O idioma vai no menú da persoa en
 * todos os anchos e, sen sesión, o botón da persoa abre un menú con «Entrar» e «Idioma». No móbil
 * (< 640 px), unha soa fila co escudo, a accesibilidade e a conta. Na portada común
 * (sen concello), a marca é unha soa liña, «Reservas deportivas».
 * Fixa ao baixar (sticky); escóndese ao baixar e volve ao subir, co foco dentro ou cun menú dela
 * aberto (HideOnScrollDirective). O seu alto vai a `--f-topbar-h` en <html> para o
 * `scroll-padding-top` e o que vai pegado debaixo (`--f-topbar-offset`).
 */
@Component({
  selector: 'furar-site-bar',
  imports: [
    RouterLink,
    RouterLinkActive,
    TranslocoPipe,
    DisplayPreferencesComponent,
    IconComponent,
    PickMenuComponent,
    UserMenuComponent,
    HideOnScrollDirective,
  ],
  host: {
    class: 'f-topbar',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './site-bar.component.html',
})
export class SiteBarComponent {
  protected readonly session = inject(SessionService);
  private readonly staff = inject(StaffAccessService);
  private readonly platform = inject(PlatformAccessService);
  /** O cambio de concello, cargado á parte en canto hai sesión (non entra na carga inicial). */
  private readonly switching = signal<{
    /** As funcións do cambio (`municipality-switcher.ts`). */
    readonly lib: typeof Switcher;
    readonly memberships: StaffMembershipsService;
    /** Os concellos da instalación: «Concellos ›» ou «Novo concello» da plataforma. */
    readonly directory: MunicipalityDirectory;
  } | null>(null);
  protected readonly municipality = inject(CurrentMunicipality);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  /** Na barra do móbil (o mesmo corte), «Entrar» é só a icona; no escritorio, icona e texto. */
  protected readonly compact = mediaMatches(this.document.defaultView, COMPACT_QUERY, inject(DestroyRef));
  /** Nunha páxina da xestión (`/:concello/xestion/...`), non na web pública. */
  private readonly inManagement = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => isManagementUrl(this.router.url)),
    ),
    { initialValue: isManagementUrl(this.router.url) },
  );
  private readonly bar = viewChild.required<ElementRef<HTMLElement>>('bar');
  protected readonly exact = EXACT_WITH_FRAGMENT;
  protected readonly initials = initials;
  private readonly language = inject(LanguageService);

  /**
   * A marca leva ao inicio de todo (a portada común) en toda a web pública: é o «volver» ao
   * principio, sen botóns de atrás nas portadas. Na xestión, á portada do concello que se xestiona.
   */
  protected readonly homeLink = computed(() => {
    const slug = this.municipality.slug();
    return slug && this.inManagement() ? ['/', slug] : ['/'];
  });
  /** O menú da xestión: os apartados, co actual marcado e os contadores. */
  protected readonly manage = inject(ManageNav);
  /** Co menú da xestión no canto do público. */
  protected readonly canManage = computed(() => this.manage.sections().length > 0);

  private readonly transloco = inject(TranslocoService);
  /** Para que os textos do menú cambien co idioma (cando a tradución xa cargou). */
  private readonly lang = toSignal(this.transloco.selectTranslation());
  /** Traducir que cambia co idioma (para os menús calculados). */
  private readonly t = computed(() => {
    this.lang();
    return (key: string) => this.transloco.translate(key);
  });
  /** Conta de traballo (persoal deste ou doutro concello) ou plataforma: sen «As miñas reservas». */
  protected readonly workAccount = computed(
    () =>
      this.staff.role() !== null ||
      this.staff.workAccount() ||
      this.platform.platformAdmin() ||
      (this.switching()?.memberships.memberships().length ?? 0) > 0,
  );
  protected readonly roleLabel = computed(() => {
    this.lang();
    const role = this.municipality.slug() === null ? null : this.staff.mainRole();
    return role ? this.transloco.translate('session.role.' + role) : '';
  });
  protected readonly contextLabel = computed(() => {
    this.lang();
    const name = this.municipality.name();
    return name
      ? this.transloco.translate('session.municipality', placeParams(name, this.transloco.getActiveLang()))
      : '';
  });
  protected readonly menuItems = computed(() => {
    this.lang();
    const context = {
      slug: this.municipality.slug(),
      staffRole: this.staff.role(),
      workAccount: this.workAccount(),
      platformAdmin: this.platform.platformAdmin(),
      inManagement: this.inManagement(),
      municipalities: this.platform.platformAdmin()
        ? (this.switching()?.directory.municipalities()?.length ?? null)
        : null,
    };
    return userMenuItems(context, (key) => this.transloco.translate(key));
  });

  /** «Idioma ›» no menú da persoa (en todos os anchos), co idioma actual á dereita. */
  protected readonly languageMenu = computed(() => languageMenu(this.language.lang(), this.t()));
  /** Sen sesión: «Entrar» (o idioma vai despois, tras unha liña). */
  protected readonly guestItems = computed(() => guestMenuItems(this.t()));

  /**
   * O cambio no menú da persoa: a plataforma, «Concellos ›» (todos, e «Novo concello»); o persoal
   * de dous ou máis concellos, «Concello ›».
   */
  protected readonly switcher = computed(() => {
    const t = this.t();
    const switching = this.switching();
    if (!switching) return null;
    const slug = this.municipality.slug();
    const memberships = switching.memberships.memberships();
    if (this.platform.platformAdmin()) {
      const list = switching.directory.municipalities() ?? [];
      return switching.lib.platformSwitcher(list, slug, t, this.transloco.getActiveLang(), memberships);
    }
    return switching.lib.municipalitySwitcher(memberships, slug, t);
  });

  /**
   * «Concello ▾» na barra: o persoal de dous ou máis concellos sen ningún escollido (na portada
   * común ou nunha páxina sen concello). Ao escoller, entra na xestión dese concello e o botón
   * desaparece; despois, o cambio vai en «Concello ›» do menú da persoa.
   */
  protected readonly pickOptions = computed((): readonly PickMenuOption[] => {
    const t = this.t();
    const switching = this.switching();
    if (!switching || this.platform.platformAdmin() || this.municipality.slug() !== null) return [];
    return switching.lib.municipalityPicks(switching.memberships.memberships(), t);
  });

  constructor() {
    const injector = inject(Injector);
    const destroyRef = inject(DestroyRef);
    let destroyed = false;
    destroyRef.onDestroy(() => (destroyed = true));
    let loading = false;
    effect(() => {
      if (!this.session.authenticated() || loading) return;
      loading = true;
      void import('./municipality-switcher').then((lib) => {
        // O módulo pode chegar despois de destruír a barra (o seu inxector xa non serve).
        if (destroyed) return;
        this.switching.set({
          lib,
          memberships: injector.get(lib.StaffMembershipsService),
          directory: injector.get(lib.MunicipalityDirectory),
        });
      });
    });
    // A plataforma: os concellos da instalación (tamén despois dunha alta, que invalida a lista).
    effect(() => {
      const directory = this.switching()?.directory;
      if (directory && this.platform.platformAdmin() && directory.municipalities() === null) {
        untracked(() => void directory.list().catch(() => undefined));
      }
    });
    afterNextRender(() => {
      const root = this.document.documentElement;
      const resize =
        typeof ResizeObserver === 'undefined'
          ? null
          : new ResizeObserver(() =>
              root.style.setProperty('--f-topbar-h', `${this.bar().nativeElement.offsetHeight}px`),
            );
      resize?.observe(this.bar().nativeElement);
      destroyRef.onDestroy(() => resize?.disconnect());
    });
  }

  protected login(): void {
    void this.session.login(this.here());
  }

  /**
   * «Persoas ao meu cargo» desde calquera páxina: primeiro a folla das capas e das listas (non vai
   * na carga inicial) e despois o diálogo e o servizo das capas, que tamén se cargan á parte
   * (orzamento, D27).
   */
  private async openDependents(): Promise<void> {
    await runInInjectionContext(this.injector, lazyStylesheet(BOOKING_STYLESHEET, 'furar-booking-css'));
    const [{ openDependents }, { DialogService }] = await Promise.all([
      import('../../shared/dependents/open-dependents'),
      import('../../shared/ui/dialog/dialog.service'),
    ]);
    await openDependents(this.injector.get(DialogService));
  }

  /** «Concello ▾» no móbil: a lista nunha folla desde abaixo (todo cárgase á parte, D27). */
  protected async openPickSheet(request: PickSheetRequest): Promise<void> {
    const chosen = await this.switching()?.lib.pickInSheet(this.injector, request);
    if (chosen) this.onSwitch(chosen);
  }

  protected useLanguage(lang: string): void {
    if (isLanguage(lang)) void this.language.use(lang, true);
  }

  protected onMenu(id: string): void {
    if (id === 'login') this.login();
    if (id === 'logout') void this.session.logout();
    if (id === 'dependents') void this.openDependents();
  }

  /**
   * Á xestión do concello escollido: a plataforma, á sección «Concello»; o persoal, á
   * entrada do seu rol (a mesma ca tras aceptar a invitación).
   */
  protected onSwitch(slug: string): void {
    if (this.platform.platformAdmin()) {
      void this.router.navigateByUrl(`/${slug}/xestion/concello`);
      return;
    }
    const chosen = this.switching()
      ?.memberships.memberships()
      .find((m) => m.slug === slug);
    if (chosen) void this.router.navigateByUrl(managementHome(chosen.slug, chosen.role));
  }

  /** O URL do navegador (non router.url): vale aínda que a primeira navegación non rematase. */
  private here(): string {
    const location = this.document.defaultView?.location;
    return location ? location.pathname + location.search + location.hash : this.router.url;
  }
}

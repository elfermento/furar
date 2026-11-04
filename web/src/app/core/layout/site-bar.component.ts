// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
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
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { initials } from '../../shared/ui/user-menu/initials';
import { UserMenuComponent } from '../../shared/ui/user-menu/user-menu.component';
import { PlatformAccessService } from '../auth/platform-access.service';
import { SessionService } from '../auth/session.service';
import { managementHome } from '../auth/roles';
import { StaffAccessService } from '../auth/staff-access.service';
import type { StaffMembershipsService } from '../auth/staff-memberships.service';
import { CurrentMunicipality } from '../municipality/current-municipality';
import type { MunicipalityDirectory } from '../municipality/municipality-directory';
import { LanguageSwitcherComponent } from './language-switcher.component';
import { isManagementUrl } from './last-sign-in';
import { EXACT_WITH_FRAGMENT, manageKind, manageLinks } from './manage-links';
import type { municipalitySwitcher, platformSwitcher } from './municipality-switcher';
import { userMenuItems } from './user-menu-items';
import { ScrollHide } from './scroll-hide';
import { placeParams } from '../../shared/i18n/place-name';

/**
 * Barra superior de cristal, unha soa para todo (docs/deseno.md §6): marca do concello (nome e
 * «Deportes municipais», unha soa vez; leva á portada pública), menú, idioma e sesión. Sen sesión
 * da xestión, o menú público («Instalacións»); coa xestión, o seu (Instalacións · Reservas ·
 * Indicadores · Concello, todas ligazóns, coa activa marcada con `aria-current`); Recepción (STAFF),
 * só «Reservas». O rol sae da API (`StaffAccessService`). Con sesión, o menú da persoa
 * (`furar-user-menu`), co nome no botón (sen o rol; maqueta 50): a súa conta en Keycloak, «As miñas
 * reservas» (só a veciñanza: non as contas de traballo nin a plataforma), a web pública (Xestión e
 * a plataforma) e pechar sesión; o persoal e a plataforma, tamén «Seguridade da conta» (o
 * contrasinal). Primeiro, a entrada á xestión (maqueta 56): a plataforma, «Concellos ›» (ou
 * «Novo concello» se non hai ningún); Xestión e Recepción, «Xestión do concello» fóra dela. O
 * cambio de concello do persoal, só con dous ou máis concellos.
 * Fixa ao baixar (sticky); escóndese ao baixar pasados 80 px e volve ao subir ou co foco dentro
 * (ScrollHide). O seu alto vai a `--f-topbar-h` en <html> para o `scroll-padding-top`.
 */
@Component({
  selector: 'furar-site-bar',
  imports: [
    RouterLink,
    RouterLinkActive,
    TranslocoPipe,
    LanguageSwitcherComponent,
    ButtonDirective,
    IconComponent,
    UserMenuComponent,
  ],
  host: {
    class: 'f-topbar',
    '(focusin)': 'scroll.focus(true)',
    '(focusout)': 'onFocusOut($event)',
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
    readonly memberships: StaffMembershipsService;
    readonly build: typeof municipalitySwitcher;
    /** Os concellos da instalación: «Concellos ›» ou «Novo concello» da plataforma. */
    readonly directory: MunicipalityDirectory;
    readonly buildPlatform: typeof platformSwitcher;
  } | null>(null);
  protected readonly municipality = inject(CurrentMunicipality);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  /** Nunha páxina da xestión (`/:concello/xestion/...`), non na web pública. */
  private readonly inManagement = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => isManagementUrl(this.router.url)),
    ),
    { initialValue: isManagementUrl(this.router.url) },
  );
  private readonly bar = viewChild.required<ElementRef<HTMLElement>>('bar');
  protected readonly scroll = new ScrollHide();
  protected readonly exact = EXACT_WITH_FRAGMENT;
  protected readonly initials = initials;

  protected readonly homeLink = computed(() => {
    const slug = this.municipality.slug();
    return slug ? ['/', slug] : ['/'];
  });
  protected readonly manageLinks = computed(() => {
    const slug = this.municipality.slug();
    const kind = manageKind(this.session.authenticated(), this.staff.role(), this.platform.platformAdmin());
    return slug !== null && kind !== null ? manageLinks(slug, kind) : [];
  });
  /** Co menú da xestión no canto do público. */
  protected readonly canManage = computed(() => this.manageLinks().length > 0);

  private readonly transloco = inject(TranslocoService);
  /** Para que os textos do menú cambien co idioma (cando a tradución xa cargou). */
  private readonly lang = toSignal(this.transloco.selectTranslation());
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
      canManage: this.canManage(),
      workAccount: this.workAccount(),
      platformAdmin: this.platform.platformAdmin(),
      inManagement: this.inManagement(),
      municipalities: this.platform.platformAdmin()
        ? (this.switching()?.directory.municipalities()?.length ?? null)
        : null,
      homeLink: this.homeLink(),
      accountUrl: this.session.accountUrl(),
      securityUrl: this.session.securityUrl(),
    };
    return userMenuItems(context, (key) => this.transloco.translate(key));
  });

  /**
   * O cambio no menú da persoa: a plataforma, «Concellos ›» (todos, e «Novo concello»); o persoal
   * de dous ou máis concellos, «Concello ›».
   */
  protected readonly switcher = computed(() => {
    this.lang();
    const switching = this.switching();
    if (!switching) return null;
    const t = (key: string) => this.transloco.translate(key);
    const slug = this.municipality.slug();
    if (this.platform.platformAdmin()) {
      const list = switching.directory.municipalities() ?? [];
      return switching.buildPlatform(list, slug, t, this.transloco.getActiveLang());
    }
    return switching.build(switching.memberships.memberships(), slug, t);
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
      void import('./municipality-switcher').then(
        ({ StaffMembershipsService, MunicipalityDirectory, municipalitySwitcher, platformSwitcher }) => {
          // O módulo pode chegar despois de destruír a barra (o seu inxector xa non serve).
          if (destroyed) return;
          this.switching.set({
            memberships: injector.get(StaffMembershipsService),
            build: municipalitySwitcher,
            directory: injector.get(MunicipalityDirectory),
            buildPlatform: platformSwitcher,
          });
        },
      );
    });
    // A plataforma: os concellos da instalación (tamén despois dunha alta, que invalida a lista).
    effect(() => {
      const directory = this.switching()?.directory;
      if (directory && this.platform.platformAdmin() && directory.municipalities() === null) {
        untracked(() => void directory.list().catch(() => undefined));
      }
    });
    const view = this.document.defaultView;
    if (view) {
      let frame = 0;
      const onScroll = () => {
        view.cancelAnimationFrame(frame);
        frame = view.requestAnimationFrame(() => this.scroll.update(view.scrollY));
      };
      view.addEventListener('scroll', onScroll, { passive: true });
      destroyRef.onDestroy(() => {
        view.removeEventListener('scroll', onScroll);
        view.cancelAnimationFrame(frame);
      });
    }
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

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (!next || !(event.currentTarget as HTMLElement).contains(next)) {
      this.scroll.focus(false);
    }
  }

  protected login(): void {
    void this.session.login(this.here());
  }

  protected onMenu(id: string): void {
    if (id === 'logout') void this.session.logout();
  }

  /**
   * Á xestión do concello escollido: a plataforma, á sección «Concello» (maqueta 56); o persoal, á
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

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import type { IsActiveMatchOptions } from '@angular/router';
import { Router, RouterLink, RouterLinkActive, isActive } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import type { IconName } from '../../shared/ui/icon/icons';
import type { MoreMenuLink } from '../../shared/ui/menu/more-menu.component';
import { MoreMenuComponent } from '../../shared/ui/menu/more-menu.component';
import { overflow } from '../../shared/ui/menu/overflow';
import { PlatformAccessService } from '../auth/platform-access.service';
import { SessionService } from '../auth/session.service';
import { StaffAccessService } from '../auth/staff-access.service';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { EXACT_WITH_FRAGMENT, manageKind, manageLinks } from './manage-links';

/** Sitios da barra: con máis destinos, o último é «Máis» (a 320 px, cinco nomes parten palabras). */
const SLOTS = 4;

/** O `exact` de `routerLinkActiveOptions`, como opcións completas (as mesmas de Angular). */
const EXACT: IsActiveMatchOptions = {
  paths: 'exact',
  queryParams: 'exact',
  matrixParams: 'ignored',
  fragment: 'ignored',
};
const SUBTREE: IsActiveMatchOptions = {
  paths: 'subset',
  queryParams: 'subset',
  matrixParams: 'ignored',
  fragment: 'ignored',
};

interface BottomLink {
  readonly key: string;
  readonly icon: IconName;
  readonly path: readonly string[];
  readonly options: IsActiveMatchOptions;
}

/**
 * Barra inferior de cristal no móbil (< 900 px; no escritorio, `display: none`): os mesmos destinos
 * ca o menú da barra superior. Sen sesión do persoal, o inicio e «Instalacións»; co persoal, os da
 * xestión (Recepción, só «Reservas»). Só co concello. Como moito catro sitios: se hai máis destinos, os tres primeiros e
 * «Máis» (`furar-more-menu`: patrón disclosure, botón con `aria-expanded` que despregue enriba a
 * lista das ligazóns que faltan; Esc pecha e o foco volve ao botón), marcado como activo, e co
 * nome «Máis (páxina actual)», se a páxina é dunha das súas entradas.
 */
@Component({
  selector: 'furar-bottom-nav',
  imports: [RouterLink, RouterLinkActive, TranslocoPipe, IconComponent, MoreMenuComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (municipality.slug()) {
      <nav
        class="f-bottomnav f-glass"
        [attr.data-manage]="canManage() ? '' : null"
        [attr.aria-label]="(canManage() ? 'app.manageNav' : 'site.mobileNav') | transloco"
      >
        <ul>
          @for (link of slots().visible; track link.key) {
            <li>
              <a
                [routerLink]="link.path"
                routerLinkActive="f-bottomnav__link--active"
                ariaCurrentWhenActive="page"
                [routerLinkActiveOptions]="link.options"
              >
                <furar-icon [name]="link.icon" [size]="24" />
                {{ link.key | transloco }}
              </a>
            </li>
          }
          @if (moreLinks().length) {
            <li>
              <!-- «Máis» cárgase á parte: non entra na carga inicial. -->
              @defer (on immediate) {
                <furar-more-menu
                  buttonClass="f-bottomnav__more"
                  [label]="'nav.more' | transloco"
                  [links]="moreLinks()"
                  [active]="moreActive()"
                  [currentLabel]="'nav.currentPage' | transloco"
                />
              } @placeholder {
                <span class="f-bottomnav__more" aria-hidden="true">
                  <furar-icon name="more" [size]="24" />
                  {{ 'nav.more' | transloco }}
                </span>
              }
            </li>
          }
        </ul>
      </nav>
    }
  `,
})
export class BottomNavComponent {
  private readonly session = inject(SessionService);
  private readonly staff = inject(StaffAccessService);
  private readonly platform = inject(PlatformAccessService);
  private readonly router = inject(Router);
  protected readonly municipality = inject(CurrentMunicipality);
  private readonly manage = computed(() => {
    const slug = this.municipality.slug();
    const kind = manageKind(this.session.authenticated(), this.staff.role(), this.platform.platformAdmin());
    return slug !== null && kind !== null ? manageLinks(slug, kind) : [];
  });
  protected readonly canManage = computed(() => this.manage().length > 0);

  /** Os destinos da barra: os da xestión ou os públicos. */
  private readonly links = computed<BottomLink[]>(() => {
    const slug = this.municipality.slug() ?? '';
    if (this.canManage()) {
      return this.manage().map(({ key, icon, path, exact }) => ({
        key,
        icon,
        path,
        options: exact ? EXACT : SUBTREE,
      }));
    }
    return [
      { key: 'site.home', icon: 'home', path: ['/', slug], options: EXACT_WITH_FRAGMENT },
      { key: 'site.facilities', icon: 'grid', path: ['/', slug, 'instalacions'], options: EXACT_WITH_FRAGMENT },
    ];
  });

  protected readonly slots = computed(() => overflow(this.links(), SLOTS));
  private readonly transloco = inject(TranslocoService);
  /** Para que os textos de «Máis» cambien co idioma (cando a tradución xa cargou). */
  private readonly translations = toSignal(this.transloco.selectTranslation());
  protected readonly moreLinks = computed<MoreMenuLink[]>(() => {
    this.translations();
    return this.slots().more.map(({ key, icon, path, options }) => ({
      label: this.transloco.translate(key),
      icon,
      link: path,
      options,
    }));
  });
  protected readonly moreActive = computed(() =>
    this.slots().more.some((link) => isActive(this.router.createUrlTree([...link.path]), this.router, link.options)()),
  );
}

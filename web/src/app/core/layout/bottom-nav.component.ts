// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { NgTemplateOutlet } from '@angular/common';
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
import { CurrentMunicipality } from '../municipality/current-municipality';
import type { ManageSection, ManageTab } from './manage-links';
import { EXACT_WITH_FRAGMENT } from './manage-links';
import { ManageNav } from './manage-nav';

/** Sitios da barra sen contar o botón do QR: con máis apartados, o último é «Máis». */
const SLOTS = 4;

/** Activa nas páxinas debaixo (para «Máis»). */
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
  /** Só na xestión: o apartado (activo en calquera das súas pestanas). */
  readonly section: ManageSection | null;
}

/**
 * Barra inferior de cristal no móbil (< 900 px; no escritorio, `display: none`): os mesmos
 * apartados ca o menú da barra superior. Sen sesión do persoal, o inicio e «Instalacións»; co
 * persoal, os da xestión e, no medio, «Validar QR» como botón redondo elevado:
 * Instalacións · Reservas · [QR] · Indicadores · Concello; Recepción, Reservas · [QR] e as
 * súas outras páxinas. Ningún nome se parte nin se corta (letra que baixa co ancho da pantalla,
 * site.css). Como moito catro apartados: se hai máis, os tres primeiros e «Máis»
 * (`furar-more-menu`, patrón disclosure; Esc pecha e o foco volve ao botón), marcado como activo
 * se a páxina é dunha das súas entradas.
 */
/**
 * Nomes máis curtos só para a barra inferior: en castelán, «Municipio» no canto de
 * «Ayuntamiento», que non cabía; o menú do escritorio segue co de sempre.
 */
const SHORT_KEYS: Readonly<Record<string, string>> = { 'nav.municipality': 'nav.municipalityShort' };

@Component({
  selector: 'furar-bottom-nav',
  imports: [NgTemplateOutlet, RouterLink, RouterLinkActive, TranslocoPipe, IconComponent, MoreMenuComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (municipality.slug()) {
      <nav
        class="f-bottomnav f-glass"
        [attr.data-manage]="canManage() ? '' : null"
        [attr.data-qr]="manage.qr() ? '' : null"
        [attr.aria-label]="(canManage() ? 'app.manageNav' : 'site.mobileNav') | transloco"
      >
        <ul>
          @for (link of slots().visible; track link.key; let i = $index) {
            @if (i === qrAt()) {
              <ng-container [ngTemplateOutlet]="qrButton" />
            }
            <li>
              @if (link.section; as section) {
                <!-- En «Validar QR» a marca é o anel do botón central, non o apartado. -->
                @let active = manage.section() === section && !manage.tab()?.qr;
                <a
                  [routerLink]="link.path"
                  [class.f-bottomnav__link--active]="active"
                  [attr.aria-current]="active ? 'page' : null"
                >
                  <span class="f-bottomnav__icon">
                    <furar-icon [name]="link.icon" [size]="24" />
                    @if (manage.sectionCount(section, mobileTabs(section)); as n) {
                      <span class="f-count" data-count aria-hidden="true">{{ n }}</span>
                    }
                  </span>
                  {{ link.key | transloco }}
                  @if (manage.sectionCount(section, mobileTabs(section))) {
                    <span class="sr-only">, {{ manage.sectionCountText(section, mobileTabs(section)) }}</span>
                  }
                </a>
              } @else {
                <a
                  [routerLink]="link.path"
                  routerLinkActive="f-bottomnav__link--active"
                  ariaCurrentWhenActive="page"
                  [routerLinkActiveOptions]="link.options"
                >
                  <span class="f-bottomnav__icon"><furar-icon [name]="link.icon" [size]="24" /></span>
                  {{ link.key | transloco }}
                </a>
              }
            </li>
          }
          @if (qrAt() === slots().visible.length) {
            <ng-container [ngTemplateOutlet]="qrButton" />
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
        <!-- «Validar QR»: redondo, primario, elevado sobre a barra; co anel, na súa páxina. -->
        <ng-template #qrButton>
          @if (manage.qr(); as qr) {
            <li class="f-bottomnav__qr-slot">
              <a
                class="f-bottomnav__qr"
                data-bottom-qr
                [routerLink]="qr.path"
                [attr.aria-label]="qr.key | transloco"
                [attr.title]="qr.key | transloco"
                [attr.aria-current]="manage.tab() === qr ? 'page' : null"
              >
                <furar-icon name="qr" [size]="28" />
              </a>
            </li>
          }
        </ng-template>
      </nav>
    }
  `,
})
export class BottomNavComponent {
  private readonly router = inject(Router);
  protected readonly municipality = inject(CurrentMunicipality);
  protected readonly manage = inject(ManageNav);
  protected readonly canManage = computed(() => this.manage.sections().length > 0);

  /** Os destinos da barra: os apartados da xestión (sen os que só son o QR) ou os públicos. */
  private readonly links = computed<BottomLink[]>(() => {
    const slug = this.municipality.slug() ?? '';
    if (this.canManage()) {
      return this.manage.sections().flatMap((section) => {
        const [first] = this.mobileTabs(section);
        const key = SHORT_KEYS[section.key] ?? section.key;
        return first ? [{ key, icon: section.icon, path: first.path, options: SUBTREE, section }] : [];
      });
    }
    return [
      { key: 'site.home', icon: 'home', path: ['/', slug], options: EXACT_WITH_FRAGMENT, section: null },
      {
        key: 'site.facilities',
        icon: 'grid',
        path: ['/', slug, 'instalacions'],
        options: EXACT_WITH_FRAGMENT,
        section: null,
      },
    ];
  });

  protected readonly slots = computed(() => overflow(this.links(), SLOTS));
  /** O botón do QR vai no medio (Reservas · [QR] · Incidencias; dous e dous co xestor). */
  protected readonly qrAt = computed(() => (this.manage.qr() ? Math.ceil(this.slots().visible.length / 2) : -1));

  /** As pestanas dun apartado que hai no móbil: «Validar QR» é o botón central. */
  protected mobileTabs(section: ManageSection): readonly ManageTab[] {
    return section.tabs.filter((tab) => !tab.qr);
  }

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
  protected readonly moreActive = computed(() => {
    const current = this.manage.section();
    return this.slots().more.some((link) =>
      link.section
        ? link.section === current
        : isActive(this.router.createUrlTree([...link.path]), this.router, link.options)(),
    );
  });
}

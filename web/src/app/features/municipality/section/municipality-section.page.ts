// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { TabLinkDirective, TabNavComponent } from '../../../shared/ui/tabs/tab-nav.component';
import { MunicipalitySection, SECTION_TABS } from './section';

/**
 * Sección «Concello» (maqueta 49): sen título á vista (o `h1` é só para lectores de pantalla), unha
 * fila de pestanas, cada unha co seu URL, e a pestana escollida debaixo (`router-outlet`). Xeral,
 * Aparencia, Servizo e Legal son formularios co seu propio «Gardar»: a que ten cambios sen gardar
 * leva un punto e saír dela pregunta (`leaveTabGuard`). «Legal» leva un triángulo se faltan datos
 * que usan os textos legais ou algunha páxina está «Sen páxina». Persoal, Campos personalizados e
 * Rexistro son as súas páxinas de sempre.
 */
@Component({
  selector: 'furar-municipality-section-page',
  imports: [
    IconComponent,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    TabLinkDirective,
    TabNavComponent,
    TranslocoPipe,
  ],
  providers: [MunicipalitySection],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-section' },
  template: `
    <h1 class="sr-only">{{ 'section.title' | transloco }}</h1>
    <furar-tab-nav [label]="'section.title' | transloco">
      @for (tab of tabs; track tab.key) {
        <a
          furarTabLink
          [routerLink]="tab.path"
          routerLinkActive
          ariaCurrentWhenActive="page"
          [attr.data-section-tab]="tab.key"
        >
          {{ 'section.tab.' + tab.key | transloco }}
          @if (section.dirty() === tab.key) {
            <span class="f-tabs__dirty" aria-hidden="true" data-dirty></span>
            <span class="sr-only">{{ 'section.unsaved' | transloco }}</span>
          }
          @if (tab.key === 'legal' && section.legalWarning(); as warning) {
            <furar-icon class="f-tabs__warn" name="warn" data-legal-warning />
            <span class="sr-only">{{ 'section.legalWarning.' + warning | transloco }}</span>
          }
        </a>
      }
    </furar-tab-nav>
    <router-outlet />
  `,
})
export class MunicipalitySectionPage {
  protected readonly section = inject(MunicipalitySection);
  protected readonly tabs = SECTION_TABS;
}

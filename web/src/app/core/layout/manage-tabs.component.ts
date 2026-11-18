// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import type { IsActiveMatchOptions } from '@angular/router';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { TabLinkDirective, TabNavComponent } from '../../shared/ui/tabs/tab-nav.component';
import { ManageNav } from './manage-nav';

/** A pestana da páxina á vista: a súa ruta exacta, con calquera filtro no URL. */
const TAB: IsActiveMatchOptions = {
  paths: 'exact',
  queryParams: 'ignored',
  matrixParams: 'ignored',
  fragment: 'ignored',
};

/**
 * Pestanas do apartado da xestión: debaixo da barra, nas páxinas que son unha
 * pestana dun apartado con dúas ou máis (Reservas · Validar QR). Pestanas de liña
 * (`furar-tab-nav`), cada unha co seu URL; o título da páxina vai só para lectores de pantalla
 * (`ManageNav.underTabs`). No móbil, «Validar QR» non vai na franxa (é o botón central da barra
 * inferior); se só queda unha pestana ou se está nela, sen franxa.
 */
@Component({
  selector: 'furar-manage-tabs',
  imports: [RouterLink, RouterLinkActive, TabLinkDirective, TabNavComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (manage.section(); as section) {
      @if (manage.strip().length) {
        <furar-tab-nav
          class="f-manage-tabs"
          data-manage-tabs
          [attr.data-mobile-hidden]="mobileHidden() ? '' : null"
          [label]="section.key | transloco"
        >
          @for (tab of manage.strip(); track tab.key) {
            <a
              furarTabLink
              [routerLink]="tab.path"
              routerLinkActive
              ariaCurrentWhenActive="page"
              [routerLinkActiveOptions]="options"
              [attr.data-manage-tab]="tab.key"
              [attr.data-qr]="tab.qr ? '' : null"
            >
              {{ tab.key | transloco }}
              @if (manage.count(tab); as n) {
                <span class="f-count" data-count aria-hidden="true">{{ n }}</span>
                <span class="sr-only">, {{ manage.countText(tab) }}</span>
              }
            </a>
          }
        </furar-tab-nav>
      }
    }
  `,
})
export class ManageTabsComponent {
  protected readonly manage = inject(ManageNav);
  protected readonly options = TAB;
  /** No móbil, sen franxa en «Validar QR» (o botón central xa a marca) ou se queda unha pestana. */
  protected readonly mobileHidden = computed(
    () => this.manage.tab()?.qr === true || this.manage.strip().filter((tab) => !tab.qr).length < 2,
  );
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TabDirective, TabsComponent } from '../../../shared/ui/tabs/tabs.component';
import { MyOrganizationsStore } from './my-organizations.store';
import { OrganizationBookingsComponent } from './organization-bookings.component';

/** A pestana das reservas da propia persoa. */
export const ME = 'me';

/**
 * De quen son as reservas en «As miñas reservas» (ADR-0039): se a persoa é responsable
 * dalgunha entidade, pestanas de liña «Eu · ‹Entidade›» enriba; en «Eu», o de sempre (o contido
 * proxectado), e na dunha entidade, as súas reservas (`furar-organization-bookings`). Sen
 * entidades, só o contido, sen pestanas.
 */
@Component({
  selector: 'furar-holder-tabs',
  imports: [OrganizationBookingsComponent, StatusStateComponent, TabDirective, TabsComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (store.items().length) {
      <furar-tabs look="line" data-mine-holders [label]="'booking.seasons.club.tabs' | transloco" [(selected)]="holder">
        <button type="button" furarTab="me" data-mine-holder="me">{{ 'booking.who.me' | transloco }}</button>
        @for (org of store.items(); track org.id) {
          <button type="button" [furarTab]="org.id" [attr.data-mine-holder]="org.id">{{ org.name }}</button>
        }
        @if (organization(); as org) {
          @defer {
            <furar-organization-bookings [slug]="slug()" [organization]="org" />
          } @placeholder {
            <furar-status-state mode="skeleton" shape="card" [rows]="3" [description]="'common.loading' | transloco" />
          }
        }
      </furar-tabs>
    }
    <!-- «Eu»: o de sempre, sen caixa de máis (a reixa da páxina segue igual). -->
    <div class="contents" data-mine-own [hidden]="!!organization()"><ng-content /></div>
  `,
})
export class HolderTabsComponent {
  protected readonly store = inject(MyOrganizationsStore);

  readonly slug = input.required<string>();

  /** `me` ou o id dunha entidade. */
  protected readonly holder = signal<string>(ME);
  protected readonly organization = computed(
    () => this.store.items().find((organization) => organization.id === this.holder()) ?? null,
  );

  constructor() {
    effect(() => {
      const slug = this.slug();
      if (slug) untracked(() => void this.store.load(slug));
    });
  }
}

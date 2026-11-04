// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CardComponent } from '../shared/ui/card/card.component';
import { CarouselComponent, CarouselItemDirective } from '../shared/ui/carousel/carousel.component';
import { DeleteButtonComponent } from '../shared/ui/delete-button/delete-button.component';
import type { UserMenuItem } from '../shared/ui/user-menu/user-menu.component';
import { UserMenuComponent } from '../shared/ui/user-menu/user-menu.component';

/**
 * Sección «Tira, borrar e menú da persoa» do catálogo (só desenvolvemento): `furar-carousel`
 * ordenable (arrastrar ou Alt + frechas) con aviso, `furar-delete-button` redondo e con texto e
 * `furar-user-menu`.
 */
@Component({
  selector: 'furar-catalog-media',
  imports: [
    TranslocoPipe,
    CardComponent,
    CarouselComponent,
    CarouselItemDirective,
    DeleteButtonComponent,
    UserMenuComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-card [heading]="'catalog.media.title' | transloco" id="tira">
      <div class="gap-form grid">
        <furar-carousel
          reorderable
          [label]="'catalog.media.strip' | transloco"
          [hint]="'catalog.media.hint' | transloco"
          [items]="items()"
          [key]="key"
          (reordered)="items.set($event)"
        >
          <ng-template furarCarouselItem let-item let-i="index" let-hint="hintId">
            <button
              type="button"
              class="bg-tint rounded-field aspect-[3/2] w-full font-bold"
              [attr.aria-describedby]="hint"
            >
              {{ 'catalog.media.item' | transloco: { n: item } }}
            </button>
          </ng-template>
        </furar-carousel>
        <div class="gap-item flex flex-wrap items-center">
          <furar-delete-button [label]="'catalog.media.deleteRound' | transloco" />
          <furar-delete-button
            [label]="'catalog.media.deleteLabel' | transloco"
            [text]="'catalog.media.delete' | transloco"
          />
          <furar-user-menu
            [name]="'catalog.media.user' | transloco"
            [roleName]="'catalog.media.role' | transloco"
            [context]="'catalog.media.context' | transloco"
            email="persoa@concello.example"
            [items]="menu"
          />
        </div>
      </div>
    </furar-card>
  `,
})
export class CatalogMediaComponent {
  protected readonly items = signal(['1', '2', '3', '4', '5', '6', '7', '8']);
  protected readonly key = (item: string) => item;
  private readonly transloco = inject(TranslocoService);
  protected readonly menu: UserMenuItem[] = [
    { id: 'account', label: this.transloco.translate('session.account'), icon: 'user', href: '#tira' },
    { id: 'logout', label: this.transloco.translate('session.logout'), icon: 'logout', separatorBefore: true },
  ];
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ActionBarComponent } from '../shared/ui/action-bar/action-bar.component';
import { BilingualFieldComponent } from '../shared/ui/bilingual-field/bilingual-field.component';
import { ButtonDirective } from '../shared/ui/button/button.directive';
import { CardComponent } from '../shared/ui/card/card.component';
import { DialogService } from '../shared/ui/dialog/dialog.service';
import { MediaCardComponent } from '../shared/ui/media-card/media-card.component';
import { TagComponent } from '../shared/ui/tag/tag.component';
import { TileDirective } from '../shared/ui/tile/tile.directive';

/**
 * Sección «Edición e tarxetas» do catálogo (só desenvolvemento): `furar-media-card` (a tarxeta con
 * imaxe da portada e do listado), tarxetas que se pulsan (`[furarTile]`), o texto en dous idiomas
 * (`furar-bilingual-field`), a confirmación escribindo unha frase e a barra fixa
 * (`furar-action-bar`), que se amosa cun botón.
 */
@Component({
  selector: 'furar-catalog-editing',
  imports: [
    ActionBarComponent,
    BilingualFieldComponent,
    ButtonDirective,
    CardComponent,
    MediaCardComponent,
    TagComponent,
    TileDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'catalog.editing.';
    <furar-card [heading]="t + 'title' | transloco" id="edicion">
      <div class="gap-form grid">
        <ul class="gap-item m-0 grid list-none p-0 sm:grid-cols-2">
          <li class="flex">
            <furar-media-card
              src="/icons/icon.svg"
              [heading]="t + 'card' | transloco"
              [place]="t + 'place' | transloco"
              [link]="['/catalogo-ui']"
            >
              <span class="f-tag-list">
                <furar-tag look="soft">{{ 'catalog.sample.tag' | transloco }}</furar-tag>
              </span>
              <furar-tag cardActions tone="success">{{ 'catalog.sample.tag' | transloco }}</furar-tag>
            </furar-media-card>
          </li>
        </ul>
        <ul class="f-tile-grid" [attr.aria-label]="t + 'tiles' | transloco">
          <li>
            <button furarTile type="button">
              <span class="font-bold">{{ t + 'tile' | transloco }}</span>
              <span class="f-tag-list"
                ><furar-tag look="soft">{{ 'catalog.sample.tag' | transloco }}</furar-tag></span
              >
            </button>
          </li>
          <li>
            <button furarTile data-dashed type="button">{{ t + 'add' | transloco }}</button>
          </li>
        </ul>
        <furar-bilingual-field
          fieldId="catalog-bilingual"
          multiline
          [label]="t + 'bilingual' | transloco"
          [gl]="gl"
          [es]="es"
        />
        <div class="gap-unit flex flex-wrap">
          <button type="button" furarButton="danger" data-open-type-confirm (click)="confirm()">
            {{ t + 'confirm' | transloco }}
          </button>
          <button type="button" furarButton data-toggle-bar (click)="bar.set(!bar())">
            {{ t + 'bar' | transloco }}
          </button>
        </div>
      </div>
    </furar-card>
    @if (bar()) {
      <furar-action-bar [label]="t + 'bar' | transloco">
        <p actionBarStatus class="f-action-bar__state">
          <span data-dirty>{{ t + 'unsaved' | transloco }}</span>
        </p>
        <button type="button" furarButton (click)="bar.set(false)">{{ 'ui.confirm.cancel' | transloco }}</button>
        <button type="button" furarButton="primary" (click)="bar.set(false)">
          {{ 'ui.editSection.save' | transloco }}
        </button>
      </furar-action-bar>
    }
  `,
})
export class CatalogEditingComponent {
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  protected readonly gl = new FormControl('', { nonNullable: true });
  protected readonly es = new FormControl('', { nonNullable: true });
  protected readonly bar = signal(false);

  protected confirm(): void {
    const t = (key: string) => this.transloco.translate(`catalog.editing.${key}`);
    void this.dialogs.confirm({
      heading: t('confirmTitle'),
      message: t('confirmText'),
      confirmLabel: t('confirm'),
      tone: 'danger',
      typeToConfirm: t('phrase'),
    });
  }
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { DeleteButtonComponent } from '../../shared/ui/delete-button/delete-button.component';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import type { Incident } from './incidents';
import { IncidentsStore } from './incidents.store';

/**
 * As miniaturas das fotos dun aviso: cada unha é un botón que a amplía nun visor con «Anterior» e
 * «Seguinte»; a xestión pode quitar cada unha. Os bytes pídense coa sesión.
 */
@Component({
  selector: 'furar-incident-photos',
  imports: [DeleteButtonComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="f-incident-photos" data-incident-detail-photos>
      @for (photo of incident().photos; track photo.id; let i = $index) {
        <li class="f-incident-photos__item">
          <button
            type="button"
            class="f-incident-photos__zoom"
            aria-haspopup="dialog"
            [attr.aria-label]="'incident.detail.openPhoto' | transloco: { n: i + 1 }"
            (click)="zoom(i)"
          >
            @if (thumbs()[i]; as src) {
              <img [src]="src" alt="" [width]="photo.width" [height]="photo.height" />
            }
          </button>
          @if (removable()) {
            <furar-delete-button
              class="f-incident-photos__remove"
              data-incident-photo-delete
              [label]="'incident.detail.deletePhoto.label' | transloco: { n: i + 1 }"
              (pressed)="removed.emit(photo.id)"
            />
          }
        </li>
      }
    </ul>
  `,
})
export class IncidentPhotosComponent {
  readonly slug = input.required<string>();
  readonly incident = input.required<Incident>();
  /** A xestión pode quitar cada foto (por exemplo, se sae unha persoa). */
  readonly removable = input(false, { transform: booleanAttribute });
  readonly removed = output<string>();

  private readonly store = inject(IncidentsStore);
  private readonly dialogs = inject(DialogService);
  protected readonly thumbs = signal<readonly (string | null)[]>([]);

  constructor() {
    effect(() => {
      const incident = this.incident();
      const slug = this.slug();
      this.thumbs.set(incident.photos.map(() => null));
      incident.photos.forEach((photo, i) => {
        void this.store
          .photo(slug, incident.id, photo.id, 'thumb')
          .then((src) => this.thumbs.update((all) => all.map((value, j) => (j === i ? src : value))))
          .catch(() => undefined);
      });
    });
  }

  protected async zoom(index: number): Promise<void> {
    const { IncidentPhotoDialogComponent } = await import('./incident-photo-dialog.component');
    this.dialogs.open(IncidentPhotoDialogComponent, {
      data: { slug: this.slug(), incident: this.incident(), index },
    });
  }
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { DialogComponent } from '../../shared/ui/dialog/dialog.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import type { Incident } from './incidents';
import { IncidentsStore } from './incidents.store';

export interface IncidentPhotoData {
  readonly slug: string;
  readonly incident: Incident;
  readonly index: number;
}

/** Visor dunha foto dunha incidencia: «Foto 1 de 2», a grande, «Anterior» e «Seguinte». */
@Component({
  selector: 'furar-incident-photo-dialog',
  imports: [ButtonDirective, DialogComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog
      size="md"
      [heading]="'incident.detail.photoTitle' | transloco: { n: index() + 1, total: photos.length }"
    >
      <div class="f-incident-viewer" data-incident-viewer>
        @if (current(); as photo) {
          @if (src(); as image) {
            <img [src]="image" alt="" [width]="photo.width" [height]="photo.height" />
          }
        }
      </div>
      @if (photos.length > 1) {
        <ng-container dialogActions>
          <button type="button" furarButton [disabled]="index() === 0" (click)="index.set(index() - 1)">
            <furar-icon name="chevron-left" />
            {{ 'incident.detail.previous' | transloco }}
          </button>
          <button type="button" furarButton [disabled]="index() === photos.length - 1" (click)="index.set(index() + 1)">
            {{ 'incident.detail.next' | transloco }}
            <furar-icon name="chevron-right" />
          </button>
        </ng-container>
      }
    </furar-dialog>
  `,
})
export class IncidentPhotoDialogComponent {
  private readonly data = inject<IncidentPhotoData>(DIALOG_DATA);
  private readonly store = inject(IncidentsStore);
  protected readonly photos = this.data.incident.photos;
  protected readonly index = signal(this.data.index);
  protected readonly current = computed(() => this.photos[this.index()]);
  protected readonly src = signal<string | null>(null);

  constructor() {
    effect(() => {
      const photo = this.current();
      this.src.set(null);
      if (!photo) return;
      this.store
        .photo(this.data.slug, this.data.incident.id, photo.id, 'large')
        .then((src) => {
          if (this.current()?.id === photo.id) this.src.set(src);
        })
        .catch(() => undefined);
    });
  }
}

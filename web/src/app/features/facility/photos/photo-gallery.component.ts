// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import type { PhotosDialogResult } from './photos-dialog.component';
import { PhotosDialogComponent } from './photos-dialog.component';
import type { DraftImage, Image } from './photos';
import { GALLERY_SIZE, altIn } from './photos';
import { PhotosDraft } from './photos-draft';

/** Unha imaxe da galería: gardada ou aceptada no modal aínda sen gardar. */
type GalleryImage = Image | DraftImage;

/**
 * Situación de cada foto na grella de escritorio (2fr 1fr 1fr, dúas filas) segundo cantas hai: a
 * portada sempre grande; con menos de cinco, as que hai enchen o oco. Clases literais para Tailwind.
 */
const DESKTOP_TILES: Record<number, readonly string[]> = {
  1: ['sm:col-span-3 sm:row-span-2'],
  2: ['sm:col-span-2 sm:row-span-2', 'sm:row-span-2'],
  3: ['sm:col-span-2 sm:row-span-2', '', ''],
  4: ['sm:row-span-2', '', '', 'sm:col-span-2'],
  5: ['sm:row-span-2', '', '', '', ''],
};

/**
 * Galería da ficha (D1-21c; especificación, bloque 1; maqueta `02-ficha-instalacion`): enriba da
 * cabeceira, a portada grande e catro pequenas; no móbil, unha cada vez con «1 de 5» e botóns
 * para pasar. No modo edición da ficha, «Cambiar fotos» (secundario, opaco sobre a foto) abre o
 * modal de fotos e a galería mostra o aceptado aínda sen gardar; a barra gárdao (`PhotosDraft`).
 * Nunca está baleira: sen imaxes gardadas, a API trae o debuxo suxerido.
 */
@Component({
  selector: 'furar-facility-photos',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './photo-gallery.component.html',
})
export class PhotoGalleryComponent {
  private readonly detail = inject(FacilityDetailStore);
  private readonly language = inject(LanguageService);
  private readonly dialogs = inject(DialogService);
  private readonly injector = inject(Injector);

  private readonly draft = inject(PhotosDraft);
  protected readonly session = inject(EditSession);
  /**
   * As do modo edición aínda sen gardar ou, se non hai, as gardadas; se só está o suxerido e
   * «Datos» o cambiou, o novo.
   */
  protected readonly photos = computed<readonly GalleryImage[]>(() =>
    this.draft.withSuggestion(this.draft.images() ?? this.detail.facility()?.images ?? []),
  );
  /** Foto que se ve no móbil. */
  protected readonly current = signal(0);

  constructor() {
    const leave = this.session.register({
      dirty: this.draft.dirty,
      validate: () => true,
      save: () => this.draft.save(),
      reset: () => this.draft.reset(),
    });
    inject(DestroyRef).onDestroy(leave);
    // Se a galería mingua (quitouse unha foto), o móbil non queda nun oco baleiro.
    effect(() => {
      const last = Math.max(this.photos().length - 1, 0);
      if (this.current() > last) this.current.set(last);
    });
  }

  protected alt(photo: GalleryImage): string {
    return altIn(photo, this.language.lang());
  }

  /**
   * No móbil vese só a actual; en escritorio, as cinco primeiras na súa praza da grella. O resto
   * só se ven no diálogo.
   */
  protected tileClass(index: number): string {
    const shown = Math.min(this.photos().length, GALLERY_SIZE);
    const desktop = index < GALLERY_SIZE ? (DESKTOP_TILES[shown]?.[index] ?? '') : 'sm:hidden';
    return [index === this.current() ? '' : 'max-sm:hidden', desktop].filter(Boolean).join(' ');
  }

  protected step(by: -1 | 1): void {
    this.current.update((index) => Math.min(Math.max(index + by, 0), this.photos().length - 1));
  }

  protected open(): void {
    this.dialogs.open<PhotosDialogResult>(PhotosDialogComponent, { injector: this.injector });
  }
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { asProblem } from '../../../shared/ui/edit-section/section-errors';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TabDirective, TabsComponent } from '../../../shared/ui/tabs/tabs.component';
import type { DraftImage } from './photos';
import { PHOTO_MAX_LABEL, fileProblem, fromDrawing, fromLibrary } from './photos';
import type { DrawingResponse, LibraryPhotoResponse } from './photos.store';
import { PhotosStore } from './photos.store';

/** O que precisa a biblioteca: o concello, o que xa ten a instalación e cantas caben. */
export interface PhotoLibraryData {
  readonly slug: string;
  /** Claves (`PHOTO:id`, `DRAWING:código`) que xa están na instalación. */
  readonly inList: ReadonlySet<string>;
  readonly left: number;
  /** Cada subida feita aquí (pendente): quen abre descártaa se non se garda. */
  readonly uploaded: (photoId: string) => void;
}

/** As escollidas, na orde en que se marcaron; `undefined` se se cancelou. */
export type PhotoLibraryResult = DraftImage[] | undefined;

type Tab = 'municipality' | 'drawings';

interface Pick {
  readonly image: DraftImage;
  readonly caption: string;
  readonly label: string;
}

interface UploadNote {
  readonly id: number;
  readonly text: string;
  readonly error: boolean;
}

let nextNote = 0;

/**
 * Biblioteca de imaxes (especificación, bloque 1, «Fotos»; maqueta aprobada «Engadir fotos»):
 * pestanas «Do concello» (as fotos que xa subiu o concello, para calquera instalación) e «Debuxos
 * de Furar»; subir fotos (quedan na biblioteca do concello cando se garda a instalación), marcar
 * varias e «Engadir N». As que xa están na instalación vense pero non se marcan. Os límites e
 * erros da subida son os do servidor (413, 415, 422) e saen por foto.
 */
@Component({
  selector: 'furar-photo-library-dialog',
  imports: [
    ButtonDirective,
    DialogComponent,
    IconComponent,
    StatusStateComponent,
    TabsComponent,
    TabDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './photo-library-dialog.component.html',
})
export class PhotoLibraryDialogComponent {
  protected readonly ref = inject<DialogRef<PhotoLibraryResult>>(DialogRef);
  protected readonly data = inject<PhotoLibraryData>(DIALOG_DATA);
  private readonly store = inject(PhotosStore);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly maxSize = PHOTO_MAX_LABEL;
  protected readonly tab = signal<Tab>('municipality');
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  private readonly photos = signal<readonly LibraryPhotoResponse[]>([]);
  private readonly uploads = signal<readonly LibraryPhotoResponse[]>([]);
  private readonly drawingList = signal<readonly DrawingResponse[]>([]);
  protected readonly notes = signal<readonly UploadNote[]>([]);
  protected readonly uploading = signal(0);
  protected readonly uploadingName = signal('');
  /** Marcadas, na orde de marcado. */
  protected readonly chosen = signal<readonly DraftImage[]>([]);
  protected readonly chosenKeys = computed(() => new Set(this.chosen().map((image) => image.key)));
  protected readonly tooMany = computed(() => this.chosen().length > this.data.left);

  protected readonly municipalityPicks = computed<Pick[]>(() =>
    [...this.uploads(), ...this.photos()].map((photo) => {
      const image = fromLibrary(photo);
      const lang = this.language.lang();
      const alt = (lang === 'es' ? photo.alt?.es : photo.alt?.gl) ?? '';
      const caption = photo.pending
        ? this.transloco.translate('facility.photos.library.new')
        : photo.usedIn.length
          ? photo.usedIn.join(', ')
          : this.transloco.translate('facility.photos.library.unused');
      return { image, caption, label: [alt || caption, photo.pending ? '' : caption].filter(Boolean).join('. ') };
    }),
  );
  protected readonly drawingPicks = computed<Pick[]>(() =>
    this.drawingList().map((drawing) => {
      const name = this.language.lang() === 'es' ? drawing.name.es : drawing.name.gl;
      return { image: fromDrawing(drawing), caption: name, label: name };
    }),
  );

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.state.set('loading');
    try {
      const [photos, drawings] = await Promise.all([
        this.store.library(this.data.slug),
        this.store.drawings(this.data.slug),
      ]);
      this.photos.set(photos);
      this.drawingList.set(drawings);
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  protected inList(image: DraftImage): boolean {
    return this.data.inList.has(image.key);
  }

  protected toggle(image: DraftImage): void {
    if (this.inList(image)) return;
    this.chosen.update((list) =>
      list.some((i) => i.key === image.key) ? list.filter((i) => i.key !== image.key) : [...list, image],
    );
  }

  protected pickFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    void this.upload(Array.from(input.files ?? []));
    input.value = '';
  }

  protected dragOver(event: DragEvent): void {
    if (event.dataTransfer?.types.includes('Files')) event.preventDefault();
  }

  protected drop(event: DragEvent): void {
    const files = Array.from(event.dataTransfer?.files ?? []);
    if (!files.length) return;
    event.preventDefault();
    void this.upload(files);
  }

  /** Sobe unha a unha (o servidor reencoda dúas á vez como moito); cada nova queda marcada. */
  private async upload(files: readonly File[]): Promise<void> {
    for (const file of files) {
      const local = fileProblem(file);
      if (local) {
        this.note(file.name, this.transloco.translate(`errors.${local}`, { max: this.maxSize }), true);
        continue;
      }
      this.uploading.update((n) => n + 1);
      this.uploadingName.set(file.name);
      try {
        const photo = await this.store.upload(this.data.slug, file);
        this.data.uploaded(photo.id);
        this.uploads.update((list) => [photo, ...list]);
        this.chosen.update((list) => [...list, fromLibrary(photo)]);
        this.note(file.name, this.transloco.translate('facility.photos.library.uploaded', { name: file.name }), false);
      } catch (error) {
        const problem = asProblem(error);
        this.note(file.name, this.transloco.translate(`errors.${problem.code}`, problem.params), true);
      } finally {
        this.uploading.update((n) => n - 1);
        this.uploadingName.set('');
      }
    }
  }

  private note(name: string, message: string, error: boolean): void {
    const text = error ? this.transloco.translate('facility.photos.library.uploadError', { name, message }) : message;
    this.notes.update((list) => [...list, { id: nextNote++, text, error }].slice(-5));
  }

  protected add(): void {
    if (!this.chosen().length || this.tooMany()) return;
    this.ref.close([...this.chosen()]);
  }

  protected cancel(): void {
    this.ref.close();
  }
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
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
  /** Nome curto (texto alternativo, pé ou nome do debuxo): o da bandexa e dos avisos. */
  readonly name: string;
}

interface UploadNote {
  readonly id: number;
  readonly text: string;
  readonly error: boolean;
}

let nextNote = 0;

/**
 * Biblioteca de imaxes (especificación, bloque 1, «Fotos»; «Engadir fotos»):
 * pestanas «Do concello» (as fotos que xa subiu o concello, para calquera instalación) e «Debuxos
 * de Furar»; subir fotos (quedan na biblioteca do concello cando se garda a instalación), marcar
 * varias e «Engadir N». Cada marcada leva o seu número de orde, que é a orde en que se engaden;
 * a bandexa, enriba do pé, mostra as marcadas das dúas pestanas con «✕» para quitar cada unha. As
 * que xa están na instalación vense pero non se marcan. Os límites e erros da subida son os do
 * servidor (413, 415, 422) e saen por foto.
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
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

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
  /** Número de orde (1, 2…) de cada marcada, pola clave. */
  protected readonly order = computed(() => new Map(this.chosen().map((image, i) => [image.key, i + 1])));
  /** Último cambio (marcar, quitar), para o lector de pantalla. */
  protected readonly announcement = signal('');
  protected readonly tooMany = computed(() => this.chosen().length > this.data.left);
  /** Premeuse «Engadir» sen ningunha marcada: o aviso no pé ata marcar unha. */
  protected readonly pickOne = signal(false);

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
      const label = [alt || caption, photo.pending ? '' : caption].filter(Boolean).join('. ');
      return { image, caption, label, name: alt || caption };
    }),
  );
  protected readonly drawingPicks = computed<Pick[]>(() =>
    this.drawingList().map((drawing) => {
      const name = this.language.lang() === 'es' ? drawing.name.es : drawing.name.gl;
      return { image: fromDrawing(drawing), caption: name, label: name, name };
    }),
  );
  /** As marcadas das dúas pestanas, na orde de marcado, co seu nome. */
  protected readonly tray = computed(() => {
    const names = new Map([...this.municipalityPicks(), ...this.drawingPicks()].map((p) => [p.image.key, p.name]));
    return this.chosen().map((image) => ({ image, name: names.get(image.key) ?? '' }));
  });

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

  protected toggle(pick: Pick): void {
    const { image, name } = pick;
    if (this.inList(image)) return;
    if (this.order().has(image.key)) {
      this.chosen.update((list) => list.filter((i) => i.key !== image.key));
      this.announce('removed', { name });
    } else {
      this.chosen.update((list) => [...list, image]);
      this.announce('marked', { name, n: this.chosen().length });
    }
  }

  /** «✕» na bandexa: o foco pasa á seguinte (ou á anterior, se era a última) ou, se non queda ningunha, á cuadrícula. */
  protected remove(key: string, name: string): void {
    const index = this.chosen().findIndex((image) => image.key === key);
    if (index < 0) return;
    this.chosen.update((list) => list.filter((image) => image.key !== key));
    this.announce('removed', { name });
    const left = this.chosen().length;
    this.afterRender((root) => {
      if (left) {
        root.querySelectorAll<HTMLElement>('[data-tray-remove]')[Math.min(index, left - 1)]?.focus();
      } else {
        this.focusGrid(root, key);
      }
    });
  }

  protected clear(): void {
    this.chosen.set([]);
    this.announce('cleared');
    this.afterRender((root) => this.focusGrid(root));
  }

  /** A foto quitada, se se ve nesta pestana; se non, a primeira que se pode marcar. */
  private focusGrid(root: HTMLElement, key?: string): void {
    const picks = [...root.querySelectorAll<HTMLElement>('.f-library__pick')];
    const target =
      picks.find((pick) => key && pick.dataset['key'] === key) ??
      picks.find((pick) => pick.getAttribute('aria-disabled') !== 'true') ??
      root.querySelector<HTMLElement>('[role="tab"][aria-selected="true"]');
    target?.focus();
  }

  private announce(key: string, params: Record<string, unknown> = {}): void {
    this.announcement.set(this.transloco.translate(`facility.photos.library.${key}`, params));
  }

  private afterRender(work: (root: HTMLElement) => void): void {
    afterNextRender(() => work(this.host.nativeElement), { injector: this.injector });
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

  /**
   * «Engadir» non se desactiva por falta de fotos: sen ningunha marcada, o aviso e o foco na
   * primeira que se pode marcar; con máis das que caben, o foco na bandexa para quitar algunha.
   * Só agarda (desactivado) mentres sobe unha foto.
   */
  protected add(): void {
    if (!this.chosen().length) {
      this.pickOne.set(true);
      this.focusGrid(this.host.nativeElement);
      return;
    }
    if (this.tooMany()) {
      this.host.nativeElement.querySelector<HTMLElement>('[data-tray-remove]')?.focus();
      return;
    }
    this.ref.close([...this.chosen()]);
  }

  protected cancel(): void {
    this.ref.close();
  }
}

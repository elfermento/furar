// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import type { Language } from '../../../../core/i18n/languages';
import { CurrentMunicipality } from '../../../../core/municipality/current-municipality';
import { localized } from '../../../../shared/custom-fields/localized';
import { BilingualFieldComponent } from '../../../../shared/ui/bilingual-field/bilingual-field.component';
import { ButtonDirective } from '../../../../shared/ui/button/button.directive';
import { CarouselComponent, CarouselItemDirective } from '../../../../shared/ui/carousel/carousel.component';
import { DeleteButtonComponent } from '../../../../shared/ui/delete-button/delete-button.component';
import { asProblem } from '../../../../shared/ui/edit-section/section-errors';
import { FileDropComponent } from '../../../../shared/ui/file-drop/file-drop.component';
import type { DraftImage } from '../../photos/photos';
import {
  ALT_MAX,
  IMAGE_LIMIT,
  PHOTO_MAX_LABEL,
  PHOTO_TYPES,
  altIn,
  fileProblem,
  fromLibrary,
  reorder,
} from '../../photos/photos';
import type { DrawingResponse } from '../../photos/photos.store';
import { PhotosStore } from '../../photos/photos.store';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

/** Debuxos que se ven sen pedir máis: o suxerido e cinco. */
const DRAWINGS_SHOWN = 6;

interface AltControls {
  readonly gl: FormControl<string>;
  readonly es: FormControl<string>;
}

function altText(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  if (!value) return { 'validation.required': {} };
  return value.length > ALT_MAX ? { maxlength: { requiredLength: ALT_MAX } } : null;
}

/**
 * Paso 4, «Fotos» (opcional; maqueta 53): o principal é subir fotos (zona grande, tamén
 * arrastrando). As subidas quedan nunha tira que se ordena (a primeira é a portada) e cada foto
 * leva a súa descrición en gl e es. Sen fotos, e só entón, aparte, o debuxo que se ve mentres
 * tanto: o suxerido xa vai marcado.
 */
@Component({
  selector: 'furar-wizard-photos-step',
  imports: [
    BilingualFieldComponent,
    ButtonDirective,
    CarouselComponent,
    CarouselItemDirective,
    DeleteButtonComponent,
    FileDropComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './photos-step.component.html',
})
export class PhotosStepComponent implements OnInit {
  protected readonly store = inject(WizardStore);
  private readonly photos = inject(PhotosStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);

  protected readonly accept = PHOTO_TYPES.join(',');
  protected readonly altMax = ALT_MAX;
  protected readonly images = this.store.images;
  protected readonly selected = signal(0);
  protected readonly current = computed(() => this.images()[this.selected()] ?? this.images()[0] ?? null);
  protected readonly altLang = signal<Language>(this.language.lang());
  protected readonly uploading = signal('');
  protected readonly notes = signal<readonly string[]>([]);
  protected readonly announcement = signal('');
  protected readonly drawings = this.store.drawings;
  protected readonly all = signal(false);
  protected readonly tried = computed(() => this.store.tried().has(STEP.photos));
  protected readonly left = computed(() => IMAGE_LIMIT - this.images().length);
  protected readonly key = (image: DraftImage) => image.key;
  protected readonly suggestedCode = computed(() => this.store.facility()?.suggestedImage?.id ?? null);
  private readonly altControls = new Map<string, AltControls>();

  protected readonly help = computed(() => {
    const left = this.left();
    const params = { max: PHOTO_MAX_LABEL, limit: IMAGE_LIMIT, left };
    return this.transloco.translate(
      this.images().length ? 'facility.wizard.photos.helpLeft' : 'facility.wizard.photos.help',
      params,
    );
  });

  /** O suxerido primeiro; despois, por nome no idioma de quen mira. Sempre o escollido. */
  protected readonly shownDrawings = computed(() => {
    const lang = this.language.lang();
    const suggested = this.suggestedCode();
    const chosen = this.store.shownDrawing();
    const sorted = [...this.drawings()].sort((a, b) => {
      if (a.code === suggested) return -1;
      if (b.code === suggested) return 1;
      return localized(a.name, lang).localeCompare(localized(b.name, lang), lang);
    });
    if (this.all()) return sorted;
    const shown = sorted.slice(0, DRAWINGS_SHOWN);
    const extra = sorted.find((drawing) => drawing.code === chosen && !shown.includes(drawing));
    return extra ? [...shown, extra] : shown;
  });

  ngOnInit(): void {
    this.store.loadDrawings();
  }

  protected name(drawing: DrawingResponse): string {
    return localized(drawing.name, this.language.lang());
  }

  protected thumbLabel(index: number): string {
    const key = index === 0 ? 'facility.photos.photoCover' : 'facility.photos.photoPosition';
    const image = this.images()[index];
    const position = this.transloco.translate(key, { current: index + 1, total: this.images().length });
    const alt = image ? altIn(image, this.language.lang()) : '';
    return alt ? `${position}: ${alt}` : position;
  }

  protected altFor(image: DraftImage): AltControls {
    let controls = this.altControls.get(image.key);
    if (!controls) {
      const control = (lang: Language) => {
        const c = new FormControl(image.alt[lang], { nonNullable: true, validators: altText });
        c.valueChanges.subscribe((value) => this.setAlt(image.key, lang, value));
        return c;
      };
      controls = { gl: control('gl'), es: control('es') };
      this.altControls.set(image.key, controls);
    }
    return controls;
  }

  private setAlt(key: string, lang: Language, value: string): void {
    this.store.forms.setPhotos(
      this.images().map((image) => (image.key === key ? { ...image, alt: { ...image.alt, [lang]: value } } : image)),
    );
  }

  protected reordered(keys: string[]): void {
    const selectedKey = this.current()?.key;
    this.store.forms.setPhotos(reorder(this.images(), keys));
    this.selected.set(Math.max(0, keys.indexOf(selectedKey ?? '')));
  }

  protected remove(image: DraftImage): void {
    const index = this.images().indexOf(image);
    this.store.removePhoto(image);
    this.selected.set(Math.max(0, Math.min(index, this.images().length - 1)));
    this.announcement.set(this.transloco.translate('facility.photos.removed'));
  }

  protected async upload(files: readonly File[]): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    const errors: string[] = [];
    for (const file of files) {
      if (this.left() <= 0) break;
      const local = fileProblem(file);
      if (local) {
        errors.push(this.error(file.name, this.transloco.translate(`errors.${local}`, { max: PHOTO_MAX_LABEL })));
        continue;
      }
      this.uploading.set(file.name);
      try {
        const photo = await this.photos.upload(slug, file);
        this.store.addPhoto(fromLibrary(photo));
        this.selected.set(this.images().length - 1);
        this.announcement.set(this.transloco.translate('facility.photos.library.uploaded', { name: file.name }));
      } catch (error) {
        const problem = asProblem(error);
        errors.push(this.error(file.name, this.transloco.translate(`errors.${problem.code}`, problem.params)));
      } finally {
        this.uploading.set('');
      }
    }
    this.notes.set(errors);
  }

  private error(name: string, message: string): string {
    return this.transloco.translate('facility.photos.library.uploadError', { name, message });
  }
}

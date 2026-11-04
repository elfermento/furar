// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { LanguageService } from '../../../core/i18n/language.service';
import type { Language } from '../../../core/i18n/languages';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { UnsavedChangesService } from '../../../core/unsaved-changes/unsaved-changes.service';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CarouselComponent, CarouselItemDirective } from '../../../shared/ui/carousel/carousel.component';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { BilingualFieldComponent } from '../../../shared/ui/bilingual-field/bilingual-field.component';
import { formatInstant } from '../../../shared/ui/calendar/madrid-time';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import type { PhotoLibraryData, PhotoLibraryResult } from './photo-library-dialog.component';
import { PhotoLibraryDialogComponent } from './photo-library-dialog.component';
import type { DraftImage } from './photos';
import {
  ALT_LANGS,
  ALT_MAX,
  IMAGE_LIMIT,
  altIn,
  countChanges,
  fromImage,
  isOnlySuggestion,
  missingAlt,
  reorder,
} from './photos';
import { PhotosDraft } from './photos-draft';
import { RequiredNoteComponent } from '../../../shared/ui/required/required.component';

/** Os dous idiomas do texto alternativo dunha foto. */
interface AltControls {
  readonly gl: FormControl<string>;
  readonly es: FormControl<string>;
}

/** Texto alternativo obrigatorio (sen contar os espazos) e de ata `ALT_MAX` caracteres. */
function altText(control: AbstractControl): ValidationErrors | null {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  if (!value) return { 'validation.required': {} };
  return value.length > ALT_MAX ? { maxlength: { requiredLength: ALT_MAX } } : null;
}

/** `saved` se se aceptou; `undefined` se se pechou sen aceptar. */
export type PhotosDialogResult = 'saved' | undefined;

/**
 * Modal de fotos (especificación, bloque 1, «Fotos»; maqueta aprobada «fotos a pantalla
 * completa»). Arriba, a imaxe escollida en grande e unha tarxeta co seu texto alternativo (GL/ES),
 * cando se subiu e «Eliminar»; debaixo, «N fotos» e a tira (`furar-carousel`) que se ordena
 * arrastrando ou con Alt + frechas, e ao final, fóra da tira, «Engadir» (abre a biblioteca).
 * Ábrese no modo edición da ficha: «Aceptar» deixa a lista na ficha (`PhotosDraft`) e **nada vai
 * á API ata que a barra garda**, tampouco as subidas; se se cancela, as fotos subidas neste modal
 * descártanse. Se se quitan todas, volve o debuxo suxerido.
 */
@Component({
  selector: 'furar-facility-photos-dialog',
  imports: [
    RequiredNoteComponent,
    ButtonDirective,
    CarouselComponent,
    CarouselItemDirective,
    DeleteButtonComponent,
    DialogComponent,
    IconComponent,
    BilingualFieldComponent,
    ProblemAlertComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './photos-dialog.component.html',
})
export class PhotosDialogComponent {
  protected readonly ref = inject<DialogRef<PhotosDialogResult>>(DialogRef);
  private readonly detail = inject(FacilityDetailStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly langs = ALT_LANGS;
  protected readonly altMax = ALT_MAX;
  protected readonly limit = IMAGE_LIMIT;
  protected readonly name = computed(() => this.detail.facility()?.name ?? '');
  private readonly draft = inject(PhotosDraft);
  /** O suxerido de agora: cos deportes aínda sen gardar, se «Datos» os cambiou. */
  private readonly suggested = this.draft.suggested;
  /** O gardado, para contar cambios (só o suxerido gardado conta coma o de agora). */
  private readonly initial = this.draft.withSuggestion((this.detail.facility()?.images ?? []).map(fromImage));
  /** Parte do que xa se aceptou no modo edición (aínda sen gardar) ou do gardado. */
  protected readonly images = signal<readonly DraftImage[]>(
    this.draft.images() ? this.draft.withSuggestion(this.draft.images() ?? []) : this.initial,
  );
  protected readonly selected = signal(0);
  protected readonly current = computed(() => this.images()[this.selected()] ?? null);
  protected readonly altLang = signal<Language>(this.language.lang());
  /** Só o suxerido conta coma «sen imaxes»: cambialo por outras non é quitar unha. */
  protected readonly changes = computed(() =>
    countChanges(this.withoutSuggestion(this.initial), this.withoutSuggestion(this.images())),
  );
  protected readonly problem = signal<ApiProblem | null>(null);
  private readonly altControls = new Map<string, AltControls>();
  protected readonly announcement = signal('');
  protected readonly hintOpen = signal(false);
  protected readonly key = (image: DraftImage) => image.key;
  /** Fotos subidas mentres está aberto e aínda sen gardar: se non se garda, descártanse. */
  private readonly fresh = new Set<string>();

  constructor() {
    const untrack = inject(UnsavedChangesService).track(computed(() => this.changes() > 0));
    inject(DestroyRef).onDestroy(untrack);
    // Ao pechar sen aceptar (Cancelar, ✕ ou Esc), as subidas deste modal non quedan en ningures.
    this.ref.closed.subscribe((result) => {
      if (result !== 'saved') this.draft.discard([...this.fresh]);
    });
  }

  private withoutSuggestion(list: readonly DraftImage[]): readonly DraftImage[] {
    return isOnlySuggestion(list, this.suggested()) ? [] : list;
  }

  protected thumbLabel(index: number): string {
    const key = index === 0 ? 'facility.photos.photoCover' : 'facility.photos.photoPosition';
    return this.transloco.translate(key, { current: index + 1, total: this.images().length });
  }

  protected alt(image: DraftImage): string {
    return altIn(image, this.language.lang());
  }

  protected date(iso: string | undefined): string {
    return formatInstant(iso, false) ?? '';
  }

  protected select(index: number): void {
    this.selected.set(index);
  }

  /** Os dous campos do texto alternativo dunha foto (créanse ao vela por primeira vez). */
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
    this.images.update((list) =>
      list.map((image) => (image.key === key ? { ...image, alt: { ...image.alt, [lang]: value } } : image)),
    );
  }

  protected reordered(keys: string[]): void {
    const selectedKey = this.current()?.key;
    this.images.update((list) => reorder(list, keys));
    this.selected.set(Math.max(0, keys.indexOf(selectedKey ?? '')));
  }

  /** Quita a escollida desta instalación (segue na biblioteca). Sen ningunha, volve a suxerida. */
  protected remove(): void {
    const index = this.selected();
    const list = this.images().filter((_, i) => i !== index);
    const suggested = this.suggested();
    this.images.set(list.length || !suggested ? list : [fromImage(suggested)]);
    this.selected.set(Math.min(index, Math.max(this.images().length - 1, 0)));
    this.announce(list.length ? 'facility.photos.removed' : 'facility.photos.suggested');
    this.afterRender((root) => root.querySelector<HTMLElement>('.f-photos__thumb[aria-pressed="true"]')?.focus());
  }

  protected async openLibrary(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) return;
    const onlySuggestion = isOnlySuggestion(this.images(), this.suggested());
    const data: PhotoLibraryData = {
      slug,
      inList: new Set(this.images().map((image) => image.key)),
      left: IMAGE_LIMIT - (onlySuggestion ? 0 : this.images().length),
      uploaded: (id) => this.fresh.add(id),
    };
    const libraryRef = this.dialogs.open<PhotoLibraryResult, PhotoLibraryData>(PhotoLibraryDialogComponent, {
      data,
      injector: this.injector,
    });
    const added = await firstValueFrom(libraryRef.closed, { defaultValue: undefined });
    if (!added?.length) return;
    const base = onlySuggestion ? [] : this.images();
    this.images.set([...base, ...added]);
    this.selected.set(base.length);
    this.announce(added.length === 1 ? 'facility.photos.addedOne' : 'facility.photos.added', { count: added.length });
  }

  /**
   * «Aceptar»: comproba os textos alternativos e deixa a lista na ficha (`PhotosDraft`); vai á API
   * cando a barra garda.
   */
  protected save(): void {
    this.problem.set(null);
    const missing = missingAlt(this.images());
    if (missing) {
      this.showAltError(missing);
      return;
    }
    if (this.changes() || this.draft.images()) this.draft.set(this.images(), this.fresh);
    this.fresh.clear();
    this.ref.close('saved');
  }

  protected close(): void {
    this.ref.close();
  }

  private showAltError(error: { index: number; lang: Language }): void {
    this.selected.set(error.index);
    this.altLang.set(error.lang);
    const image = this.images()[error.index];
    if (image) this.altFor(image)[error.lang].markAsTouched();
    this.afterRender((root) => root.querySelector<HTMLElement>('#facility-photo-alt')?.focus());
  }

  private announce(key: string, params: Record<string, unknown> = {}): void {
    this.announcement.set(this.transloco.translate(key, params));
  }

  private afterRender(work: (root: HTMLElement) => void): void {
    afterNextRender(() => work(this.host.nativeElement), { injector: this.injector });
  }
}

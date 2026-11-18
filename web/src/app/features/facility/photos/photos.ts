// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Language } from '../../../core/i18n/languages';
import type { Facility } from '../detail/facility-detail.store';
import type { ArrangeImagesRequest, DrawingResponse, LibraryPhotoResponse } from './photos.store';

/** Os mesmos límites ca o servidor (`FacilityImages.MAX`, `Photo.ALT_MAX`, subida de 8 MB). */
export const IMAGE_LIMIT = 12;
export const PHOTO_MAX_BYTES = 8 * 1024 * 1024;
export const PHOTO_MAX_LABEL = '8 MB';
export const PHOTO_TYPES = ['image/jpeg', 'image/png'] as const;
export const ALT_MAX = 250;
export const ALT_LANGS = ['gl', 'es'] as const satisfies readonly Language[];
/** Fotos que se ven á vez na galería de escritorio: unha grande e catro pequenas. */
export const GALLERY_SIZE = 5;

export type Image = Facility['images'][number];
export interface Alt {
  readonly gl: string;
  readonly es: string;
}

/**
 * Unha imaxe no borrador do modal de fotos: foto da biblioteca (`PHOTO`, co texto alternativo
 * editable) ou debuxo de Furar (`DRAWING`, texto fixo). `fresh`: subida neste modal e aínda sen
 * gardar (se se cancela, descártase).
 */
export interface DraftImage {
  readonly key: string;
  readonly kind: 'PHOTO' | 'DRAWING';
  readonly id: string;
  readonly alt: Alt;
  readonly thumb: string;
  readonly large: string;
  readonly width: number;
  readonly height: number;
  readonly uploadedAt?: string;
  readonly fresh?: boolean;
}

export const imageKey = (kind: 'PHOTO' | 'DRAWING', id: string) => `${kind}:${id}`;

export function fromImage(image: Image): DraftImage {
  return {
    key: imageKey(image.kind, image.id),
    kind: image.kind,
    id: image.id,
    alt: { gl: image.alt.gl, es: image.alt.es },
    thumb: image.thumb,
    large: image.large,
    width: image.width,
    height: image.height,
    uploadedAt: image.uploadedAt,
  };
}

export function fromLibrary(photo: LibraryPhotoResponse): DraftImage {
  return {
    key: imageKey('PHOTO', photo.id),
    kind: 'PHOTO',
    id: photo.id,
    alt: { gl: photo.alt?.gl ?? '', es: photo.alt?.es ?? '' },
    thumb: photo.thumb,
    large: photo.large,
    width: photo.width,
    height: photo.height,
    uploadedAt: photo.uploadedAt,
    fresh: photo.pending,
  };
}

export function fromDrawing(drawing: DrawingResponse): DraftImage {
  return {
    key: imageKey('DRAWING', drawing.code),
    kind: 'DRAWING',
    id: drawing.code,
    alt: drawing.alt,
    thumb: drawing.thumb,
    large: drawing.large,
    width: drawing.width,
    height: drawing.height,
  };
}

/** Texto alternativo no idioma de quen mira (por se acaso, o outro). */
export function altIn(image: { readonly alt: Alt }, lang: Language): string {
  return (lang === 'es' ? image.alt.es || image.alt.gl : image.alt.gl || image.alt.es) ?? '';
}

/** Sen imaxes gardadas, a ficha trae só o debuxo suxerido: é o mesmo ca unha lista baleira. */
export function isOnlySuggestion(list: readonly DraftImage[], suggested: Image | undefined): boolean {
  return list.length === 1 && !!suggested && list[0].key === imageKey(suggested.kind, suggested.id);
}

/**
 * Cantos cambios hai fronte ao gardado: cada imaxe engadida ou quitada, cada texto alternativo
 * cambiado e un máis se as que seguen cambiaron de orde. Volver ao de antes deixa 0.
 */
export function countChanges(before: readonly DraftImage[], after: readonly DraftImage[]): number {
  const was = new Map(before.map((image) => [image.key, image]));
  const now = new Map(after.map((image) => [image.key, image]));
  let changes = 0;
  for (const key of now.keys()) if (!was.has(key)) changes++;
  for (const key of was.keys()) if (!now.has(key)) changes++;
  for (const [key, image] of now) {
    const old = was.get(key);
    if (old && image.kind === 'PHOTO' && (old.alt.gl !== image.alt.gl || old.alt.es !== image.alt.es)) changes++;
  }
  const keptBefore = before.filter((image) => now.has(image.key)).map((image) => image.key);
  const keptAfter = after.filter((image) => was.has(image.key)).map((image) => image.key);
  if (keptBefore.some((key, i) => key !== keptAfter[i])) changes++;
  return changes;
}

/** A primeira foto sen texto alternativo nalgún idioma: posición e idioma. */
export function missingAlt(list: readonly DraftImage[]): { index: number; lang: Language } | null {
  for (let index = 0; index < list.length; index++) {
    const image = list[index];
    if (image.kind !== 'PHOTO') continue;
    for (const lang of ALT_LANGS) {
      const text = image.alt[lang].trim();
      if (!text || text.length > ALT_MAX) return { index, lang };
    }
  }
  return null;
}

/** Corpo do `PUT …/images`. Se só queda o suxerido, lista baleira (segue sendo o suxerido). */
export function arrangeBody(list: readonly DraftImage[], suggested: Image | undefined): ArrangeImagesRequest {
  if (isOnlySuggestion(list, suggested)) return { images: [] };
  return {
    images: list.map((image) =>
      image.kind === 'PHOTO'
        ? { photoId: image.id, alt: { gl: image.alt.gl.trim(), es: image.alt.es.trim() } }
        : { drawing: image.id },
    ),
  };
}

/** Move `key` á posición de `to` (Alt + frecha ou arrastrar). */
export function reorder(list: readonly DraftImage[], keys: readonly string[]): DraftImage[] {
  const byKey = new Map(list.map((image) => [image.key, image]));
  return keys.map((key) => byKey.get(key)).filter((image): image is DraftImage => !!image);
}

/** Erro da API dun texto alternativo: «images[2].alt.gl» → posición e idioma. */
export function altField(field: string): { index: number; lang: Language } | null {
  const match = /^images\[(\d+)\]\.alt\.(gl|es)$/.exec(field);
  return match ? { index: Number(match[1]), lang: match[2] as Language } : null;
}

/** Comprobación do ficheiro antes de subilo: o mesmo tipo e tamaño que admite o servidor. */
export function fileProblem(file: File): 'facility.photo-unsupported-type' | 'facility.photo-too-large' | null {
  if (!(PHOTO_TYPES as readonly string[]).includes(file.type)) return 'facility.photo-unsupported-type';
  if (file.size > PHOTO_MAX_BYTES) return 'facility.photo-too-large';
  return null;
}

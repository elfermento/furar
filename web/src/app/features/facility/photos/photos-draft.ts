// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { Facility } from '../detail/facility-detail.store';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import type { DraftImage, Image } from './photos';
import { arrangeBody, fromImage } from './photos';
import { PhotosStore } from './photos.store';

/** Os datos de «Datos» dos que depende o debuxo suxerido (o nome non se edita na ficha). */
export interface SuggestionInput {
  /** Só os do catálogo: os do concello non contan. */
  readonly sports: readonly string[];
  readonly type: Facility['details']['type'] | null;
  readonly covered: boolean | null;
}

const inputKey = ({ sports, type, covered }: SuggestionInput): string =>
  JSON.stringify([[...sports].sort(), type ?? null, covered ?? null]);

/** Os datos gardados da instalación, para saber se o editado cambia o suxerido. */
function savedInput(facility: Facility | null): SuggestionInput | null {
  if (!facility) return null;
  const { sports, type, covered } = facility.details;
  return {
    sports: sports.flatMap((sport) => (sport.code ? [sport.code] : [])),
    type: type ?? null,
    covered: covered ?? null,
  };
}

/**
 * Imaxes da instalación no modo edición da ficha (especificación, bloque 1, «Fotos»): o modal de
 * fotos deixa aquí a lista con «Aceptar» (cos textos alternativos e as subidas) e nada vai á API
 * ata que a barra garda (`PUT …/images` co `If-Match` da última versión). Cancelar descarta as
 * subidas: non queda nada, tampouco na biblioteca. Un por ficha (`providers`).
 *
 * O debuxo suxerido depende dos deportes, do tipo e de se é cuberta: se «Datos» os cambia no
 * mesmo modo edición (`suggestFor`), pídese á API o novo e a lista que só tiña o vello pasa ao novo
 * (`withSuggestion`), sen agardar a gardar.
 */
@Injectable()
export class PhotosDraft {
  private readonly detail = inject(FacilityDetailStore);
  private readonly store = inject(PhotosStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly staged = signal<readonly DraftImage[] | null>(null);
  /** Fotos subidas no modo edición e aínda sen gardar. */
  private fresh = new Set<string>();

  readonly images = this.staged.asReadonly();
  readonly dirty = computed(() => this.staged() !== null);

  /** Datos de «Datos» aínda sen gardar que cambian o suxerido (`null`: os gardados). */
  private readonly pending = signal<SuggestionInput | null>(null);
  private readonly fetched = signal<{ readonly key: string; readonly image: Image } | null>(null);
  /** O suxerido gardado (o que trae a ficha). */
  private readonly savedSuggestion = computed(() => this.detail.facility()?.suggestedImage);
  /** O suxerido de agora: o dos datos sen gardar, en canto chega; se non, o gardado. */
  readonly suggested = computed<Image | undefined>(() => {
    const pending = this.pending();
    const fetched = this.fetched();
    return pending && fetched?.key === inputKey(pending) ? fetched.image : this.savedSuggestion();
  });

  /**
   * «Datos» cambiou deportes, tipo ou cuberta (`null`: saíu da edición). Se difire do gardado,
   * pide o suxerido para eses datos; se volve ao gardado, queda o da ficha.
   */
  suggestFor(input: SuggestionInput | null): void {
    const saved = savedInput(this.detail.facility());
    const next = input && saved && inputKey(input) !== inputKey(saved) ? input : null;
    const current = this.pending();
    if ((next && current && inputKey(next) === inputKey(current)) || (!next && !current)) return;
    this.pending.set(next);
    const slug = this.municipality.slug();
    if (!next || !slug) return;
    const key = inputKey(next);
    if (this.fetched()?.key === key) return;
    this.store
      .suggestion({
        slug,
        name: this.detail.facility()?.name,
        sport: [...next.sports],
        type: next.type ?? undefined,
        covered: next.covered ?? undefined,
      })
      .then((image) => this.fetched.set({ key, image }))
      // Sen resposta, segue o gardado: é só unha suxestión.
      .catch(() => undefined);
  }

  /**
   * A lista que só ten o suxerido gardado pasa ao de agora (sen imaxes gardadas, a ficha trae o
   * suxerido); calquera outra lista queda como está.
   */
  withSuggestion(list: readonly DraftImage[]): readonly DraftImage[];
  withSuggestion(list: readonly (Image | DraftImage)[]): readonly (Image | DraftImage)[];
  withSuggestion(list: readonly (Image | DraftImage)[]): readonly (Image | DraftImage)[] {
    const saved = this.savedSuggestion();
    const now = this.suggested();
    if (!saved || !now || now.id === saved.id) return list;
    const only = list.length === 1 && list[0].kind === saved.kind && list[0].id === saved.id;
    return only ? [fromImage(now)] : list;
  }

  /** «Aceptar» no modal: a lista que se verá e as subidas que trae. */
  set(images: readonly DraftImage[], uploaded: Iterable<string>): void {
    for (const id of uploaded) this.fresh.add(id);
    this.staged.set(images);
  }

  /** Garda a lista; as subidas que xa non están nela descártanse. */
  async save(): Promise<void> {
    const images = this.staged();
    if (!images) return;
    // Se só queda o suxerido (o vello ou o de agora), lista baleira: a API pon o que toque.
    const body = arrangeBody(this.withSuggestion(images), this.suggested());
    await this.detail.saveFacility((target) => this.store.arrange(target, body));
    const kept = new Set(body.images.flatMap((image) => (image.photoId ? [image.photoId] : [])));
    this.discard([...this.fresh].filter((id) => !kept.has(id)));
    this.fresh.clear();
    this.staged.set(null);
  }

  /** Cancelar: fóra a lista e as subidas. */
  reset(): void {
    this.discard([...this.fresh]);
    this.fresh.clear();
    this.staged.set(null);
  }

  /** Descarta subidas (sen agardar: se falla, a API borra a pendente ao día). */
  discard(ids: readonly string[]): void {
    const slug = this.municipality.slug();
    if (!slug) return;
    for (const id of ids) void this.store.discard(slug, id).catch(() => undefined);
  }
}

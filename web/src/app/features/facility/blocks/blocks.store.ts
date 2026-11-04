// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { createFacilityBlock } from '../../../api/fn/facilities/create-facility-block';
import { deleteFacilityBlock } from '../../../api/fn/facilities/delete-facility-block';
import { listFacilityBlocks } from '../../../api/fn/facilities/list-facility-blocks';
import { updateFacilityBlock } from '../../../api/fn/facilities/update-facility-block';
import type { BlockRequest } from '../../../api/models/block-request';
import type { BlockResponse } from '../../../api/models/block-response';
import { ApiProblem } from '../../../core/errors/api-problem';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import type { BookingDecision } from '../affected-bookings/affected-bookings.store';
import type { LoadState } from '../detail/facility-detail.store';
import { VersionedElements } from '../detail/versioned-elements';

export type { BlockResponse };

/** Bloqueo dun día: un tramo de reloxo de parede (todo o día = 0:00–24:00, P13) e o motivo. */
export interface Block {
  readonly id: string;
  readonly date: IsoDate;
  readonly startMinute: number;
  readonly endMinute: number;
  readonly reason: string;
  readonly version: number;
}
export type BlockBody = Omit<BlockRequest, 'decisions'>;

export function toBlock(response: BlockResponse): Block {
  return {
    id: response.id ?? '',
    date: response.date ?? '',
    startMinute: response.startMinute ?? 0,
    endMinute: response.endMinute ?? 0,
    reason: response.reason ?? '',
    version: response.version ?? 0,
  };
}

/**
 * Bloqueos dunha instalación (D1-24): a lista viva da API, cada bloqueo coa súa versión
 * (`VersionedElements`, contrato de estado da ficha). Un por sección (`providers`). É o único sitio
 * da sección que fai HTTP, sempre co cliente xerado. Borrar non leva decisións: quitar un bloqueo
 * non afecta a ningunha reserva (plan, §7).
 */
@Injectable()
export class BlocksStore {
  private readonly api = inject(Api);
  private readonly elements = new VersionedElements<Block>();
  private readonly loadState = signal<LoadState>('loading');
  private where: { slug: string; facilityId: string } | null = null;

  /** Por data e hora de inicio (os novos colócanse no seu sitio). */
  readonly blocks = computed(() =>
    [...this.elements.list()].sort((a, b) => a.date.localeCompare(b.date) || a.startMinute - b.startMinute),
  );
  readonly empty = this.elements.empty;
  readonly state = this.loadState.asReadonly();

  async load(slug: string, facilityId: string): Promise<void> {
    this.where = { slug, facilityId };
    this.loadState.set('loading');
    try {
      await this.reload();
      this.loadState.set('ready');
    } catch {
      this.loadState.set('error');
    }
  }

  /** Volve ler a lista (tras un 412): os formularios abertos conservan o escrito. */
  async reload(): Promise<void> {
    const { slug, facilityId } = this.target();
    this.elements.set((await this.api.invoke(listFacilityBlocks, { slug, facilityId })).map(toBlock));
  }

  get(id: string): Block | undefined {
    return this.elements.get(id);
  }

  create(body: BlockBody, decisions?: BookingDecision[]): Promise<Block> {
    const { slug, facilityId } = this.target();
    return this.elements.create(async () =>
      toBlock(await this.api.invoke(createFacilityBlock, { slug, facilityId, body: { ...body, decisions } })),
    );
  }

  update(id: string, body: BlockBody, decisions?: BookingDecision[]): Promise<Block> {
    const { slug, facilityId } = this.target();
    return this.elements.save(id, async (ifMatch) =>
      toBlock(
        await this.api.invoke(updateFacilityBlock, {
          slug,
          facilityId,
          blockId: id,
          'If-Match': ifMatch,
          body: { ...body, decisions },
        }),
      ),
    );
  }

  remove(id: string): Promise<void> {
    const { slug, facilityId } = this.target();
    return this.elements.remove(id, (ifMatch) =>
      this.api.invoke(deleteFacilityBlock, { slug, facilityId, blockId: id, 'If-Match': ifMatch }),
    );
  }

  private target(): { slug: string; facilityId: string } {
    if (!this.where) throw new ApiProblem(0, 'common.unexpected', {}, [], undefined, '');
    return this.where;
  }
}

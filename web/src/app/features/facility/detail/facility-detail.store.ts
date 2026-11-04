// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { deleteFacility } from '../../../api/fn/facilities/delete-facility';
import { changeFacilityStatus } from '../../../api/fn/facilities/change-facility-status';
import { getFacility } from '../../../api/fn/facilities/get-facility';
import type { FacilityResponse } from '../../../api/models/facility-response';
import { ApiProblem } from '../../../core/errors/api-problem';
import type { BookingDecision } from '../affected-bookings/affected-bookings.store';

export type { FacilityResponse };
export type Facility = FacilityResponse;
export type FacilityStatus = Facility['status'];
export type MissingItem = Facility['missing'][number];

/** Valor de `If-Match` para unha versión (o mesmo formato ca o `ETag` da API). */
export const ifMatch = (version: number): string => `"${version}"`;

/** 412: a versión que mandamos xa non é a última. */
export const isStale = (error: unknown): error is ApiProblem => error instanceof ApiProblem && error.status === 412;

/** O que precisa unha sección para gardar contra a instalación: onde e con que versión. */
export interface FacilityTarget {
  readonly slug: string;
  readonly id: string;
  /** `If-Match` da última versión coñecida no momento de enviar (non no de pulsar «Gardar»). */
  readonly ifMatch: string;
}

export type LoadState = 'loading' | 'ready' | 'error';

/**
 * Contrato de estado da ficha (D1-21a; plan do dominio 1, §1.2.7 e §1.2.17). Un por páxina
 * (`providers` da ficha): todas as seccións len e gardan a mesma instalación.
 *
 * - A instalación e a súa `version` viven nun signal. Cada resposta trae o recurso enteiro: ao
 *   gardar substitúese (nunca se mestura a man).
 * - Os gardados que comparten a versión da instalación (nome, Datos, Descrición, enderezo,
 *   servizos, fotos, estado) van **en cola**: un detrás doutro, e cada un manda `If-Match` coa
 *   versión que deixou o anterior. Así dúas seccións abertas á vez non se pisan a versión.
 * - Un 412 non toca a instalación nin os formularios: marca `stale` para ofrecer «Recargar» e
 *   o erro volve á sección, que conserva o escrito (`furar-edit-section`).
 * - Cada sección enche o seu formulario coa instalación só mentres non estea sucio
 *   (`syncWhenPristine`). Usos, tempadas, excepcións e bloqueos levan a súa versión por elemento
 *   (`VersionedElements`), fóra desta cola.
 */
@Injectable()
export class FacilityDetailStore {
  private readonly api = inject(Api);
  private readonly current = signal<Facility | null>(null);
  private readonly loadState = signal<LoadState>('loading');
  private readonly loadError = signal<ApiProblem | null>(null);
  private readonly staleVersion = signal(false);
  private where: { slug: string; id: string } | null = null;
  /** Fin da cola de gardados da instalación; nunca rexeita. */
  private tail: Promise<unknown> = Promise.resolve();

  readonly facility = this.current.asReadonly();
  readonly version = computed(() => this.current()?.version ?? null);
  readonly state = this.loadState.asReadonly();
  readonly problem = this.loadError.asReadonly();
  /** Outra persoa gardou antes: hai que recargar para gardar sen 412. */
  readonly stale = this.staleVersion.asReadonly();
  /** O que lle falta para saír de borrador (calculado no servidor). */
  readonly missing = computed<readonly MissingItem[]>(() => this.current()?.missing ?? []);

  /** Primeira carga da ficha. */
  async load(slug: string, id: string): Promise<void> {
    this.where = { slug, id };
    this.current.set(null);
    this.loadState.set('loading');
    this.loadError.set(null);
    try {
      this.current.set(await this.api.invoke(getFacility, { slug, id }));
      this.loadState.set('ready');
    } catch (error) {
      this.loadError.set(error instanceof ApiProblem ? error : null);
      this.loadState.set('error');
    }
  }

  /** Instalación acabada de crear (asistente): xa a temos enteira, sen volver lela. */
  adopt(slug: string, facility: Facility): void {
    this.where = { slug, id: facility.id };
    this.current.set(facility);
    this.loadState.set('ready');
    this.loadError.set(null);
  }

  /**
   * Volve ler a instalación (tras un 412 ou tras cambiar un elemento que move `missing`). Vai na
   * cola para non cruzarse cun gardado. As seccións sucias conservan o escrito: o seu seguinte
   * «Gardar» vai xa coa versión nova.
   */
  reload(): Promise<Facility> {
    return this.enqueue(async () => {
      const { slug, id } = this.target();
      const fresh = await this.api.invoke(getFacility, { slug, id });
      this.current.set(fresh);
      this.staleVersion.set(false);
      return fresh;
    });
  }

  /**
   * Garda unha sección da instalación. `send` fai a petición (desde o `*.store.ts` da sección)
   * co `If-Match` que recibe e devolve a instalación enteira. Resolve coa instalación gardada;
   * se falla, rexeita co mesmo erro e a instalación non cambia.
   */
  saveFacility(send: (target: FacilityTarget) => Promise<Facility>): Promise<Facility> {
    return this.enqueue(async () => {
      const facility = this.current();
      if (!facility) throw new Error('A ficha aínda non cargou');
      try {
        const saved = await send({ ...this.target(), ifMatch: ifMatch(facility.version) });
        this.accept(saved);
        return saved;
      } catch (error) {
        if (isStale(error)) this.staleVersion.set(true);
        throw error;
      }
    });
  }

  /** Cambio de estado (`decisions` só no segundo paso do modal de reservas afectadas). */
  changeStatus(status: FacilityStatus, decisions?: BookingDecision[]): Promise<Facility> {
    return this.saveFacility(({ slug, id, ifMatch: version }) =>
      this.api.invoke(changeFacilityStatus, { slug, id, 'If-Match': version, body: { status, decisions } }),
    );
  }

  /** Elimina a instalación co `If-Match` da última versión (só se nunca tivo reservas). */
  delete(): Promise<void> {
    return this.enqueue(async () => {
      const facility = this.current();
      if (!facility) throw new Error('A ficha aínda non cargou');
      const { slug, id } = this.target();
      try {
        await this.api.invoke(deleteFacility, { slug, id, 'If-Match': ifMatch(facility.version) });
      } catch (error) {
        if (isStale(error)) this.staleVersion.set(true);
        throw error;
      }
    });
  }

  /** Só se acepta unha resposta igual ou máis nova ca a que temos. */
  private accept(saved: Facility): void {
    const facility = this.current();
    if (!facility || saved.version >= facility.version) this.current.set(saved);
  }

  private target(): { slug: string; id: string } {
    if (!this.where) throw new Error('A ficha aínda non cargou');
    return this.where;
  }

  private enqueue<T>(work: () => Promise<T>): Promise<T> {
    const result = this.tail.then(work, work);
    this.tail = result.catch(() => undefined);
    return result;
  }
}

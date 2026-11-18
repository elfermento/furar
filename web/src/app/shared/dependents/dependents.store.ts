// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../api/api';
import { createMyDependent } from '../../api/fn/people/create-my-dependent';
import { deleteMyDependent } from '../../api/fn/people/delete-my-dependent';
import { listMyDependents } from '../../api/fn/people/list-my-dependents';
import { updateMyDependent } from '../../api/fn/people/update-my-dependent';
import type { DependentResponse } from '../../api/models/dependent-response';
import type { SaveDependentRequest } from '../../api/models/save-dependent-request';

export type { DependentResponse, SaveDependentRequest };

/** Fichas activas por conta, como moito (o mesmo límite ca na API). */
export const MAX_DEPENDENTS = 10;

/** O ano de nacemento vai do actual menos este número ao actual. */
export const MAX_AGE = 120;

/**
 * As persoas a cargo da conta da sesión (`/api/v1/me/dependents`; ADR-0031): son da conta, non dun
 * concello, así que hai unha soa lista para toda a web. Úsana o menú da conta, «Para quen» ao
 * reservar e os seus diálogos. A lista vai sempre por nome, coma na API.
 */
@Injectable({ providedIn: 'root' })
export class DependentsStore {
  private readonly api = inject(Api);
  private loading: Promise<void> | null = null;

  readonly items = signal<readonly DependentResponse[]>([]);
  readonly state = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');

  /** Pide a lista unha vez; `force` vólvea pedir. */
  load(force = false): Promise<void> {
    if (this.loading && !force) return this.loading;
    this.state.set('loading');
    this.loading = this.api.invoke(listMyDependents).then(
      (items) => {
        this.items.set(items);
        this.state.set('ready');
      },
      () => {
        this.state.set('error');
        this.loading = null;
      },
    );
    return this.loading;
  }

  /** Esquece a lista (ao pechar sesión). */
  clear(): void {
    this.loading = null;
    this.items.set([]);
    this.state.set('idle');
  }

  async create(body: SaveDependentRequest): Promise<DependentResponse> {
    const created = await this.api.invoke(createMyDependent, { body });
    this.items.update((items) => sorted([...items, created]));
    return created;
  }

  async update(current: DependentResponse, body: SaveDependentRequest): Promise<DependentResponse> {
    const updated = await this.api.invoke(updateMyDependent, {
      id: current.id,
      'If-Match': `"${current.version}"`,
      body,
    });
    this.items.update((items) => sorted(items.map((item) => (item.id === updated.id ? updated : item))));
    return updated;
  }

  async remove(id: string): Promise<void> {
    await this.api.invoke(deleteMyDependent, { id });
    this.items.update((items) => items.filter((item) => item.id !== id));
  }
}

function sorted(items: readonly DependentResponse[]): DependentResponse[] {
  return [...items].sort((a, b) => a.name.localeCompare(b.name) || a.birthYear - b.birthYear);
}

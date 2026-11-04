// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { exportBlockedActivity } from '../../../api/fn/activity/export-blocked-activity';
import { listActivity } from '../../../api/fn/activity/list-activity';
import type { ListActivity$Params } from '../../../api/fn/activity/list-activity';
import { listPlatformActivity } from '../../../api/fn/activity/list-platform-activity';
import { listFacilities } from '../../../api/fn/facilities/list-facilities';
import { getPrivacyNotice } from '../../../api/fn/privacy/get-privacy-notice';
import { listStaff } from '../../../api/fn/staff/list-staff';
import type { ActivityEventResponse } from '../../../api/models/activity-event-response';
import type { ActivityPageResponse } from '../../../api/models/activity-page-response';
import type { BlockedActivityExportResponse } from '../../../api/models/blocked-activity-export-response';
import type { ExportBlockedActivityRequest } from '../../../api/models/export-blocked-activity-request';
import type { StaffMemberResponse } from '../../../api/models/staff-member-response';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';

export type {
  ActivityEventResponse,
  BlockedActivityExportResponse,
  ExportBlockedActivityRequest,
  ListActivity$Params,
  StaffMemberResponse,
};

/** Os prazos do rexistro do despregue (ISO 8601): canto se ve e canto se garda. */
export interface ActivityRetention {
  readonly visible: string;
  readonly retention: string;
}

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/** Que pedir: o rexistro do concello `slug` cos seus filtros, ou o da plataforma (só o período). */
export type ActivityRequest =
  | { readonly scope: 'municipality'; readonly params: ListActivity$Params }
  | { readonly scope: 'platform'; readonly params: Pick<ListActivity$Params, 'from' | 'to'> };

/** Instalacións que se len de unha vez para poñerlles nome (o máximo dunha páxina da API). */
const FACILITY_PAGE = 100;

/**
 * Rexistro de actividade (D4-16): a primeira páxina de cada consulta e «Cargar máis» co
 * `nextCursor` (páxinas de 50, do máis novo ao máis vello). A última consulta manda: unha resposta
 * vella non pisa a nova. Na vista do concello, tamén as opcións do filtro de persoa (o persoal,
 * tamén o desactivado) e os nomes das instalacións; se fallan, o rexistro vese igual, sen eles. Os
 * prazos do rexistro (`activityVisible`, `activityRetention`) veñen da API de privacidade: din ata
 * onde se ve e que tramo se pode exportar bloqueado.
 */
@Injectable()
export class ActivityStore {
  private readonly api = inject(Api);
  private request = 0;
  private current: ActivityRequest | null = null;

  readonly items = signal<readonly ActivityEventResponse[]>([]);
  readonly state = signal<LoadState>('idle');
  readonly next = signal<string | null>(null);
  readonly loadingMore = signal(false);
  /** O erro da última consulta (un 400 `audit.range-invalid` trae o seu texto). */
  readonly problem = signal<ApiProblem | null>(null);
  readonly members = signal<readonly StaffMemberResponse[]>([]);
  readonly facilities = signal<ReadonlyMap<string, string>>(new Map());
  /** Os prazos; `null` mentres non chegan ou se fallou (a páxina usa os de por defecto). */
  readonly retention = signal<ActivityRetention | null>(null);

  async load(request: ActivityRequest): Promise<void> {
    const id = ++this.request;
    this.current = request;
    this.state.set('loading');
    this.problem.set(null);
    this.loadingMore.set(false);
    try {
      const page = await this.page(request, undefined);
      if (id !== this.request) return;
      this.items.set(page.items);
      this.next.set(page.nextCursor ?? null);
      this.state.set('ready');
    } catch (error) {
      if (id !== this.request) return;
      this.items.set([]);
      this.next.set(null);
      this.problem.set(asProblem(error));
      this.state.set('error');
    }
  }

  /** A páxina seguinte, ao final da lista. Devolve cantas filas chegaron (0 se fallou). */
  async loadMore(): Promise<number> {
    const cursor = this.next();
    const request = this.current;
    if (!cursor || !request || this.loadingMore()) return 0;
    const id = this.request;
    this.loadingMore.set(true);
    try {
      const page = await this.page(request, cursor);
      if (id !== this.request) return 0;
      this.items.update((items) => [...items, ...page.items]);
      this.next.set(page.nextCursor ?? null);
      return page.items.length;
    } catch (error) {
      if (id === this.request) this.problem.set(asProblem(error));
      return 0;
    } finally {
      if (id === this.request) this.loadingMore.set(false);
    }
  }

  /** O persoal (para o filtro de persoa) e os nomes das instalacións do concello. */
  async loadOptions(slug: string): Promise<void> {
    const [members, facilities] = await Promise.allSettled([
      this.api.invoke(listStaff, { slug }),
      this.api.invoke(listFacilities, { slug, page: 0, size: FACILITY_PAGE }),
    ]);
    if (members.status === 'fulfilled') this.members.set(members.value);
    if (facilities.status === 'fulfilled') {
      this.facilities.set(new Map(facilities.value.items.map((facility) => [facility.id, facility.name])));
    }
  }

  /** Os prazos do rexistro do despregue. */
  async loadRetention(): Promise<void> {
    try {
      const notice = await this.api.invoke(getPrivacyNotice);
      this.retention.set({ visible: notice.activityVisible, retention: notice.activityRetention });
    } catch {
      // Sen eles, a nota e o diálogo usan os de por defecto (P1Y e P3Y).
    }
  }

  /** Exporta os bloqueados dun período co motivo e a referencia; rexeita co erro da API. */
  exportBlocked(slug: string, body: ExportBlockedActivityRequest): Promise<BlockedActivityExportResponse> {
    return this.api.invoke(exportBlockedActivity, { slug, body });
  }

  private page(request: ActivityRequest, cursor: string | undefined): Promise<ActivityPageResponse> {
    const more = cursor ? { cursor } : {};
    return request.scope === 'platform'
      ? this.api.invoke(listPlatformActivity, { ...request.params, ...more })
      : this.api.invoke(listActivity, { ...request.params, ...more });
  }
}

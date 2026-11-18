// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { acceptOpenMatchRequest } from '../../api/fn/bookings/accept-open-match-request';
import { changeMyOpenMatchStatus } from '../../api/fn/bookings/change-my-open-match-status';
import { getMyOpenMatch } from '../../api/fn/bookings/get-my-open-match';
import { getPublicOpenMatch } from '../../api/fn/bookings/get-public-open-match';
import { hideOpenMatch } from '../../api/fn/bookings/hide-open-match';
import { joinOpenMatch } from '../../api/fn/bookings/join-open-match';
import { leaveOpenMatch } from '../../api/fn/bookings/leave-open-match';
import { listMyOpenMatches } from '../../api/fn/bookings/list-my-open-matches';
import { listPublicOpenMatches } from '../../api/fn/bookings/list-public-open-matches';
import { rejectOpenMatchRequest } from '../../api/fn/bookings/reject-open-match-request';
import { saveMyOpenMatch } from '../../api/fn/bookings/save-my-open-match';
import { listCatalogOpenMatches } from '../../api/fn/catalog/list-catalog-open-matches';
import { getPublicFacility } from '../../api/fn/facilities/get-public-facility';
import type { BookingOpenMatchResponse } from '../../api/models/booking-open-match-response';
import type { CatalogOpenMatchesResponse } from '../../api/models/catalog-open-matches-response';
import type { MyOpenMatchResponse } from '../../api/models/my-open-match-response';
import type { OpenMatchCardResponse } from '../../api/models/open-match-card-response';
import type { OpenMatchPolicyResponse } from '../../api/models/open-match-policy-response';
import type { OpenMatchRequest } from '../../api/models/open-match-request';
import type { OpenMatchResponse } from '../../api/models/open-match-response';
import type { MapPoint } from '../ui/map/map-point';
import { pointOf } from '../ui/map/map-point';

export type {
  BookingOpenMatchResponse,
  CatalogOpenMatchesResponse,
  MyOpenMatchResponse,
  OpenMatchCardResponse,
  OpenMatchPolicyResponse,
  OpenMatchRequest,
  OpenMatchResponse,
};

/** Partidas por lista (portada, ficha): a API dá como moito 50. */
const LIST_SIZE = 20;

/**
 * A única porta ao cliente xerado de «Buscar xente para xogar» (ADR-0040): as listas públicas
 * (portada do concello, ficha da instalación e portada común), a páxina da partida, as da persoa,
 * abrir, editar e deixar de buscar desde a súa reserva, unirse, saír, aceptar e rexeitar, e ocultar
 * desde a xestión. Compartida: a usan a portada, a ficha, a portada común e as reservas, que son
 * features distintas.
 */
@Injectable({ providedIn: 'root' })
export class OpenMatches {
  private readonly api = inject(Api);

  /** As que buscan xente nun concello (ou nunha instalación), da que empeza antes. */
  async list(slug: string, facilityId?: string): Promise<OpenMatchCardResponse[]> {
    const page = await this.api.invoke(listPublicOpenMatches, {
      slug,
      ...(facilityId ? { facilityId } : {}),
      page: 0,
      size: LIST_SIZE,
    });
    return page.items;
  }

  /** As de todos os concellos, para a portada común (só os concellos que teñen algunha). */
  catalog(): Promise<CatalogOpenMatchesResponse> {
    return this.api.invoke(listCatalogOpenMatches, { page: 0, size: LIST_SIZE });
  }

  /** Onde está a instalación da partida, para «Como chegar» (o punto da súa ficha pública). */
  async facilityPoint(slug: string, facilityId: string): Promise<MapPoint | null> {
    return pointOf((await this.api.invoke(getPublicFacility, { slug, id: facilityId })).address);
  }

  get(slug: string, id: string): Promise<OpenMatchResponse> {
    return this.api.invoke(getPublicOpenMatch, { slug, id });
  }

  /** A partida vista pola persoa con sesión: o seu papel, as persoas e (quen organiza) as solicitudes. */
  mine(slug: string, id: string): Promise<MyOpenMatchResponse> {
    return this.api.invoke(getMyOpenMatch, { slug, id });
  }

  /** As que organiza ou nas que está ou pediu entrar, aínda sen rematar. */
  listMine(slug: string): Promise<MyOpenMatchResponse[]> {
    return this.api.invoke(listMyOpenMatches, { slug });
  }

  /** Abre (sen `version`) ou edita (coa versión que se viu, `If-Match`) a partida dunha reserva. */
  save(slug: string, bookingId: string, body: OpenMatchRequest, version?: number): Promise<MyOpenMatchResponse> {
    return this.api.invoke(saveMyOpenMatch, {
      slug,
      bookingId,
      ...(version === undefined ? {} : { 'If-Match': `"${version}"` }),
      body,
    });
  }

  /** «Deixar de buscar» (`CLOSED`). */
  stop(slug: string, bookingId: string): Promise<MyOpenMatchResponse> {
    return this.api.invoke(changeMyOpenMatchStatus, { slug, bookingId, body: { status: 'CLOSED' } });
  }

  join(slug: string, id: string): Promise<MyOpenMatchResponse> {
    return this.api.invoke(joinOpenMatch, { slug, id });
  }

  /** Saír ou retirar a solicitude. */
  leave(slug: string, id: string): Promise<void> {
    return this.api.invoke(leaveOpenMatch, { slug, id });
  }

  accept(slug: string, id: string, playerId: string): Promise<MyOpenMatchResponse> {
    return this.api.invoke(acceptOpenMatchRequest, { slug, id, playerId });
  }

  reject(slug: string, id: string, playerId: string): Promise<MyOpenMatchResponse> {
    return this.api.invoke(rejectOpenMatchRequest, { slug, id, playerId });
  }

  /** O xestor oculta ou volve mostrar a partida dunha reserva. */
  hide(slug: string, bookingId: string, hidden: boolean): Promise<BookingOpenMatchResponse> {
    return this.api.invoke(hideOpenMatch, { slug, bookingId, body: { hidden } });
  }
}

/** O resumo da partida que leva a reserva, a partir da resposta da persoa. */
export function bookingOpenMatch(match: MyOpenMatchResponse): BookingOpenMatchResponse {
  return {
    id: match.id,
    status: match.status,
    hidden: match.hidden,
    total: match.total,
    minimum: match.minimum,
    maximum: match.maximum,
    confirmed: match.players.length,
    pending: match.requests.length,
  };
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Injector } from '@angular/core';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { MunicipalityDirectory } from '../municipality/municipality-directory';
import { OpeningStatus } from '../municipality/opening';
import { PlatformAccessService } from './platform-access.service';
import { NEW_MUNICIPALITY_URL } from './roles';
import { StaffAccessService } from './staff-access.service';

/** Primeiros segmentos que non son un concello: as súas páxinas quedan como están. */
const NOT_A_MUNICIPALITY = new Set(['plataforma', 'erro', 'catalogo-ui']);

/** Páxinas do concello ás que se vai a propósito: a xestión e aceptar unha invitación. */
const KEEP_IN_MUNICIPALITY = new Set(['xestion', 'convite']);

/** O que se sabe de quen acaba de entrar. Os datos só se piden se fan falta. */
export interface LandingFacts {
  readonly platformAdmin: () => Promise<boolean>;
  /** Cantos concellos hai na instalación. */
  readonly municipalityCount: () => Promise<number>;
  /** Se configura ese concello (Xestión ou a plataforma) e lle falta algún paso para abrir as reservas. */
  readonly pendingOpening: (slug: string, platformAdmin: boolean) => Promise<boolean>;
}

/**
 * Onde entra quen acaba de iniciar sesión (maqueta 56), desde a páxina de onde saíu (`from`); `null`
 * = volve a ela:
 * - A plataforma sen ningún concello: á alta («Novo concello»).
 * - Xestión ou a plataforma desde unha páxina pública dun concello ao que lle falta algún paso de
 *   «Para abrir as reservas»: á pestana «Xeral» da sección «Concello», onde está a lista.
 * Quen ía á xestión, a unha invitación ou ás páxinas da plataforma segue alí.
 */
export async function loginLanding(from: string, facts: LandingFacts): Promise<string | null> {
  const [first = '', second = ''] = from.split(/[?#]/)[0].split('/').filter(Boolean);
  if (NOT_A_MUNICIPALITY.has(first)) return null;
  const platformAdmin = await facts.platformAdmin();
  if (platformAdmin && (await facts.municipalityCount()) === 0) return NEW_MUNICIPALITY_URL;
  if (!first || KEEP_IN_MUNICIPALITY.has(second)) return null;
  return (await facts.pendingOpening(first, platformAdmin)) ? `/${first}/xestion/concello/xeral` : null;
}

/** Os datos de `loginLanding` desde a API (os mesmos servizos ca as gardas, que os gardan). */
export function apiLandingFacts(injector: Injector): LandingFacts {
  return {
    platformAdmin: () => injector.get(PlatformAccessService).load(),
    municipalityCount: async () => (await injector.get(MunicipalityDirectory).list()).length,
    pendingOpening: async (slug, platformAdmin) => {
      const access = await injector.get(StaffAccessService).load(slug);
      if (access?.role !== 'MANAGER' && !platformAdmin) return false;
      const municipality = await injector.get(CurrentMunicipality).load(slug);
      return municipality !== null && (await injector.get(OpeningStatus).pending(municipality));
    },
  };
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IncidentType } from './incident-report.store';

export type { IncidentType };

/** Que pasa: os tipos dunha incidencia, na orde dos chips. */
export const INCIDENT_TYPES: readonly IncidentType[] = ['BREAKDOWN', 'CLEANING', 'SAFETY', 'OTHER'];

/** Os mesmos límites ca o servidor (`Incident`, subida de 8 MB por foto). */
export const MAX_PHOTOS = 3;
export const DESCRIPTION_MAX = 500;
export const PHOTO_MAX_BYTES = 8 * 1024 * 1024;
export const PHOTO_TYPES = ['image/jpeg', 'image/png'] as const;

/** Query param co que se volve de entrar: abre o aviso só. */
export const REPORT_PARAM = 'avisar';

/** O texto do erro dun ficheiro antes de subilo, ou `null` se vale. */
export function photoProblem(file: File): 'incident.report.photoType' | 'incident.report.photoSize' | null {
  if (!(PHOTO_TYPES as readonly string[]).includes(file.type)) return 'incident.report.photoType';
  if (file.size > PHOTO_MAX_BYTES) return 'incident.report.photoSize';
  return null;
}

/** O que leva o diálogo «Avisar dun problema» ou «Nova incidencia». */
export interface IncidentReportData {
  readonly slug: string;
  /** `resident`: a veciñanza (ficha ou historial); `staff`: «Nova incidencia» da xestión. */
  readonly mode: 'resident' | 'staff';
  /** A instalación fixa (aviso da veciñanza); no persoal, escóllese. */
  readonly facilityId?: string;
  /** O que vai baixo o título: a instalación e, desde unha reserva, cando. */
  readonly subheading?: string;
  /** A reserva da que vén o aviso (historial). */
  readonly bookingId?: string;
  /** As instalacións que se poden escoller («Nova incidencia»). */
  readonly facilities?: readonly { readonly value: string; readonly label: string }[];
}

/** Le un ficheiro como `data:` (a CSP só deixa `self` e `data:` nas imaxes). */
export function dataUrl(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error ?? new Error('read'));
    reader.readAsDataURL(file);
  });
}

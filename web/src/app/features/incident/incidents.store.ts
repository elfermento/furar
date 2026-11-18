// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../api/api';
import { deleteIncident } from '../../api/fn/incidents/delete-incident';
import { deleteIncidentPhoto } from '../../api/fn/incidents/delete-incident-photo';
import { getIncident } from '../../api/fn/incidents/get-incident';
import { getIncidentPhoto } from '../../api/fn/incidents/get-incident-photo';
import { listIncidents } from '../../api/fn/incidents/list-incidents';
import { reviewIncident } from '../../api/fn/incidents/review-incident';
import { listFacilities } from '../../api/fn/facilities/list-facilities';
import type { IncidentResponse } from '../../api/models/incident-response';
import type { ReviewIncidentRequest } from '../../api/models/review-incident-request';
import { NewIncidents } from '../../core/incidents/new-incidents';
import { dataUrl } from '../../shared/incident-report/incident-report';
export type { IncidentResponse, ReviewIncidentRequest };
type Incident = IncidentResponse;

/**
 * A única porta ao cliente xerado das incidencias da xestión (ADR-0036): a lista, o detalle,
 * «Gardar» con If-Match e os bytes das fotos (con sesión: chegan como `data:`, a CSP non deixa
 * `blob:`). Cada cambio refresca o contador do menú.
 */
@Injectable({ providedIn: 'root' })
export class IncidentsStore {
  private readonly api = inject(Api);
  private readonly counter = inject(NewIncidents);
  private readonly photos = new Map<string, Promise<string>>();

  readonly items = signal<readonly Incident[]>([]);
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');

  async load(slug: string): Promise<void> {
    this.state.set('loading');
    try {
      this.items.set(await this.api.invoke(listIncidents, { slug }));
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  get(slug: string, id: string): Promise<Incident> {
    return this.api.invoke(getIncident, { slug, id });
  }

  async review(slug: string, incident: Incident, body: ReviewIncidentRequest): Promise<Incident> {
    const saved = await this.api.invoke(reviewIncident, {
      slug,
      id: incident.id,
      'If-Match': `"${incident.version}"`,
      body,
    });
    this.replace(saved);
    this.counter.refresh();
    return saved;
  }

  /** Elimina a incidencia enteira (só a xestión). */
  async remove(slug: string, incident: Incident): Promise<void> {
    await this.api.invoke(deleteIncident, { slug, id: incident.id });
    this.items.update((items) => items.filter((item) => item.id !== incident.id));
    this.counter.refresh();
  }

  /** Quita unha foto (só a xestión) e devolve a incidencia como queda. */
  async removePhoto(slug: string, incident: Incident, photo: string): Promise<Incident> {
    const saved = await this.api.invoke(deleteIncidentPhoto, { slug, id: incident.id, photoId: photo });
    this.replace(saved);
    return saved;
  }

  /** Unha incidencia nova («Nova incidencia») ou cambiada: volve ler a lista e o contador. */
  async changed(slug: string): Promise<void> {
    this.counter.refresh();
    await this.load(slug);
  }

  /** As instalacións do concello, para «Nova incidencia». */
  async facilities(slug: string): Promise<{ value: string; label: string }[]> {
    const page = await this.api.invoke(listFacilities, { slug, size: 100 });
    return page.items
      .map((facility) => ({ value: facility.id, label: facility.name }))
      .sort((a, b) => a.label.localeCompare(b.label, 'gl'));
  }

  /** A foto como `data:` (unha vez por foto e variante). */
  photo(slug: string, incident: string, photo: string, variant: 'large' | 'thumb'): Promise<string> {
    const key = `${incident}/${photo}/${variant}`;
    let pending = this.photos.get(key);
    if (!pending) {
      pending = this.api.invoke(getIncidentPhoto, { slug, id: incident, photoId: photo, variant }).then(dataUrl);
      pending.catch(() => this.photos.delete(key));
      this.photos.set(key, pending);
    }
    return pending;
  }

  private replace(saved: Incident): void {
    this.items.update((items) => items.map((item) => (item.id === saved.id ? { ...saved, booking: undefined } : item)));
  }
}

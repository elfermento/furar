// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import type { MapPoint } from '../../../../shared/ui/map/map-point';

/** Por que non se puido saber onde está quen mira: sen permiso, ou o navegador non o sabe. */
export type NearMeFailure = 'denied' | 'unavailable';

/** `GeolocationPositionError.PERMISSION_DENIED`. */
const PERMISSION_DENIED = 1;
/** Canto se agarda á posición antes de darse por vencido (ms). */
const TIMEOUT_MS = 15_000;
/** Unha posición de hai menos disto vale (ms): non fai falta pedila outra vez ao volver pulsar. */
const MAX_AGE_MS = 5 * 60_000;

/**
 * «Preto de min» (portada común): pide ao navegador onde está quen mira. A posición
 * queda na memoria da páxina: só serve para ordenar a lista e centrar o mapa, non se manda á API
 * nin se garda. Sen precisión alta: para «a 1,2 km» abonda a aproximada, e gasta menos batería.
 * Un por páxina (`providers`).
 */
@Injectable()
export class NearMe {
  private readonly geolocation = inject(DOCUMENT).defaultView?.navigator?.geolocation;

  /** O navegador sabe dar a posición (se non, nin se ofrece «Preto de min»). */
  readonly supported = !!this.geolocation;
  /** Onde está quen mira, mentres «Preto de min» está activo. */
  readonly here = signal<MapPoint | null>(null);
  /** Agardando a posición. */
  readonly locating = signal(false);
  /** Por que non se puido saber onde está (o aviso); `null`, sen aviso. */
  readonly failure = signal<NearMeFailure | null>(null);

  /** O botón «Preto de min» do mapa á vista: o foco volve a el ao pechar o aviso. */
  button: HTMLElement | null = null;

  /** Pecha o aviso; o foco volve ao botón. */
  dismiss(): void {
    this.failure.set(null);
    this.button?.focus();
  }

  /** Activo, apágase; se non, pide a posición. Sen ela, nada cambia e queda o motivo en `failure`. */
  toggle(): void {
    if (this.here()) {
      this.here.set(null);
      return;
    }
    if (this.locating()) return;
    this.locating.set(true);
    this.failure.set(null);
    this.locate().then(
      (point) => {
        this.locating.set(false);
        this.here.set(point);
      },
      (failure: NearMeFailure) => {
        this.locating.set(false);
        this.failure.set(failure === 'denied' ? 'denied' : 'unavailable');
      },
    );
  }

  private locate(): Promise<MapPoint> {
    const geolocation = this.geolocation;
    if (!geolocation) return Promise.reject<MapPoint>('unavailable' satisfies NearMeFailure);
    return new Promise((resolve, reject) =>
      geolocation.getCurrentPosition(
        ({ coords }) => resolve({ latitude: coords.latitude, longitude: coords.longitude }),
        (error) => reject((error.code === PERMISSION_DENIED ? 'denied' : 'unavailable') satisfies NearMeFailure),
        { enableHighAccuracy: false, timeout: TIMEOUT_MS, maximumAge: MAX_AGE_MS },
      ),
    );
  }
}

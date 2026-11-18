// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { EnvironmentProviders } from '@angular/core';
import { DestroyRef, Injectable, Injector, computed, inject, provideAppInitializer, signal } from '@angular/core';
import { CurrentMunicipality } from '../municipality/current-municipality';

/** `beforeinstallprompt` (navegadores Chromium): non vén nos tipos do DOM. */
export interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ readonly outcome: 'accepted' | 'dismissed'; readonly platform?: string }>;
}

/** O que se mira do concello cargado para decidir sobre a barriña do navegador. */
export interface InstallHintSetting {
  readonly slug: string;
  readonly installHint: boolean;
}

/**
 * Deixarlle ao navegador a súa barriña de instalar (non chamar a `preventDefault`): só na portada
 * dun concello que apagou o aviso propio (`/:concello`, co concello xa cargado). Noutra páxina, na
 * portada común (aviso sempre aceso) ou co concello aínda sen cargar, non: o evento gárdase para o
 * aviso propio, coma sempre. A decisión ten que ser no momento: despois xa non se pode volver
 * pedir a barriña.
 */
export function keepBrowserInstallBar(path: string, municipality: InstallHintSetting | null): boolean {
  if (!municipality || municipality.installHint) return false;
  const [segment, ...rest] = path.split('/').filter(Boolean);
  if (segment === undefined || rest.length > 0) return false;
  try {
    return decodeURIComponent(segment) === municipality.slug;
  } catch {
    return false;
  }
}

/**
 * Instalar a PWA (ADR-0014) cando o navegador o ofrece. Chromium dispara `beforeinstallprompt`
 * só se a web é instalable e aínda non está instalada (e só con HTTPS); o evento chega ao pouco de
 * cargar, en calquera páxina, así que se escoita desde o arranque (`provideInstallPrompt`) e
 * gárdase para cando a portada o pida. `preventDefault` quita a barriña propia do navegador, porque
 * o aviso é o da portada; na portada dun concello que o apagou, non (`keepBrowserInstallBar`): alí
 * sae a do navegador. `appinstalled` (instalada desde o menú do navegador ou desde o aviso) apágao
 * para sempre nesta páxina.
 */
@Injectable({ providedIn: 'root' })
export class InstallPrompt {
  private readonly view = inject(DOCUMENT).defaultView;
  // Ao chegar o evento, non ao arrancar: o concello cárgao o resolver da ruta.
  private readonly injector = inject(Injector);
  private readonly deferred = signal<BeforeInstallPromptEvent | null>(null);
  private readonly installedState = signal(false);

  /** O navegador deixa instalar agora mesmo (hai un evento sen usar). */
  readonly available = computed(() => this.deferred() !== null);
  /** Instalada nesta visita (aceptou a xanela do navegador ou chegou `appinstalled`). */
  readonly installed = this.installedState.asReadonly();

  constructor() {
    const view = this.view;
    if (!view) return;
    const onPrompt = (event: Event) => {
      const municipality = this.injector.get(CurrentMunicipality).value();
      if (!keepBrowserInstallBar(view.location.pathname, municipality)) event.preventDefault();
      this.deferred.set(event as BeforeInstallPromptEvent);
    };
    const onInstalled = () => {
      this.deferred.set(null);
      this.installedState.set(true);
    };
    view.addEventListener('beforeinstallprompt', onPrompt);
    view.addEventListener('appinstalled', onInstalled);
    inject(DestroyRef).onDestroy(() => {
      view.removeEventListener('beforeinstallprompt', onPrompt);
      view.removeEventListener('appinstalled', onInstalled);
    });
  }

  /**
   * Abre a xanela de instalar do navegador. O evento só vale unha vez: despois, `available` queda a
   * `false` ata que o navegador dispare outro (noutra carga). `true` se a persoa aceptou.
   */
  async prompt(): Promise<boolean> {
    const event = this.deferred();
    if (!event) return false;
    this.deferred.set(null);
    try {
      await event.prompt();
      const { outcome } = await event.userChoice;
      if (outcome === 'accepted') this.installedState.set(true);
      return outcome === 'accepted';
    } catch {
      return false;
    }
  }
}

/** Escoita `beforeinstallprompt` desde o arranque: o evento pode chegar antes ca a portada. */
export function provideInstallPrompt(): EnvironmentProviders {
  return provideAppInitializer(() => void inject(InstallPrompt));
}

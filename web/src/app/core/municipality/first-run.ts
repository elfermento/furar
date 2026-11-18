// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, signal } from '@angular/core';

/**
 * Se a instalación aínda non ten ningún concello (ADR-0028): a portada da raíz móstrao cos
 * exemplos e «Entrar» pídelle a Keycloak o rexistro sen a frase de privacidade (`furar_setup=1`),
 * porque aínda non hai concello do que informar. Ponse ao ler a lista de concellos na raíz.
 */
@Injectable({ providedIn: 'root' })
export class FirstRun {
  private readonly empty = signal(false);

  /** Verdadeiro só despois de ver que non hai ningún concello. */
  readonly noMunicipality = this.empty.asReadonly();

  set(noMunicipality: boolean): void {
    this.empty.set(noMunicipality);
  }
}

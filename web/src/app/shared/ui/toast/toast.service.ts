// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, signal } from '@angular/core';

/** Un aviso de «feito»: a clave do texto e os seus parámetros; `id` distingue dous iguais seguidos. */
export interface Toast {
  readonly id: number;
  readonly key: string;
  readonly params: Readonly<Record<string, unknown>>;
}

/**
 * Avisos de «feito» (docs/deseno.md §8, «Toast de feito»): un só á vez, e o novo substitúe ao anterior, tamén
 * despois dunha navegación (o toast vive no shell, fóra das páxinas). Os erros e os avisos non van
 * aquí: quedan fixos na páxina, onde se poden ler e arranxar.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly state = signal<Toast | null>(null);
  private sequence = 0;
  readonly current = this.state.asReadonly();

  /** Amosa o aviso (texto sen punto final) e quita o que houbese. */
  show(key: string, params: Readonly<Record<string, unknown>> = {}): void {
    this.state.set({ id: ++this.sequence, key, params });
  }

  /** Pecha o aviso; con `id`, só se segue sendo ese (un temporizador vello non pecha o novo). */
  dismiss(id?: number): void {
    if (id === undefined || this.state()?.id === id) this.state.set(null);
  }
}

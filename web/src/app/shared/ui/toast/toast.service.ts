// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, signal } from '@angular/core';
import { ApiProblem } from '../../../core/errors/api-problem';

/** Un aviso: a clave do texto e os seus parámetros; `id` distingue dous iguais seguidos. */
export interface Toast {
  readonly id: number;
  readonly key: string;
  readonly params: Readonly<Record<string, unknown>>;
  /** `success`: «feito», marcha só. `danger`: un erro dunha acción solta, queda ata pechalo. */
  readonly tone: 'success' | 'danger';
  /** No erro, o motivo despois do texto: o da API ou, sen el, «Téntao de novo.». */
  readonly reason: { readonly key: string; readonly params: Readonly<Record<string, unknown>> } | null;
}

/** Sen motivo concreto da API: pedir que se volva tentar. */
const RETRY = { key: 'ui.toast.retry', params: {} };

/**
 * Avisos flotantes (docs/deseno.md §8): un só á vez, e o novo substitúe ao anterior, tamén despois
 * dunha navegación (o toast vive no shell, fóra das páxinas).
 *
 * - `show`: o que saíu ben («feito»); desaparece só.
 * - `error`: un erro dunha acción solta sobre unha lista ou unha tarxeta:
 *   o texto xenérico («Non se puido saír da partida.») e o motivo real da API, se o hai. Non
 *   desaparece só: queda ata pechalo (✕, Esc) ou ata a seguinte acción que saia ben.
 *
 * Os erros dun formulario e os da carga dunha páxina non van aquí: quedan fixos na páxina.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly state = signal<Toast | null>(null);
  private sequence = 0;
  readonly current = this.state.asReadonly();

  /** Amosa o aviso de «feito» (texto sen punto final) e quita o que houbese. */
  show(key: string, params: Readonly<Record<string, unknown>> = {}): void {
    this.state.set({ id: ++this.sequence, key, params, tone: 'success', reason: null });
  }

  /**
   * Amosa un erro: `key` é o texto xenérico (unha frase con punto) e `failure`, o que lanzou a
   * acción; se é un erro da API cun motivo concreto, vai detrás.
   */
  error(key: string, failure?: unknown, params: Readonly<Record<string, unknown>> = {}): void {
    const reason =
      failure instanceof ApiProblem && failure.code !== 'common.unexpected'
        ? { key: failure.messageKey, params: failure.params }
        : RETRY;
    this.state.set({ id: ++this.sequence, key, params, tone: 'danger', reason });
  }

  /** Pecha o aviso; con `id`, só se segue sendo ese (un temporizador vello non pecha o novo). */
  dismiss(id?: number): void {
    if (id === undefined || this.state()?.id === id) this.state.set(null);
  }
}

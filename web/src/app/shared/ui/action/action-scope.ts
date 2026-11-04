// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ApplicationRef, Directive, Injectable, computed, inject, signal } from '@angular/core';
import type { Observable } from 'rxjs';
import { firstValueFrom, isObservable } from 'rxjs';

/** Traballo dunha acción: unha promesa ou un Observable (colle o primeiro valor ou o remate). */
export type ActionWork<T> = () => Promise<T> | Observable<T>;

/**
 * Contexto de acción (docs/deseno.md §8, «Accións en curso»): mentres unha acción corre, o resto
 * de accións do mesmo contexto quedan bloqueadas. Hai un por páxina (o de raíz), un por capa
 * (`DialogService` dálle un propio a cada diálogo) e os que se poñan con `[furarActionScope]`.
 *
 * Os botóns `[furarButton]` rexístranse sós: o que ten `busy` é o dono e os outros do contexto
 * levan `aria-disabled="true"`; calquera clic (ou envío co Intro) mentres hai dono non chega á
 * feature. Sen `disabled`: o foco non se perde.
 */
@Injectable({ providedIn: 'root' })
export class ActionScope {
  private readonly app = inject(ApplicationRef);
  private readonly owners = signal<readonly object[]>([]);
  /** O botón que se acaba de premer, mentres corre o seu clic (protexe o dobre clic inmediato). */
  private pending: object | null = null;

  /** Hai unha acción en curso (algún botón `busy` ou `run` sen rematar). */
  readonly busy = computed(() => this.owners().length > 0);

  /** `owner` é quen leva a acción en curso. */
  owns(owner: object): boolean {
    return this.owners().includes(owner);
  }

  /** Marca o comezo dunha acción de `owner` (un botón con `busy`, ou `run`). */
  claim(owner: object): void {
    if (!this.owns(owner)) this.owners.update((list) => [...list, owner]);
  }

  /** Marca o final da acción de `owner`. */
  release(owner: object): void {
    if (this.owns(owner)) this.owners.update((list) => list.filter((item) => item !== owner));
  }

  /**
   * Un clic de `owner`: devolve `false` se hai que deixalo sen efecto (hai outra acción en curso,
   * ou outro clic aínda sen rematar). Se pasa, xusto despois do clic píntanse os cambios: se a
   * feature marcou o `busy`, o contexto xa queda ocupado antes de que chegue o seguinte clic.
   */
  admit(owner: object): boolean {
    if (this.pending !== null) return false;
    // Decídese co estado de agora: un `busy` que acaba de cambiar e aínda non se pintou tamén conta.
    this.render();
    if (this.busy()) return false;
    this.pending = owner;
    queueMicrotask(() => {
      this.render();
      this.pending = null;
    });
    return true;
  }

  /** Pinta xa os cambios pendentes (os `busy` dos botóns); non se pode se xa se está pintando. */
  private render(): void {
    try {
      this.app.tick();
    } catch {
      // Dentro dun render (NG0101): o estado xa é o actual.
    }
  }

  /**
   * Corre `work` como acción do contexto: se xa hai outra, non fai nada e devolve `undefined`.
   * Para as accións que non saen dun `[furarButton]` con `busy` (p. ex. un elemento de menú).
   */
  async run<T>(work: ActionWork<T>, owner: object = {}): Promise<T | undefined> {
    if (this.busy()) return undefined;
    this.claim(owner);
    try {
      const result = work();
      return await (isObservable(result) ? firstValueFrom(result, { defaultValue: undefined }) : result);
    } finally {
      this.release(owner);
    }
  }
}

/**
 * Contexto de acción propio para o que vai dentro (un formulario ou unha sección que non deben
 * bloquear o resto da páxina, nin ser bloqueados por ela).
 */
@Directive({
  selector: '[furarActionScope]',
  providers: [ActionScope],
})
export class ActionScopeDirective {}

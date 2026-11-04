// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { DialogConfig, DialogRef } from '@angular/cdk/dialog';
import { Dialog } from '@angular/cdk/dialog';
import { createGlobalPositionStrategy } from '@angular/cdk/overlay';
import type { ComponentType } from '@angular/cdk/portal';
import { DOCUMENT } from '@angular/common';
import { Injectable, Injector, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ActionScope } from '../action/action-scope';
import { canTransition, withViewTransition } from '../view-transition';
import type { ConfirmData, ConfirmOptions, ConfirmResult } from './confirm.component';
import { ConfirmComponent } from './confirm.component';
import type { UnsavedChoice, UnsavedChoiceData, UnsavedChoiceOptions } from './unsaved-choice.component';
import { UnsavedChoiceComponent } from './unsaved-choice.component';
import { DialogContainerComponent } from './dialog-container.component';

let nextId = 0;

/** Ancho no que unha capa con `sheet` sae como folla desde abaixo (o mesmo de `furar-field-popover`). */
const SHEET_QUERY = '(max-width: 39.99rem)';

/** Cada capa, o seu contexto de acción. */
const OWN_SCOPE = { provide: ActionScope, useClass: ActionScope, deps: [] };

/** O que pode abrir unha capa. */
const TRIGGER = 'button, a[href], [role="button"]';

/** Un clic de hai máis ca isto xa non é o que abriu a capa. */
const ORIGIN_MS = 1500;

/** O que pode axustar quen abre; o resto (contedor, `aria-modal`, clases, foco) fíxao o servizo. */
export type OpenDialogConfig<D> = Pick<
  DialogConfig<D>,
  'data' | 'disableClose' | 'autoFocus' | 'injector' | 'restoreFocus'
> & {
  /** No móbil (< 640 px), folla desde abaixo a todo o ancho no canto da card centrada. */
  readonly sheet?: boolean;
  /**
   * O elemento desde o que se abre (a tarxeta, o botón): a capa sae del e, ao pechar, volve a
   * el. Por defecto, o botón ou ligazón que se acaba de premer; `null`, sen orixe (aparece no sitio).
   */
  readonly from?: HTMLElement | null;
};

/**
 * Abre capas modais (D1-05a) sobre CDK Dialog co contedor propio sen estilos en liña
 * (dialog-container.component.ts), velo `--f-scrim`, `aria-modal="true"`, scroll da páxina
 * bloqueado e retorno do foco a quen abriu. O compoñente que se abre ten `<furar-dialog>` como raíz.
 *
 * Cada capa é o seu propio contexto de acción (`ActionScope`): mentres unha acción dela corre, as
 * outras accións da capa quedan bloqueadas e a capa non se pecha.
 *
 * Transición (docs/deseno.md §8): se se abre desde un elemento, a capa medra desde el cunha View
 * Transition e, ao pechar (Pechar, Esc, picar fóra ou unha acción), encolle de volta cara a el;
 * se o elemento xa non se ve, pecha no sitio. Sen a API ou con movemento reducido, sen transición
 * (a capa aparece cun fundido curto, dialog.css).
 */
@Injectable({ providedIn: 'root' })
export class DialogService {
  private readonly dialog = inject(Dialog);
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);
  /** O último botón ou ligazón premido (rato, dedo ou teclado), e cando. */
  private lastActivated: { element: HTMLElement; at: number } | null = null;

  constructor() {
    this.document.addEventListener(
      'click',
      (event) => {
        const element = (event.target as Element | null)?.closest?.<HTMLElement>(TRIGGER);
        this.lastActivated = element ? { element, at: Date.now() } : null;
      },
      { capture: true },
    );
  }

  /**
   * Abre `component` co foco no título (`autoFocus: 'first-heading'`, o que pide unha capa longa).
   * Con `sheet`, no móbil sae como folla pegada abaixo (filtros, listas de opcións).
   */
  open<R = unknown, D = unknown, C = unknown>(
    component: ComponentType<C>,
    { sheet = false, from, ...config }: OpenDialogConfig<D> = {},
  ): DialogRef<R, C> {
    const asSheet = sheet && (this.document.defaultView?.matchMedia?.(SHEET_QUERY).matches ?? false);
    // A folla xa ten a súa entrada (desde abaixo).
    const origin = asSheet ? null : this.origin(from);
    const ref = this.dialog.open<R, D, C>(component, {
      autoFocus: 'first-heading',
      // O foco volve á orixe aínda que o clic non a enfocase (Safari non enfoca os botóns).
      ...(origin ? { restoreFocus: origin } : {}),
      ...config,
      ...(asSheet ? { positionStrategy: createGlobalPositionStrategy(this.injector).bottom('0').left('0') } : {}),
      container: DialogContainerComponent,
      providers: [OWN_SCOPE],
      ariaModal: true,
      panelClass: asSheet ? ['f-dialog-pane', 'f-dialog-pane--sheet'] : 'f-dialog-pane',
      backdropClass: 'f-dialog-backdrop',
    });
    if (origin) this.morph(ref, origin);
    return ref;
  }

  /**
   * A orixe: a que se pasa ou, se non se di, o botón que se acaba de premer (ou o que ten o foco,
   * co teclado), se segue á vista.
   * Só se hai transición (sen ela non fai falta).
   */
  private origin(from: HTMLElement | null | undefined): HTMLElement | null {
    if (from === null || !canTransition(this.document)) return null;
    const recent =
      this.lastActivated && Date.now() - this.lastActivated.at < ORIGIN_MS ? this.lastActivated.element : null;
    const focused = this.document.activeElement?.closest?.<HTMLElement>(TRIGGER) ?? null;
    const origin = from ?? recent ?? focused;
    return origin && visible(origin) ? origin : null;
  }

  /**
   * A capa xa está no DOM (o foco e o resultado van como sempre), pero transparente: a transición
   * fotografa a páxina sen ela coa orixe nomeada e despois amósaa co mesmo nome, así medra desde a
   * orixe. Ao pechar, ao revés: o panel nomeado encolle ata a orixe.
   */
  private morph<R, C>(ref: DialogRef<R, C>, from: HTMLElement): void {
    const name = `furar-dialog-${nextId++}`;
    const overlay = ref.overlayRef;
    const pane = () => overlay.overlayElement;
    const layers = [overlay.hostElement, overlay.backdropElement].filter((layer): layer is HTMLElement => !!layer);
    for (const layer of layers) layer.style.setProperty('opacity', '0');
    // A transición fotografa o DOM ao rematar `update`: o contido xa ten que estar pintado.
    ref.componentRef?.changeDetectorRef.detectChanges();
    void withViewTransition(
      this.document,
      () => {
        for (const layer of layers) layer.style.removeProperty('opacity');
      },
      { name, from, to: pane },
    );
    // Todos os peches pasan por `close` (o CDK chámao tamén con Esc e ao picar fóra).
    const close = ref.close.bind(ref);
    let closing = false;
    ref.close = (result, options) => {
      if (closing) return;
      closing = true;
      if (!visible(from) || !canTransition(this.document)) {
        close(result, options);
        return;
      }
      void withViewTransition(this.document, () => close(result, options), { name, from: pane(), to: () => from });
    };
  }

  /**
   * Pregunta antes dunha acción (`alertdialog`, foco en «Cancelar» ou no campo da frase). Resolve `true` se se confirma
   * e `false` se se cancela ou pecha. Con `options.action`, rexeita co seu erro se falla.
   */
  async confirm(options: ConfirmOptions, { from }: Pick<OpenDialogConfig<unknown>, 'from'> = {}): Promise<boolean> {
    const descriptionId = `furar-confirm-${nextId++}-text`;
    const origin = this.origin(from);
    const ref = this.dialog.open<ConfirmResult, ConfirmData, ConfirmComponent>(ConfirmComponent, {
      data: { ...options, descriptionId },
      container: DialogContainerComponent,
      providers: [OWN_SCOPE],
      role: 'alertdialog',
      ariaModal: true,
      ariaDescribedBy: descriptionId,
      // Se hai que escribir a frase, o foco vai ao campo; se non, á acción menos destrutiva.
      autoFocus: options.typeToConfirm
        ? '[data-confirm-phrase]'
        : options.informOnly
          ? '[data-confirm-accept]'
          : '[data-confirm-cancel]',
      ...(origin ? { restoreFocus: origin } : {}),
      panelClass: 'f-dialog-pane',
      backdropClass: 'f-dialog-backdrop',
    });
    if (origin) this.morph(ref, origin);
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (typeof result === 'object') {
      throw result.failed;
    }
    return result === true;
  }

  /**
   * Saír dunha parte con cambios sen gardar: Gardar, Descartar ou Cancelar (`alertdialog`, foco en
   * Gardar). Pechar con Esc ou picando fóra é Cancelar.
   */
  async unsavedChoice(options: UnsavedChoiceOptions): Promise<UnsavedChoice> {
    const descriptionId = `furar-unsaved-${nextId++}-text`;
    const ref = this.dialog.open<UnsavedChoice, UnsavedChoiceData, UnsavedChoiceComponent>(UnsavedChoiceComponent, {
      data: { ...options, descriptionId },
      container: DialogContainerComponent,
      providers: [OWN_SCOPE],
      role: 'alertdialog',
      ariaModal: true,
      ariaDescribedBy: descriptionId,
      autoFocus: '[data-choice="save"]',
      panelClass: 'f-dialog-pane',
      backdropClass: 'f-dialog-backdrop',
    });
    return (await firstValueFrom(ref.closed, { defaultValue: undefined })) ?? 'cancel';
  }
}

/** Está no documento e pintado (non `hidden` nin `display: none`). */
function visible(element: HTMLElement): boolean {
  return element.isConnected && (element.checkVisibility?.() ?? true);
}

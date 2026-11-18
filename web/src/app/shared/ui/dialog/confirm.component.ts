// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { Observable } from 'rxjs';
import { firstValueFrom, isObservable } from 'rxjs';
import { ButtonDirective } from '../button/button.directive';
import { focusFirstError } from '../focus-error/focus-error';
import { IconComponent } from '../icon/icon.component';
import type { IconName } from '../icon/icons';
import { TypeToConfirmComponent } from '../type-to-confirm/type-to-confirm.component';
import { DialogComponent } from './dialog.component';

/**
 * Unha liña de texto con icona (sen fondos): a cousa sobre a que se
 * pregunta (`title` en negra e o texto debaixo) ou o que pasará se se confirma. `tone` só colorea
 * a icona; o significado vai sempre no texto.
 */
export interface ConfirmDetail {
  readonly icon: IconName;
  readonly text: string;
  readonly title?: string;
  readonly tone?: 'primary' | 'success' | 'warn' | 'danger';
}

/** Que pregunta a confirmación. Os textos chegan xa traducidos de quen chama. */
export interface ConfirmOptions {
  /** Título curto, en forma de pregunta ou acción: «Dar de baixa?», «Quitar a foto?». */
  readonly heading: string;
  /** Que pasa se se confirma, nunha ou dúas frases (ou `details`). */
  readonly message?: string;
  /**
   * No canto de `message`, liñas con icona: o que se cancela e o que pasará. Son a
   * descrición do `alertdialog`.
   */
  readonly details?: readonly ConfirmDetail[];
  /** Rótulo da acción: un verbo concreto («Dar de baixa»), nunca «Aceptar». */
  readonly confirmLabel: string;
  /** Rótulo en xerundio mentres corre `action` («Dando de baixa»). Por defecto, `common.saving`. */
  readonly busyLabel?: string;
  /** Rótulo de volver atrás; por defecto `ui.confirm.cancel` («Cancelar»). */
  readonly cancelLabel?: string;
  /** `danger` para o que borra ou retira; `primary` para o resto. */
  readonly tone?: 'danger' | 'primary';
  /**
   * Traballo que se fai ao confirmar, coa capa aberta e ocupada (`busy`). Se vai ben, a capa
   * pecha con `true`; se falla, pecha e a promesa de `confirm()` rexeita co erro, para que a
   * feature o amose no seu aviso.
   */
  readonly action?: () => Promise<unknown> | Observable<unknown>;
  /**
   * Frase que hai que escribir para poder confirmar («eliminar instalación», «desactivar»), para o
   * que custa desfacer. Compárase sen distinguir maiúsculas, tildes nin espazos de máis.
   */
  readonly typeToConfirm?: string;
  /**
   * Casiña obrigatoria antes de confirmar, co texto exacto do que a persoa entende que pasa (non é
   * un consentimento). A acción non se activa ata marcala.
   */
  readonly acknowledgement?: string;
  /**
   * Só informa de que non se pode facer o que se pediu («Non se pode eliminar: ten datos»): un
   * único botón, `confirmLabel` («Pechar»), sen «Cancelar».
   */
  readonly informOnly?: boolean;
}

/** Como pecha: `true`/`false`, ou co erro de `action` (pode non ser un `Error`, p. ex. un HTTP). */
export type ConfirmResult = boolean | { readonly failed: unknown };

/** Datos internos: as opcións e o id do texto que describe o `alertdialog`. */
export interface ConfirmData extends ConfirmOptions {
  readonly descriptionId: string;
}

/**
 * Confirmación curta (patrón APG «alertdialog»): título, mensaxe e dúas accións, cancelar á
 * esquerda e a acción á dereita. O foco entra en «Cancelar» (a menos destrutiva) ou, se hai que
 * escribir unha frase, no campo. Con `typeToConfirm` (`furar-type-to-confirm`) e `acknowledgement`
 * (casiña obrigatoria), a acción segue activa: premida antes de que a frase coincida e a casiña
 * estea marcada, non fai nada, o erro sae debaixo do que falta e o foco vai ao primeiro. Con `details`, liñas de
 * texto con icona no canto da mensaxe.
 * Ábrese con `DialogService.confirm()`, que devolve `true` se se confirma.
 */
@Component({
  selector: 'furar-confirm',
  imports: [ButtonDirective, DialogComponent, IconComponent, TranslocoPipe, TypeToConfirmComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog
      size="sm"
      [heading]="data.heading"
      [description]="data.details?.length ? '' : (data.message ?? '')"
      [descriptionId]="data.descriptionId"
      [busy]="busy()"
    >
      @if (data.details?.length) {
        <div class="f-confirm__details" [id]="data.descriptionId">
          @for (detail of data.details; track $index) {
            <p class="f-confirm__detail" [attr.data-tone]="detail.tone ?? null" data-confirm-detail>
              <furar-icon [name]="detail.icon" />
              <span>
                @if (detail.title) {
                  <b>{{ detail.title }}</b>
                }
                {{ detail.text }}
              </span>
            </p>
          }
        </div>
      }
      @if (data.typeToConfirm; as phrase) {
        <furar-type-to-confirm
          [phrase]="phrase"
          [invalid]="tried() && !typed()"
          [(matched)]="typed"
          (entered)="accept()"
        />
      }
      @if (data.acknowledgement; as text) {
        <label class="f-confirm__ack" data-confirm-ack>
          <input
            type="checkbox"
            aria-required="true"
            [attr.aria-invalid]="ackError() || null"
            [attr.aria-describedby]="data.descriptionId + (ackError() ? ' ' + ackId + '-error' : '')"
            [checked]="acknowledged()"
            (change)="onAcknowledge($event)"
          />
          <span [id]="ackId">{{ text }}</span>
        </label>
        @if (ackError()) {
          <span class="f-field-error" data-confirm-ack-error [id]="ackId + '-error'">{{
            'ui.confirm.ackError' | transloco
          }}</span>
        }
      }
      <ng-container dialogActions>
        @if (!data.informOnly) {
          <button type="button" furarButton data-confirm-cancel (click)="ref.close(false)">
            {{ data.cancelLabel || ('ui.confirm.cancel' | transloco) }}
          </button>
        }
        <button
          type="button"
          data-confirm-accept
          [furarButton]="data.tone ?? 'primary'"
          [busy]="busy()"
          (click)="accept()"
        >
          {{ busy() ? data.busyLabel || ('common.saving' | transloco) : data.confirmLabel }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class ConfirmComponent {
  protected readonly data = inject<ConfirmData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<ConfirmResult>>(DialogRef);
  protected readonly busy = signal(false);
  /** A frase escrita coincide (sen frase, sempre). */
  protected readonly typed = signal(!this.data.typeToConfirm);
  /** A casiña marcada (sen casiña, sempre). */
  protected readonly acknowledged = signal(false);
  protected readonly ackId = `${this.data.descriptionId}-ack`;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  /** Pódese confirmar: a frase coincide e a casiña, se a hai, está marcada. */
  protected readonly matches = computed(() => this.typed() && (!this.data.acknowledgement || this.acknowledged()));
  /** Premeuse a acción antes de tempo: o que falta vese como erro. */
  protected readonly tried = signal(false);
  protected readonly ackError = computed(() => this.tried() && !!this.data.acknowledgement && !this.acknowledged());

  protected onAcknowledge(event: Event): void {
    this.acknowledged.set((event.target as HTMLInputElement).checked);
  }

  protected async accept(): Promise<void> {
    const action = this.data.action;
    if (this.busy()) {
      return;
    }
    if (!this.matches()) {
      this.tried.set(true);
      afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
      return;
    }
    if (!action) {
      this.ref.close(true);
      return;
    }
    this.busy.set(true);
    try {
      const result = action();
      await (isObservable(result) ? firstValueFrom(result, { defaultValue: undefined }) : result);
      this.busy.set(false);
      this.ref.close(true);
    } catch (error) {
      this.busy.set(false);
      this.ref.close({ failed: error });
    }
  }
}

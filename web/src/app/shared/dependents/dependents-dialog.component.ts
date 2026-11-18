// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { ButtonDirective } from '../ui/button/button.directive';
import { CalloutComponent } from '../ui/callout/callout.component';
import { DeleteButtonComponent } from '../ui/delete-button/delete-button.component';
import { DialogComponent } from '../ui/dialog/dialog.component';
import { DialogService } from '../ui/dialog/dialog.service';
import { IconComponent } from '../ui/icon/icon.component';
import { StatusStateComponent } from '../ui/status-state/status-state.component';
import { ToastService } from '../ui/toast/toast.service';
import type { DependentDialogData } from './dependent-dialog.component';
import { DependentDialogComponent } from './dependent-dialog.component';
import type { DependentResponse } from './dependents.store';
import { DependentsStore, MAX_DEPENDENTS } from './dependents.store';

/**
 * «Persoas ao meu cargo»: as fichas da conta, cada unha con «Editar» e eliminar (con
 * confirmación), e «Engadir persoa» no pé. Ábrese desde o menú da conta e desde «Xestionar» en
 * «Para quen». Eliminar di o que pasa: xa non se poderá reservar para esa persoa e as reservas
 * feitas consérvanse (ADR-0031, punto 6).
 */
@Component({
  selector: 'furar-dependents-dialog',
  imports: [
    ButtonDirective,
    CalloutComponent,
    DeleteButtonComponent,
    DialogComponent,
    IconComponent,
    StatusStateComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'dependents.';
    <furar-dialog size="sm" [heading]="t + 'title' | transloco">
      @if (problem(); as p) {
        <furar-callout tone="danger" live="alert" data-dependents-error>{{ p.translated }}</furar-callout>
      }
      @switch (store.state()) {
        @case ('error') {
          <furar-status-state mode="error" [heading]="t + 'loadError' | transloco">
            <button type="button" furarButton (click)="store.load(true)">
              {{ 'booking.mine.loadError.retry' | transloco }}
            </button>
          </furar-status-state>
        }
        @case ('ready') {
          @if (store.items().length) {
            <ul class="f-dependents" data-dependents-list>
              @for (person of store.items(); track person.id) {
                <li>
                  <b>{{ person.name }}</b>
                  <small>{{ person.birthYear }}</small>
                  <!-- As accións baixan debaixo, á dereita, só se non caben. -->
                  <span class="f-dependents__acts">
                    <button
                      type="button"
                      furarButton="quiet"
                      size="sm"
                      data-dependent-edit
                      [attr.aria-label]="t + 'editName' | transloco: { name: person.name }"
                      (click)="edit(person)"
                    >
                      <furar-icon name="pencil" />
                      <span>{{ t + 'edit' | transloco }}</span>
                    </button>
                    <furar-delete-button
                      data-dependent-delete
                      [label]="t + 'removeName' | transloco: { name: person.name }"
                      [tooltip]="t + 'remove' | transloco"
                      (pressed)="remove(person)"
                    />
                  </span>
                </li>
              }
            </ul>
          } @else {
            <p class="f-dependents__empty" data-dependents-empty>{{ t + 'empty' | transloco }}</p>
          }
          <!-- «Engadir», ao centro debaixo da lista (coma en toda a app). -->
          <div class="mt-unit flex justify-center">
            <button type="button" furarButton="secondary" data-dependents-add [disabled]="full()" (click)="edit(null)">
              <furar-icon name="plus" />
              {{ t + 'add' | transloco }}
            </button>
          </div>
        }
        @default {
          <furar-status-state mode="skeleton" [rows]="2" [description]="t + 'loading' | transloco" />
        }
      }
    </furar-dialog>
  `,
})
export class DependentsDialogComponent {
  protected readonly store = inject(DependentsStore);
  private readonly dialogs = inject(DialogService);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);

  protected readonly problem = signal<ApiProblem | null>(null);

  constructor() {
    void this.store.load();
  }

  /** Coas dez fichas gardadas non cabe outra. */
  protected full(): boolean {
    return this.store.items().length >= MAX_DEPENDENTS;
  }

  protected edit(dependent: DependentResponse | null): void {
    this.problem.set(null);
    this.dialogs.open<DependentResponse | null, DependentDialogData>(DependentDialogComponent, {
      data: { dependent },
      autoFocus: '[data-dependent-name]',
    });
  }

  protected async remove(person: DependentResponse): Promise<void> {
    this.problem.set(null);
    try {
      const done = await this.dialogs.confirm({
        heading: this.transloco.translate('dependents.delete.title', { name: person.name }),
        message: this.transloco.translate('dependents.delete.body'),
        confirmLabel: this.transloco.translate('dependents.remove'),
        busyLabel: this.transloco.translate('dependents.delete.busy'),
        tone: 'danger',
        action: () => this.store.remove(person.id),
      });
      if (done) this.toasts.show('dependents.deleted');
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }
}

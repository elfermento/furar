// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SessionService } from '../../../core/auth/session.service';
import { DependentsStore, MAX_DEPENDENTS } from '../../../shared/dependents/dependents.store';
import { addDependent, openDependents } from '../../../shared/dependents/open-dependents';
import { ParticipantListComponent } from '../../../shared/dependents/participant-list.component';
import type { ParticipantOption } from '../../../shared/dependents/participants';
import { SELF } from '../../../shared/dependents/participants';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ReserveStore } from './reserve.store';

/**
 * O panel de «Para quen» da páxina de reservar: «Eu» e as persoas a cargo da conta,
 * varias á vez (`furar-participant-list`), e debaixo «Engadir persoa» (abre «Persoa ao meu cargo»
 * e deixa marcada a que se garda) e «Xestionar» (abre «Persoas ao meu cargo»). Sen sesión só hai
 * «Eu»: as persoas a cargo son da conta.
 */
@Component({
  selector: 'furar-reserve-who',
  imports: [ButtonDirective, IconComponent, ParticipantListComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'dependents.';
    <div class="f-who" data-reserve-who>
      <furar-participant-list
        [label]="'booking.who.label' | transloco"
        [options]="options()"
        [selected]="store.who.selected()"
        (toggled)="store.who.toggle($event)"
      />
      @if (store.authenticated()) {
        <div class="f-who__acts">
          <button type="button" furarButton size="sm" data-who-add [disabled]="full()" (click)="add()">
            <furar-icon name="plus" />
            {{ t + 'add' | transloco }}
          </button>
          <button type="button" furarButton="quiet" size="sm" data-who-manage (click)="manage()">
            {{ t + 'manage' | transloco }}
          </button>
        </div>
      }
    </div>
  `,
})
export class ReserveWhoComponent {
  protected readonly store = inject(ReserveStore);
  private readonly dependents = inject(DependentsStore);
  private readonly session = inject(SessionService);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly options = computed<ParticipantOption[]>(() => {
    this.langChanges();
    const name = this.session.name();
    return [
      { id: SELF, label: this.transloco.translate('booking.who.me'), ...(name ? { detail: name } : {}) },
      ...this.dependents
        .items()
        .map((person) => ({ id: person.id, label: person.name, detail: String(person.birthYear) })),
    ];
  });
  /** Coas dez fichas gardadas non cabe outra. */
  protected readonly full = computed(() => this.dependents.items().length >= MAX_DEPENDENTS);

  protected async add(): Promise<void> {
    const id = await addDependent(this.dialogs);
    if (id) this.store.who.add(id);
  }

  protected manage(): void {
    void openDependents(this.dialogs);
  }
}

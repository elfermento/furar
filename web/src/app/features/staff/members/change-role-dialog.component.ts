// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { RadioCardsComponent } from '../../../shared/ui/radio-cards/radio-cards.component';
import { TypeToConfirmComponent } from '../../../shared/ui/type-to-confirm/type-to-confirm.component';
import type { StaffMemberResponse, StaffRole } from './staff-members';
import { memberLabel, roleCards } from './staff-members';

export interface ChangeRoleData {
  readonly member: StaffMemberResponse;
  /** Cambia o rol; resolve coa persoa cambiada e rexeita co erro da API. */
  readonly change: (role: StaffRole) => Promise<StaffMemberResponse>;
}

/** A persoa co rol novo, o erro (que amosa a páxina) ou `undefined` se se pechou sen cambiar. */
export type ChangeRoleResult = StaffMemberResponse | { readonly failed: unknown } | undefined;

/**
 * «Cambiar o rol» (D4-14): os dous roles en tarxetas co que pode facer cada un. Garda
 * coa capa ocupada; se o rol non cambia, «Gardar» só pecha. Pasar unha persoa de Xestión a
 * Recepción (tamén a propia) pide escribir «recepción» (P7, 2026-10-30; nomes do 2026-11-03): «Gardar» queda bloqueado
 * (`aria-disabled`) e descrito polo rótulo do campo ata que coincide. Os erros (último xestor, 412) pechan a
 * capa e amósaos a páxina, coma ao cancelar unha reserva.
 */
@Component({
  selector: 'furar-change-role-dialog',
  imports: [ButtonDirective, DialogComponent, RadioCardsComponent, TranslocoPipe, TypeToConfirmComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'staff.members.changeRole.';
    <furar-dialog size="sm" [heading]="t + 'title' | transloco" [subheading]="name" [busy]="busy()">
      <div class="gap-unit grid">
        <span class="f-label" id="staff-change-role">{{ t + 'label' | transloco }}</span>
        <furar-radio-cards
          labelledBy="staff-change-role"
          [attr.inert]="busy() ? '' : null"
          [options]="roleCards()"
          [value]="role()"
          (changed)="pick($event)"
        />
        @if (demoting()) {
          <p class="f-help" id="staff-change-role-demote" data-demote>{{ t + 'demote' | transloco }}</p>
          <furar-type-to-confirm
            [phrase]="phrase()"
            describedBy="staff-change-role-demote"
            [(matched)]="typed"
            (entered)="save()"
          />
        }
      </div>
      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-cancel (click)="ref.close()">
          {{ 'common.cancel' | transloco }}
        </button>
        <button
          type="button"
          furarButton="primary"
          data-save
          [busy]="busy()"
          [unavailable]="blocked()"
          [attr.aria-describedby]="blocked() ? confirmPhrase()?.labelId : null"
          (click)="save()"
        >
          {{ (busy() ? 'common.saving' : 'common.save') | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class ChangeRoleDialogComponent {
  private readonly data = inject<ChangeRoleData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<ChangeRoleResult>>(DialogRef);

  protected readonly name = memberLabel(this.data.member);
  protected readonly role = signal<StaffRole | null>(this.data.member.role);
  protected readonly busy = signal(false);
  /** A palabra escrita coincide. */
  protected readonly typed = signal(false);
  protected readonly confirmPhrase = viewChild(TypeToConfirmComponent);
  private readonly transloco = inject(TranslocoService);
  private readonly lang = toSignal(this.transloco.langChanges$, { initialValue: this.transloco.getActiveLang() });
  protected readonly roleCards = computed(() => roleCards(this.transloco, this.lang()));
  /** Unha persoa de Xestión pasa a Recepción: hai que escribir a palabra. */
  protected readonly demoting = computed(() => this.data.member.role === 'MANAGER' && this.role() === 'STAFF');
  /** «recepción», no idioma da pantalla. */
  protected readonly phrase = computed(() => this.transloco.translate('staff.members.changeRole.phrase'));
  protected readonly blocked = computed(() => this.demoting() && !this.typed());

  /** Outro rol: a palabra escrita xa non vale (o campo volve baleiro se reaparece). */
  protected pick(role: StaffRole | null): void {
    this.role.set(role);
    this.typed.set(false);
  }

  protected async save(): Promise<void> {
    const role = this.role();
    if (this.busy() || !role || this.blocked()) return;
    if (role === this.data.member.role) {
      this.ref.close();
      return;
    }
    this.busy.set(true);
    try {
      const changed = await this.data.change(role);
      this.busy.set(false);
      this.ref.close(changed);
    } catch (failed) {
      this.busy.set(false);
      this.ref.close({ failed });
    }
  }
}

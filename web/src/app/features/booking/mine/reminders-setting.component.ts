// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { SwitchComponent } from '../../../shared/ui/switch/switch.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { RemindersStore } from '../reminders/reminders.store';

/**
 * «Recordatorios por correo» (ADR-0035): o interruptor da persoa neste concello, ao final de «As
 * miñas reservas», co nome á esquerda e o interruptor á dereita. Volve acender o que apagou desde
 * a ligazón do correo. Se gardar falla, o interruptor volve onde estaba e avísase.
 */
@Component({
  selector: 'furar-reminders-setting',
  imports: [ReactiveFormsModule, SwitchComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-mine__settings' },
  template: `
    @if (ready()) {
      <furar-switch
        switchId="mine-reminders"
        data-mine-reminders
        [label]="'booking.mine.reminders.label' | transloco"
        [tip]="'booking.mine.reminders.help' | transloco"
        [formControl]="enabled"
      />
    }
  `,
})
export class RemindersSettingComponent implements OnInit {
  readonly slug = input.required<string>();

  private readonly reminders = inject(RemindersStore);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly ready = signal(false);
  protected readonly enabled = new FormControl(true, { nonNullable: true });

  ngOnInit(): void {
    void this.reminders.enabled(this.slug()).then(
      (enabled) => {
        this.enabled.setValue(enabled, { emitEvent: false });
        this.ready.set(true);
      },
      // Sen poder lelo, o interruptor non sae: mellor nada ca un estado inventado.
      () => undefined,
    );
    const changes = this.enabled.valueChanges.subscribe((on) => void this.save(on));
    this.destroyRef.onDestroy(() => changes.unsubscribe());
  }

  private async save(on: boolean): Promise<void> {
    try {
      await this.reminders.setEnabled(this.slug(), on);
    } catch (error) {
      this.enabled.setValue(!on, { emitEvent: false });
      this.toasts.error('booking.mine.reminders.error', error);
    }
  }
}

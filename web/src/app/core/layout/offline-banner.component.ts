// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { OnlineStatus } from '../pwa/online-status';

@Component({
  selector: 'furar-offline-banner',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div role="status" aria-live="polite">
      @if (!status.online()) {
        <p class="bg-ink text-surface px-item py-unit text-center font-bold">{{ 'offline.message' | transloco }}</p>
      }
    </div>
  `,
})
export class OfflineBannerComponent {
  protected readonly status = inject(OnlineStatus);
}

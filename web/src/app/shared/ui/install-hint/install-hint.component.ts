// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  effect,
  inject,
  input,
  runInInjectionContext,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { BOOKING_STYLESHEET } from '../../../core/layout/booking-styles';
import { lazyStylesheet } from '../../../core/layout/lazy-stylesheet';
import { InstallPrompt } from '../../../core/pwa/install-prompt';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';
import type { IosBrowser } from './install-hint';
import { deviceTraits, installHintMode, readDismissed, storeDismissed } from './install-hint';

/**
 * Aviso para instalar Furar coma unha app (PWA, ADR-0014): unha cápsula de cristal, coma a barra
 * superior, con «Instalar» e un botón de pechar que o lembra neste navegador. Só no móbil e coa
 * app sen instalar (`installHintMode`): en Chromium, «Instalar» abre a xanela do navegador; en
 * Safari ou Chrome de iPhone e iPad, unha folla cos dous pasos dese navegador. Vai na portada do
 * concello, que lle pasa se o concello o ten aceso, e na portada común, sempre aceso. Pechalo vale
 * para todo o sitio (unha soa clave).
 */
@Component({
  selector: 'furar-install-hint',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-install-hint' },
  template: `
    @if (mode(); as mode) {
      <div class="f-install-hint__capsule f-glass" [attr.data-install-hint]="mode">
        <span class="f-install-hint__icon" aria-hidden="true"><furar-icon name="install-mobile" /></span>
        <p class="f-install-hint__text">{{ 'ui.installHint.text' | transloco }}</p>
        <button type="button" furarButton="primary" data-install-hint-install (click)="install()">
          {{ 'ui.installHint.install' | transloco }}
        </button>
        <button
          type="button"
          furarButton="quiet"
          round
          data-install-hint-close
          [attr.aria-label]="'ui.installHint.close' | transloco"
          (click)="dismiss()"
        >
          <furar-icon name="close" />
        </button>
      </div>
    }
  `,
})
export class InstallHintComponent {
  private readonly view = inject(DOCUMENT).defaultView;
  private readonly injector = inject(Injector);
  private readonly prompt = inject(InstallPrompt);
  private readonly traits = deviceTraits(this.view);
  private readonly dismissed = signal(readDismissed(this.view));

  /** Aceso: `installHint` do concello na súa portada; `true` na portada común. */
  readonly enabled = input(false);

  protected readonly mode = computed(() =>
    installHintMode({
      enabled: this.enabled(),
      dismissed: this.dismissed() || this.prompt.installed(),
      mobile: this.traits.mobile,
      iosBrowser: this.traits.iosBrowser,
      standalone: this.traits.standalone,
      promptAvailable: this.prompt.available(),
    }),
  );

  constructor() {
    // Aberta coma app: lémbrase tamén para o navegador cando comparten almacenamento (Android).
    if (this.traits.standalone) storeDismissed(this.view);
    // Instalada (aceptou a xanela ou chegou `appinstalled`): non volve neste navegador.
    effect(() => {
      if (this.prompt.installed()) storeDismissed(this.view);
    });
  }

  protected dismiss(): void {
    this.dismissed.set(true);
    storeDismissed(this.view);
  }

  protected async install(): Promise<void> {
    if (this.mode() === 'android') {
      // Aceptada, non volve; cancelada, o evento xa non vale e o aviso marcha ata a seguinte carga.
      await this.prompt.prompt();
      return;
    }
    await this.openSteps(this.mode() === 'ios-chrome' ? 'chrome' : 'safari');
  }

  /**
   * A folla cos pasos de Safari ou Chrome: o diálogo e a súa folla de estilos cárganse á parte (D27).
   * En iOS o navegador non sabe se a app xa está instalada: quen viu os pasos non volve ver o aviso
   * neste navegador, e ao pechar a folla o aviso marcha.
   */
  private async openSteps(browser: IosBrowser): Promise<void> {
    storeDismissed(this.view);
    await runInInjectionContext(this.injector, lazyStylesheet(BOOKING_STYLESHEET, 'furar-booking-css'));
    const [{ DialogService }, { InstallStepsDialogComponent }] = await Promise.all([
      import('../dialog/dialog.service'),
      import('./install-steps-dialog.component'),
    ]);
    const ref = this.injector.get(DialogService).open(InstallStepsDialogComponent, { sheet: true, data: browser });
    ref.closed.subscribe(() => this.dismissed.set(true));
  }
}

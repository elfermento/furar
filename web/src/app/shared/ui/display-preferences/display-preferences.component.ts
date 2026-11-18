// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { DisplayPreferencesService } from '../../../core/preferences/display-preferences.service';
import { loadPreferencesStyles } from '../../../core/preferences/preferences-styles';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';
import { DisplayPreferencesFormComponent } from './display-preferences-form.component';

let nextId = 0;

/**
 * Botón de accesibilidade da barra superior: a figura cos brazos abertos,
 * co nome «Accesibilidade» (tamén en `title`), que abre un panel non modal (`role="dialog"`) cos
 * axustes de visualización. No escritorio, unha capa debaixo do botón; no móbil, unha folla co
 * ancho da barra, debaixo dela (styles/components/display-preferences.css). Pecha con Esc (o foco
 * volve ao botón), co clic fóra e ao saír del co tabulador. O contido carga á parte (orzamento):
 * prepárase cando o navegador está ocioso.
 */
@Component({
  selector: 'furar-display-preferences',
  imports: [ButtonDirective, IconComponent, DisplayPreferencesFormComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-display-prefs',
    '(keydown.escape)': 'onEscape($event)',
    '(focusout)': 'onFocusOut($event)',
  },
  template: `
    <button
      #trigger
      type="button"
      furarButton="quiet"
      size="sm"
      round
      class="f-display-prefs__trigger"
      aria-haspopup="dialog"
      [attr.aria-label]="'ui.displayPrefs.button' | transloco"
      [attr.title]="'ui.displayPrefs.button' | transloco"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="open() ? panelId : null"
      (click)="toggle()"
    >
      <furar-icon name="accessibility" [size]="24" />
    </button>
    @defer (when open(); prefetch on idle) {
      @if (open()) {
        <div
          class="f-display-prefs__panel"
          role="dialog"
          [id]="panelId"
          [attr.aria-label]="'ui.displayPrefs.title' | transloco"
        >
          <furar-display-preferences-form />
        </div>
      }
    }
  `,
})
export class DisplayPreferencesComponent {
  /** Créase xa co botón: o servizo segue o sistema e aplica os axustes desde o principio. */
  protected readonly prefs = inject(DisplayPreferencesService);
  private readonly document = inject(DOCUMENT);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  protected readonly open = signal(false);
  protected readonly panelId = `furar-display-prefs-panel-${nextId++}`;

  constructor() {
    void loadPreferencesStyles();
    const host = inject<ElementRef<HTMLElement>>(ElementRef);
    const onPointer = (event: Event) => {
      if (this.open() && !host.nativeElement.contains(event.target as Node)) this.open.set(false);
    };
    this.document.addEventListener('pointerdown', onPointer, true);
    inject(DestroyRef).onDestroy(() => this.document.removeEventListener('pointerdown', onPointer, true));
  }

  protected toggle(): void {
    this.open.update((open) => !open);
  }

  protected onEscape(event: Event): void {
    if (!this.open()) return;
    event.stopPropagation();
    this.open.set(false);
    this.trigger().nativeElement.focus();
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (next && !(event.currentTarget as HTMLElement).contains(next)) this.open.set(false);
  }
}

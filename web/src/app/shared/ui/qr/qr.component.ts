// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Código QR que xa debuxou a API (ADR-0025, §1.2.13): `size` módulos por lado e o `path` SVG dos
 * módulos escuros. Sen dependencia nin `innerHTML`: o `path` vai como atributo `d`. Pinta sempre
 * escuro sobre branco, con marxe, tamén no tema escuro (un lector precisa ese contraste).
 * O nome accesible leva o código: «Código QR da reserva FUR-7K2Q». Estilos en
 * `styles/reservas.css`: a ruta leva `bookingStylesResolver`.
 */
@Component({
  selector: 'furar-qr',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-qr' },
  template: `
    <svg
      role="img"
      [attr.viewBox]="viewBox()"
      [attr.aria-label]="'ui.qr.name' | transloco: { code: code() }"
      shape-rendering="crispEdges"
      focusable="false"
    >
      <path [attr.d]="path()" />
    </svg>
  `,
})
export class QrComponent {
  /** Módulos por lado (o `qr.size` da API). */
  readonly size = input.required<number>();
  /** Módulos escuros como `path` SVG en unidades de módulo (o `qr.path` da API). */
  readonly path = input.required<string>();
  /** Código que leva o QR, para o nome accesible. */
  readonly code = input.required<string>();

  protected readonly viewBox = computed(() => `0 0 ${this.size()} ${this.size()}`);
}

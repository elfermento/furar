// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { _CdkPrivateStyleLoader } from '@angular/cdk/private';
import type { Provider } from '@angular/core';

/**
 * Estilos estruturais do CDK (Overlay, `cdk-visually-hidden`) sen `<style>` en liña.
 *
 * O CDK cárgaos creando un compoñente con estilos, e Angular méteos nun `<style>` que a CSP
 * `style-src 'self'` bloquea (deploy/caddy/csp.sh). Por iso van na folla global
 * (`styles.css` importa `overlay-prebuilt.css` e `a11y-prebuilt.css`) e este provedor deixa o
 * cargador do CDK sen efecto. Vai en app.config.ts; os tests non o precisan (jsdom non ten CSP).
 */
export function provideCdkGlobalStyles(): Provider {
  return { provide: _CdkPrivateStyleLoader, useValue: { load: () => undefined } };
}

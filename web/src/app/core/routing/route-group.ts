// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { CanMatchFn } from '@angular/router';

/**
 * Para un grupo de rutas sen prefixo (`path: ''` con `loadChildren`) baixo `/:concello`: só entra
 * se queda URL despois do concello. Sen isto, o router dá por bo o grupo para `/:concello` aínda
 * que ningunha das súas rutas case (unha ruta `''` sen compoñente vale para un URL baleiro) e a
 * portada do concello, que vai despois, quedaría en branco.
 */
export const restOfUrl: CanMatchFn = (_route, segments) => segments.length > 0;

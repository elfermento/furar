// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import localeGl from '@angular/common/locales/gl';

registerLocaleData(localeGl);
registerLocaleData(localeEs);

// As probas con axe levan 1–3 s e algúns compoñentes cargan módulos á parte; cun equipo de 8 GB
// cargado (D28 corre tras os IT e as imaxes) pasaban dos 5 s por proba ou do 1 s de `vi.waitFor`
// e fallaban por tempo, non por erro. Marxe, non omisión: o que non chega igual segue fallando.
vi.setConfig({ testTimeout: 20_000 });
const waitFor = vi.waitFor.bind(vi);
vi.waitFor = ((callback, options) =>
  waitFor(callback, typeof options === 'number' ? options : { timeout: 5_000, ...options })) as typeof vi.waitFor;

// As probas corren sen illamento (un só documento por traballador): o tema dun concello que unha
// proba aplica a <html> chegaba á seguinte e facía fallar, segundo a orde, as que len o tema por
// defecto (p. ex. readChartTokens).
afterEach(() => document.documentElement.removeAttribute('style'));

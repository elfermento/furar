// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Clave do navegador onde queda que a persoa pechou o aviso (ou instalou desde el). */
export const INSTALL_HINT_DISMISSED_KEY = 'furar.installHint.dismissed';

/**
 * Que aviso toca: `android`, o botón abre a xanela do navegador (Chromium co evento
 * `beforeinstallprompt`: Chrome, Edge, Samsung Internet…); `ios`, a folla cos dous pasos de Safari;
 * `ios-chrome`, a mesma folla cos pasos de Chrome de iPhone e iPad; `null`, ningún.
 */
export type InstallHintMode = 'android' | 'ios' | 'ios-chrome' | null;

/**
 * Navegador de iOS co que o aviso sabe explicar os pasos (desde iOS 16.4, os de fóra tamén poden
 * engadir á pantalla de inicio): Safari e Chrome. Os demais (Edge, Firefox…), non: os seus menús
 * cambian dunha versión a outra e non sempre o ofrecen.
 */
export type IosBrowser = 'safari' | 'chrome';

export interface InstallHintInput {
  /** Aceso: o do concello na súa portada (Concello › Xeral); sempre na portada común. */
  readonly enabled: boolean;
  /** Pechado antes neste navegador (ou instalado desde o aviso). */
  readonly dismissed: boolean;
  /** Móbil ou tableta (`deviceTraits`); nunca un ordenador, aínda que teña pantalla táctil. */
  readonly mobile: boolean;
  /** Safari ou Chrome de iPhone ou iPad, os de iOS cos que o aviso sabe explicar os pasos. */
  readonly iosBrowser: IosBrowser | null;
  /** Xa aberta coma app (`display-mode` standalone, fullscreen ou minimal-ui; `navigator.standalone`). */
  readonly standalone: boolean;
  /** O navegador deixou instalar (`beforeinstallprompt` gardado e sen usar). */
  readonly promptAvailable: boolean;
}

/**
 * Mostrar o aviso só no móbil e coa app sen instalar. En Chromium, o evento xa di que é instalable
 * e que non está instalada; en iOS non hai evento, así que só en Safari ou Chrome e fóra do modo
 * app. Nos demais (Firefox de Android, Edge ou Firefox de iOS, navegadores dentro doutras apps),
 * nada: non hai botón que funcione nin pasos que valan para todos.
 */
export function installHintMode(input: InstallHintInput): InstallHintMode {
  if (!input.enabled || input.dismissed || !input.mobile || input.standalone) return null;
  if (input.promptAvailable) return 'android';
  if (input.iosBrowser === 'safari') return 'ios';
  if (input.iosBrowser === 'chrome') return 'ios-chrome';
  return null;
}

/** O que se mira do navegador para decidir; cada dato, unha soa vez ao pintar a portada. */
export interface DeviceTraits {
  readonly mobile: boolean;
  readonly iosBrowser: IosBrowser | null;
  readonly standalone: boolean;
}

const MOBILE_UA = /Android|iPhone|iPad|iPod|Mobile/i;
const IOS_UA = /iPhone|iPad|iPod/;
/** Webs abertas dentro doutras apps: levan «Safari» (ou «CriOS») no UA pero non deixan engadir á pantalla de inicio. */
const IN_APP_UA = /GSA\/|FBAN|FBAV|FBIOS|Instagram|Line\//;
/** Outros navegadores de iOS: levan «Safari» no UA pero non son Safari. */
const NOT_SAFARI_UA = /CriOS|FxiOS|EdgiOS|OPiOS|OPT\/|DuckDuckGo|Brave|YaBrowser/;
/** Chrome de iOS: «CriOS/x» no canto de «Version/x». */
const CHROME_IOS_UA = /CriOS\/[\d.]+/;
const STANDALONE_QUERIES = [
  '(display-mode: standalone)',
  '(display-mode: fullscreen)',
  '(display-mode: minimal-ui)',
] as const;
/** Por debaixo disto (o ancho de `md`), unha pantalla táctil conta como móbil aínda sen UA de móbil. */
const NARROW_QUERY = '(max-width: 47.99rem)';

/**
 * Móbil = punteiro groso (`pointer: coarse`: o dedo é o punteiro principal) e, ademais, UA de móbil,
 * iPadOS (Safari de iPad dise Mac, pero ten varios puntos táctiles) ou pantalla estreita. Só o
 * punteiro non abonda (un portátil con pantalla táctil en modo tableta) e só o ancho tampouco (unha
 * xanela estreita no escritorio): por iso as dúas cousas á vez. Chrome tamén dispara
 * `beforeinstallprompt` no escritorio; con isto, alí nunca hai aviso.
 */
export function deviceTraits(view: Window | null): DeviceTraits {
  if (!view) return { mobile: false, iosBrowser: null, standalone: false };
  const navigator = view.navigator;
  const ua = navigator.userAgent ?? '';
  const matches = (query: string) => view.matchMedia?.(query).matches ?? false;
  const iPadOs = navigator.platform === 'MacIntel' && (navigator.maxTouchPoints ?? 0) > 1;
  const ios = IOS_UA.test(ua) || iPadOs;
  const coarse = matches('(pointer: coarse)');
  const mobile = coarse && (MOBILE_UA.test(ua) || iPadOs || matches(NARROW_QUERY));
  const standalone =
    (navigator as Navigator & { standalone?: boolean }).standalone === true || STANDALONE_QUERIES.some(matches);
  return { mobile, iosBrowser: ios ? iosBrowserOf(ua) : null, standalone };
}

/**
 * Safari leva «Version/x» e «Safari/x»; Chrome, «CriOS/x»; os outros de iOS levan o seu nome no
 * canto de «Version/». Dentro doutra app (Instagram, Facebook, a app de Google…), ningún.
 */
function iosBrowserOf(ua: string): IosBrowser | null {
  if (IN_APP_UA.test(ua)) return null;
  if (CHROME_IOS_UA.test(ua)) return 'chrome';
  if (/Version\/[\d.]+.*Safari\//.test(ua) && !NOT_SAFARI_UA.test(ua)) return 'safari';
  return null;
}

/** Se o pechou antes neste navegador. Sen almacenamento (modo privado, bloqueado), como se non. */
export function readDismissed(view: Window | null): boolean {
  try {
    return view?.localStorage.getItem(INSTALL_HINT_DISMISSED_KEY) === '1';
  } catch {
    return false;
  }
}

/** Lembra que o pechou. Se o navegador non deixa gardar, só vale para esta visita. */
export function storeDismissed(view: Window | null): void {
  try {
    view?.localStorage.setItem(INSTALL_HINT_DISMISSED_KEY, '1');
  } catch {
    // Sen almacenamento: quen chama xa o escondeu para esta visita.
  }
}

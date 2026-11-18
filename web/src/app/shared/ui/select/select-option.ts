// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ConnectedPosition } from '@angular/cdk/overlay';

/** Unha opción: o valor que se garda e o texto que se ve (xa traducido). */
export interface SelectOption<T = unknown> {
  readonly value: T;
  readonly label: string;
  /** Vese pero non se pode escoller (o motivo explícao a axuda do campo). */
  readonly disabled?: boolean;
}

/**
 * Debaixo do campo e, se non cabe, enriba; aliñada á esquerda do campo ou, se unha opción longa a
 * fai saír da pantalla, á dereita. O ancho do campo vai como mínimo do panel (`minWidth`) e a lista
 * énchea (select.css): sen iso, a lista quedaba co ancho do seu texto dentro dun panel máis ancho.
 */
export const SELECT_POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 4 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -4 },
];

/**
 * Un si/non que pode quedar sen dato, nun despregable (tres valores): baleiro («Sen indicar», que
 * garda `null`), «Si» e «Non». Para «Cuberta» na ficha da instalación e os campos propios si/non
 * opcionais. `t` traduce as claves.
 */
export function yesNoOptions(t: (key: string) => string): SelectOption<boolean | null>[] {
  return [
    { value: null, label: t('ui.editSection.empty') },
    { value: true, label: t('common.yes') },
    { value: false, label: t('common.no') },
  ];
}

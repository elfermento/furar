// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** O botón dun furar-select (o host, ou un elemento que o contén). */
export function selectButton(host: Element): HTMLButtonElement {
  const button = host.matches('button.f-select__button')
    ? (host as HTMLButtonElement)
    : host.querySelector<HTMLButtonElement>('.f-select__button');
  if (!button) throw new Error('Non hai furar-select');
  return button;
}

/** Texto que amosa o furar-select (o da opción escollida ou o baleiro). */
export function selectText(host: Element): string {
  return selectButton(host).textContent?.trim() ?? '';
}

/**
 * Abre o furar-select e pica a opción co texto `label`, coma unha persoa. Non agarda despois de
 * picar: se a escolla pide algo á API, quen chama atende a petición e logo estabiliza.
 */
export async function pickOption(host: Element, label: string, settle: () => Promise<unknown>): Promise<void> {
  selectButton(host).click();
  await settle();
  const option = [...document.querySelectorAll<HTMLElement>('.f-select__option')].find(
    (o) => o.textContent?.trim() === label,
  );
  if (!option) throw new Error(`Non hai a opción «${label}»`);
  option.click();
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { FUTBOL_7, SAMAIN, use } from '../app/features/facility/uses/uses.fixture';
import type { Use } from '../app/features/facility/uses/uses.store';
import { renderDetail } from './facility-detail';

/** Ficha con usos e axudas para a sección e o seu modal. */
export async function renderUses(uses: Use[] | 'error' = [use(), FUTBOL_7, SAMAIN]) {
  const detail = await renderDetail({ uses });
  const section = () => detail.el.querySelector('#facility-uses') as HTMLElement;
  const form = () => detail.dialog()?.querySelector('furar-facility-use-dialog form') as HTMLFormElement;
  /** O panel do «Cando» (furar-field-popover), se está aberto. */
  const when = () => document.querySelector<HTMLElement>('.f-field-popover__panel');
  /** A fila-resumo do «Cando». */
  const whenTrigger = () => form().querySelector<HTMLButtonElement>('[data-use-when] .f-field-popover__trigger')!;
  const openWhen = async () => {
    if (!when()) await detail.click(whenTrigger());
  };
  /** Opción dun segmentado do modal ou do panel do «Cando». */
  const radio = (text: string) =>
    [
      ...form().querySelectorAll<HTMLButtonElement>('[role="radio"]'),
      ...(when()?.querySelectorAll<HTMLButtonElement>('[role="radio"]') ?? []),
    ].find((b) => b.textContent?.trim() === text) as HTMLButtonElement;
  /** «Aceptar» do modal. */
  const apply = () => detail.click(detail.dialog()!.querySelector('[data-use-apply]')!);
  /** Modo edición e modal do uso `id` (ou dun novo). */
  const open = async (id: string | null) => {
    if (!detail.el.querySelector('[data-edit-save]')) await detail.edit();
    await detail.click(section().querySelector(id ? `[data-use-id="${id}"]` : '[data-use-add]')!);
  };
  return { ...detail, section, form, radio, apply, open, when, whenTrigger, openWhen };
}

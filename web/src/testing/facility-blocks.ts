// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { BlockResponse } from '../app/api/models/block-response';
import { block } from '../app/features/facility/blocks/blocks.fixture';
import { renderDetail } from './facility-detail';

/** Ficha cos bloqueos e axudas para a sección e o seu modal. */
export async function renderBlocks(blocks: BlockResponse[] | 'error' = [block()]) {
  const detail = await renderDetail({ blocks });
  const section = () => detail.el.querySelector('#facility-blocks') as HTMLElement;
  const form = () => detail.dialog()?.querySelector('furar-facility-block-dialog form') as HTMLFormElement;
  const apply = () => detail.click(detail.dialog()!.querySelector('[data-block-apply]')!);
  const open = async (id: string | null) => {
    if (!detail.el.querySelector('[data-edit-save]')) await detail.edit();
    await detail.click(section().querySelector(id ? `[data-block-id="${id}"]` : '[data-block-add]')!);
  };
  return { ...detail, section, form, apply, open };
}

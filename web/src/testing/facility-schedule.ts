// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ScheduleResponse } from '../app/api/models/schedule-response';
import { schedule } from '../app/features/facility/schedule/schedule.fixture';
import { renderDetail } from './facility-detail';

/** Ficha co horario e axudas para a sección. */
export async function renderSchedule(response: ScheduleResponse | 'error' = schedule()) {
  const detail = await renderDetail({ schedule: response });
  const section = () => detail.el.querySelector('#facility-schedule') as HTMLElement;
  const rows = () => [...section().querySelectorAll<HTMLElement>('furar-facility-week-rows li')];
  const editing = () => section().querySelectorAll('fieldset[data-day-row]');
  /** Abre a fila `index` (modo edición) picando no seu texto resolto. */
  const openRow = (index: number) => detail.click(section().querySelector(`[data-day-edit="${index}"]`)!);
  return { ...detail, section, rows, editing, openRow };
}

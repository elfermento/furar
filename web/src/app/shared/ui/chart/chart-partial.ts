// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// O punto en curso dunha liña (ADR-0026, revisión do 2026-10-29): o tramo desde o punto anterior
// vai discontinuo nunha segunda serie igual (trazo, non cor) e o tooltip di «en curso». Puro.

/** Trazo do tramo en curso: discontinuo, co ancho e a cor da liña. */
export const PARTIAL_DASH = [6, 4] as const;

/**
 * Parte unha serie no punto en curso: a liña continua ata o punto anterior a el e a discontinua
 * desde ese punto ata o final (comparten o punto de unión). Sen punto en curso, ou se del en
 * diante non hai ningún dato, todo continuo.
 */
export function splitAtPartial<T>(
  values: readonly (T | null)[],
  partial: number | undefined,
): { solid: (T | null)[]; dashed: (T | null)[] | null } {
  const none = partial === undefined || partial < 0 || !values.slice(partial).some((value) => value !== null);
  if (none) return { solid: [...values], dashed: null };
  const join = Math.max(0, partial - 1);
  return {
    solid: values.map((value, i) => (i <= join && i < partial ? value : null)),
    dashed: values.map((value, i) => (i >= join ? value : null)),
  };
}

/** Un punto do tooltip de eixo (só o que se le). */
interface AxisParam {
  readonly dataIndex: number;
  readonly seriesName?: string;
  readonly name?: string;
  readonly value?: unknown;
}

/**
 * Tooltip dunha liña partida en dúas series: un só valor por categoría (no punto de unión as dúas
 * levan o mesmo) e, no punto en curso, `partialLabel`. Texto plano (`richText`).
 */
export function partialTooltip(format: (value: number) => string, partial: number, partialLabel: string) {
  return (raw: unknown): string => {
    const params = (Array.isArray(raw) ? raw : [raw]) as AxisParam[];
    const point = params.find((p) => typeof p.value === 'number') ?? params[0];
    if (!point) return '';
    const value = typeof point.value === 'number' && Number.isFinite(point.value) ? format(point.value) : '-';
    const suffix = point.dataIndex === partial ? ` (${partialLabel})` : '';
    return `${point.name ?? ''}\n${point.seriesName ?? ''}: ${value}${suffix}`;
  };
}

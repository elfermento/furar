// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { LazySymbolName } from './lazy-symbols';
import type { SymbolName } from './material-symbols';

/**
 * Nome dunha icona (docs/deseno.md P1-10): unha soa familia, Material Symbols de liña
 * (`material-symbols.ts`), só as que se usan. As de `lazy-symbols.ts` só se ven nas páxinas
 * que as rexistran (`registerSymbols`).
 */
export type IconName = SymbolName | LazySymbolName;

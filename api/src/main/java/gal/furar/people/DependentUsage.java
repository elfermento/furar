// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people;

import java.util.UUID;

/**
 * SPI cara a {@code booking} (ADR-0031, punto 3): se unha ficha figura nalgunha reserva, de
 * calquera concello e en calquera estado. Sen reservas, eliminar unha ficha bórraa; con elas,
 * desactívaa. Impleméntaa {@code booking}, que depende de {@code people}: a dependencia invértese
 * cun porto (ADR-0005). Un só bean; sen ningún (probas dun módulo illado), {@link
 * NoDependentUsage}.
 */
public interface DependentUsage {

  boolean isUsed(UUID dependentId);
}

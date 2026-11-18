// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Veciñanza (docs/glosario.md; ADR-0031): o que é da conta dunha persoa e non dun concello. Hoxe,
 * as persoas a cargo (fichas con nome, ano de nacemento e a declaración «está ao meu cargo») e as
 * contas coñecidas polo seu correo verificado. As dúas táboas son de plataforma, sen concello
 * ({@code TenancyAllowlist}): toda sentenza leva o {@code sub} da conta. SQL nativo, sen JPA, e
 * fóra de {@code furar-export}, coma as contas (ADR-0018). Só depende de {@code shared}: implementa
 * a SPI {@link gal.furar.shared.security.VerifiedAccounts} e publica {@link
 * gal.furar.people.PeopleQueries} para {@code booking}, que á súa vez implementa {@link
 * gal.furar.people.DependentUsage} (se unha ficha figura nalgunha reserva).
 */
@ApplicationModule(
    displayName = "People",
    allowedDependencies = {"shared"})
package gal.furar.people;

import org.springframework.modulith.ApplicationModule;

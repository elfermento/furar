// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Quen actuou.
 *
 * @param kind persoal do concello, a plataforma ou outra conta
 * @param name o nome, se é persoal e o ten
 * @param email o correo, se é persoal
 * @param roles os seus roles no concello no momento da acción (baleiro nos eventos anteriores)
 * @param blocked persoal desactivado hai máis de {@code FURAR_AUDIT_VISIBLE} e aínda sen
 *     anonimizar: sen nome nin correo, a web di «Persoa desactivada»
 */
public record ActivityActorResponse(
    ActivityActorKindValue kind,
    @Nullable String name,
    @Nullable String email,
    List<ActivityRoleValue> roles,
    boolean blocked) {}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.port.in;

import gal.furar.customfields.domain.DefinitionSpec;
import gal.furar.customfields.domain.DefinitionStatus;
import gal.furar.customfields.domain.FieldType;
import java.time.Instant;
import java.util.UUID;

/** Definición tal como vén no ficheiro de portabilidade: co seu id, estado e datas. */
public record RestoreCustomFieldDefinitionCommand(
    UUID id,
    String code,
    FieldType type,
    DefinitionSpec spec,
    DefinitionStatus status,
    Instant createdAt,
    Instant updatedAt) {}

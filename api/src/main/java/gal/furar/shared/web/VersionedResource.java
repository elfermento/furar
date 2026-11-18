// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Método de controlador que responde un recurso con versión: leva {@code ETag} ({@link ETags}) e o
 * contrato OpenAPI declárao, para que o cliente saiba que ten que mandar {@code If-Match} ao
 * cambialo (ADR-0009, ADR-0010). {@code ContractHeadersIT} comproba que o contrato e as respostas
 * reais coinciden.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VersionedResource {}

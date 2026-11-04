// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.adapter.in.web;

import gal.furar.@module@.adapter.in.web.dto.Create@Name@Request;
import gal.furar.@module@.adapter.in.web.dto.Update@Name@Request;
import gal.furar.@module@.adapter.in.web.dto.@Name@Response;
import gal.furar.@module@.application.port.in.Create@Name@Command;
import gal.furar.@module@.application.port.in.Update@Name@Command;
import gal.furar.@module@.application.port.in.@Name@View;
import gal.furar.@module@.domain.@Name@;
import gal.furar.@module@.domain.@Name@Id;

/** DTO ↔ comandos e dominio, a man. */
final class @Name@WebMapper {

  private @Name@WebMapper() {}

  static Create@Name@Command toCommand(Create@Name@Request request) {
    return new Create@Name@Command(
        request.customFields(), // @cf
        request.name());
  }

  static Update@Name@Command toCommand(@Name@Id id, long version, Update@Name@Request request) {
    return new Update@Name@Command(
        id,
        version,
        request.customFields(), // @cf
        request.name());
  }

  static @Name@Response toResponse(@Name@View view) {
    @Name@ @name@ = view.value();
    return new @Name@Response(
        @name@.id().value(),
        @name@.name(),
        view.visibleCustomFields().toJson(), // @cf
        @name@.version() == null ? 0 : @name@.version(),
        @name@.createdAt(),
        @name@.updatedAt());
  }
}

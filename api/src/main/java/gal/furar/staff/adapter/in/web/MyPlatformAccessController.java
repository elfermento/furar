// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web;

import gal.furar.staff.adapter.in.web.dto.MyPlatformAccessResponse;
import gal.furar.staff.application.port.in.MyPlatformAccessUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Se a persoa da sesión é administración da plataforma (ADR-0028). Fóra de {@code
 * /municipalities/{slug}}: a plataforma non é dun concello e, na primeira visita, aínda non hai
 * ningún. Só sesión e correo verificado; a primeira petición coa conta de {@code FURAR_ADMIN_EMAIL}
 * é a que lle dá o rol.
 */
@RestController
@Tag(name = "staff", description = "Persoal")
class MyPlatformAccessController {

  private final MyPlatformAccessUseCase access;

  MyPlatformAccessController(MyPlatformAccessUseCase access) {
    this.access = access;
  }

  @GetMapping("/api/v1/me/platform-access")
  @Operation(operationId = "getMyPlatformAccess", summary = "Se son administración da plataforma")
  MyPlatformAccessResponse mine() {
    return new MyPlatformAccessResponse(access.platformAdmin());
  }
}

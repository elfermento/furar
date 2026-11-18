// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web;

import gal.furar.staff.adapter.in.web.dto.MyStaffMembershipResponse;
import gal.furar.staff.adapter.in.web.dto.StaffRoleValue;
import gal.furar.staff.application.port.in.MyStaffMembershipsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Os concellos onde a persoa da sesión é persoal activo (ADR-0027, revisión do 2026-11-03): o
 * cambio de concello do menú da persoa. Fóra de {@code /municipalities/{slug}} porque cruza
 * concellos; só sesión e correo verificado, e só os da propia persoa.
 */
@RestController
@Tag(name = "staff", description = "Persoal")
class MyStaffMembershipsController {

  private final MyStaffMembershipsUseCase memberships;

  MyStaffMembershipsController(MyStaffMembershipsUseCase memberships) {
    this.memberships = memberships;
  }

  @GetMapping("/api/v1/me/staff-memberships")
  @Operation(
      operationId = "listMyStaffMemberships",
      summary = "Os concellos onde son persoal activo, co meu rol en cada un")
  List<MyStaffMembershipResponse> mine() {
    return memberships.mine().stream()
        .map(
            m ->
                new MyStaffMembershipResponse(
                    m.slug(), m.name(), StaffRoleValue.valueOf(m.role().name())))
        .toList();
  }
}

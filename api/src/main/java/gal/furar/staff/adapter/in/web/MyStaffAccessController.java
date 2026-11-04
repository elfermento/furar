// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web;

import gal.furar.staff.adapter.in.web.dto.MyStaffAccessResponse;
import gal.furar.staff.adapter.in.web.dto.StaffRoleValue;
import gal.furar.staff.application.port.in.MyStaffAccess;
import gal.furar.staff.application.port.in.MyStaffAccessUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O acceso da persoa da sesión á xestión do concello (plan do dominio 4, §2.3). Sen rol: só sesión
 * e correo verificado; quen non é persoal recibe {@code role} nulo.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/me/staff")
@Tag(name = "staff", description = "Persoal")
class MyStaffAccessController {

  private final MyStaffAccessUseCase access;

  MyStaffAccessController(MyStaffAccessUseCase access) {
    this.access = access;
  }

  @GetMapping
  @Operation(
      operationId = "getMyStaffAccess",
      summary =
          "O meu rol de persoal no concello, o acceso anterior e se a conta é de traballo nalgún"
              + " concello")
  MyStaffAccessResponse mine(@PathVariable String slug) {
    MyStaffAccess mine = access.mine();
    return new MyStaffAccessResponse(
        mine.role() == null ? null : StaffRoleValue.valueOf(mine.role().name()),
        mine.previousSignInAt(),
        mine.workAccount());
  }
}

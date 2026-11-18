// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.FurarApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** D12 (ADR-0005): M1–M3 e documentación dos módulos (M4) en {@code target/modulith-docs}. */
class ModularityTest {

  static final ApplicationModules MODULES = ApplicationModules.of(FurarApplication.class);

  @Test
  void modulesAreVerified() {
    ModularityRules.verifyModules(MODULES);
  }

  @Test
  void dependenciesAreExplicit() {
    ModularityRules.verifyExplicitDependencies(MODULES);
    assertThat(ModularityRules.annotationOf("gal.furar.customfields").allowedDependencies())
        .containsExactly("shared");
    assertThat(ModularityRules.annotationOf("gal.furar.municipality").allowedDependencies())
        .containsExactlyInAnyOrder("shared", "customfields");
    // ADR-0022: facility depende de shared e da API de customfields (destino FACILITY).
    assertThat(ModularityRules.annotationOf("gal.furar.facility").allowedDependencies())
        .containsExactlyInAnyOrder("shared", "customfields");
    // ADR-0025: booking usa a API pública de facility (usos, horario, bloqueo) e de municipality.
    // Non depende de staff: pregúntalle pola SPI StaffAccounts, que staff implementa. Os campos
    // propios da reserva (destino BOOKING) pola API pública de customfields (ADR-0011).
    // ADR-0031: booking pídelle a people as persoas a cargo da conta (PeopleQueries) e implementa
    // a súa SPI DependentUsage; people só depende de shared.
    assertThat(ModularityRules.annotationOf("gal.furar.booking").allowedDependencies())
        .containsExactlyInAnyOrder("shared", "facility", "municipality", "customfields", "people");
    assertThat(ModularityRules.annotationOf("gal.furar.people").allowedDependencies())
        .containsExactly("shared");
    // ADR-0025: notification só escoita os eventos de booking e le a súa API e a do concello.
    // ADR-0027: staff chama a notification.StaffInvitationMailer; notification nunca depende de
    // staff (sería un ciclo).
    assertThat(ModularityRules.annotationOf("gal.furar.notification").allowedDependencies())
        .containsExactlyInAnyOrder("shared", "booking", "facility", "municipality");
    // ADR-0036: incident le facility (instalación e fotos), booking (a reserva), staff (a quen
    // avisar) e envía os correos con notification.IncidentMailer; ningún deles depende de incident.
    assertThat(ModularityRules.annotationOf("gal.furar.incident").allowedDependencies())
        .containsExactlyInAnyOrder("shared", "facility", "booking", "staff", "notification");
    // ADR-0038: event le facility (o lugar e a súa portada) e envía os correos con
    // notification.EventMailer; ningún deles depende de event. ADR-0041: as sesións dunha
    // actividade ocupan a instalación con booking.ActivityBookings e as persoas a cargo len
    // people.PeopleQueries; tampouco booking nin people dependen de event.
    assertThat(ModularityRules.annotationOf("gal.furar.event").allowedDependencies())
        .containsExactlyInAnyOrder("shared", "facility", "notification", "booking", "people");
    // indicators le os agregados de booking e as horas abertas de
    // facility. ADR-0026, revisión do 2026-11-11: os indicadores da plataforma len o nome de cada
    // concello en municipality, que non depende de indicators.
    assertThat(ModularityRules.annotationOf("gal.furar.indicators").allowedDependencies())
        .containsExactlyInAnyOrder("shared", "booking", "facility", "municipality");
    // ADR-0027: staff envía as invitacións coa API de notification.
    // ADR-0027, revisión do 2026-10-30: staff pregunta a booking se a conta xa reservou e
    // implementa a súa SPI StaffAccounts; booking nunca depende de staff (staff → notification →
    // booking faría un ciclo). ADR-0027, revisión do 2026-11-03: staff le o nome dos concellos da
    // persoa coa API pública de municipality (municipality nunca depende de staff).
    assertThat(ModularityRules.annotationOf("gal.furar.staff").allowedDependencies())
        .containsExactlyInAnyOrder("shared", "notification", "booking", "municipality");
    assertThat(ModularityRules.annotationOf("gal.furar.shared").type())
        .isEqualTo(ApplicationModule.Type.OPEN);
  }

  @Test
  void writesModuleDocumentation() {
    new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder("target/modulith-docs"))
        .writeModulesAsPlantUml()
        .writeIndividualModulesAsPlantUml()
        .writeModuleCanvases();
  }
}

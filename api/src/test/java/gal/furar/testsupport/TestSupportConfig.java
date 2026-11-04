// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import gal.furar.facility.ProgrammableBookingImpact;
import gal.furar.shared.security.PublicEndpoint;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pezas só para tests. {@link BoomEndpoint} provoca un 500 para comprobar que sae en {@code
 * problem+json} sen detalle técnico; está oculto no OpenAPI e fóra de {@code EndpointSecurityIT}.
 * {@link ProgrammableBookingImpact} é o {@code BookingImpact} principal nos IT (ADR-0022): as
 * instalacións que un test programa usan o dobre; o resto, o real de {@code booking}. {@link
 * StatementCounter} conta as sentenzas SQL para as probas de N+1. {@link RecordingMailSender} garda
 * os correos en vez de envialos.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({
  TestSupportConfig.BoomEndpoint.class,
  ProbeEvents.ProbeListener.class,
  ProgrammableBookingImpact.class,
  StatementCounter.class,
  RecordingMailSender.class
})
public class TestSupportConfig {

  public static final String BOOM_PATH = "/api/v1/test-support/boom";

  @Hidden
  @TestComponent
  @RestController
  public static class BoomEndpoint {

    @GetMapping(BOOM_PATH)
    @PublicEndpoint
    String boom() {
      throw new IllegalStateException("secret internal detail: jdbc:postgresql://db/furar");
    }
  }
}

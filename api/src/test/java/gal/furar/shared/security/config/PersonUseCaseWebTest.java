// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-0025, punto 3: un caso de uso con {@link PersonUseCase} pide sesión (401 {@code
 * auth.unauthenticated}) e o correo verificado no token (403 {@code auth.email-not-verified}), sen
 * rol en ningún concello. Coa seguridade real e un caso de uso de proba.
 */
@WebMvcTest(controllers = PersonUseCaseWebTest.ProbeController.class)
@Import({
  WebTestConfig.class,
  PersonUseCaseWebTest.ProbeUseCase.class,
  PersonUseCaseWebTest.ProbeController.class
})
@ActiveProfiles("test")
class PersonUseCaseWebTest {

  static final String PATH = "/api/v1/municipalities/naron/me/probe";

  @Autowired MockMvc mvc;
  @Autowired ProbeUseCase useCase;

  /** Caso de uso de proba: conta as entradas e devolve o {@code sub} de quen o chama. */
  static class ProbeUseCase {
    private final AtomicInteger calls = new AtomicInteger();
    private final CurrentActor actor;

    ProbeUseCase(CurrentActor actor) {
      this.actor = actor;
    }

    @PersonUseCase
    public String mine() {
      calls.incrementAndGet();
      return actor.current().orElseThrow().subject();
    }

    /** Por método: o campo dun proxy CGLIB non é o do obxecto. */
    public AtomicInteger calls() {
      return calls;
    }
  }

  @RestController
  static class ProbeController {
    private final ProbeUseCase useCase;

    ProbeController(ProbeUseCase useCase) {
      this.useCase = useCase;
    }

    @GetMapping(PATH)
    String mine() {
      return useCase.mine();
    }
  }

  @BeforeEach
  void reset() {
    useCase.calls().set(0);
  }

  @Test
  void withoutASessionIs401() throws Exception {
    mvc.perform(get(PATH))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    assertThat(useCase.calls()).hasValue(0);
  }

  @Test
  void anUnverifiedEmailIs403() throws Exception {
    for (Object claim : new Object[] {false, "true"}) {
      mvc.perform(get(PATH).with(jwt().jwt(j -> j.subject("p-1").claim("email_verified", claim))))
          .andExpect(status().isForbidden())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("auth.email-not-verified"));
    }
    mvc.perform(get(PATH).with(jwt().jwt(j -> j.subject("p-1"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("auth.email-not-verified"));
    assertThat(useCase.calls()).hasValue(0);
  }

  @Test
  void aVerifiedPersonWithoutRolesGetsIn() throws Exception {
    mvc.perform(
            get(PATH)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("p-1")
                                    .claim("email_verified", true)
                                    .claim("furar_roles", List.of()))))
        .andExpect(status().isOk())
        .andExpect(content().string("p-1"));
    assertThat(useCase.calls()).hasValue(1);
  }

  /** A segunda capa: aínda que o endpoint fose público, o caso de uso pide a persoa. */
  @Test
  void theUseCaseItselfRejectsAnAnonymousCall() {
    assertThatThrownBy(useCase::mine)
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(AuthError.UNAUTHENTICATED));
    assertThat(useCase.calls()).hasValue(0);
  }
}

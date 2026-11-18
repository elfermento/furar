// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gal.furar.notification.application.port.in.SubmitJoinRequestUseCase;
import gal.furar.notification.domain.JoinRequestRateLimitedException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.security.config.WebTestConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * «¿Es un concello?» (ADR-0042) na capa web: pública, sen sesión nin concello; 202; os campos que
 * non valen dan 400 cos erros por campo; o límite leva {@code Retry-After}; a IP e o idioma ({@code
 * Accept-Language}) chegan ao caso de uso.
 */
@WebMvcTest(MunicipalityJoinRequestController.class)
@Import(WebTestConfig.class)
@ActiveProfiles("test")
class MunicipalityJoinRequestWebTest {

  static final String URL = "/api/v1/municipality-join-requests";
  static final String VALID =
      """
      {"municipality":"Concello de Exemplo","name":"Ana Rey","role":"Concelleira",
       "email":"ana@example.org","phone":"981 000 000","message":"Seis instalacións","website":""}
      """;

  @Autowired MockMvc mvc;
  @MockitoBean SubmitJoinRequestUseCase joinRequests;

  @Test
  void anyoneSendsARequestWithoutSessionAndGetsA202() throws Exception {
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID)
                .header("Accept-Language", "es")
                .with(
                    request -> {
                      request.setRemoteAddr("203.0.113.9");
                      return request;
                    }))
        .andExpect(status().isAccepted());
    ArgumentCaptor<SubmitJoinRequestUseCase.Submission> sent =
        ArgumentCaptor.forClass(SubmitJoinRequestUseCase.Submission.class);
    verify(joinRequests).submit(sent.capture());
    assertThat(sent.getValue().municipality()).isEqualTo("Concello de Exemplo");
    assertThat(sent.getValue().email()).isEqualTo("ana@example.org");
    assertThat(sent.getValue().trap()).isEmpty();
    assertThat(sent.getValue().client()).isEqualTo("203.0.113.9");
    // O correo, no idioma da web de quen a envía.
    assertThat(sent.getValue().language()).isEqualTo(Language.ES);
  }

  @Test
  void withoutAcceptLanguageTheMailIsInGalician() throws Exception {
    mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isAccepted());
    ArgumentCaptor<SubmitJoinRequestUseCase.Submission> sent =
        ArgumentCaptor.forClass(SubmitJoinRequestUseCase.Submission.class);
    verify(joinRequests).submit(sent.capture());
    assertThat(sent.getValue().language()).isEqualTo(Language.GL);
  }

  @Test
  void missingOrMalformedFieldsAreA400WithTheFieldErrors() throws Exception {
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"municipality":" ","name":"Ana","email":"ana@example","phone":"981 abc"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(
            jsonPath("$.errors[?(@.field == 'municipality')].code").value("validation.required"))
        .andExpect(jsonPath("$.errors[?(@.field == 'email')].code").value("validation.pattern"))
        .andExpect(jsonPath("$.errors[?(@.field == 'phone')].code").value("validation.pattern"));
    verify(joinRequests, never()).submit(any());
  }

  @Test
  void theLimitIsA429WithRetryAfter() throws Exception {
    doThrow(new JoinRequestRateLimitedException(Duration.ofMinutes(20)))
        .when(joinRequests)
        .submit(any());
    mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "1200"))
        .andExpect(jsonPath("$.code").value("notification.join-request-rate-limited"));
  }
}

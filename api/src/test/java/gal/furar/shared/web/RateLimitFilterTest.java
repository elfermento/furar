// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Límite por IP (decisión do mantedor do 2026-11-04): pasado o límite do minuto, 429 {@code
 * common.rate-limited} con {@code Retry-After} e sen chegar á API; as escrituras teñen o seu, máis
 * baixo; cada IP conta á parte e o minuto seguinte empeza de cero. A 0, sen límite.
 */
class RateLimitFilterTest {

  static final Instant START = Instant.parse("2026-11-04T10:00:15Z");

  final AtomicReference<Instant> now = new AtomicReference<>(START);
  ProblemWriter problems;

  @BeforeEach
  void problems() throws Exception {
    var messages = new ResourceBundleMessageSource();
    messages.setBasenames(WebConfig.messageBasenames().toArray(String[]::new));
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    messages.setDefaultLocale(Locales.GALICIAN);
    problems = new ProblemWriter(messages, JsonMapper.builder().build());
  }

  @Test
  void stopsTheRequestsOverTheLimitUntilTheNextMinute() throws Exception {
    RateLimitFilter filter = filter(3, 100);
    for (int i = 0; i < 3; i++) {
      assertThat(send(filter, "GET", "203.0.113.7").getStatus()).isEqualTo(200);
    }
    MockHttpServletResponse refused = send(filter, "GET", "203.0.113.7");
    assertThat(refused.getStatus()).isEqualTo(429);
    assertThat(refused.getContentAsString()).contains("\"code\":\"common.rate-limited\"");
    assertThat(refused.getHeader("Retry-After")).isEqualTo("45");
    assertThat(send(filter, "GET", "198.51.100.2").getStatus()).as("outra IP").isEqualTo(200);

    now.set(START.plus(Duration.ofSeconds(45)));
    assertThat(send(filter, "GET", "203.0.113.7").getStatus()).as("minuto novo").isEqualTo(200);
  }

  @Test
  void writesHaveTheirOwnLowerLimit() throws Exception {
    RateLimitFilter filter = filter(100, 2);
    assertThat(send(filter, "POST", "203.0.113.8").getStatus()).isEqualTo(200);
    assertThat(send(filter, "PUT", "203.0.113.8").getStatus()).isEqualTo(200);
    assertThat(send(filter, "DELETE", "203.0.113.8").getStatus()).isEqualTo(429);
    assertThat(send(filter, "GET", "203.0.113.8").getStatus()).as("ler aínda vale").isEqualTo(200);
  }

  @Test
  void zeroMeansNoLimit() throws Exception {
    RateLimitFilter filter = filter(0, 0);
    for (int i = 0; i < 50; i++) {
      assertThat(send(filter, "POST", "203.0.113.9").getStatus()).isEqualTo(200);
    }
  }

  private RateLimitFilter filter(int requests, int writes) {
    Clock clock =
        new Clock() {
          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return now.get();
          }
        };
    return new RateLimitFilter(problems, clock, requests, writes);
  }

  private static MockHttpServletResponse send(RateLimitFilter filter, String method, String ip)
      throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest(method, "/api/v1/municipalities/naron/me/bookings");
    request.setRemoteAddr(ip);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}

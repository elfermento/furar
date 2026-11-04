// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** OPS-8: cada petición leva un id no MDC e na resposta; un id inseguro substitúese. */
class RequestIdFilterTest {

  @Test
  void keepsASafeIncomingIdAndCleansTheMdc() throws Exception {
    assertThat(run("caddy-1234-abcd")).isEqualTo("caddy-1234-abcd");
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void replacesMissingOrUnsafeIds() throws Exception {
    assertThat(run(null)).hasSize(36);
    assertThat(run("bad\nlog-injection")).hasSize(36).doesNotContain("\n");
    assertThat(run("x".repeat(65))).hasSize(36);
  }

  private static String run(String incoming) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/municipalities");
    if (incoming != null) {
      request.addHeader(RequestIdFilter.HEADER, incoming);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seen = new AtomicReference<>();
    new RequestIdFilter()
        .doFilter(
            request,
            response,
            new MockFilterChain() {
              @Override
              public void doFilter(
                  jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(RequestIdFilter.MDC_KEY));
              }
            });
    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(seen.get());
    return seen.get();
  }
}

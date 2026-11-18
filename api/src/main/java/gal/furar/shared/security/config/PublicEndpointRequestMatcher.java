// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.security.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Coincide coas rutas dos métodos marcados {@link PublicEndpoint}. Constrúese na primeira petición
 * desde os controladores rexistrados, así que non hai lista de rutas que manter á man.
 */
final class PublicEndpointRequestMatcher implements RequestMatcher {

  private final ApplicationContext context;
  private volatile List<RequestMatcher> matchers;

  PublicEndpointRequestMatcher(ApplicationContext context) {
    this.context = context;
  }

  @Override
  public boolean matches(HttpServletRequest request) {
    for (RequestMatcher matcher : matchers()) {
      if (matcher.matches(request)) {
        return true;
      }
    }
    return false;
  }

  private List<RequestMatcher> matchers() {
    List<RequestMatcher> local = matchers;
    if (local == null) {
      synchronized (this) {
        local = matchers;
        if (local == null) {
          local = build();
          matchers = local;
        }
      }
    }
    return local;
  }

  private List<RequestMatcher> build() {
    var mapping =
        context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
    List<RequestMatcher> result = new ArrayList<>();
    mapping
        .getHandlerMethods()
        .forEach(
            (RequestMappingInfo info, HandlerMethod method) -> {
              if (!method.hasMethodAnnotation(PublicEndpoint.class)) {
                return;
              }
              for (String pattern : info.getPatternValues()) {
                var methods = info.getMethodsCondition().getMethods();
                if (methods.isEmpty()) {
                  throw new IllegalStateException(
                      "@PublicEndpoint needs an explicit HTTP method: " + method);
                }
                for (RequestMethod m : methods) {
                  result.add(
                      PathPatternRequestMatcher.withDefaults()
                          .matcher(HttpMethod.valueOf(m.name()), pattern));
                }
              }
            });
    return List.copyOf(result);
  }
}

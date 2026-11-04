// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security.config;

import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.web.ProblemWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Seguridade pechada por defecto (ADR-0007): toda ruta pide token salvo os {@code @PublicEndpoint};
 * os permisos compróbanse no caso de uso ({@code @RequiresPermission}) e os casos de uso da persoa
 * piden sesión e correo verificado ({@code @PersonUseCase}).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = false)
class SecurityConfig {

  @Bean
  @Order(2)
  SecurityFilterChain apiChain(
      HttpSecurity http, ApplicationContext context, ProblemWriter problems) throws Exception {
    var entryPoint = new ProblemAuthenticationEntryPoint(problems);
    var deniedHandler = new ProblemAccessDeniedHandler(problems);
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(new PublicEndpointRequestMatcher(context))
                    .permitAll()
                    .requestMatchers(
                        PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.GET, "/api/v1/openapi.json"))
                    .permitAll()
                    .requestMatchers(
                        PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.GET, "/swagger-ui/**"))
                    .permitAll()
                    // Actuator vive no porto 9081; aquí non hai nada: 404, non 401 (D31).
                    .requestMatchers(
                        PathPatternRequestMatcher.withDefaults().matcher("/actuator/**"))
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(jwt -> {})
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(deniedHandler))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler));
    return http.build();
  }

  /**
   * Quen fai a petición, lido antes do controlador e fóra de transacción (R3): unha petición nunca
   * ten dúas conexións á vez polo rol da táboa de persoal ({@link ActorPreloadInterceptor}).
   */
  @Bean
  WebMvcConfigurer actorPreload(ObjectProvider<CurrentActor> currentActor) {
    return new WebMvcConfigurer() {
      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry
            .addInterceptor(new ActorPreloadInterceptor(currentActor))
            .addPathPatterns("/api/v1/municipalities/**");
      }
    };
  }

  /** Interceptor de {@code @RequiresPermission} nos casos de uso. */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  static Advisor requiresPermissionAdvisor(ObjectProvider<CurrentActor> currentActor) {
    var manager = new PermissionAuthorizationManager(new LazyCurrentActor(currentActor));
    var interceptor =
        new AuthorizationManagerBeforeMethodInterceptor(
            new AnnotationMatchingPointcut(null, RequiresPermission.class, true), manager);
    interceptor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder());
    return interceptor;
  }

  /** Interceptor de {@code @PersonUseCase}: sesión e correo verificado (ADR-0025, punto 3). */
  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  static Advisor personUseCaseAdvisor(ObjectProvider<CurrentActor> currentActor) {
    var advisor =
        new DefaultPointcutAdvisor(
            new AnnotationMatchingPointcut(null, PersonUseCase.class, true),
            new PersonUseCaseInterceptor(new LazyCurrentActor(currentActor)));
    advisor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder());
    return advisor;
  }

  /** Evita crear {@code CurrentActor} cedo, mentres se rexistran os {@code Advisor}. */
  private record LazyCurrentActor(ObjectProvider<CurrentActor> provider) implements CurrentActor {
    @Override
    public java.util.Optional<gal.furar.shared.security.Actor> current() {
      return provider.getObject().current();
    }
  }
}

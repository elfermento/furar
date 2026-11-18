// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  final TenantRef naron = new TenantRef(UUID.randomUUID(), "naron");
  final TenantRef ferrol = new TenantRef(UUID.randomUUID(), "ferrol");
  final TenantIdentifierResolver resolver = new TenantIdentifierResolver();

  @Test
  void emptyByDefaultAndRequireFails() {
    assertThat(TenantContext.current()).isEmpty();
    assertThat(TenantContext.isPlatform()).isFalse();
    assertThatThrownBy(TenantContext::require).isInstanceOf(MissingTenantException.class);
    assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(TenantIdentifierResolver.NONE);
  }

  @Test
  void runAsSetsAndRestoresEvenWhenNested() {
    String result =
        TenantContext.runAs(
            naron,
            () -> {
              assertThat(TenantContext.require()).isEqualTo(naron);
              assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(naron.id());
              TenantContext.runAs(
                  ferrol, () -> assertThat(TenantContext.require()).isEqualTo(ferrol));
              return TenantContext.require().slug();
            });
    assertThat(result).isEqualTo("naron");
    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void scopeIsRestoredAfterAnException() {
    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    naron.id(),
                    () -> {
                      throw new IllegalArgumentException("boom");
                    }))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(TenantContext.current()).isEmpty();
  }

  @Test
  void platformOnlyFromPlatformTasks() {
    assertThatThrownBy(() -> TenantContext.runAsPlatform(() -> 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TenantContextTest.class.getName());
    assertThat(new Platform().run(resolver)).isEqualTo(TenantIdentifierResolver.PLATFORM);
    assertThat(TenantContext.isPlatform()).isFalse();
    assertThat(resolver.isRoot(TenantIdentifierResolver.PLATFORM)).isTrue();
    assertThat(resolver.isRoot(TenantIdentifierResolver.NONE)).isFalse();
    assertThat(resolver.validateExistingCurrentSessions()).isFalse();
  }

  @Test
  void listenerInterceptorOpensTheEventTenant() throws Throwable {
    var interceptor = new TenantListenerInterceptor();
    TenantAware event = () -> naron.id();
    Object seen =
        interceptor.invoke(
            new FakeInvocation(new Object[] {event}, () -> TenantContext.require().id()));
    assertThat(seen).isEqualTo(naron.id());
    Object none =
        interceptor.invoke(new FakeInvocation(new Object[] {"x"}, () -> TenantContext.current()));
    assertThat(none).isEqualTo(java.util.Optional.empty());
  }

  @PlatformTask
  static final class Platform {
    Object run(TenantIdentifierResolver resolver) {
      return TenantContext.runAsPlatform(resolver::resolveCurrentTenantIdentifier);
    }
  }

  record FakeInvocation(Object[] arguments, java.util.function.Supplier<Object> body)
      implements org.aopalliance.intercept.MethodInvocation {
    @Override
    public java.lang.reflect.Method getMethod() {
      return null;
    }

    @Override
    public Object[] getArguments() {
      return arguments;
    }

    @Override
    public Object proceed() {
      return body.get();
    }

    @Override
    public Object getThis() {
      return this;
    }

    @Override
    public java.lang.reflect.AccessibleObject getStaticPart() {
      return null;
    }
  }
}

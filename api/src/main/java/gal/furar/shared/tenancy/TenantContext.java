// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Concello actual do fío. O fixa {@code TenantFilter} para cada petición e {@link #runAs} nas
 * tarefas e eventos. Hibernate le-o ao abrir cada sesión ({@link TenantIdentifierResolver}).
 */
public final class TenantContext {

  /** Estado do fío: sen concello, un concello ou plataforma (todos). */
  private sealed interface State {}

  private record None() implements State {}

  private record Platform() implements State {}

  private record Tenant(TenantRef ref) implements State {}

  private static final State NONE = new None();
  private static final State PLATFORM = new Platform();
  private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(() -> NONE);
  private static final StackWalker WALKER =
      StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

  private TenantContext() {}

  /** Concello actual, se o hai. */
  public static Optional<TenantRef> current() {
    return STATE.get() instanceof Tenant(TenantRef ref) ? Optional.of(ref) : Optional.empty();
  }

  /** Concello actual ou {@link MissingTenantException}. */
  public static TenantRef require() {
    return current().orElseThrow(MissingTenantException::new);
  }

  /** Se o fío traballa como plataforma (sen filtro de concello). */
  public static boolean isPlatform() {
    return STATE.get() instanceof Platform;
  }

  /** Executa {@code work} no concello indicado e restaura o estado anterior. */
  public static <T> T runAs(TenantRef tenant, Supplier<T> work) {
    try (Scope ignored = open(tenant)) {
      return work.get();
    }
  }

  /** Executa {@code work} no concello indicado e restaura o estado anterior. */
  public static void runAs(TenantRef tenant, Runnable work) {
    try (Scope ignored = open(tenant)) {
      work.run();
    }
  }

  /** Atallo para eventos, que só levan o id. */
  public static void runAs(UUID municipalityId, Runnable work) {
    runAs(TenantRef.of(municipalityId), work);
  }

  /**
   * Executa {@code work} sen filtro de concello (Hibernate {@code isRoot}). Só desde clases {@link
   * PlatformTask} (A17); compróbase tamén en execución.
   */
  public static <T> T runAsPlatform(Supplier<T> work) {
    Class<?> caller = WALKER.getCallerClass();
    if (!caller.isAnnotationPresent(PlatformTask.class)) {
      throw new IllegalStateException(
          "TenantContext.runAsPlatform called from " + caller.getName() + " without @PlatformTask");
    }
    State previous = STATE.get();
    enter(PLATFORM);
    try {
      return work.get();
    } finally {
      STATE.set(previous);
    }
  }

  /**
   * Abre un ámbito co concello indicado; ao pechalo restaúrase o anterior. Para o filtro de entrada
   * e para {@link #runAs}.
   */
  public static Scope open(TenantRef tenant) {
    State previous = STATE.get();
    enter(new Tenant(tenant));
    return () -> STATE.set(previous);
  }

  /**
   * Cambia o estado do fío. Dentro dunha transacción en curso só se pode «cambiar» ao mesmo
   * concello: a RLS segue co concello co que comezou a transacción (ADR-0006, caso d de {@code
   * TenantRlsSpringIT}). Para outro concello, abre antes o ámbito e despois a transacción.
   */
  private static void enter(State next) {
    TransactionTenant.active()
        .filter(started -> !sameScope(started, next))
        .ifPresent(
            started -> {
              throw new IllegalStateException(
                  "Cannot switch municipality inside a transaction started as "
                      + describe(started)
                      + ": open TenantContext.runAs before the transaction (REQUIRES_NEW)");
            });
    STATE.set(next);
  }

  /** Mesmo concello (polo id: o slug pode faltar nos eventos) ou mesmo estado sen concello. */
  private static boolean sameScope(Object a, State b) {
    if (a instanceof Tenant(TenantRef x) && b instanceof Tenant(TenantRef y)) {
      return x.id().equals(y.id());
    }
    return a.getClass() == b.getClass();
  }

  private static String describe(Object state) {
    return state instanceof Tenant(TenantRef ref) ? ref.id().toString() : state.toString();
  }

  /** Estado actual, para que {@link TenantJpaTransactionManager} lembre o da transacción. */
  static Object snapshot() {
    return STATE.get();
  }

  /** Ámbito que restaura o concello anterior ao pecharse. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}

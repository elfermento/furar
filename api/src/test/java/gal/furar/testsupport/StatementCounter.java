// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestComponent;

/**
 * Contador de sentenzas SQL para os IT que comproban que unha petición non fai N+1. Envolve o
 * {@code DataSource} da API e, mentres hai unha {@link Recording} aberta, apunta o SQL de cada
 * sentenza preparada ou executada (tamén as das peticións HTTP, noutros fíos; non as das escoitas
 * asíncronas). Os IT corren en serie nun só proceso, así que unha gravación ve só o seu.
 *
 * <p>Tamén mide cantas conexións do pool ten aberto á vez un mesmo fío (R3: unha petición non debe
 * coller unha segunda conexión mentres ten a primeira, ou un pool cheo de peticións así queda
 * bloqueado agardando).
 */
@TestComponent
public class StatementCounter implements BeanPostProcessor {

  private static final Queue<String> STATEMENTS = new ConcurrentLinkedQueue<>();
  private static volatile boolean recording;

  /** Conexións abertas agora por cada fío, contadas desde {@link #record()}. */
  private static final Map<Long, AtomicInteger> OPEN = new ConcurrentHashMap<>();

  private static final AtomicInteger MAX_OPEN_PER_THREAD = new AtomicInteger();

  /**
   * Fíos das escoitas asíncronas (os correos das reservas, despois do commit): non son a petición
   * que se mide e poden coincidir coa gravación.
   */
  private static final String ASYNC_LISTENERS = "task-";

  /** Empeza a apuntar as sentenzas desde agora. */
  public static Recording record() {
    STATEMENTS.clear();
    OPEN.clear();
    MAX_OPEN_PER_THREAD.set(0);
    recording = true;
    return new Recording();
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof HikariDataSource dataSource && !(bean instanceof CountingDataSource)) {
      return new CountingDataSource(dataSource);
    }
    return bean;
  }

  /** As sentenzas apuntadas desde {@link #record()}. */
  public static final class Recording implements AutoCloseable {

    private Recording() {}

    public List<String> statements() {
      return List.copyOf(STATEMENTS);
    }

    /** O máximo de conexións que un mesmo fío (unha petición) tivo abertas á vez. */
    public int maxConnectionsPerThread() {
      return MAX_OPEN_PER_THREAD.get();
    }

    /** Cantas sentenzas cumpren {@code filter}. */
    public long count(Predicate<String> filter) {
      return STATEMENTS.stream().filter(filter).count();
    }

    @Override
    public void close() {
      recording = false;
    }
  }

  /**
   * O mesmo pool de Hikari (aínda sen abrir: copia a configuración), pero coas conexións envoltas.
   * Segue sendo un {@code HikariDataSource}, como espera o rexistro de actividade.
   */
  static final class CountingDataSource extends HikariDataSource {

    CountingDataSource(HikariDataSource original) {
      original.copyStateTo(this);
    }

    @Override
    public Connection getConnection() throws SQLException {
      return counting(super.getConnection());
    }

    private static Connection counting(Connection target) {
      AtomicInteger open = opened();
      AtomicBoolean closed = new AtomicBoolean();
      InvocationHandler handler =
          (proxy, method, args) -> {
            if (open != null
                && method.getName().equals("close")
                && closed.compareAndSet(false, true)) {
              open.decrementAndGet();
            }
            note(method, args);
            try {
              return method.invoke(target, args);
            } catch (InvocationTargetException e) {
              throw e.getCause();
            }
          };
      return (Connection)
          Proxy.newProxyInstance(
              StatementCounter.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    /** Conta unha conexión collida por este fío; nulo se non se está gravando. */
    private static @Nullable AtomicInteger opened() {
      if (!recording || Thread.currentThread().getName().startsWith(ASYNC_LISTENERS)) {
        return null;
      }
      AtomicInteger open =
          OPEN.computeIfAbsent(Thread.currentThread().threadId(), t -> new AtomicInteger());
      MAX_OPEN_PER_THREAD.accumulateAndGet(open.incrementAndGet(), Math::max);
      return open;
    }

    private static void note(Method method, Object[] args) {
      if (recording
          && !Thread.currentThread().getName().startsWith(ASYNC_LISTENERS)
          && (method.getName().equals("prepareStatement") || method.getName().equals("prepareCall"))
          && args != null
          && args.length > 0
          && args[0] instanceof String sql) {
        STATEMENTS.add(sql);
      }
    }
  }
}

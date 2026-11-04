// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Defensa en profundidade baixo {@code @TenantId} (SEC-02, ADR-0015): a API conecta cun rol que non
 * é superusuario e non pode crear obxectos no esquema. Así unha inxección SQL non chega á base
 * {@code keycloak}, a {@code COPY ... TO PROGRAM} nin a saltarse unha RLS futura. Co perfil {@code
 * prod} négase a arrincar; nos outros, avisa.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason = "Le os atributos do rol da conexión ao arrincar; non le ningunha táboa")
public class DatabaseRoleCheck implements SmartInitializingSingleton {

  private static final Logger LOG = LoggerFactory.getLogger(DatabaseRoleCheck.class);

  private final JdbcClient jdbc;
  private final Environment environment;

  public DatabaseRoleCheck(JdbcClient jdbc, Environment environment) {
    this.jdbc = jdbc;
    this.environment = environment;
  }

  /** Problemas do rol actual; baleiro se é un rol só de datos. */
  public List<String> problems() {
    List<String> problems = new ArrayList<>();
    jdbc.sql(
            """
            select current_user as role_name,
                   current_setting('is_superuser') = 'on' as superuser,
                   has_schema_privilege(current_user, 'public', 'CREATE') as can_create
            """)
        .query(
            (rs, n) -> {
              String role = rs.getString("role_name");
              if (rs.getBoolean("superuser")) {
                problems.add("database role '" + role + "' is a superuser");
              }
              if (rs.getBoolean("can_create")) {
                problems.add("database role '" + role + "' can create objects in schema public");
              }
              return role;
            })
        .single();
    return problems;
  }

  @Override
  public void afterSingletonsInstantiated() {
    List<String> problems = problems();
    if (problems.isEmpty()) {
      return;
    }
    String message =
        "The API must connect as a data-only role such as furar_app (ADR-0015): "
            + String.join("; ", problems);
    if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
      throw new IllegalStateException("Refusing to start with profile 'prod': " + message);
    }
    LOG.warn(message);
  }
}

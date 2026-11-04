// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.RowLevelSecurity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * D14 (ADR-0006): toda táboa fóra de {@link TenancyAllowlist#TABLES} ten {@code municipality_id
 * uuid not null}, FK a {@code municipality(id)}, un índice que empeza por esa columna e {@code
 * unique (municipality_id, id)}; e toda FK cara a outra táboa do concello é composta e inclúe
 * {@code municipality_id} nos dous lados, para que a base impida apuntar a un rexistro doutro
 * concello (ARQ-5); e cumpre a regra de RLS de {@link RowLevelSecurity} (ARQ4-1, T18). Le o esquema
 * real despois de Flyway: unha migración nova que o esqueza rompe o build. Ademais, unha FK cara a
 * unha táboa doutro módulo (o dono é o da migración que a crea, {@code V…__<modulo>_<que>.sql}) só
 * vale se está na lista pechada {@link CrossModuleForeignKeys} (ADR-0005, ADR-0012).
 */
class SchemaTenancyIT extends AbstractIT {

  @Test
  void everyBusinessTableIsScopedByMunicipality() {
    List<String> tables =
        jdbc.sql(
                """
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                order by table_name
                """)
            .query(String.class)
            .list();
    assertThat(tables).contains("municipality", "custom_field");

    List<String> problems = new ArrayList<>();
    for (String table : tables) {
      if (TenancyAllowlist.TABLES.contains(table)) {
        continue;
      }
      List<String> column =
          jdbc.sql(
                  """
                  select data_type || ':' || is_nullable from information_schema.columns
                  where table_schema = 'public' and table_name = :t and column_name = 'municipality_id'
                  """)
              .param("t", table)
              .query(String.class)
              .list();
      if (!column.equals(List.of("uuid:NO"))) {
        problems.add(table + ": municipality_id uuid not null missing (" + column + ")");
        continue;
      }
      Integer fk =
          jdbc.sql(
                  """
                  select count(*) from pg_constraint c
                  join pg_class t on t.oid = c.conrelid
                  join pg_class r on r.oid = c.confrelid
                  join pg_attribute a on a.attrelid = t.oid and a.attnum = any (c.conkey)
                  where c.contype = 'f' and t.relname = :t and r.relname = 'municipality'
                    and a.attname = 'municipality_id' and cardinality(c.conkey) = 1
                  """)
              .param("t", table)
              .query(Integer.class)
              .single();
      if (fk == 0) {
        problems.add(table + ": no foreign key municipality_id -> municipality(id)");
      }
      Integer index =
          jdbc.sql(
                  """
                  select count(*) from pg_index i
                  join pg_class t on t.oid = i.indrelid
                  join pg_attribute a on a.attrelid = t.oid and a.attnum = i.indkey[0]
                  where t.relname = :t and a.attname = 'municipality_id'
                  """)
              .param("t", table)
              .query(Integer.class)
              .single();
      if (index == 0) {
        problems.add(table + ": no index starting with municipality_id");
      }
      problems.addAll(rowLevelSecurityProblems(table));
      Integer tenantKey =
          jdbc.sql(
                  """
                  select count(*) from pg_index i
                  join pg_class t on t.oid = i.indrelid
                  where t.relname = :t and i.indisunique and i.indnatts = 2
                    and (select array_agg(a.attname::text order by k.ord)
                         from unnest(i.indkey::int2[]) with ordinality k(attnum, ord)
                         join pg_attribute a on a.attrelid = t.oid and a.attnum = k.attnum)
                        = array['municipality_id', 'id']
                  """)
              .param("t", table)
              .query(Integer.class)
              .single();
      Integer hasId =
          jdbc.sql(
                  """
                  select count(*) from information_schema.columns
                  where table_schema = 'public' and table_name = :t and column_name = 'id'
                  """)
              .param("t", table)
              .query(Integer.class)
              .single();
      // Só as táboas con id poden ser destino dunha FK composta (seat_slot, p. ex., non ten id).
      if (hasId > 0 && tenantKey == 0) {
        problems.add(table + ": no unique (municipality_id, id) for composite foreign keys");
      }
      jdbc.sql(
              """
              select r.relname as ref,
                (select string_agg(a.attname, ', ' order by a.attnum) from pg_attribute a
                 where a.attrelid = c.conrelid and a.attnum = any (c.conkey)
                   and a.attname <> 'municipality_id') as cols,
                exists (select 1 from pg_attribute a where a.attrelid = c.conrelid
                        and a.attnum = any (c.conkey) and a.attname = 'municipality_id') as local_ok,
                exists (select 1 from pg_attribute a where a.attrelid = c.confrelid
                        and a.attnum = any (c.confkey) and a.attname = 'municipality_id') as ref_ok
              from pg_constraint c
              join pg_class t on t.oid = c.conrelid
              join pg_class r on r.oid = c.confrelid
              where c.contype = 'f' and t.relname = :t and r.relname <> 'municipality'
              order by c.conname
              """)
          .param("t", table)
          .query(
              (rs, n) -> {
                String ref = rs.getString("ref");
                boolean scoped = rs.getBoolean("local_ok") && rs.getBoolean("ref_ok");
                if (!TenancyAllowlist.TABLES.contains(ref) && !scoped) {
                  problems.add(
                      table
                          + ": foreign key ("
                          + rs.getString("cols")
                          + ") to "
                          + ref
                          + " without municipality_id");
                }
                return ref;
              })
          .list();
    }
    assertThat(problems).as("Tables without municipality scope (ADR-0006)").isEmpty();
  }

  /**
   * ADR-0005: ningún módulo apunta ás táboas doutro salvo a {@code municipality} e ás FK da lista
   * pechada {@link CrossModuleForeignKeys}. E a lista non ten entradas mortas.
   */
  @Test
  void foreignKeysToOtherModulesAreInTheClosedList() throws IOException {
    Map<String, String> owners = tableOwners();
    List<CrossModuleForeignKeys.ForeignKey> foreignKeys =
        jdbc.sql(
                """
                select distinct t.relname as source, r.relname as target
                from pg_constraint c
                join pg_class t on t.oid = c.conrelid
                join pg_class r on r.oid = c.confrelid
                join pg_namespace n on n.oid = t.relnamespace
                where c.contype = 'f' and n.nspname = 'public' and r.relname <> 'municipality'
                order by 1, 2
                """)
            .query(
                (rs, n) ->
                    new CrossModuleForeignKeys.ForeignKey(
                        rs.getString("source"), rs.getString("target")))
            .list();
    List<String> problems = new ArrayList<>();
    for (CrossModuleForeignKeys.ForeignKey fk : foreignKeys) {
      String from = owners.get(fk.from());
      String to = owners.get(fk.to());
      if (from == null || to == null) {
        problems.add(fk + ": no migration V…__<module>_….sql creates one of the tables");
      } else if (!from.equals(to) && !CrossModuleForeignKeys.allows(fk.from(), fk.to())) {
        problems.add(fk + ": " + from + " -> " + to + " is not in CrossModuleForeignKeys");
      }
    }
    assertThat(problems).as("Foreign keys between modules (ADR-0005)").isEmpty();
    assertThat(foreignKeys)
        .as("CrossModuleForeignKeys entries that no longer exist")
        .containsAll(CrossModuleForeignKeys.ALLOWED);
  }

  /** Autoproba: o dono dunha táboa sae do nome da súa migración. */
  @Test
  void tableOwnersComeFromTheMigrationNames() throws IOException {
    Map<String, String> owners = tableOwners();
    assertThat(owners)
        .containsEntry("municipality", "municipality")
        .containsEntry("facility_use", "facility")
        // Renomeada (alter table … rename to): o dono segue a migración que a deixa así.
        .containsEntry("library_photo_content", "facility")
        .containsEntry("booking_occupancy", "booking")
        .containsEntry("use_slot", "booking");
  }

  /** {@code create table} ou {@code alter table … rename to}: a táboa resultante é do módulo. */
  private static final Pattern TABLE_DDL =
      Pattern.compile(
          "(?i)\\b(?:create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?"
              + "|alter\\s+table\\s+[a-z_][a-z0-9_]*\\s+rename\\s+to\\s+)([a-z_][a-z0-9_]*)");

  /** Táboa → módulo: o primeiro anaco do nome da migración que a crea (ADR-0010). */
  static Map<String, String> tableOwners() throws IOException {
    Map<String, String> owners = new TreeMap<>();
    Resource[] migrations =
        new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*__*.sql");
    Arrays.sort(migrations, Comparator.comparing(Resource::getFilename));
    for (Resource migration : migrations) {
      String name = migration.getFilename();
      String module = name.substring(name.indexOf("__") + 2).split("[_.]")[0];
      // Sen comentarios: só conta o DDL.
      String sql = migration.getContentAsString(StandardCharsets.UTF_8).replaceAll("--[^\\n]*", "");
      Matcher m = TABLE_DDL.matcher(sql);
      while (m.find()) {
        owners.put(m.group(1).toLowerCase(Locale.ROOT), module);
      }
    }
    return owners;
  }

  /** Autoproba: unha táboa da plataforma (sen RLS) dá o erro; a do concello, non. */
  @Test
  void rowLevelSecurityCheckDetectsATableWithoutIt() {
    assertThat(rowLevelSecurityProblems("municipality"))
        .containsExactly("municipality: row level security not enabled");
    assertThat(rowLevelSecurityProblems("custom_field")).isEmpty();
  }

  private List<String> rowLevelSecurityProblems(String table) {
    return RowLevelSecurity.problems(jdbc, table);
  }
}

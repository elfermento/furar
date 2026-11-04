// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import gal.furar.shared.persistence.NativeSql;
import gal.furar.shared.tenancy.TenancyAllowlist;
import gal.furar.testsupport.AbstractTenantIT;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Condición da regra A8b: le os literais SQL do bytecode dunha clase {@link NativeSql} (API {@code
 * java.lang.classfile}) e comproba que cumpren o alcance declarado.
 */
final class NativeSqlScope {

  static final String TENANT_IT_SUFFIX = "TenantIT";

  private static final Pattern SQL =
      Pattern.compile(
          "^\\s*(select|insert|update|delete|with|table|values|merge|explain|call|copy)\\b",
          Pattern.CASE_INSENSITIVE);

  /**
   * Táboa despois de {@code from|join|into|update|using|table}; cun esquema diante ({@code
   * public.t}) captúrase a táboa, non o esquema (QA Q12). Só é a primeira rede: {@link #tables}
   * suma calquera identificador que nomee unha táboa do esquema (QN1).
   */
  private static final Pattern TABLE =
      Pattern.compile(
          "\\b(?:from|join|into|update|using|table)\\s+(?:only\\s+)?(?:\"?[a-z_][a-z0-9_]*\"?\\s*\\.\\s*)?"
              + "\"?([a-z_][a-z0-9_]*)\"?",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern SCHEMA_QUALIFIED =
      Pattern.compile(
          "\\b(?:from|join|into|update|using)\\s+(?:only\\s+)?\"?[a-z_][a-z0-9_]*\"?\\s*\\.\\s*\"?[a-z_]",
          Pattern.CASE_INSENSITIVE);

  /** Inicio dunha lista de táboas. */
  private static final Pattern FROM_LIST =
      Pattern.compile("\\b(?:from|using)\\b", Pattern.CASE_INSENSITIVE);

  /** Palabras que pechan a lista de táboas do {@code FROM}. */
  private static final Pattern FROM_END =
      Pattern.compile(
          "\\b(?:where|group|order|limit|offset|having|window|for|returning|conflict|union|intersect"
              + "|except|fetch)\\b",
          Pattern.CASE_INSENSITIVE);

  /** Identificador SQL (tamén dentro de comiñas: {@code query_to_xml('select … from t')}). */
  private static final Pattern IDENTIFIER =
      Pattern.compile("[a-z_][a-z0-9_]*", Pattern.CASE_INSENSITIVE);

  /** Táboas e vistas que crean ou renomean as migracións de Flyway. */
  private static final Pattern CREATED_RELATION =
      Pattern.compile(
          "\\b(?:create\\s+(?:unlogged\\s+)?(?:table|(?:materialized\\s+)?view)\\s+"
              + "(?:if\\s+not\\s+exists\\s+)?|rename\\s+to\\s+)(?:\"?[a-z_][a-z0-9_]*\"?\\s*\\.\\s*)?"
              + "\"?([a-z_][a-z0-9_]*)\"?",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern WHERE = Pattern.compile("\\bwhere\\b", Pattern.CASE_INSENSITIVE);

  private static volatile Set<String> schemaRelations;

  private static final Pattern TENANT_PREDICATE =
      Pattern.compile("\\bmunicipality_id\\s*(?:=|in)\\s*\\(?\\s*[:?]", Pattern.CASE_INSENSITIVE);

  /** {@code insert into t (cols...)}: o concello vai na lista de columnas (ARQ-1). */
  private static final Pattern INSERT_COLUMNS =
      Pattern.compile(
          "^\\s*insert\\s+into\\s+\"?[a-z_][a-z0-9_]*\"?\\s*\\(([^)]*)\\)",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern MUNICIPALITY_COLUMN =
      Pattern.compile("(?:^|,)\\s*municipality_id\\s*(?:,|$)", Pattern.CASE_INSENSITIVE);

  private static final Pattern OR = Pattern.compile("\\bor\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern SET_OPERATION =
      Pattern.compile("\\b(?:union|intersect|except)\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern CASE = Pattern.compile("\\bcase\\b", Pattern.CASE_INSENSITIVE);

  /** {@code not} xusto diante do predicado (o texto anterior remata en {@code not}). */
  private static final Pattern NOT_BEFORE =
      Pattern.compile("\\bnot\\s*$", Pattern.CASE_INSENSITIVE);

  private static final Pattern LEADING_PARENS = Pattern.compile("^[\\s(]+");

  /** Dollar quoting ou literais con escapes: o lector de comentarios non os entende. */
  private static final Pattern DOLLAR_OR_ESCAPE =
      Pattern.compile("\\$[a-z_]*\\$|\\b[e]'", Pattern.CASE_INSENSITIVE);

  static final String UNPARSEABLE = "<A8b cannot parse> ";

  private static final String JDBC_CLIENT = "org/springframework/jdbc/core/simple/JdbcClient";
  private static final String ENTITY_MANAGER = "jakarta/persistence/EntityManager";
  private static final Set<String> TEMPLATES =
      Set.of(
          "org/springframework/jdbc/core/JdbcTemplate",
          "org/springframework/jdbc/core/JdbcOperations",
          "org/springframework/jdbc/core/namedparam/NamedParameterJdbcTemplate",
          "org/springframework/jdbc/core/namedparam/NamedParameterJdbcOperations");

  /**
   * Tipos JDBC que abren conexións ou executan SQL. Ler un {@code ResultSet} nun {@code RowMapper}
   * está permitido; executar por aquí, non (QA2), porque A8b só le o argumento de {@code sql(..)}.
   */
  private static final Set<String> RAW_JDBC =
      Set.of(
          "java/sql/Connection",
          "java/sql/Statement",
          "java/sql/PreparedStatement",
          "java/sql/CallableStatement",
          "java/sql/DriverManager",
          "java/sql/Driver");

  private NativeSqlScope() {}

  static ArchCondition<JavaClass> isRespected() {
    return new ArchCondition<>("respect its declared @NativeSql scope") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (String problem : problems(item)) {
          events.add(SimpleConditionEvent.violated(item, item.getName() + " " + problem));
        }
      }
    };
  }

  static List<String> problems(JavaClass item) {
    NativeSql annotation = item.reflect().getAnnotation(NativeSql.class);
    List<String> problems = new ArrayList<>();
    List<String> statements = sqlLiterals(item);
    if (statements.isEmpty()) {
      problems.add("has no literal SQL statement that A8b can check");
    }
    problems.addAll(nonLiteralSqlCalls(item));
    statements.stream()
        .filter(sql -> sql.startsWith(UNPARSEABLE))
        .forEach(
            sql ->
                problems.add(
                    "has SQL that A8b cannot parse (dollar quoting, E'' or an unclosed quote or"
                        + " comment): "
                        + sql.substring(UNPARSEABLE.length())));
    for (String sql : statements) {
      if (sql.startsWith(UNPARSEABLE)) {
        continue;
      }
      if (hasCommaInFromList(sql)) {
        problems.add("has a comma in FROM/USING; use an explicit JOIN: " + sql);
      }
      if (SCHEMA_QUALIFIED.matcher(sql).find()) {
        problems.add("has a schema-qualified table name; use the bare table name: " + sql);
      }
    }
    switch (annotation.scope()) {
      case GLOBAL -> {
        if (annotation.reason().isBlank()) {
          problems.add("is GLOBAL without a reason");
        }
        Set<String> outside = new TreeSet<>();
        statements.forEach(
            sql -> tables(sql).stream().filter(t -> !allowed(t)).forEach(outside::add));
        // QN1: tampouco vale nomear unha táboa do concello noutro texto da clase (SQL dinámico
        // como query_to_xml('…'), ou unha sentenza que A8b non recoñeza como tal).
        stringConstants(item)
            .forEach(
                text ->
                    relationsNamedIn(text).stream().filter(t -> !allowed(t)).forEach(outside::add));
        if (!outside.isEmpty()) {
          problems.add("is GLOBAL but reads tables outside TenancyAllowlist: " + outside);
        }
      }
      case MUNICIPALITY -> {
        for (String sql : statements) {
          problems.addAll(municipalityProblems(sql));
        }
        problems.addAll(tenantItProblems(item));
      }
    }
    return problems;
  }

  /**
   * O {@code <Clase>TenantIT} ten que ser unha proba de illamento de verdade (QA Q3, Q11): existe,
   * estende {@link AbstractTenantIT} (dous concellos, unha fila en cada un, e a proba fixa {@code
   * seesOnlyItsOwnMunicipality}, que a subclase non pode baleirar porque é {@code final}) e o seu
   * {@code visibleTo} chama á clase probada. Un {@code @Test} que só fai {@code new} xa non vale.
   * Que ademais correu con tests &gt; 0 compróbao {@code scripts/sen-omitidos.sh} en D11.
   */
  static List<String> tenantItProblems(JavaClass item) {
    List<String> problems = new ArrayList<>();
    String itName = item.getName() + TENANT_IT_SUFFIX;
    Optional<Class<?>> type = loadType(itName);
    if (type.isEmpty()) {
      problems.add("is MUNICIPALITY but " + itName + " does not exist");
      return problems;
    }
    if (!AbstractTenantIT.class.isAssignableFrom(type.get())) {
      problems.add(
          "is MUNICIPALITY but "
              + itName
              + " does not extend AbstractTenantIT (two municipalities, fixed isolation test)");
      return problems;
    }
    JavaClass it = new ClassFileImporter().importClass(type.get());
    boolean visibleToCallsSubject =
        it.getMethods().stream()
            .filter(m -> m.getName().equals("visibleTo"))
            .flatMap(m -> m.getCallsFromSelf().stream())
            .anyMatch(call -> call.getTargetOwner().getName().equals(item.getName()));
    if (!visibleToCallsSubject) {
      problems.add("is MUNICIPALITY but " + itName + ".visibleTo does not call " + item.getName());
    }
    return problems;
  }

  /**
   * Unha sentenza MUNICIPALITY é válida se: (a) é un {@code insert into t (...)} con {@code
   * municipality_id} na lista de columnas (o valor vai como parámetro; RLS e a FK composta
   * compróbano na base), ou (b) leva {@code municipality_id = :param} (ou {@code in (:…)}) <b>fóra
   * de parénteses</b> e sen {@code not} diante. Ademais, no nivel 0 non pode haber {@code or},
   * {@code union}/{@code intersect}/{@code except} nin {@code case}: así o nivel 0 é unha
   * conxunción e o predicado do concello sempre se cumpre. Dentro de parénteses un {@code or} vale
   * ({@code and (taken = 0 or capacity > 1)}), pero non conta como predicado ({@code
   * (municipality_id = :m or 1 = 1)} falla: QA Q3). Un {@code with} leva o predicado tamén na
   * consulta principal. Non é unha análise SQL completa: a garantía é o TenantIT e RLS ({@code
   * NativeSqlRowLevelSecurityIT}).
   */
  static List<String> municipalityProblems(String sql) {
    List<String> problems = new ArrayList<>();
    Matcher insert = INSERT_COLUMNS.matcher(sql);
    boolean isInsert = insert.find();
    boolean scoped =
        isInsert
            ? MUNICIPALITY_COLUMN.matcher(insert.group(1)).find() || hasTopLevelPredicate(sql)
            : hasTopLevelPredicate(sql);
    if (!scoped) {
      boolean onlyNested = TENANT_PREDICATE.matcher(sql).find();
      problems.add(
          (onlyNested
                  ? "is MUNICIPALITY but its municipality_id predicate is only inside parentheses,"
                      + " after NOT or outside the top-level WHERE: "
                  : "is MUNICIPALITY but has SQL without municipality_id = :param"
                      + " (or, for INSERT, without municipality_id in the column list): ")
              + sql);
    }
    String topLevel = topLevel(sql);
    if (OR.matcher(topLevel).find()) {
      problems.add(
          "is MUNICIPALITY but has a top-level OR that can bypass municipality_id: " + sql);
    }
    if (SET_OPERATION.matcher(topLevel).find()) {
      problems.add(
          "is MUNICIPALITY but has a top-level UNION/INTERSECT/EXCEPT; use one statement per"
              + " query: "
              + sql);
    }
    if (!isInsert && CASE.matcher(topLevel).find()) {
      problems.add("is MUNICIPALITY but has a top-level CASE; move it inside parentheses: " + sql);
    }
    return problems;
  }

  /**
   * QA Q12: unha táboa despois dunha coma no {@code FROM} (ou {@code USING}) non a ve {@link
   * #tables}. En vez de ler a lista, rexéitase a coma a calquera profundidade (tamén en
   * subconsultas e despois dun {@code JOIN}): obriga a {@code JOIN} explícito. As comas dentro de
   * funcións ({@code generate_series(1, 3)}) van a outra profundidade e valen.
   */
  static boolean hasCommaInFromList(String sql) {
    int[] depth = depths(sql);
    Matcher from = FROM_LIST.matcher(sql);
    while (from.find()) {
      int level = depth[from.start()];
      Matcher end = FROM_END.matcher(sql);
      for (int i = from.end(); i < sql.length(); i++) {
        if (depth[i] < level) {
          break;
        }
        if (depth[i] == level
            && end.region(i, sql.length()).lookingAt()
            && (i == 0
                || !Character.isLetterOrDigit(sql.charAt(i - 1)) && sql.charAt(i - 1) != '_')) {
          break;
        }
        if (depth[i] == level && sql.charAt(i) == ',') {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Algún {@code municipality_id = :p} (ou {@code in (:…)}) a profundidade 0, sen {@code not}
   * diante e dentro do {@code WHERE} principal: despois do último {@code where} de nivel 0 e antes
   * de calquera palabra que o peche ({@code group}, {@code order}, {@code returning}...). Na lista
   * do {@code SELECT} ou nun {@code ON} non filtra filas (QA Q11).
   */
  static boolean hasTopLevelPredicate(String sql) {
    int[] depth = depths(sql);
    String top = topLevel(sql);
    Matcher m = TENANT_PREDICATE.matcher(sql);
    while (m.find()) {
      if (depth[m.start()] != 0 || NOT_BEFORE.matcher(sql.substring(0, m.start())).find()) {
        continue;
      }
      String before = top.substring(0, m.start());
      int where = -1;
      Matcher w = WHERE.matcher(before);
      while (w.find()) {
        where = w.end();
      }
      if (where >= 0 && !FROM_END.matcher(before.substring(where)).find()) {
        return true;
      }
    }
    return false;
  }

  /** O texto a profundidade 0: o que hai entre parénteses ou comiñas queda en branco. */
  static String topLevel(String sql) {
    int[] depth = depths(sql);
    StringBuilder out = new StringBuilder(sql.length());
    for (int i = 0; i < sql.length(); i++) {
      char ch = sql.charAt(i);
      out.append(depth[i] == 0 && ch != '(' && ch != ')' && ch != '\'' ? ch : ' ');
    }
    return out.toString();
  }

  /**
   * Profundidade de parénteses de cada carácter; os que van entre comiñas simples contan como
   * profundidade 1 (non son SQL).
   */
  private static int[] depths(String sql) {
    int[] result = new int[sql.length()];
    int depth = 0;
    boolean quoted = false;
    for (int i = 0; i < sql.length(); i++) {
      char ch = sql.charAt(i);
      if (ch == '\'') {
        quoted = !quoted;
        result[i] = 1;
        continue;
      }
      if (!quoted && ch == ')') {
        depth = Math.max(0, depth - 1);
      }
      result[i] = quoted ? Math.max(1, depth) : depth;
      if (!quoted && ch == '(') {
        depth++;
      }
    }
    return result;
  }

  /**
   * As sentenzas que A8b analiza, sen comentarios. Son SQL (1) todo texto constante que chega a
   * {@code JdbcClient.sql(String)} ou {@code EntityManager.createNativeQuery(String)} (T11 obriga a
   * que sexa constante), empece como empece, e (2) por se acaso, calquera outra constante da clase
   * que, sen comentarios nin parénteses iniciais, empece por {@code
   * select|insert|update|delete|with}. Antes recoñecíase só (2) sobre o texto cru, e {@code "/* x
   * *\/ select … from t"} non se analizaba (QA1).
   */
  static List<String> sqlLiterals(JavaClass item) {
    ClassModel model = classModel(item);
    if (model == null) {
      return List.of();
    }
    Set<String> raw = new LinkedHashSet<>(sqlCallArguments(model));
    for (PoolEntry entry : model.constantPool()) {
      if (entry instanceof StringEntry s
          && SQL.matcher(LEADING_PARENS.matcher(withoutComments(s.stringValue())).replaceFirst(""))
              .find()) {
        raw.add(s.stringValue());
      }
    }
    return raw.stream().map(NativeSqlScope::withoutComments).map(String::strip).toList();
  }

  /** Textos constantes pasados directamente a {@code sql(..)} / {@code createNativeQuery(..)}. */
  private static List<String> sqlCallArguments(ClassModel model) {
    List<String> result = new ArrayList<>();
    for (MethodModel method : model.methods()) {
      CodeModel code = method.code().orElse(null);
      if (code == null) {
        continue;
      }
      Instruction previous = null;
      for (CodeElement element : code) {
        if (!(element instanceof Instruction instruction)) {
          continue;
        }
        if (instruction instanceof InvokeInstruction call
            && isSqlCall(call)
            && isStringConstant(previous)) {
          result.add((String) ((ConstantInstruction) previous).constantValue());
        }
        previous = instruction;
      }
    }
    return result;
  }

  private static boolean isSqlCall(InvokeInstruction call) {
    String owner = call.owner().asInternalName();
    String name = call.name().stringValue();
    return (owner.equals(JDBC_CLIENT) && name.equals("sql"))
        || (owner.equals(ENTITY_MANAGER) && name.equals("createNativeQuery"));
  }

  /**
   * Substitúe cada comentario ({@code --} ata o fin de liña e {@code /* *\/}, aniñados como en
   * PostgreSQL) por un espazo, respectando literais {@code '…'} e identificadores {@code "…"}. As
   * formas que este lector non entende (dollar quoting, {@code E'…'}, comiñas ou comentarios sen
   * pechar) quedan marcadas con {@link #UNPARSEABLE} para que A8b as rexeite.
   */
  static String withoutComments(String sql) {
    if (DOLLAR_OR_ESCAPE.matcher(sql).find()) {
      return UNPARSEABLE + sql;
    }
    StringBuilder out = new StringBuilder(sql.length());
    int i = 0;
    int n = sql.length();
    while (i < n) {
      char ch = sql.charAt(i);
      if (ch == '\'' || ch == '"') {
        int end = sql.indexOf(ch, i + 1);
        if (end < 0) {
          return UNPARSEABLE + sql;
        }
        out.append(sql, i, end + 1);
        i = end + 1;
      } else if (ch == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
        int end = sql.indexOf('\n', i);
        i = end < 0 ? n : end;
        out.append(' ');
      } else if (ch == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
        int depth = 0;
        do {
          if (sql.startsWith("/*", i)) {
            depth++;
            i += 2;
          } else if (sql.startsWith("*/", i)) {
            depth--;
            i += 2;
          } else {
            i++;
          }
        } while (depth > 0 && i < n);
        if (depth > 0) {
          return UNPARSEABLE + sql;
        }
        out.append(' ');
      } else {
        out.append(ch);
        i++;
      }
    }
    return out.toString();
  }

  /**
   * T11: o SQL ten que chegar ao driver tal como está no bytecode. Cada chamada a {@code
   * JdbcClient.sql(String)} ou {@code EntityManager.createNativeQuery(String)} ten que recibir unha
   * constante (a instrución anterior é un {@code ldc} dun texto: literal, bloque de texto ou {@code
   * static final} de compilación); se non, A8b lería anacos ({@code "select".trim() + " … from t"})
   * e non a sentenza. {@code JdbcTemplate}, as outras sobrecargas, o JDBC cru ({@code java.sql},
   * {@code javax.sql}: QA2) e o SQL nativo de Hibernate non se usan en clases {@code @NativeSql}.
   */
  static List<String> nonLiteralSqlCalls(JavaClass item) {
    List<String> problems = new ArrayList<>();
    ClassModel model = classModel(item);
    if (model == null) {
      return problems;
    }
    for (MethodModel method : model.methods()) {
      CodeModel code = method.code().orElse(null);
      if (code == null) {
        continue;
      }
      Instruction previous = null;
      for (CodeElement element : code) {
        if (!(element instanceof Instruction instruction)) {
          continue;
        }
        if (instruction instanceof InvokeInstruction call) {
          String where = method.methodName().stringValue() + "()";
          String owner = call.owner().asInternalName();
          String name = call.name().stringValue();
          String descriptor = call.type().stringValue();
          boolean sqlCall = isSqlCall(call);
          if (sqlCall && !descriptor.startsWith("(Ljava/lang/String;)")) {
            problems.add("calls " + name + " with a non-String overload in " + where);
          } else if (sqlCall && !isStringConstant(previous)) {
            problems.add(
                "builds the SQL of "
                    + name
                    + "(...) at runtime in "
                    + where
                    + "; pass a literal or a compile-time constant");
          } else if (RAW_JDBC.contains(owner) || owner.startsWith("javax/sql/")) {
            problems.add(
                "uses "
                    + owner.replace('/', '.')
                    + "."
                    + name
                    + " in "
                    + where
                    + "; A8b cannot read raw JDBC, use JdbcClient.sql with a literal");
          } else if (!sqlCall
              && ArchitectureRules.NATIVE_SQL_METHODS.contains(name)
              && (owner.startsWith("jakarta/persistence/") || owner.startsWith("org/hibernate/"))) {
            problems.add(
                "calls "
                    + owner.substring(owner.lastIndexOf('/') + 1)
                    + "."
                    + name
                    + " in "
                    + where
                    + "; only EntityManager.createNativeQuery(String) with a literal is checked");
          } else if (TEMPLATES.contains(owner)) {
            problems.add(
                "uses "
                    + owner.substring(owner.lastIndexOf('/') + 1)
                    + " in "
                    + where
                    + "; use JdbcClient.sql with a literal");
          }
        }
        previous = instruction;
      }
    }
    return problems;
  }

  private static boolean isStringConstant(Instruction instruction) {
    return instruction instanceof ConstantInstruction constant
        && constant.constantValue() instanceof String;
  }

  private static ClassModel classModel(JavaClass item) {
    String resource = item.getName().replace('.', '/') + ".class";
    try (InputStream in = item.reflect().getClassLoader().getResourceAsStream(resource)) {
      return in == null ? null : ClassFile.of().parse(in.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Táboas que le ou escribe unha sentenza: as que van despois dunha palabra clave ({@link #TABLE},
   * tamén as que non existen no esquema) e, ademais, todo identificador que nomee unha táboa ou
   * vista das migracións, estea onde estea (QN1: {@code table t}, {@code from (table t)}, {@code
   * query_to_xml('select … from t')}). Un falso positivo (unha columna co nome dunha táboa) fai
   * fallar o build; é o lado seguro.
   */
  static Set<String> tables(String sql) {
    Set<String> tables = new TreeSet<>();
    Matcher m = TABLE.matcher(sql);
    while (m.find()) {
      tables.add(m.group(1).toLowerCase(Locale.ROOT));
    }
    tables.addAll(relationsNamedIn(sql));
    return tables;
  }

  /** Identificadores do texto que nomean unha táboa ou vista do esquema. */
  static Set<String> relationsNamedIn(String text) {
    Set<String> known = schemaRelations();
    Set<String> found = new TreeSet<>();
    Matcher m = IDENTIFIER.matcher(text);
    while (m.find()) {
      String id = m.group().toLowerCase(Locale.ROOT);
      if (known.contains(id)) {
        found.add(id);
      }
    }
    return found;
  }

  /**
   * As táboas e vistas que crean as migracións ({@code classpath*:db/migration/*.sql}). Sen
   * migracións a regra non pode saber que é unha táboa: falla en vez de pasar.
   */
  static Set<String> schemaRelations() {
    Set<String> cached = schemaRelations;
    if (cached != null) {
      return cached;
    }
    Set<String> relations = new TreeSet<>();
    try {
      for (org.springframework.core.io.Resource r :
          new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
              .getResources("classpath*:db/migration/**/*.sql")) {
        try (InputStream in = r.getInputStream()) {
          Matcher m =
              CREATED_RELATION.matcher(
                  withoutComments(
                      new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
          while (m.find()) {
            relations.add(m.group(1).toLowerCase(Locale.ROOT));
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (relations.isEmpty()) {
      throw new IllegalStateException("A8b found no table in classpath*:db/migration/**/*.sql");
    }
    schemaRelations = Set.copyOf(relations);
    return schemaRelations;
  }

  /** Todos os textos constantes da clase (pool de constantes), sen filtrar. */
  static List<String> stringConstants(JavaClass item) {
    ClassModel model = classModel(item);
    if (model == null) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (PoolEntry entry : model.constantPool()) {
      if (entry instanceof StringEntry s) {
        result.add(s.stringValue());
      }
    }
    return result;
  }

  private static boolean allowed(String table) {
    return TenancyAllowlist.TABLES.contains(table);
  }

  private static Optional<Class<?>> loadType(String name) {
    try {
      return Optional.of(
          Class.forName(name, false, Thread.currentThread().getContextClassLoader()));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Inmutabilidade das migracións (ADR-0010): compara {@code db/migration} co rexistro {@code
 * db/migrations.lock} (SHA-256 e nome). Unha base nova sempre migra ben; o que pararía o despregue
 * dun concello xa instalado é unha migración aplicada que cambiou. Este rexistro fai de historial
 * dese concello no repositorio.
 */
public final class MigrationLock {

  public static final Path DIR = Path.of("src/main/resources/db/migration");
  public static final Path LOCK = Path.of("src/main/resources/db/migrations.lock");
  private static final Pattern LINE = Pattern.compile("^([0-9a-f]{64})  (V(\\d+)__\\S+\\.sql)$");
  private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");

  private MigrationLock() {}

  /** Lista baleira se o rexistro e as migracións coinciden. */
  public static List<String> problems(Path dir, Path lock) {
    List<String> problems = new ArrayList<>();
    Map<String, String> locked = new LinkedHashMap<>();
    String previous = null;
    for (String line : read(lock)) {
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      Matcher m = LINE.matcher(line);
      if (!m.matches()) {
        problems.add("malformed line in migrations.lock: " + line);
        continue;
      }
      if (locked.put(m.group(2), m.group(1)) != null) {
        problems.add(m.group(2) + " is twice in migrations.lock");
      }
      if (previous != null && Long.parseLong(m.group(3)) <= Long.parseLong(previous)) {
        problems.add(m.group(2) + " is out of order in migrations.lock");
      }
      previous = m.group(3);
    }
    Map<String, String> actual = new TreeMap<>();
    try (Stream<Path> files = Files.list(dir)) {
      files
          .filter(f -> VERSION.matcher(f.getFileName().toString()).matches())
          .forEach(f -> actual.put(f.getFileName().toString(), sha256(f)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    locked.forEach(
        (name, sha) -> {
          String now = actual.get(name);
          if (now == null) {
            problems.add(name + " was applied (migrations.lock) but no longer exists");
          } else if (!now.equals(sha)) {
            problems.add(
                name
                    + " was applied (migrations.lock) and has been edited: an applied migration"
                    + " is never changed; revert it and add a new one");
          }
        });
    actual.keySet().stream()
        .filter(name -> !locked.containsKey(name))
        .forEach(
            name ->
                problems.add(name + " is not in migrations.lock: run scripts/migracions.sh fixar"));
    return problems;
  }

  /** Nomes rexistrados, na orde do rexistro. */
  public static List<String> lockedScripts(Path lock) {
    return read(lock).stream()
        .map(LINE::matcher)
        .filter(Matcher::matches)
        .map(m -> m.group(2))
        .toList();
  }

  private static List<String> read(Path lock) {
    try {
      return Files.readAllLines(lock);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String sha256(Path file) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

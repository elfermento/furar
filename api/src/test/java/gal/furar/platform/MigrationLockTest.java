// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.platform;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.MigrationLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** QA8: unha migración aplicada que se edita, borra ou non se rexistra fai fallar o build. */
class MigrationLockTest {

  @TempDir Path tmp;
  Path dir;
  Path lock;

  @Test
  void theRepositoryMatchesItsLock() {
    assertThat(MigrationLock.problems(MigrationLock.DIR, MigrationLock.LOCK)).isEmpty();
  }

  @BeforeEach
  void copy() throws IOException {
    dir = Files.createDirectories(tmp.resolve("migration"));
    try (var files = Files.list(MigrationLock.DIR)) {
      for (Path f : files.toList()) {
        Files.copy(f, dir.resolve(f.getFileName()));
      }
    }
    lock = Files.copy(MigrationLock.LOCK, tmp.resolve("migrations.lock"));
  }

  @Test
  void editingAnAppliedMigrationFails() throws IOException {
    Files.writeString(
        dir.resolve("V202610220900__municipality_create.sql"),
        "\ncreate index qa on municipality (name);\n",
        StandardOpenOption.APPEND);
    assertThat(MigrationLock.problems(dir, lock))
        .singleElement()
        .asString()
        .contains("V202610220900__municipality_create.sql was applied")
        .contains("has been edited");
  }

  @Test
  void deletingAnAppliedMigrationFails() throws IOException {
    Files.delete(dir.resolve("V202610220910__customfields_create.sql"));
    assertThat(MigrationLock.problems(dir, lock))
        .singleElement()
        .asString()
        .contains("no longer exists");
  }

  @Test
  void anUnregisteredMigrationFails() throws IOException {
    Files.writeString(dir.resolve("V209912312359__qa_new.sql"), "select 1;\n");
    assertThat(MigrationLock.problems(dir, lock))
        .singleElement()
        .asString()
        .contains("V209912312359__qa_new.sql is not in migrations.lock");
  }

  @Test
  void reorderedOrDuplicatedLinesFail() throws IOException {
    var lines = Files.readAllLines(lock);
    lines.add(lines.stream().filter(l -> l.contains("V202610220850")).findFirst().orElseThrow());
    Files.write(lock, lines);
    assertThat(MigrationLock.problems(dir, lock))
        .anyMatch(p -> p.contains("twice"))
        .anyMatch(p -> p.contains("out of order"));
  }
}

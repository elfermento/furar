// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.drawing;

import gal.furar.facility.application.port.out.DrawingFiles;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Debuxos de Furar no classpath ({@code drawings/{code}.jpg}, CC0, anotados en REUSE.toml). Só
 * códigos coma os da táboa {@code drawing}: nada de rutas.
 */
@Component
class ClasspathDrawingFiles implements DrawingFiles {

  static final String FOLDER = "drawings/";
  private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_]{1,39}$");

  @Override
  public Optional<byte[]> read(String code) {
    if (!CODE.matcher(code).matches()) {
      return Optional.empty();
    }
    try (InputStream in =
        ClasspathDrawingFiles.class.getClassLoader().getResourceAsStream(FOLDER + code + ".jpg")) {
      return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

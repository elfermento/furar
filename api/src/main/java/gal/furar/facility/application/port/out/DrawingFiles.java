// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import java.util.Optional;

/** Bytes orixinais dun debuxo de Furar (JPEG), se existe o ficheiro. */
public interface DrawingFiles {

  Optional<byte[]> read(String code);
}

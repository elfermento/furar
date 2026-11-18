// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.portability;

import java.util.List;
import java.util.Map;

/**
 * Sección dun módulo no ficheiro de portabilidade {@code furar-export} (ADR-0018). Cada módulo con
 * datos dun concello publica unha como bean; a orde dos beans ({@code @Order}) é a orde de
 * importación (primeiro as seccións das que dependen as outras).
 *
 * <p>As dúas operacións traballan sempre no concello do {@code TenantContext}: na exportación, o
 * concello exportado; na importación, o concello novo, xa creado e na mesma transacción.
 *
 * @param <T> tipo dunha fila: un DTO do contrato (aparece no OpenAPI como esquema da sección)
 */
public interface MunicipalityDataSection<T> {

  /** Clave da sección no JSON (nome do módulo: {@code customfields}, {@code facility}…). */
  String key();

  /** Tipo das filas, para ler o JSON da importación e xerar o esquema. */
  Class<T> rowType();

  /** Todas as filas do concello actual, tamén as arquivadas, nunha orde estable. */
  List<T> exportRows();

  /** Carga as filas no concello actual conservando ids e datas; un id xa usado → 409. */
  void importRows(List<T> rows);

  /**
   * Pasa unha fila dun ficheiro dunha {@code version} anterior á forma actual, antes de lela como
   * {@link #rowType()}. Só a sobrescribe a sección cuxa fila cambiou ao subir a versión do formato
   * (ADR-0018: o importador le todas as versións anteriores); por defecto a fila queda igual.
   *
   * @param version versión do ficheiro, menor ca a actual
   * @param row a fila tal como vén no JSON
   */
  default Map<String, Object> upgrade(int version, Map<String, Object> row) {
    return row;
  }
}

// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Que cambiou nunha escritura de configuración, para os detalles do rexistro (ADR-0020, revisión do
 * 2026-11-10): {@code changes: [{field, before, after}]}. Só valores curtos e sen datos persoais
 * (datas, horas, prezos, nomes de instalacións e de tempadas, estados, regras). Un campo que pode
 * levar datos persoais ou un texto longo (teléfonos, correos, campos propios, descricións) vai
 * {@linkplain #hidden oculto}: {@code {field, hidden: true}}, sen valores, e a web di só «cambiou o
 * teléfono».
 *
 * <p>Uso: unha {@link Snapshot} antes e outra despois, e {@link #compare}; ou {@link #value} e
 * {@link #hidden} campo a campo. Non é segura entre fíos: unha por escritura.
 */
public final class AuditChanges {

  /** Clave dos detalles. */
  public static final String KEY = "changes";

  /** Caracteres como moito dun valor: o resto córtase cun «…». */
  public static final int VALUE_MAX = 80;

  /** Cambios como moito nun evento: os demais xúntanse nun {@value #MORE}. */
  public static final int ITEMS_MAX = 30;

  /** Campo que di que houbo máis cambios dos que se gardan. */
  public static final String MORE = "more";

  private final List<Map<String, Object>> items = new ArrayList<>();
  private final Set<String> seen = new LinkedHashSet<>();
  private int dropped;

  /** Unha foto dos campos dun obxecto: os valores curtos e os ocultos, por orde. */
  public static Snapshot snapshot() {
    return new Snapshot();
  }

  /** Engade os campos que cambian entre dúas fotos (os da segunda que non estaban, tamén). */
  public AuditChanges compare(Snapshot before, Snapshot after) {
    Set<String> fields = new LinkedHashSet<>(before.values.keySet());
    fields.addAll(after.values.keySet());
    for (String field : fields) {
      Object a = before.values.get(field);
      Object b = after.values.get(field);
      if (before.hidden.contains(field) || after.hidden.contains(field)) {
        hidden(field, a, b);
      } else {
        value(field, a, b);
      }
    }
    return this;
  }

  /** O campo cambiou de {@code before} a {@code after}: gárdanse os dous valores, curtos. */
  public AuditChanges value(String field, @Nullable Object before, @Nullable Object after) {
    String a = text(before);
    String b = text(after);
    if (Objects.equals(a, b)) {
      return this;
    }
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("field", field);
    if (a != null) {
      item.put("before", a);
    }
    if (b != null) {
      item.put("after", b);
    }
    return add(field, item);
  }

  /** O campo cambiou, pero os valores non se gardan (dato persoal ou texto longo). */
  public AuditChanges hidden(String field, @Nullable Object before, @Nullable Object after) {
    if (Objects.equals(blankToNull(before), blankToNull(after))) {
      return this;
    }
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("field", field);
    item.put("hidden", true);
    return add(field, item);
  }

  public boolean isEmpty() {
    return items.isEmpty() && dropped == 0;
  }

  /** Os cambios tal como van nos detalles. */
  public List<Map<String, Object>> list() {
    List<Map<String, Object>> list = new ArrayList<>(items);
    if (dropped > 0) {
      list.add(Map.of("field", MORE, "after", String.valueOf(dropped)));
    }
    return List.copyOf(list);
  }

  /** Pon os cambios nos detalles, se os hai. */
  public Map<String, Object> into(Map<String, Object> details) {
    if (!isEmpty()) {
      details.put(KEY, list());
    }
    return details;
  }

  private AuditChanges add(String field, Map<String, Object> item) {
    if (!seen.add(field)) {
      return this;
    }
    if (items.size() >= ITEMS_MAX) {
      dropped++;
    } else {
      items.add(Map.copyOf(item));
    }
    return this;
  }

  /** Unha hora do día en minutos como «09:30» (1440 = «24:00»). */
  public static String clock(int minute) {
    return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
  }

  /** Un tramo como «09:00–21:00». */
  public static String range(int startMinute, int endMinute) {
    return clock(startMinute) + "–" + clock(endMinute);
  }

  /** Un importe en céntimos como «12,00 €» (igual en galego e en castelán). */
  public static @Nullable String money(@Nullable Integer cents) {
    if (cents == null) {
      return null;
    }
    return String.format(Locale.ROOT, "%d,%02d €", cents / 100, cents % 100);
  }

  private static @Nullable Object blankToNull(@Nullable Object value) {
    return value instanceof String s && s.isBlank() ? null : value;
  }

  private static @Nullable String text(@Nullable Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString().strip();
    if (text.isEmpty()) {
      return null;
    }
    return text.length() > VALUE_MAX ? text.substring(0, VALUE_MAX - 1) + "…" : text;
  }

  /** Os campos dun obxecto nun momento. */
  public static final class Snapshot {

    private final Map<String, @Nullable Object> values = new LinkedHashMap<>();
    private final Set<String> hidden = new LinkedHashSet<>();

    private Snapshot() {}

    /** Un valor curto e sen datos persoais. */
    public Snapshot value(String field, @Nullable Object value) {
      values.put(field, value);
      return this;
    }

    /** Un valor que se compara pero nunca se garda. */
    public Snapshot hidden(String field, @Nullable Object value) {
      values.put(field, value);
      hidden.add(field);
      return this;
    }
  }
}

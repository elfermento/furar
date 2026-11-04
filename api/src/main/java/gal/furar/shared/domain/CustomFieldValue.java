// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Valor dun campo personalizado, xa validado contra a súa definición. */
public sealed interface CustomFieldValue {

  /** Valor en forma JSON: texto, número, {@code "AAAA-MM-DD"}, booleano ou lista de códigos. */
  Object toJson();

  record Text(String value) implements CustomFieldValue {
    public Text {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public Object toJson() {
      return value;
    }
  }

  record Number(BigDecimal value) implements CustomFieldValue {
    public Number {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public Object toJson() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Number n && n.value.compareTo(value) == 0;
    }

    @Override
    public int hashCode() {
      return value.stripTrailingZeros().hashCode();
    }
  }

  record Date(LocalDate value) implements CustomFieldValue {
    public Date {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public Object toJson() {
      return value.toString();
    }
  }

  record Bool(boolean value) implements CustomFieldValue {
    @Override
    public Object toJson() {
      return value;
    }
  }

  record Choice(String code) implements CustomFieldValue {
    public Choice {
      Objects.requireNonNull(code, "code");
    }

    @Override
    public Object toJson() {
      return code;
    }
  }

  record Choices(List<String> codes) implements CustomFieldValue {
    public Choices {
      codes = List.copyOf(codes);
    }

    @Override
    public Object toJson() {
      return codes;
    }
  }
}

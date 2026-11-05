// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.BookingExportSink;
import gal.furar.booking.application.port.in.BookingFieldColumn;
import gal.furar.booking.application.port.in.ManagedBooking;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.BookingStatus;
import gal.furar.booking.domain.Holder;
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.LocalizedText;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * O CSV das reservas para Excel e LibreOffice en galego ou castelán (o mesmo formato ca os CSV da
 * web): UTF-8 con BOM, {@code ;} entre celas, CRLF ao final de cada fila, comiñas RFC 4180, coma
 * decimal, datas e horas de Europe/Madrid e as cabeceiras e os valores no idioma de quen descarga.
 * Unha fila por reserva; despois das columnas fixas (co teléfono e quen do persoal a fixo, se a
 * fixo o persoal), unha por campo propio. Os textos que unha folla de cálculo tomaría por fórmula
 * ({@code =}, {@code +}, {@code -}, {@code @}, tabulador ou retorno) levan un {@code '} diante
 * (OWASP «CSV Injection»).
 */
final class BookingCsv implements BookingExportSink {

  /**
   * Marca de orde de bytes: Excel só le ben o UTF-8 (acentos, «€») se o ficheiro empeza por ela.
   */
  static final String BOM = "\uFEFF";

  private static final char SEPARATOR = ';';
  private static final String LINE_END = "\r\n";
  private static final Pattern FORMULA_START = Pattern.compile("^[=+\\-@\\t\\r]");
  private static final Pattern NEEDS_QUOTES = Pattern.compile("[\";\\r\\n]");
  private static final DateTimeFormatter DAY =
      DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(BookingRules.ZONE);
  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("HH:mm").withZone(BookingRules.ZONE);
  private static final DateTimeFormatter DAY_TIME =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(BookingRules.ZONE);
  private static final DateTimeFormatter PLAIN_DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final List<String> COLUMNS =
      List.of(
          "date",
          "start",
          "end",
          "facility",
          "use",
          "person",
          "email",
          "phone",
          "status",
          "payment",
          "amount",
          "code",
          "createdAt",
          "bookedBy");
  private static final MessageSource TEXTS = texts();

  private final Writer out;
  private final Language language;
  private final Instant now;
  private List<BookingFieldColumn> fields = List.of();

  BookingCsv(Writer out, Language language, Instant now) {
    this.out = out;
    this.language = language;
    this.now = now;
  }

  @Override
  public void begin(long total, List<BookingFieldColumn> fields) {
    this.fields = List.copyOf(fields);
    List<String> header = new ArrayList<>();
    COLUMNS.forEach(c -> header.add(text(label("column." + c))));
    this.fields.forEach(f -> header.add(text(f.label().in(language))));
    append(BOM);
    row(header);
    flush();
  }

  @Override
  public void write(List<ManagedBooking> bookings) {
    for (ManagedBooking view : bookings) {
      row(cells(view));
    }
    flush();
  }

  private void flush() {
    try {
      out.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<String> cells(ManagedBooking view) {
    Booking b = view.booking();
    Holder holder = b.holder();
    Integer cents = b.price().amountCents();
    List<String> cells = new ArrayList<>();
    cells.add(DAY.format(b.slot().start()));
    cells.add(TIME.format(b.slot().start()));
    cells.add(TIME.format(b.slot().end()));
    cells.add(text(view.facilityName()));
    cells.add(
        text(view.partName() == null ? view.useName() : view.useName() + " · " + view.partName()));
    cells.add(text(holder == null ? label("anonymized") : holder.name()));
    cells.add(text(holder == null ? "" : holder.email()));
    cells.add(text(holder == null ? "" : holder.phone()));
    cells.add(text(label("status." + status(b))));
    cells.add(text(label("payment." + b.paymentStatus().name())));
    cells.add(cents == null ? "" : number(BigDecimal.valueOf(cents, 2)));
    cells.add(text(b.code().value()));
    cells.add(DAY_TIME.format(b.createdAt()));
    cells.add(text(bookedBy(view.bookedBy())));
    for (BookingFieldColumn field : fields) {
      cells.add(view.customFields().get(field.code()).map(v -> value(field, v)).orElse(""));
    }
    return cells;
  }

  /**
   * Quen do persoal a fixo no nome da persoa: o seu nome ou, se a conta non ten nome, o rol;
   * baleiro se a fixo a persoa coa súa conta.
   */
  private @Nullable String bookedBy(StaffAccounts.@Nullable Member member) {
    if (member == null) {
      return null;
    }
    return member.name() != null && !member.name().isBlank()
        ? member.name()
        : label("role." + member.role().name());
  }

  /** «Realizada» non é un estado: confirmada e xa rematada (como na lista). */
  private String status(Booking b) {
    return b.status() == BookingStatus.CONFIRMED && b.isDone(now) ? "DONE" : b.status().name();
  }

  private String value(BookingFieldColumn field, CustomFieldValue value) {
    return switch (value) {
      case CustomFieldValue.Text(String text) -> text(text);
      case CustomFieldValue.Number(BigDecimal number) -> number(number);
      case CustomFieldValue.Date(java.time.LocalDate date) -> PLAIN_DAY.format(date);
      case CustomFieldValue.Bool(boolean yes) -> text(label(yes ? "yes" : "no"));
      case CustomFieldValue.Choice(String code) -> text(option(field, code));
      case CustomFieldValue.Choices(List<String> codes) ->
          text(codes.stream().map(c -> option(field, c)).collect(Collectors.joining(", ")));
    };
  }

  /** O nome da opción; se xa non existe, o seu código. */
  private String option(BookingFieldColumn field, String code) {
    LocalizedText label = field.options().get(code);
    return label == null ? code : label.in(language);
  }

  private String label(String key) {
    return TEXTS.getMessage(key, null, locale());
  }

  private Locale locale() {
    return Locale.of(language.code());
  }

  private void row(List<String> cells) {
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        line.append(SEPARATOR);
      }
      line.append(cells.get(i));
    }
    append(line.append(LINE_END).toString());
  }

  private void append(String value) {
    try {
      out.write(value);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Cela de texto: neutraliza a fórmula cun {@code '} diante e, se fai falta, comiñas. */
  static String text(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    String safe = FORMULA_START.matcher(value).find() ? "'" + value : value;
    return NEEDS_QUOTES.matcher(safe).find() ? '"' + safe.replace("\"", "\"\"") + '"' : safe;
  }

  /** Número sen separador de miles e coa coma decimal; un número non se neutraliza. */
  static String number(BigDecimal value) {
    return value.toPlainString().replace('.', ',');
  }

  private static MessageSource texts() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("csv/bookings");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }
}

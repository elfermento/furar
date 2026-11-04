// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import java.util.List;
import java.util.Objects;

/**
 * Un correo listo para enviar: remitente visible, destinatario, asunto, HTML e texto, imaxes
 * incrustadas ({@code cid:}) e adxuntos.
 *
 * @param fromName nome visible do remitente (o enderezo é o do despregue)
 */
public record OutgoingMail(
    String fromName,
    String to,
    String subject,
    String html,
    String text,
    List<Part> inline,
    List<Part> attachments) {

  public OutgoingMail {
    Objects.requireNonNull(fromName, "fromName");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(html, "html");
    Objects.requireNonNull(text, "text");
    inline = List.copyOf(inline);
    attachments = List.copyOf(attachments);
  }

  /**
   * Unha parte binaria: no HTML, {@code name} é o {@code cid}; nos adxuntos, o nome do ficheiro.
   */
  public record Part(String name, String contentType, byte[] bytes) {

    public Part {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(contentType, "contentType");
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }
}

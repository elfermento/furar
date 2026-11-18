// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a2.application.service;

public class UploadService {
  public long size(org.springframework.web.multipart.MultipartFile file) {
    return file.getSize();
  }
}

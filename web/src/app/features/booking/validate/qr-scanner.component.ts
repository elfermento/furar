// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { QrReader } from './qr-reader';
import { createQrReader } from './qr-reader';
import { isBookingCode, typedCode } from './validate';

/** Cada canto se mira un fotograma. */
const SCAN_EVERY_MS = 200;

/**
 * A cámara de «Validar QR» (ADR-0032, punto 12): o vídeo da cámara de atrás nun cadro
 * e, debaixo, que facer. Cando ve un QR cun código de reserva, emíteo (`scanned`) e deixa de mirar;
 * un QR que non é dunha reserva dío e segue mirando, sen chamar á API. Se non hai cámara ou a
 * persoa nega o permiso, emite `failed`: a páxina queda co código tecleado. Ao saír, apaga a
 * cámara.
 */
@Component({
  selector: 'furar-qr-scanner',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-validate__camera' },
  template: `
    <video
      #video
      class="f-validate__video"
      playsinline
      muted
      [attr.aria-label]="'validate.mode.camera' | transloco"
    ></video>
    <p class="f-validate__hint" role="status">
      {{ (foreign() ? 'validate.camera.notFurar' : 'validate.camera.hint') | transloco }}
    </p>
  `,
})
export class QrScannerComponent {
  /** Un código de reserva lido. */
  readonly scanned = output<string>();
  /** Sen cámara, sen permiso ou sen lector: queda o código tecleado. */
  readonly failed = output();

  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private readonly view = inject(DOCUMENT).defaultView;
  /** O último QR que se viu non era dunha reserva. */
  protected readonly foreign = signal(false);
  private stream: MediaStream | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private stopped = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop());
    afterNextRender(() => void this.start());
  }

  private async start(): Promise<void> {
    const view = this.view;
    try {
      if (!view) throw new Error('no window');
      const stream = await view.navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false,
      });
      if (this.stopped) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      this.stream = stream;
      const video = this.video().nativeElement;
      video.srcObject = stream;
      await video.play();
      const read = await createQrReader(view);
      this.scan(read);
    } catch {
      this.stop();
      this.failed.emit();
    }
  }

  private scan(read: QrReader): void {
    if (this.stopped) return;
    this.timer = setTimeout(() => {
      void read(this.video().nativeElement)
        .catch(() => null)
        .then((text) => {
          if (this.stopped) return;
          if (text !== null) {
            const code = typedCode(text);
            if (isBookingCode(code)) {
              this.stop();
              this.scanned.emit(code);
              return;
            }
            this.foreign.set(true);
          }
          this.scan(read);
        });
    }, SCAN_EVERY_MS);
  }

  private stop(): void {
    this.stopped = true;
    if (this.timer !== null) clearTimeout(this.timer);
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
  }
}

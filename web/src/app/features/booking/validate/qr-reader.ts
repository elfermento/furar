// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Le o texto dun QR nun fotograma do vídeo; `null` se non hai ningún á vista. */
export type QrReader = (video: HTMLVideoElement) => Promise<string | null>;

/** O `BarcodeDetector` do navegador (aínda sen tipos en `lib.dom`). */
interface NativeDetector {
  detect(source: CanvasImageSource): Promise<readonly { rawValue: string }[]>;
}
type NativeDetectorClass = new (options: { formats: string[] }) => NativeDetector;

/**
 * O lector de QR (ADR-0032, punto 12): o `BarcodeDetector` do navegador se o trae e sabe ler QR;
 * se non, un descodificador que se carga só aquí (`jsqr`, fóra da carga inicial e de calquera
 * outra páxina), sobre os píxeles do fotograma. Nada sae do navegador: o servidor só recibe o
 * código.
 */
export async function createQrReader(view: Window & typeof globalThis): Promise<QrReader> {
  const native = await nativeReader(view);
  return native ?? decoderReader(view.document);
}

async function nativeReader(view: Window & typeof globalThis): Promise<QrReader | null> {
  const Detector = (view as { BarcodeDetector?: NativeDetectorClass & { getSupportedFormats?(): Promise<string[]> } })
    .BarcodeDetector;
  if (!Detector) return null;
  try {
    const formats = (await Detector.getSupportedFormats?.()) ?? [];
    if (!formats.includes('qr_code')) return null;
    const detector = new Detector({ formats: ['qr_code'] });
    return async (video) => (await detector.detect(video))[0]?.rawValue ?? null;
  } catch {
    return null;
  }
}

/** Fotogramas reducidos a este lado como moito: de sobra para un QR que enche o cadro. */
const MAX_SIDE = 640;

async function decoderReader(document: Document): Promise<QrReader> {
  const { default: decode } = await import('jsqr');
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d', { willReadFrequently: true });
  return (video) => {
    const { videoWidth: width, videoHeight: height } = video;
    if (!context || !width || !height) return Promise.resolve(null);
    const scale = Math.min(1, MAX_SIDE / Math.max(width, height));
    canvas.width = Math.round(width * scale);
    canvas.height = Math.round(height * scale);
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    const frame = context.getImageData(0, 0, canvas.width, canvas.height);
    return Promise.resolve(decode(frame.data, frame.width, frame.height)?.data ?? null);
  };
}

/**
 * Se nesta pantalla se ofrece a cámara (ADR-0032, punto 12): só no móbil. Hai `getUserMedia`, o
 * punteiro principal é táctil e a pantalla é a estreita da barra inferior.
 */
export function cameraOffered(view: (Window & typeof globalThis) | null | undefined): boolean {
  if (!view?.navigator?.mediaDevices?.getUserMedia || !view.matchMedia) return false;
  return view.matchMedia('(pointer: coarse)').matches && view.matchMedia('(max-width: 899px)').matches;
}

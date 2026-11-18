// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { InstallHintInput } from './install-hint';
import {
  INSTALL_HINT_DISMISSED_KEY,
  deviceTraits,
  installHintMode,
  readDismissed,
  storeDismissed,
} from './install-hint';

const UA = {
  androidChrome:
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36',
  androidFirefox: 'Mozilla/5.0 (Android 14; Mobile; rv:132.0) Gecko/132.0 Firefox/132.0',
  iphoneSafari:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1',
  iphoneChrome:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/130.0.6723.90 Mobile/15E148 Safari/604.1',
  iphoneFirefox:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) FxiOS/132.0 Mobile/15E148 Safari/605.1.15',
  ipadChrome:
    'Mozilla/5.0 (iPad; CPU OS 17_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/130.0.6723.90 Mobile/15E148 Safari/604.1',
  iphoneEdge:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 EdgiOS/130.0.2849.80 Mobile/15E148 Safari/605.1.15',
  // A app de Google: leva «Version/» e «Safari/», pero é unha web dentro doutra app.
  iphoneGoogleApp:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) GSA/340.0.689570226 Mobile/15E148 Safari/604.1',
  iphoneFacebook:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 [FBAN/FBIOS;FBAV/490.0.0.40.107;FBBV/123;FBDV/iPhone15,2;FBMD/iPhone;FBSN/iOS;FBSV/18.1;FBSS/3;FBLC/es_ES]',
  iphoneInstagram:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 350.0.0',
  // Safari de iPad dise Mac: só o delatan os puntos táctiles.
  ipadSafari:
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15',
  macSafari:
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15',
  desktopChrome:
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36',
};

interface FakeWindow {
  readonly ua: string;
  readonly media?: readonly string[];
  readonly platform?: string;
  readonly maxTouchPoints?: number;
  readonly standalone?: boolean;
}

/** Unha xanela de mentira: o UA e as consultas de medios que casan. */
function fakeWindow({ ua, media = [], platform = 'Linux', maxTouchPoints = 0, standalone }: FakeWindow): Window {
  return {
    navigator: { userAgent: ua, platform, maxTouchPoints, standalone },
    matchMedia: (query: string) => ({ matches: media.includes(query) }),
  } as unknown as Window;
}

const COARSE = '(pointer: coarse)';
const NARROW = '(max-width: 47.99rem)';

describe('installHintMode: cando se mostra o aviso para instalar', () => {
  const base: InstallHintInput = {
    enabled: true,
    dismissed: false,
    mobile: true,
    iosBrowser: null,
    standalone: false,
    promptAvailable: false,
  };

  it('Android (Chromium co evento): «android»', () => {
    expect(installHintMode({ ...base, promptAvailable: true })).toBe('android');
  });

  it('Safari de iPhone ou iPad sen o modo app: «ios»', () => {
    expect(installHintMode({ ...base, iosBrowser: 'safari' })).toBe('ios');
  });

  it('Chrome de iPhone ou iPad sen o modo app: «ios-chrome»', () => {
    expect(installHintMode({ ...base, iosBrowser: 'chrome' })).toBe('ios-chrome');
  });

  it('móbil sen evento nin Safari ou Chrome de iOS (Firefox de Android, Edge de iOS…): nada', () => {
    expect(installHintMode(base)).toBeNull();
  });

  it('nunca no escritorio, aínda que Chrome dispare o evento', () => {
    expect(installHintMode({ ...base, mobile: false, promptAvailable: true })).toBeNull();
    expect(installHintMode({ ...base, mobile: false, iosBrowser: 'safari' })).toBeNull();
  });

  it('nunca xa instalada e aberta coma app', () => {
    expect(installHintMode({ ...base, standalone: true, promptAvailable: true })).toBeNull();
    expect(installHintMode({ ...base, standalone: true, iosBrowser: 'safari' })).toBeNull();
  });

  it('nunca se se pechou antes neste navegador', () => {
    expect(installHintMode({ ...base, dismissed: true, promptAvailable: true })).toBeNull();
    expect(installHintMode({ ...base, dismissed: true, iosBrowser: 'safari' })).toBeNull();
  });

  it('nunca se o concello o apagou', () => {
    expect(installHintMode({ ...base, enabled: false, promptAvailable: true })).toBeNull();
    expect(installHintMode({ ...base, enabled: false, iosBrowser: 'safari' })).toBeNull();
  });

  it('todas as combinacións: só con todo a favor, e o evento manda sobre Safari e Chrome de iOS', () => {
    const keys = ['enabled', 'dismissed', 'mobile', 'standalone', 'promptAvailable'] as const;
    const browsers = [null, 'safari', 'chrome'] as const;
    for (const iosBrowser of browsers) {
      for (let bits = 0; bits < 2 ** keys.length; bits++) {
        const input = {
          ...Object.fromEntries(keys.map((key, i) => [key, Boolean(bits & (1 << i))])),
          iosBrowser,
        } as unknown as InstallHintInput;
        const allowed = input.enabled && !input.dismissed && input.mobile && !input.standalone;
        const ios = iosBrowser === 'safari' ? 'ios' : iosBrowser === 'chrome' ? 'ios-chrome' : null;
        const expected = !allowed ? null : input.promptAvailable ? 'android' : ios;
        expect(installHintMode(input), JSON.stringify(input)).toBe(expected);
      }
    }
  });
});

describe('deviceTraits: móbil, navegador de iOS e modo app', () => {
  it('Chrome de Android: móbil, non Safari', () => {
    expect(deviceTraits(fakeWindow({ ua: UA.androidChrome, media: [COARSE] }))).toEqual({
      mobile: true,
      iosBrowser: null,
      standalone: false,
    });
  });

  it('Safari de iPhone: móbil e Safari', () => {
    expect(deviceTraits(fakeWindow({ ua: UA.iphoneSafari, media: [COARSE] }))).toMatchObject({
      mobile: true,
      iosBrowser: 'safari',
    });
  });

  it('Safari de iPad (dise Mac, con puntos táctiles): móbil e Safari', () => {
    expect(
      deviceTraits(fakeWindow({ ua: UA.ipadSafari, media: [COARSE], platform: 'MacIntel', maxTouchPoints: 5 })),
    ).toMatchObject({ mobile: true, iosBrowser: 'safari' });
  });

  it('Chrome de iPhone e de iPad (CriOS): móbil e Chrome', () => {
    for (const ua of [UA.iphoneChrome, UA.ipadChrome]) {
      expect(deviceTraits(fakeWindow({ ua, media: [COARSE] })), ua).toMatchObject({
        mobile: true,
        iosBrowser: 'chrome',
      });
    }
  });

  it('Firefox e Edge de iOS e as webs dentro doutras apps (Instagram, Facebook, a app de Google): ningún', () => {
    for (const ua of [UA.iphoneFirefox, UA.iphoneEdge, UA.iphoneInstagram, UA.iphoneFacebook, UA.iphoneGoogleApp]) {
      expect(deviceTraits(fakeWindow({ ua, media: [COARSE] })).iosBrowser, ua).toBeNull();
    }
  });

  it('CriOS fóra de iOS (un UA trucado no escritorio): nin Chrome de iOS', () => {
    expect(
      deviceTraits(fakeWindow({ ua: UA.iphoneChrome.replace('iPhone; CPU iPhone OS', 'X11; Linux') })).iosBrowser,
    ).toBeNull();
  });

  it('Firefox de Android: móbil, sen Safari (e sen evento: sen aviso)', () => {
    expect(deviceTraits(fakeWindow({ ua: UA.androidFirefox, media: [COARSE] }))).toMatchObject({
      mobile: true,
      iosBrowser: null,
    });
  });

  it('escritorio: nin co rato, nin cunha xanela estreita, nin cunha pantalla táctil ancha', () => {
    expect(deviceTraits(fakeWindow({ ua: UA.desktopChrome })).mobile).toBe(false);
    expect(deviceTraits(fakeWindow({ ua: UA.desktopChrome, media: [NARROW] })).mobile).toBe(false);
    expect(deviceTraits(fakeWindow({ ua: UA.desktopChrome, media: [COARSE] })).mobile).toBe(false);
    expect(deviceTraits(fakeWindow({ ua: UA.macSafari, platform: 'MacIntel' }))).toMatchObject({
      mobile: false,
      iosBrowser: null,
    });
  });

  it('pantalla táctil estreita sen UA de móbil: conta como móbil', () => {
    expect(deviceTraits(fakeWindow({ ua: UA.desktopChrome, media: [COARSE, NARROW] })).mobile).toBe(true);
  });

  it('modo app: display-mode standalone, fullscreen ou minimal-ui, ou navigator.standalone', () => {
    for (const mode of ['standalone', 'fullscreen', 'minimal-ui']) {
      const media = [COARSE, `(display-mode: ${mode})`];
      expect(deviceTraits(fakeWindow({ ua: UA.androidChrome, media })).standalone, mode).toBe(true);
    }
    expect(deviceTraits(fakeWindow({ ua: UA.iphoneSafari, media: [COARSE], standalone: true })).standalone).toBe(true);
    expect(deviceTraits(fakeWindow({ ua: UA.iphoneSafari, media: [COARSE], standalone: false })).standalone).toBe(
      false,
    );
  });

  it('sen xanela (servidor): nada', () => {
    expect(deviceTraits(null)).toEqual({ mobile: false, iosBrowser: null, standalone: false });
  });
});

describe('lembrar que se pechou', () => {
  afterEach(() => localStorage.removeItem(INSTALL_HINT_DISMISSED_KEY));

  it('gárdase no navegador cunha clave estable', () => {
    expect(readDismissed(window)).toBe(false);
    storeDismissed(window);
    expect(localStorage.getItem('furar.installHint.dismissed')).toBe('1');
    expect(readDismissed(window)).toBe(true);
  });

  it('sen almacenamento (modo privado, bloqueado): non falla e conta como non pechado', () => {
    const blocked = {
      get localStorage(): Storage {
        throw new DOMException('denied', 'SecurityError');
      },
    } as unknown as Window;
    expect(readDismissed(blocked)).toBe(false);
    expect(() => storeDismissed(blocked)).not.toThrow();
  });
});

// "Sign in with Google" using Google Identity Services (pop-up flow). Owner: Gavin.
// Google returns an ID token ("credential") to the callback; the servlet verifies it at POST /api/auth/google.
// No client secret and no redirect URI are used (Deployment doc > Google Sign-In).
import { useEffect, useRef, useState } from 'react';

const GSI_SRC = 'https://accounts.google.com/gsi/client';

interface GoogleCredentialResponse {
  credential: string;
}

interface GoogleAccountsId {
  initialize(config: { client_id: string; callback: (r: GoogleCredentialResponse) => void; ux_mode?: 'popup' }): void;
  renderButton(el: HTMLElement, options: Record<string, string | number>): void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleAccountsId } };
  }
}

let scriptPromise: Promise<void> | null = null;

function loadGsi(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = GSI_SRC;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => {
        scriptPromise = null;
        reject(new Error('Could not load Google sign-in'));
      };
      document.head.appendChild(script);
    });
  }
  return scriptPromise;
}

export function GoogleSignInButton({ onCredential }: { onCredential: (credential: string) => void }) {
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
  const ref = useRef<HTMLDivElement>(null);
  const callbackRef = useRef(onCredential);
  const [failed, setFailed] = useState(false);
  callbackRef.current = onCredential;

  useEffect(() => {
    if (!clientId) return;
    let cancelled = false;
    loadGsi()
      .then(() => {
        if (cancelled || !ref.current || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: clientId,
          ux_mode: 'popup',
          callback: (r) => callbackRef.current(r.credential),
        });
        window.google.accounts.id.renderButton(ref.current, { theme: 'outline', size: 'large', text: 'signin_with', width: 280 });
      })
      .catch(() => !cancelled && setFailed(true));
    return () => {
      cancelled = true;
    };
  }, [clientId]);

  // Hidden entirely when VITE_GOOGLE_CLIENT_ID is not set (e.g. a fresh local checkout).
  if (!clientId) return null;
  if (failed) return <p className="muted">Google sign-in is unavailable right now.</p>;
  return <div ref={ref} data-testid="google-signin" />;
}

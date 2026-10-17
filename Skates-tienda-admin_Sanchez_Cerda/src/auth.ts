import { Amplify } from "aws-amplify";
import { fetchAuthSession, signInWithRedirect, signOut } from "aws-amplify/auth";
import { Hub } from "aws-amplify/utils";
import { useEffect, useState } from "react";
import { config } from "./config";

Amplify.configure({
  Auth: {
    Cognito: {
      userPoolId: config.cognitoUserPoolId,
      userPoolClientId: config.cognitoClientId,
      loginWith: {
        oauth: {
          domain: new URL(config.cognitoDomain).hostname,
          scopes: ["openid", "email", "skate-api/read", "skate-api/write"],
          redirectSignIn: [config.cognitoRedirectUri],
          redirectSignOut: [config.cognitoLogoutUri],
          responseType: "code",
        },
      },
    },
  },
});

export async function obtenerAccessToken(): Promise<string | null> {
  const { tokens } = await fetchAuthSession();
  return tokens?.accessToken?.toString() ?? null;
}

export function tieneGrupoAdministrador(accessToken?: string | null): boolean {
  if (!accessToken) return false;

  try {
    const payload = accessToken.split(".")[1];
    if (!payload) return false;
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(
      atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "=")),
    ) as { exp?: number; "cognito:groups"?: unknown };
    const groups = claims["cognito:groups"];
    return Boolean(
      claims.exp
      && claims.exp > Math.floor(Date.now() / 1000)
      && Array.isArray(groups)
      && groups.includes("ADMIN"),
    );
  } catch {
    return false;
  }
}

export async function iniciarSesion(): Promise<void> {
  await signInWithRedirect();
}

export async function cerrarSesion(): Promise<void> {
  await signOut({ global: true });
}

export function useCognitoAuth() {
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let active = true;

    const refreshSession = async () => {
      try {
        const token = await obtenerAccessToken();
        if (active) {
          setAccessToken(token);
          setError(null);
        }
      } catch (cause) {
        if (active) {
          const sessionError = cause instanceof Error ? cause : new Error(String(cause));
          if (sessionError.name === "UserUnAuthenticatedException") {
            setAccessToken(null);
            setError(null);
          } else {
            setError(sessionError);
          }
        }
      } finally {
        if (active) setIsLoading(false);
      }
    };

    void refreshSession();
    const cancelHubListener = Hub.listen("auth", ({ payload }) => {
      if (["signedIn", "signedOut", "signInWithRedirect", "tokenRefresh", "tokenRefresh_failure"].includes(payload.event)) {
        void refreshSession();
      }
    });

    return () => {
      active = false;
      cancelHubListener();
    };
  }, []);

  return {
    accessToken,
    isAuthenticated: accessToken !== null,
    isLoading,
    error,
    iniciarSesion,
    cerrarSesion,
  };
}

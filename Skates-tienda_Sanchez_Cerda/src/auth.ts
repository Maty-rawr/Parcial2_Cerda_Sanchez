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
          scopes: ["openid", "email", "skate-api/read", "skate-api/purchase"],
          redirectSignIn: [config.cognitoRedirectUri],
          redirectSignOut: [config.cognitoLogoutUri],
          responseType: "code",
        },
      },
    },
  },
});

let accessTokenCache: string | null = null;

export function obtenerAccessToken(): string | null {
  return accessTokenCache;
}

export async function obtenerAccessTokenDeSesion(): Promise<string | null> {
  const { tokens } = await fetchAuthSession();
  return tokens?.accessToken?.toString() ?? null;
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
        const token = await obtenerAccessTokenDeSesion();
        if (active) {
          accessTokenCache = token;
          setAccessToken(token);
          setError(null);
        }
      } catch (cause) {
        if (active) {
          const sessionError = cause instanceof Error ? cause : new Error(String(cause));
          if (sessionError.name === "UserUnAuthenticatedException") {
            accessTokenCache = null;
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

export const config = {
  apiUrl: import.meta.env.MODE === "ngrok"
    ? ""
    : (import.meta.env.VITE_API_URL as string | undefined)
      ?? (import.meta.env.DEV ? "http://localhost:8080" : ""),
  cognitoUserPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID ?? "us-east-1_AOH2j7aTl",
  cognitoDomain: import.meta.env.VITE_COGNITO_DOMAIN
    ?? "https://us-east-1aoh2j7atl.auth.us-east-1.amazoncognito.com",
  cognitoClientId: import.meta.env.VITE_COGNITO_CLIENT_ID ?? "2ndve0vjmlau9as43sapcmermb",
  cognitoRedirectUri: import.meta.env.VITE_COGNITO_REDIRECT_URI ?? `${window.location.origin}/`,
  cognitoLogoutUri: import.meta.env.VITE_COGNITO_LOGOUT_URI ?? `${window.location.origin}/`,
};
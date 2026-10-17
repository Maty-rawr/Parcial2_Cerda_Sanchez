export const config = {
  apiUrl: import.meta.env.MODE === "ngrok"
    ? ""
    : (import.meta.env.VITE_API_URL as string | undefined) ?? "http://localhost:8080",
  cognitoUserPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID ?? "us-east-1_AOH2j7aTl",
  cognitoDomain: import.meta.env.VITE_COGNITO_DOMAIN
    ?? "https://us-east-1aoh2j7atl.auth.us-east-1.amazoncognito.com",
  cognitoClientId: import.meta.env.VITE_COGNITO_CLIENT_ID ?? "7vge2gthmumgohfn84aepg41qh",
  cognitoRedirectUri: import.meta.env.VITE_COGNITO_REDIRECT_URI ?? "https://localhost:5173/",
  cognitoLogoutUri: import.meta.env.VITE_COGNITO_LOGOUT_URI ?? "https://localhost:5173/",
};
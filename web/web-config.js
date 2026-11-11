// Configuración por defecto cuando la Web App se despliega como sitio estático independiente (Vercel, Netlify, GitHub Pages, Render Static).
// Si se sirve desde el propio EchoStock-backend, el endpoint dinámico /web-config.js inyecta automáticamente la clave activa del servidor.
window.__ECHOSTOCK_SERVER_CONFIG__ = window.__ECHOSTOCK_SERVER_CONFIG__ || {
  apiBaseUrl: "http://localhost:8080/api/v1",
  apiKey: "echostock-dev-key",
  operatorCode: "OP-07"
};

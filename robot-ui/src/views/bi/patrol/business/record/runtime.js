// import { isEiopMockAuthEnabled } from "../auth/devAuth";
function isEiopMockAuthEnabled() {
  return true;
}

const defaultWsProtocol = window.location.protocol === "https:" ? "wss" : "ws";
const gatewayApiBaseUrl = import.meta.env.VITE_API_BASE_URL || "/api-gw";
const directApiBaseUrl = "/";
const defaultApiBaseUrl = isEiopMockAuthEnabled() ? directApiBaseUrl : gatewayApiBaseUrl;
const defaultControlWebSocketUrl = isEiopMockAuthEnabled()
  ? `${defaultWsProtocol}://${window.location.host}/ws/control`
  : `${defaultWsProtocol}://${window.location.host}/api-gw/ws/control`;

export const runtimeConfig = {
  managementApiBaseUrl:
    import.meta.env.VITE_EIOP_MANAGEMENT_API_BASE_URL || defaultApiBaseUrl,
  controlApiBaseUrl: import.meta.env.VITE_EIOP_CONTROL_API_BASE_URL || defaultApiBaseUrl,
  controlWebSocketUrl:
    import.meta.env.VITE_EIOP_CONTROL_WS_URL ||
    import.meta.env.VITE_CONTROL_WS_URL ||
    defaultControlWebSocketUrl,
  mediaPlaybackUrlTemplate: import.meta.env.VITE_EIOP_MEDIA_PLAYBACK_URL_TEMPLATE || ""
};

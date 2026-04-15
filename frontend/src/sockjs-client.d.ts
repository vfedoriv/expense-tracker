declare module 'sockjs-client/dist/sockjs' {
  const SockJS: {
    new (url: string, _reserved?: unknown, options?: Record<string, unknown>): WebSocket;
  };
  export default SockJS;
}

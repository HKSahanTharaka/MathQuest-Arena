import { useEffect, useState } from 'react';
import websocket from '../services/websocket';

export const useWebSocket = (event, callback) => {
  useEffect(() => {
    websocket.on(event, callback);
    return () => websocket.off(event, callback);
  }, [event, callback]);
};

export const useWebSocketConnection = () => {
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const handleConnect = () => setConnected(true);
    const handleDisconnect = () => setConnected(false);

    websocket.on('connected', handleConnect);
    websocket.on('disconnected', handleDisconnect);

    return () => {
      websocket.off('connected', handleConnect);
      websocket.off('disconnected', handleDisconnect);
    };
  }, []);

  return connected;
};


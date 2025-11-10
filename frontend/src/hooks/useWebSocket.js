import { useEffect, useState } from 'react';
import websocket from '../services/websocket';

export const useWebSocket = (event, callback) => {
  useEffect(() => {
    websocket.on(event, callback);
    return () => websocket.off(event, callback);
  }, [event, callback]);
};

export const useWebSocketConnection = () => {
  const [connectionState, setConnectionState] = useState('disconnected'); // 'disconnected', 'connecting', 'connected'

  useEffect(() => {
    // Check initial connection state
    if (websocket.isConnected()) {
      setConnectionState('connected');
    } else {
      setConnectionState(websocket.getConnectionState());
    }

    const handleConnect = () => {
      setConnectionState('connected');
    };
    
    const handleConnecting = () => {
      setConnectionState('connecting');
    };
    
    const handleDisconnect = () => {
      setConnectionState('disconnected');
    };

    websocket.on('connected', handleConnect);
    websocket.on('connecting', handleConnecting);
    websocket.on('disconnected', handleDisconnect);

    return () => {
      websocket.off('connected', handleConnect);
      websocket.off('connecting', handleConnecting);
      websocket.off('disconnected', handleDisconnect);
    };
  }, []);

  return {
    connected: connectionState === 'connected',
    connecting: connectionState === 'connecting',
    disconnected: connectionState === 'disconnected',
    state: connectionState
  };
};


class WebSocketService {
  constructor() {
    this.ws = null;
    // Connect directly to WebSocket server (no /game path needed)
    this.url = 'ws://localhost:8082';
    this.reconnectDelay = 3000;
    this.listeners = new Map();
    this.isConnecting = false;
    this.connectionState = 'disconnected'; // 'disconnected', 'connecting', 'connected'
    this.currentToken = null; // Store token for reconnection
  }

  connect(token) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      console.log('✅ WebSocket already connected');
      this.connectionState = 'connected';
      this.emit('connected', {});
      return Promise.resolve();
    }

    if (this.isConnecting) {
      console.log('⏳ WebSocket connection already in progress');
      return Promise.resolve();
    }

    this.isConnecting = true;
    this.connectionState = 'connecting';
    this.currentToken = token; // Store token for reconnection
    this.emit('connecting', {});

    return new Promise((resolve, reject) => {
      try {
        // Connect directly to WebSocket server (no path needed)
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
          console.log('✅ WebSocket connected to server');
          this.isConnecting = false;
          this.connectionState = 'connected';
          this.emit('connected', {});
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            console.log('📨 WebSocket message:', data);
            this.emit(data.type, data.payload);
          } catch (error) {
            console.error('❌ Error parsing WebSocket message:', error);
          }
        };

        this.ws.onerror = (error) => {
          console.error('❌ WebSocket error:', error);
          this.isConnecting = false;
          this.connectionState = 'disconnected';
          this.emit('error', error);
          // Don't reject immediately, let onclose handle reconnection
        };

        this.ws.onclose = (event) => {
          console.log('🔌 WebSocket disconnected', event.code, event.reason || '');
          this.isConnecting = false;
          this.connectionState = 'disconnected';
          this.emit('disconnected', {});
          // Only reconnect if not a manual disconnect (ws is not null) and we have a token
          if (this.ws !== null && this.currentToken) {
            this.reconnect();
          }
        };
      } catch (error) {
        console.error('❌ Failed to create WebSocket:', error);
        this.isConnecting = false;
        this.connectionState = 'disconnected';
        this.emit('disconnected', {});
        reject(error);
      }
    });
  }

  reconnect() {
    if (!this.currentToken) {
      console.log('⚠️  Cannot reconnect: No token available');
      return;
    }
    setTimeout(() => {
      console.log('🔄 Attempting to reconnect WebSocket...');
      this.connect(this.currentToken);
    }, this.reconnectDelay);
  }

  send(type, payload) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type, payload }));
    } else {
      console.error('WebSocket is not connected');
    }
  }

  on(event, callback) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, []);
    }
    this.listeners.get(event).push(callback);
  }

  off(event, callback) {
    if (this.listeners.has(event)) {
      const callbacks = this.listeners.get(event);
      const index = callbacks.indexOf(callback);
      if (index > -1) {
        callbacks.splice(index, 1);
      }
    }
  }

  emit(event, data) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).forEach(callback => callback(data));
    }
  }

  disconnect() {
    if (this.ws) {
      this.connectionState = 'disconnected';
      this.currentToken = null; // Clear token on manual disconnect
      this.ws.close();
      this.ws = null;
      this.emit('disconnected', {});
    }
  }
  
  getConnectionState() {
    return this.connectionState;
  }
  
  isConnected() {
    return this.ws && this.ws.readyState === WebSocket.OPEN;
  }
}

export default new WebSocketService();


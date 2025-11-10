import { useState, useEffect, useRef } from 'react';
import { Send, X, Lock } from 'lucide-react';
import { useWebSocket } from '../hooks/useWebSocket';
import websocket from '../services/websocket';
import { useAuth } from '../contexts/AuthContext';
import api from '../services/api';

const Chat = ({ isOpen, onClose }) => {
  const [messages, setMessages] = useState([]);
  const [newMessage, setNewMessage] = useState('');
  const [gameReady, setGameReady] = useState(false);
  const [chatError, setChatError] = useState('');
  const messagesEndRef = useRef(null);
  const { user } = useAuth();

  // Listen for chat messages
  useWebSocket('chat', (data) => {
    setMessages(prev => [...prev, {
      username: data.username,
      message: data.message,
      timestamp: new Date(data.timestamp)
    }]);
  });

  // Listen for chat errors
  useWebSocket('chat_error', (data) => {
    setChatError(data.message || 'Chat is disabled');
    // Clear error after 5 seconds
    setTimeout(() => setChatError(''), 5000);
  });

  // Listen for game status updates
  useWebSocket('game_status', (data) => {
    setGameReady(data.gameReady || false);
  });

  // Fetch initial game status
  useEffect(() => {
    const fetchGameStatus = async () => {
      try {
        const status = await api.getGameStatus();
        setGameReady(status.gameReady || false);
      } catch (error) {
        console.error('Failed to fetch game status:', error);
      }
    };
    
    if (isOpen) {
      fetchGameStatus();
      // Poll game status every 5 seconds while chat is open
      const interval = setInterval(fetchGameStatus, 5000);
      return () => clearInterval(interval);
    }
  }, [isOpen]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = (e) => {
    e.preventDefault();
    
    // Block sending if game is ready
    if (gameReady) {
      setChatError('Chat is disabled after the game has started. Chat is only available while waiting for players.');
      setTimeout(() => setChatError(''), 5000);
      return;
    }
    
    if (newMessage.trim()) {
      websocket.send('chat', {
        username: user.username,
        message: newMessage,
        timestamp: Date.now()
      });
      setNewMessage('');
      setChatError(''); // Clear any previous errors
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed right-4 bottom-4 w-96 h-[500px] card shadow-2xl flex flex-col z-50">
      <div className="flex items-center justify-between mb-4 pb-3 border-b border-gray-200 dark:border-dark-200">
        <div className="flex items-center space-x-2">
          <h3 className="text-lg font-bold text-gray-900 dark:text-gray-100">Live Chat</h3>
          {gameReady && (
            <div className="flex items-center space-x-1 text-xs text-yellow-600 dark:text-yellow-400">
              <Lock className="w-4 h-4" />
              <span>Disabled</span>
            </div>
          )}
        </div>
        <button
          onClick={onClose}
          className="p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-dark-200 transition-all"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {gameReady && (
        <div className="mb-4 p-3 rounded-lg bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800">
          <p className="text-sm text-yellow-800 dark:text-yellow-200">
            🔒 Chat is disabled after the game has started. Chat is only available while waiting for players.
          </p>
        </div>
      )}

      {chatError && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
          <p className="text-sm text-red-800 dark:text-red-200">{chatError}</p>
        </div>
      )}

      <div className="flex-1 overflow-y-auto space-y-3 mb-4">
        {messages.map((msg, index) => (
          <div key={index} className={`flex flex-col ${msg.username === user.username ? 'items-end' : 'items-start'}`}>
            <div className={`max-w-[80%] rounded-lg px-3 py-2 ${
              msg.username === user.username
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 dark:bg-dark-200 text-gray-900 dark:text-gray-100'
            }`}>
              <div className="text-xs font-semibold mb-1 opacity-75">
                {msg.username}
              </div>
              <div className="text-sm">{msg.message}</div>
            </div>
            <div className="text-xs text-gray-500 mt-1 px-2">
              {msg.timestamp.toLocaleTimeString()}
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <form onSubmit={handleSend} className="flex space-x-2">
        <input
          type="text"
          value={newMessage}
          onChange={(e) => setNewMessage(e.target.value)}
          placeholder={gameReady ? "Chat disabled - game has started" : "Type a message..."}
          className="input-field flex-1"
          disabled={gameReady}
        />
        <button 
          type="submit" 
          className="btn-primary px-3 disabled:opacity-50 disabled:cursor-not-allowed"
          disabled={gameReady}
        >
          <Send className="w-5 h-5" />
        </button>
      </form>
    </div>
  );
};

export default Chat;


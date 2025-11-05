import { useState, useEffect, useRef } from 'react';
import { Send, X } from 'lucide-react';
import { useWebSocket } from '../hooks/useWebSocket';
import websocket from '../services/websocket';
import { useAuth } from '../contexts/AuthContext';

const Chat = ({ isOpen, onClose }) => {
  const [messages, setMessages] = useState([]);
  const [newMessage, setNewMessage] = useState('');
  const messagesEndRef = useRef(null);
  const { user } = useAuth();

  useWebSocket('chat', (data) => {
    setMessages(prev => [...prev, {
      username: data.username,
      message: data.message,
      timestamp: new Date(data.timestamp)
    }]);
  });

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = (e) => {
    e.preventDefault();
    if (newMessage.trim()) {
      websocket.send('chat', {
        username: user.username,
        message: newMessage,
        timestamp: Date.now()
      });
      setNewMessage('');
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed right-4 bottom-4 w-96 h-[500px] card shadow-2xl flex flex-col z-50">
      <div className="flex items-center justify-between mb-4 pb-3 border-b border-gray-200 dark:border-dark-200">
        <h3 className="text-lg font-bold text-gray-900 dark:text-gray-100">Live Chat</h3>
        <button
          onClick={onClose}
          className="p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-dark-200 transition-all"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

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
          placeholder="Type a message..."
          className="input-field flex-1"
        />
        <button type="submit" className="btn-primary px-3">
          <Send className="w-5 h-5" />
        </button>
      </form>
    </div>
  );
};

export default Chat;


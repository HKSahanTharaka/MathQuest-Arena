# 🧮 MathQuest Arena Frontend

Modern, responsive React + Vite frontend for the MathQuest Arena multiplayer math platform.

## ✨ Features

- **🎯 Real-time Updates** - Live leaderboard, chat, and statistics via WebSocket
- **🌓 Dark/Light Mode** - Automatic theme switching with localStorage persistence
- **📱 Responsive Design** - Mobile-first design with Tailwind CSS
- **🔐 Secure Authentication** - Simple username-based login
- **📊 Interactive Charts** - Beautiful visualizations with Recharts
- **⚡ Fast Performance** - Lightning-fast HMR with Vite
- **🧮 Complete Math Interface** - Problems, leaderboard, profile, chat

## 🛠️ Tech Stack

- **Framework:** React 18
- **Build Tool:** Vite 5
- **Styling:** Tailwind CSS 3
- **Routing:** React Router DOM 6
- **State Management:** Zustand
- **Charts:** Recharts
- **Icons:** Lucide React
- **WebSocket:** Native WebSocket API

## 📦 Installation

### Prerequisites

- Node.js 18+ and npm
- Backend servers running on localhost

### Quick Start

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

Or use the provided script:
```bash
# From project root
.\scripts\start-frontend.bat
```

## 🚀 Running with Backend

### Option 1: Full Stack (Recommended)
```bash
# From project root - starts everything
.\scripts\start-fullstack.bat
```

This starts:
- All backend servers (Service Discovery, TCP, NIO, UDP, RMI)
- WebSocket bridge server
- React frontend dev server

### Option 2: Manual Start
```bash
# Terminal 1: Backend servers
.\scripts\start-all.bat

# Terminal 2: WebSocket bridge
java -cp target/classes com.netbattle.websocket.WebSocketGameServer

# Terminal 3: Frontend
cd frontend
npm run dev
```

## 🌐 Development URLs

- **Frontend:** http://localhost:3000
- **WebSocket:** ws://localhost:8082
- **Backend API:** http://localhost:8080
- **Service Discovery:** http://localhost:8084

## 📁 Project Structure

```
frontend/
├── public/                 # Static assets
├── src/
│   ├── components/         # Reusable components
│   │   ├── Chat.jsx       # Live chat component
│   │   ├── Layout.jsx     # App layout wrapper
│   │   └── Navbar.jsx     # Navigation bar
│   ├── pages/             # Page components
│   │   ├── Login.jsx      # Authentication page
│   │   ├── Dashboard.jsx  # Main dashboard
│   │   ├── Challenges.jsx # Math problems
│   │   ├── Leaderboard.jsx# Global rankings
│   │   └── Profile.jsx    # User profile & stats
│   ├── contexts/          # React contexts
│   │   └── AuthContext.jsx# Authentication state
│   ├── services/          # API & WebSocket
│   │   ├── api.js        # REST API client
│   │   └── websocket.js  # WebSocket service
│   ├── hooks/             # Custom React hooks
│   │   └── useWebSocket.js
│   ├── App.jsx            # Main app component
│   ├── main.jsx           # Entry point
│   └── index.css          # Global styles
├── index.html             # HTML template
├── vite.config.js         # Vite configuration
├── tailwind.config.js     # Tailwind configuration
└── package.json           # Dependencies
```

## 🎨 Features Overview

### Login Page
- Beautiful landing page with feature showcase
- Simple username-based login
- Math-themed design
- Responsive grid layout

### Dashboard
- Real-time statistics cards
- Score history chart
- Recent activity feed
- Server status indicators
- Registered services display

### Problems
- Grid layout of all math problems
- Filter by solved/unsolved
- Interactive problem cards
- Answer submission modal
- Real-time solve notifications

### Leaderboard
- Top 3 players highlighted
- Full leaderboard table
- Real-time rank updates
- Online status indicators

### Profile
- User statistics overview
- Achievement badges
- Weekly activity chart
- Recent solves history

### Live Chat
- Floating chat window
- Real-time messaging
- Message history
- Timestamp display

## 🎨 Customization

### Theme Colors
Edit `tailwind.config.js` to customize colors:
```javascript
theme: {
  extend: {
    colors: {
      primary: { ... },
      dark: { ... }
    }
  }
}
```

### API Endpoints
Edit `src/services/api.js` to change backend URLs:
```javascript
const API_BASE = '/api'; // Change this
```

### WebSocket URL
Edit `src/services/websocket.js`:
```javascript
this.url = 'ws://localhost:8082/game'; // Change this
```

## 🐛 Troubleshooting

### Port Already in Use
```bash
# Change port in vite.config.js
server: {
  port: 3001  # Change to different port
}
```

### WebSocket Connection Failed
- Ensure backend servers are running
- Check WebSocket server is running on port 8082
- Verify browser console for errors

### Build Errors
```bash
# Clear node_modules and reinstall
rm -rf node_modules package-lock.json
npm install
```

### Hot Reload Not Working
```bash
# Try clearing Vite cache
rm -rf node_modules/.vite
npm run dev
```

## 📝 Development Tips

- Use React DevTools for debugging
- Enable source maps in production for better debugging
- Use ESLint for code quality
- Follow React best practices and hooks rules

## 🚀 Production Deployment

```bash
# Build for production
npm run build

# Output will be in dist/ folder
# Serve with any static file server
```

### Environment Variables
Create `.env` file for production:
```env
VITE_API_URL=https://your-api.com
VITE_WS_URL=wss://your-ws.com
```

## 📄 License

MIT License - Educational Project

---

**Happy Coding!** 🧮


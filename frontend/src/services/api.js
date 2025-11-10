const API_BASE = '/api';

class ApiService {
  async request(endpoint, options = {}) {
    const token = localStorage.getItem('token');
    const playerId = localStorage.getItem('playerId');
    const headers = {
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...(playerId && { 'X-Player-Id': playerId }),
      ...options.headers,
    };

    try {
      const response = await fetch(`${API_BASE}${endpoint}`, {
        ...options,
        headers,
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return await response.json();
    } catch (error) {
      console.error('API request failed:', error);
      throw error;
    }
  }

  async login(username) {
    return this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username }),
    });
  }

  async register(username, password, email) {
    return this.request('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password, email }),
    });
  }

  async getProblems() {
    return this.request('/problems');
  }

  async submitAnswer(challengeId, answer) {
    return this.request('/problems/submit', {
      method: 'POST',
      body: JSON.stringify({ challengeId, answer }),
    });
  }

  async getLeaderboard() {
    return this.request('/leaderboard');
  }

  async getPlayerStats(playerId) {
    return this.request(`/stats/${playerId}`);
  }

  async getAchievements(playerId) {
    return this.request(`/achievements/${playerId}`);
  }

  async sendChatMessage(message) {
    return this.request('/chat', {
      method: 'POST',
      body: JSON.stringify({ message }),
    });
  }

  async getServerStats() {
    return this.request('/server/stats');
  }

  async getGameStatus() {
    return this.request('/game/status');
  }

  async getRegisteredServices() {
    try {
      const response = await fetch('http://localhost:8084/registry/services');
      return await response.json();
    } catch (error) {
      console.error('Failed to fetch services:', error);
      return [];
    }
  }

  async getRegistryStats() {
    try {
      const response = await fetch('http://localhost:8084/registry/stats');
      return await response.json();
    } catch (error) {
      console.error('Failed to fetch registry stats:', error);
      return {};
    }
  }
}

export default new ApiService();


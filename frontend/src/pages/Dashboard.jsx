import { useState, useEffect } from 'react';
import { Trophy, Calculator, Clock, TrendingUp, Users, Server } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useWebSocket } from '../hooks/useWebSocket';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import api from '../services/api';

const Dashboard = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState({
    score: 0,
    rank: '-',
    challengesSolved: 0,
    playTime: '0h 0m'
  });
  const [serverStats, setServerStats] = useState({
    activePlayers: 0,
    totalChallenges: 0,
    uptime: '0h',
    gameReady: false,
    currentPlayerCount: 0,
    minimumPlayers: 5
  });
  const [recentActivity, setRecentActivity] = useState([]);
  const [scoreHistory, setScoreHistory] = useState([]);
  const [services, setServices] = useState([]);

  // Real-time player stats update
  useWebSocket('stats_update', (data) => {
    if (data.playerId === user.id) {
      setStats(prev => ({
        score: data.totalScore !== undefined ? data.totalScore : prev.score,
        rank: data.rank !== undefined ? data.rank : prev.rank,
        challengesSolved: data.challengesSolved !== undefined ? data.challengesSolved : prev.challengesSolved,
        playTime: prev.playTime // Play time doesn't update in real-time
      }));
      
      // Update score history when score changes
      if (data.totalScore !== undefined && data.totalScore > 0) {
        setScoreHistory(prev => {
          const newEntry = {
            time: 'now',
            score: data.totalScore,
            timestamp: Date.now()
          };
          // Add new entry if score changed
          const updated = [...prev];
          // Convert previous "now" entries to relative time
          const now = Date.now();
          const updatedWithTime = updated.map((entry, index) => {
            if (entry.time === 'now' && entry.timestamp) {
              const elapsed = now - entry.timestamp;
              const hours = Math.floor(elapsed / 3600000);
              const minutes = Math.floor((elapsed % 3600000) / 60000);
              let timeLabel;
              if (hours > 0) {
                timeLabel = `${hours}h ago`;
              } else if (minutes > 0) {
                timeLabel = `${minutes}m ago`;
              } else {
                timeLabel = 'now';
              }
              return { ...entry, time: timeLabel };
            }
            return entry;
          });
          updatedWithTime.unshift(newEntry);
          // Keep only last 20 entries
          return updatedWithTime.slice(0, 20);
        });
        
        // Refresh full player stats to get updated play time and other details
        api.getPlayerStats(user.id).then(playerStats => {
          setStats(prev => ({
            ...prev,
            playTime: formatPlayTime(playerStats.totalPlayTime || 0)
          }));
        }).catch(console.error);
      }
    }
  });

  // Real-time server stats update
  useWebSocket('server_stats', (data) => {
    setServerStats(prev => ({
      activePlayers: data.activePlayers !== undefined ? data.activePlayers : prev.activePlayers,
      totalChallenges: data.totalChallenges !== undefined ? data.totalChallenges : prev.totalChallenges,
      uptime: data.uptime !== undefined ? formatUptime(data.uptime) : prev.uptime,
      gameReady: data.gameReady !== undefined ? data.gameReady : prev.gameReady,
      currentPlayerCount: data.currentPlayerCount !== undefined ? data.currentPlayerCount : prev.currentPlayerCount,
      minimumPlayers: data.minimumPlayers !== undefined ? data.minimumPlayers : prev.minimumPlayers
    }));
  });

  // Real-time game status update
  useWebSocket('game_status', (data) => {
    setServerStats(prev => ({
      ...prev,
      gameReady: data.gameReady !== undefined ? data.gameReady : prev.gameReady,
      currentPlayerCount: data.currentPlayerCount !== undefined ? data.currentPlayerCount : prev.currentPlayerCount,
      minimumPlayers: data.minimumPlayers !== undefined ? data.minimumPlayers : prev.minimumPlayers
    }));
  });

  // Real-time player join events
  useWebSocket('player_joined', (data) => {
    setServerStats(prev => ({
      ...prev,
      currentPlayerCount: data.currentPlayerCount !== undefined ? data.currentPlayerCount : prev.currentPlayerCount,
      gameReady: data.currentPlayerCount >= (prev.minimumPlayers || 5)
    }));
    setRecentActivity(prev => [{
      message: `${data.username || 'A player'} joined the game`,
      timestamp: Date.now()
    }, ...prev].slice(0, 10));
  });

  // Real-time activity events (challenge solves, etc.)
  useWebSocket('activity', (data) => {
    if (data && data.message) {
      setRecentActivity(prev => [{
        message: data.message,
        timestamp: data.timestamp || Date.now()
      }, ...prev].slice(0, 10));
    }
  });
  
  // Real-time leaderboard updates (for future use)
  useWebSocket('leaderboard_update', (data) => {
    // Leaderboard updates can trigger a refresh if needed
    // For now, we'll let the polling handle it, but this is available for real-time updates
    console.log('Leaderboard updated via WebSocket');
  });

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [playerStats, serverData, servicesData] = await Promise.all([
          api.getPlayerStats(user.id),
          api.getServerStats(),
          api.getRegisteredServices()
        ]);
        
        setStats({
          score: playerStats.totalScore || 0,
          rank: playerStats.rank || '-',
          challengesSolved: playerStats.challengesSolved || 0,
          playTime: formatPlayTime(playerStats.totalPlayTime || 0)
        });
        
        setServerStats({
          activePlayers: serverData.activePlayers || 0,
          totalChallenges: serverData.totalChallenges || 0,
          uptime: formatUptime(serverData.uptime || 0),
          gameReady: serverData.gameReady || false,
          currentPlayerCount: serverData.currentPlayerCount || 0,
          minimumPlayers: serverData.minimumPlayers || 5
        });

        const history = (playerStats.scoreHistory || []).map((event, index) => {
          const elapsed = Date.now() - event.timestamp;
          const hours = Math.floor(elapsed / 3600000);
          const minutes = Math.floor((elapsed % 3600000) / 60000);
          
          let timeLabel;
          if (hours > 0) {
            timeLabel = `${hours}h ago`;
          } else if (minutes > 0) {
            timeLabel = `${minutes}m ago`;
          } else {
            timeLabel = 'now';
          }
          
          return {
            time: timeLabel,
            score: event.score
          };
        });
        
        setScoreHistory(history.length > 0 ? history : [{ time: 'now', score: 0 }]);
        setServices(servicesData || []);
      } catch (error) {
        console.error('Failed to fetch dashboard data:', error);
      }
    };

    fetchData();
    
    // Poll services every 30 seconds (WebSocket handles most real-time updates)
    const servicesInterval = setInterval(() => {
      api.getRegisteredServices().then(setServices).catch(console.error);
    }, 30000);
    
    // Update score history time labels every minute
    const timeUpdateInterval = setInterval(() => {
      setScoreHistory(prev => {
        const now = Date.now();
        return prev.map(entry => {
          if (entry.timestamp) {
            const elapsed = now - entry.timestamp;
            const hours = Math.floor(elapsed / 3600000);
            const minutes = Math.floor((elapsed % 3600000) / 60000);
            let timeLabel;
            if (hours > 0) {
              timeLabel = `${hours}h ago`;
            } else if (minutes > 0) {
              timeLabel = `${minutes}m ago`;
            } else {
              timeLabel = 'now';
            }
            return { ...entry, time: timeLabel };
          }
          return entry;
        });
      });
    }, 60000); // Update every minute
    
    return () => {
      clearInterval(servicesInterval);
      clearInterval(timeUpdateInterval);
    };
  }, [user.id]);

  const formatPlayTime = (millis) => {
    const hours = Math.floor(millis / 3600000);
    const minutes = Math.floor((millis % 3600000) / 60000);
    return `${hours}h ${minutes}m`;
  };

  const formatUptime = (millis) => {
    const hours = Math.floor(millis / 3600000);
    return `${hours}h`;
  };

  const statCards = [
    { icon: Trophy, label: 'Total Score', value: stats.score.toLocaleString(), color: 'text-yellow-600' },
    { icon: TrendingUp, label: 'Global Rank', value: `#${stats.rank}`, color: 'text-purple-600' },
    { icon: Calculator, label: 'Problems Solved', value: stats.challengesSolved, color: 'text-green-600' },
    { icon: Clock, label: 'Active Time', value: stats.playTime, color: 'text-blue-600' },
  ];

  const serverCards = [
    { icon: Users, label: 'Active Solvers', value: serverStats.activePlayers },
    { icon: Calculator, label: 'Total Problems', value: serverStats.totalChallenges },
    { icon: Server, label: 'Server Uptime', value: serverStats.uptime },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2">
          Welcome back, {user?.username}!
        </h1>
        <p className="text-gray-600 dark:text-gray-400">
          Here's your mathematical performance overview
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map((stat, index) => {
          const Icon = stat.icon;
          return (
            <div key={index} className="card hover:shadow-xl transition-all">
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">
                    {stat.label}
                  </p>
                  <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                    {stat.value}
                  </p>
                </div>
                <div className={`p-3 rounded-lg bg-gray-100 dark:bg-dark-200 ${stat.color}`}>
                  <Icon className="w-6 h-6" />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 card">
          <h2 className="text-xl font-bold mb-4 text-gray-900 dark:text-gray-100">
            Score History
          </h2>
          <ResponsiveContainer width="100%" height={300}>
            <AreaChart data={scoreHistory}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="time" stroke="#9CA3AF" />
              <YAxis stroke="#9CA3AF" />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#1F2937',
                  border: 'none',
                  borderRadius: '8px',
                  color: '#F3F4F6'
                }}
              />
              <Area
                type="monotone"
                dataKey="score"
                stroke="#9333EA"
                fill="#9333EA"
                fillOpacity={0.6}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="card">
          <h2 className="text-xl font-bold mb-4 text-gray-900 dark:text-gray-100">
            Recent Activity
          </h2>
          <div className="space-y-3">
            {recentActivity.length === 0 ? (
              <p className="text-gray-500 text-sm text-center py-8">
                No recent activity
              </p>
            ) : (
              recentActivity.map((activity, index) => (
                <div key={index} className="flex items-center space-x-3 p-3 rounded-lg bg-gray-50 dark:bg-dark-200">
                  <div className="w-2 h-2 rounded-full bg-green-500"></div>
                  <div className="flex-1">
                    <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {activity.message}
                    </p>
                    <p className="text-xs text-gray-500">
                      {new Date(activity.timestamp).toLocaleTimeString()}
                    </p>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="card">
        <h2 className="text-xl font-bold mb-4 text-gray-900 dark:text-gray-100">
          Server Status
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          {serverCards.map((stat, index) => {
            const Icon = stat.icon;
            return (
              <div key={index} className="flex items-center space-x-4 p-4 rounded-lg bg-gray-50 dark:bg-dark-200">
                <div className="p-3 rounded-lg bg-primary-100 dark:bg-primary-900/30 text-primary-600">
                  <Icon className="w-6 h-6" />
                </div>
                <div>
                  <p className="text-sm text-gray-600 dark:text-gray-400">
                    {stat.label}
                  </p>
                  <p className="text-xl font-bold text-gray-900 dark:text-gray-100">
                    {stat.value}
                  </p>
                </div>
              </div>
            );
          })}
        </div>

        {/* Game Status Card */}
        <div className={`p-4 rounded-lg border-2 mb-4 ${
          serverStats.gameReady
            ? 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800'
            : 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800'
        }`}>
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <div className={`p-2 rounded-lg ${
                serverStats.gameReady
                  ? 'bg-green-100 dark:bg-green-900/30 text-green-600'
                  : 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-600'
              }`}>
                <Users className="w-5 h-5" />
              </div>
              <div>
                <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                  Game Status
                </p>
                <p className={`text-lg font-bold ${
                  serverStats.gameReady
                    ? 'text-green-900 dark:text-green-100'
                    : 'text-yellow-900 dark:text-yellow-100'
                }`}>
                  {serverStats.gameReady ? 'Ready to Play!' : 'Waiting for Players'}
                </p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                {serverStats.currentPlayerCount} / {serverStats.minimumPlayers}
              </p>
              <p className="text-xs text-gray-500">
                players joined
              </p>
            </div>
          </div>
          {!serverStats.gameReady && (
            <div className="mt-3 pt-3 border-t border-yellow-200 dark:border-yellow-800">
              <div className="flex items-center space-x-2">
                <div className="flex-1 bg-yellow-200 dark:bg-yellow-800 rounded-full h-2 overflow-hidden">
                  <div
                    className="bg-yellow-600 dark:bg-yellow-400 h-full transition-all duration-500"
                    style={{ width: `${(serverStats.currentPlayerCount / serverStats.minimumPlayers) * 100}%` }}
                  />
                </div>
                <span className="text-xs font-medium text-yellow-900 dark:text-yellow-100">
                  {serverStats.minimumPlayers - serverStats.currentPlayerCount} more needed
                </span>
              </div>
            </div>
          )}
        </div>

        <div className="border-t border-gray-200 dark:border-dark-200 pt-4">
          <h3 className="text-lg font-semibold mb-3 text-gray-900 dark:text-gray-100">
            🔍 Registered Services
          </h3>
          {services.length > 0 ? (
            <div className="space-y-2">
              {services.map((service, index) => (
                <div key={index} className="flex items-center justify-between p-3 rounded-lg bg-gray-50 dark:bg-dark-200">
                  <div className="flex items-center space-x-3">
                    <div className={`w-2 h-2 rounded-full ${service.status === 'UP' ? 'bg-green-500' : 'bg-red-500'}`}></div>
                    <div>
                      <p className="font-medium text-gray-900 dark:text-gray-100">{service.serviceName}</p>
                      <p className="text-xs text-gray-500">{service.url}</p>
                    </div>
                  </div>
                  <span className={`text-xs px-2 py-1 rounded ${service.status === 'UP' ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'}`}>
                    {service.status}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-500 text-center py-4">No services registered</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;


import { useState, useEffect } from 'react';
import { Trophy, Calculator, Clock, Award, TrendingUp, Calendar } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useWebSocket } from '../hooks/useWebSocket';
import api from '../services/api';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

const Profile = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState(null);
  const [achievements, setAchievements] = useState([]);
  const [solveHistory, setSolveHistory] = useState([]);
  const [loading, setLoading] = useState(true);

  useWebSocket('challenge_solved', (data) => {
    if (data.playerId === user.id) {
      const today = new Date().toLocaleDateString('en-US', { weekday: 'short' });
      
      setSolveHistory(prev => prev.map(day => {
        if (day.day === today) {
          return { ...day, solves: day.solves + 1 };
        }
        return day;
      }));
      
      setStats(prev => ({
        ...prev,
        challengesSolved: (prev?.challengesSolved || 0) + 1,
        totalScore: (prev?.totalScore || 0) + (data.points || 0)
      }));
    }
  });

  useEffect(() => {
    const fetchProfileData = async () => {
      try {
        const [playerStats, playerAchievements] = await Promise.all([
          api.getPlayerStats(user.id),
          api.getAchievements(user.id)
        ]);
        
        setStats(playerStats);
        setAchievements(playerAchievements);
        
        const weeklyActivity = playerStats.weeklyActivity || [];
        if (weeklyActivity.length > 0) {
          setSolveHistory(weeklyActivity);
        } else {
          const defaultHistory = Array.from({ length: 7 }, (_, i) => ({
            day: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][i],
            solves: 0
          }));
          setSolveHistory(defaultHistory);
        }
      } catch (error) {
        console.error('Failed to fetch profile data:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchProfileData();
  }, [user.id]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    );
  }

  const statCards = [
    { icon: Trophy, label: 'Total Score', value: stats?.totalScore?.toLocaleString() || '0', color: 'text-yellow-600' },
    { icon: Calculator, label: 'Problems Solved', value: stats?.challengesSolved || '0', color: 'text-green-600' },
    { icon: Clock, label: 'Active Time', value: formatPlayTime(stats?.totalPlayTime || 0), color: 'text-purple-600' },
    { icon: TrendingUp, label: 'Global Rank', value: `#${stats?.rank || '-'}`, color: 'text-blue-600' },
  ];

  function formatPlayTime(millis) {
    const hours = Math.floor(millis / 3600000);
    const minutes = Math.floor((millis % 3600000) / 60000);
    return `${hours}h ${minutes}m`;
  }

  const achievementIcons = {
    'First Blood': '🩸',
    'Speedrunner': '⚡',
    'Veteran': '🎖️',
    'Perfectionist': '💯',
    'Night Owl': '🦉',
    'Early Bird': '🐦',
    'Champion': '👑'
  };

  return (
    <div className="space-y-6">
      <div className="card">
        <div className="flex items-center space-x-6">
          <div className="w-24 h-24 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white text-4xl font-bold">
            {user?.username?.[0]?.toUpperCase()}
          </div>
          <div className="flex-1">
            <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2">
              {user?.username}
            </h1>
            <p className="text-gray-600 dark:text-gray-400 flex items-center">
              <Calendar className="w-4 h-4 mr-2" />
              Member since {stats?.firstSeen ? new Date(stats.firstSeen).toLocaleDateString() : 'Unknown'}
            </p>
          </div>
        </div>
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

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-bold text-gray-900 dark:text-gray-100">
              Weekly Activity
            </h2>
            <div className="flex items-center space-x-2">
              <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
              <span className="text-xs text-green-600 dark:text-green-400 font-medium">LIVE</span>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={solveHistory}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="day" stroke="#9CA3AF" />
              <YAxis stroke="#9CA3AF" />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#1F2937',
                  border: 'none',
                  borderRadius: '8px',
                  color: '#F3F4F6'
                }}
                labelFormatter={(label) => `${label}`}
                formatter={(value) => [`${value} problem${value !== 1 ? 's' : ''}`, 'Solved']}
              />
              <Bar dataKey="solves" fill="#9333EA" radius={[8, 8, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="card">
          <h2 className="text-xl font-bold mb-4 text-gray-900 dark:text-gray-100 flex items-center">
            <Award className="w-6 h-6 mr-2 text-yellow-500" />
            Achievements
          </h2>
          <div className="grid grid-cols-2 gap-3">
            {achievements.length === 0 ? (
              <p className="col-span-2 text-center text-gray-500 py-8">
                No achievements yet. Keep solving challenges!
              </p>
            ) : (
              achievements.map((achievement, index) => (
                <div
                  key={index}
                  className="p-4 rounded-lg bg-gradient-to-br from-yellow-50 to-orange-50 dark:from-yellow-900/20 dark:to-orange-900/20 border-2 border-yellow-200 dark:border-yellow-800 hover:shadow-lg transition-all"
                >
                  <div className="text-3xl mb-2 text-center">
                    {achievementIcons[achievement.name] || '🏆'}
                  </div>
                  <h3 className="font-bold text-sm text-center text-gray-900 dark:text-gray-100">
                    {achievement.name || achievement}
                  </h3>
                  {achievement.description && (
                    <p className="text-xs text-gray-600 dark:text-gray-400 text-center mt-1">
                      {achievement.description}
                    </p>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="card">
        <h2 className="text-xl font-bold mb-4 text-gray-900 dark:text-gray-100">
          Recent Solves
        </h2>
        {stats?.solvedChallenges?.length > 0 ? (
          <div className="space-y-2">
            {stats.solvedChallenges.map((challengeId, index) => (
              <div key={index} className="flex items-center justify-between p-3 rounded-lg bg-gray-50 dark:bg-dark-200">
                <div className="flex items-center space-x-3">
                  <div className="w-8 h-8 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center">
                    <Target className="w-5 h-5 text-green-600" />
                  </div>
                  <span className="font-medium text-gray-900 dark:text-gray-100">
                    Challenge #{challengeId}
                  </span>
                </div>
                <span className="text-sm text-gray-500">
                  Solved
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-center text-gray-500 py-8">
            No challenges solved yet
          </p>
        )}
      </div>
    </div>
  );
};

export default Profile;


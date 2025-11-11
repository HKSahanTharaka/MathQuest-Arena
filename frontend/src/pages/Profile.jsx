import { useState, useEffect } from 'react';
import { Trophy, Calculator, Clock, Award, TrendingUp, Calendar, Target } from 'lucide-react';
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

  const getAchievementDescription = (achievementName) => {
    const descriptions = {
      'First Solver': 'Solved your first challenge',
      'Math Enthusiast': 'Solved 3 or more challenges',
      'Math Wizard': 'Solved 5 or more challenges',
      'Point Collector': 'Earned 500 or more points',
      'Top Mathematician': 'Ranked #1 on the leaderboard',
      'Elite Solver': 'Ranked in the top 3'
    };
    return descriptions[achievementName] || 'Achievement unlocked!';
  };

  useWebSocket('challenge_solved', (data) => {
    if (data.playerId === user?.id) {
      const today = new Date().toLocaleDateString('en-US', { weekday: 'short' });
      
      setSolveHistory(prev => prev.map(day => {
        if (day.day === today) {
          return { ...day, solves: (day.solves || 0) + 1 };
        }
        return day;
      }));
      
      setStats(prev => ({
        ...prev,
        challengesSolved: (prev?.challengesSolved || 0) + 1,
        totalScore: (prev?.totalScore || 0) + (data.points || 0),
        solvedChallenges: prev?.solvedChallenges ? [...prev.solvedChallenges, data.challengeId] : [data.challengeId],
        totalPlayTime: prev?.totalPlayTime || 0,
        rank: prev?.rank || '-',
        firstSeen: prev?.firstSeen || Date.now(),
        weeklyActivity: prev?.weeklyActivity || []
      }));
    }
  });

  // Listen for real-time stats updates
  useWebSocket('stats_update', (data) => {
    if (data.playerId === user?.id) {
      setStats(prev => ({
        ...prev,
        totalScore: data.totalScore !== undefined ? data.totalScore : (prev?.totalScore || 0),
        rank: data.rank !== undefined ? data.rank : (prev?.rank || '-'),
        challengesSolved: data.challengesSolved !== undefined ? data.challengesSolved : (prev?.challengesSolved || 0),
        totalPlayTime: prev?.totalPlayTime || 0,
        firstSeen: prev?.firstSeen || Date.now(),
        solvedChallenges: prev?.solvedChallenges || [],
        weeklyActivity: prev?.weeklyActivity || []
      }));
    }
  });

  useEffect(() => {
    const fetchProfileData = async () => {
      try {
        setLoading(true);
        let [playerStats, playerAchievements] = await Promise.all([
          api.getPlayerStats(user.id),
          api.getAchievements(user.id)
        ]);
        
        console.log('Player Stats Response:', playerStats);
        console.log('Player Achievements Response:', playerAchievements);
        
        // Ensure we have valid stats data
        if (!playerStats) {
          console.warn('No player stats received from API');
          playerStats = {};
        }
        
        // Set stats with default values if missing
        const statsData = {
          totalScore: typeof playerStats.totalScore === 'number' ? playerStats.totalScore : 0,
          challengesSolved: typeof playerStats.challengesSolved === 'number' ? playerStats.challengesSolved : 0,
          totalPlayTime: typeof playerStats.totalPlayTime === 'number' ? playerStats.totalPlayTime : 0,
          rank: playerStats.rank !== undefined && playerStats.rank !== null ? playerStats.rank : '-',
          firstSeen: playerStats.firstSeen || Date.now(),
          lastSeen: playerStats.lastSeen || Date.now(),
          solvedChallenges: Array.isArray(playerStats.solvedChallenges) ? playerStats.solvedChallenges : [],
          scoreHistory: Array.isArray(playerStats.scoreHistory) ? playerStats.scoreHistory : [],
          weeklyActivity: Array.isArray(playerStats.weeklyActivity) ? playerStats.weeklyActivity : [],
          username: playerStats.username || user?.username || 'Unknown',
          playerId: playerStats.playerId || user?.id || ''
        };
        
        console.log('Formatted Stats Data:', statsData);
        setStats(statsData);
        
        // Handle achievements - can be array of strings or array of objects
        if (Array.isArray(playerAchievements)) {
          setAchievements(playerAchievements.map(achievement => {
            if (typeof achievement === 'string') {
              return { name: achievement, description: getAchievementDescription(achievement) };
            }
            return achievement;
          }));
        } else {
          setAchievements([]);
        }
        
        // Format weekly activity data
        const weeklyActivity = playerStats?.weeklyActivity || [];
        if (weeklyActivity.length > 0) {
          // Ensure all days are present
          const daysMap = new Map(weeklyActivity.map(day => [day.day, day.solves || 0]));
          const allDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
          const formattedHistory = allDays.map(day => ({
            day,
            solves: daysMap.get(day) || 0
          }));
          setSolveHistory(formattedHistory);
        } else {
          // Default empty history
          const defaultHistory = Array.from({ length: 7 }, (_, i) => ({
            day: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][i],
            solves: 0
          }));
          setSolveHistory(defaultHistory);
        }
      } catch (error) {
        console.error('Failed to fetch profile data:', error);
        console.error('Error details:', error.message, error.status, error.data);
        // Set default values on error - ensure all fields are present
        setStats({
          totalScore: 0,
          challengesSolved: 0,
          totalPlayTime: 0,
          rank: '-',
          firstSeen: Date.now(),
          lastSeen: Date.now(),
          solvedChallenges: [],
          scoreHistory: [],
          weeklyActivity: [],
          username: user?.username || 'Unknown',
          playerId: user?.id || ''
        });
        setAchievements([]);
        // Set default empty history
        const defaultHistory = Array.from({ length: 7 }, (_, i) => ({
          day: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][i],
          solves: 0
        }));
        setSolveHistory(defaultHistory);
      } finally {
        setLoading(false);
      }
    };

    if (user?.id) {
      fetchProfileData();
    } else {
      console.warn('No user ID available for fetching profile data');
      setLoading(false);
    }
  }, [user?.id]);

  // Ensure stats has default values even if null
  const displayStats = stats || {
    totalScore: 0,
    challengesSolved: 0,
    totalPlayTime: 0,
    rank: '-',
    firstSeen: Date.now(),
    lastSeen: Date.now(),
    solvedChallenges: [],
    scoreHistory: [],
    weeklyActivity: [],
    username: user?.username || 'Unknown',
    playerId: user?.id || ''
  };

  if (loading && !stats) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    );
  }

  function formatPlayTime(millis) {
    if (!millis || millis === 0) return '0h 0m';
    const hours = Math.floor(millis / 3600000);
    const minutes = Math.floor((millis % 3600000) / 60000);
    return `${hours}h ${minutes}m`;
  }

  // Format stat values - ensure they're always displayed
  const formatStatValue = (value, type = 'number') => {
    if (value === null || value === undefined) return type === 'number' ? '0' : '-';
    if (type === 'number') {
      const numValue = typeof value === 'number' ? value : parseInt(value) || 0;
      return numValue.toLocaleString();
    }
    return String(value);
  };

  const statCards = [
    { 
      icon: Trophy, 
      label: 'Total Score', 
      value: formatStatValue(displayStats.totalScore, 'number'), 
      color: 'text-yellow-600' 
    },
    { 
      icon: Calculator, 
      label: 'Problems Solved', 
      value: formatStatValue(displayStats.challengesSolved, 'number'), 
      color: 'text-green-600' 
    },
    { 
      icon: Clock, 
      label: 'Active Time', 
      value: formatPlayTime(displayStats.totalPlayTime || 0), 
      color: 'text-purple-600' 
    },
    { 
      icon: TrendingUp, 
      label: 'Global Rank', 
      value: displayStats.rank !== null && displayStats.rank !== undefined && displayStats.rank !== '-' 
        ? `#${displayStats.rank}` 
        : '#-', 
      color: 'text-blue-600' 
    },
  ];

  const achievementIcons = {
    'First Solver': '',
    'Math Enthusiast': '',
    'Math Wizard': '',
    'Point Collector': '',
    'First Blood': '',
    'Speedrunner': '',
    'Veteran': '',
    'Perfectionist': '',
    'Night Owl': '',
    'Early Bird': '',
    'Champion': '',
    'Top Mathematician': '',
    'Elite Solver': ''
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
              Member since {displayStats.firstSeen ? new Date(displayStats.firstSeen).toLocaleDateString() : 'Today'}
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
              achievements.map((achievement, index) => {
                const achievementName = typeof achievement === 'string' ? achievement : achievement.name;
                const achievementDesc = typeof achievement === 'string' 
                  ? getAchievementDescription(achievement)
                  : achievement.description;
                return (
                  <div
                    key={index}
                    className="p-4 rounded-lg bg-gradient-to-br from-yellow-50 to-orange-50 dark:from-yellow-900/20 dark:to-orange-900/20 border-2 border-yellow-200 dark:border-yellow-800 hover:shadow-lg transition-all"
                  >
                    <div className="text-3xl mb-2 text-center">
                      {achievementIcons[achievementName] || ''}
                    </div>
                    <h3 className="font-bold text-sm text-center text-gray-900 dark:text-gray-100">
                      {achievementName}
                    </h3>
                    {achievementDesc && (
                      <p className="text-xs text-gray-600 dark:text-gray-400 text-center mt-1">
                        {achievementDesc}
                      </p>
                    )}
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>

      <div className="card">
        <h2 className="text-xl font-bold mb-4 text-gray-900 dark:text-gray-100">
          Recent Solves
        </h2>
        {displayStats.solvedChallenges && displayStats.solvedChallenges.length > 0 ? (
          <div className="space-y-2">
            {displayStats.solvedChallenges.map((challengeId, index) => (
              <div key={index} className="flex items-center justify-between p-3 rounded-lg bg-gray-50 dark:bg-dark-200">
                <div className="flex items-center space-x-3">
                  <div className="w-8 h-8 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center">
                    <Target className="w-5 h-5 text-green-600" />
                  </div>
                  <span className="font-medium text-gray-900 dark:text-gray-100">
                    Challenge {challengeId}
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
            No challenges solved yet. Start solving challenges to see them here!
          </p>
        )}
      </div>
    </div>
  );
};

export default Profile;


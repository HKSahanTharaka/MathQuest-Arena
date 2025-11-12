import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { Calculator, Brain, TrendingUp, Users } from 'lucide-react';

const Login = () => {
  const [username, setUsername] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const result = await login(username);
      if (result.success) {
        navigate('/');
      } else {
        setError(result.error || 'Failed to join');
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const features = [
    { icon: Calculator, title: 'Math Problems', desc: 'Solve challenging mathematical problems' },
    { icon: Brain, title: 'Test Your Skills', desc: 'From basic to advanced mathematics' },
    { icon: TrendingUp, title: 'Track Progress', desc: 'Live leaderboard and statistics' },
    { icon: Users, title: 'Multiplayer', desc: 'Compete with solvers worldwide' },
  ];

  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-50 via-indigo-50 to-blue-50 dark:from-dark-50 dark:via-dark-100 dark:to-dark-100 flex items-center justify-center p-4">
      <div className="w-full max-w-6xl grid md:grid-cols-2 gap-8 items-center">
        <div className="space-y-8">
          <div>
            <div className="flex items-center space-x-3 mb-4">
              <div className="w-16 h-16 bg-gradient-to-br from-purple-500 to-indigo-700 rounded-2xl flex items-center justify-center shadow-xl">
                <Calculator className="w-10 h-10 text-white" />
              </div>
              <div>
                <h1 className="text-4xl font-bold bg-gradient-to-r from-purple-600 to-indigo-800 bg-clip-text text-transparent">
                  MathQuest Arena
                </h1>
                <p className="text-gray-600 dark:text-gray-400">Multiplayer Math Platform</p>
              </div>
            </div>
            <p className="text-xl text-gray-700 dark:text-gray-300">
              Join the ultimate mathematical challenge. Solve problems, compete globally, and climb the leaderboard!
            </p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            {features.map((feature, index) => {
              const Icon = feature.icon;
              return (
                <div key={index} className="card p-4 hover:shadow-xl transition-all">
                  <Icon className="w-8 h-8 text-purple-600 mb-2" />
                  <h3 className="font-semibold text-gray-900 dark:text-gray-100 mb-1">
                    {feature.title}
                  </h3>
                  <p className="text-sm text-gray-600 dark:text-gray-400">
                    {feature.desc}
                  </p>
                </div>
              );
            })}
          </div>
        </div>

        <div className="card max-w-md w-full mx-auto">
          <h2 className="text-2xl font-bold mb-2 text-gray-900 dark:text-gray-100">
            Join the Arena
          </h2>
          <p className="text-gray-600 dark:text-gray-400 mb-6">
            Just pick a username and start playing!
          </p>

          {error && (
            <div className="mb-4 p-3 rounded-lg bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-2 text-gray-700 dark:text-gray-300">
                Choose Your Username
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="input-field"
                placeholder="Enter a username (2-20 characters)"
                required
                minLength={2}
                maxLength={20}
                autoComplete="off"
                autoFocus
              />
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                No password needed - anyone with the link can play! Username must be unique.
              </p>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? 'Joining...' : 'Start Playing →'}
            </button>
          </form>

          <div className="mt-6 p-4 bg-gradient-to-r from-purple-50 to-indigo-50 dark:from-purple-900/20 dark:to-indigo-900/20 rounded-lg border border-purple-200 dark:border-purple-700">
            <p className="text-sm text-gray-700 dark:text-gray-300 text-center">
              🧮 No registration required • 🚀 Instant access • 🏆 Start solving now!
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;


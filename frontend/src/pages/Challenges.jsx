import { useState, useEffect } from 'react';
import { Calculator, CheckCircle, Lock, Send, AlertCircle, Users, Clock } from 'lucide-react';
import { useWebSocket } from '../hooks/useWebSocket';
import api from '../services/api';

const Problems = () => {
  const [problems, setProblems] = useState([]);
  const [selectedProblem, setSelectedProblem] = useState(null);
  const [answerInput, setAnswerInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [filter, setFilter] = useState('all');
  const [gameStatus, setGameStatus] = useState({
    gameReady: false,
    currentPlayerCount: 0,
    minimumPlayers: 5,
    playersNeeded: 5
  });

  useWebSocket('challenge_solved', (data) => {
    setProblems(prev => prev.map(ch =>
      ch.id === data.challengeId
        ? { ...ch, solved: true }
        : ch
    ));
    setMessage({ type: 'success', text: `${data.username} solved "${data.challengeName}"!` });
    setTimeout(() => setMessage({ type: '', text: '' }), 5000);
  });

  useWebSocket('game_status', (data) => {
    setGameStatus({
      gameReady: data.gameReady || false,
      currentPlayerCount: data.currentPlayerCount || 0,
      minimumPlayers: data.minimumPlayers || 5,
      playersNeeded: data.playersNeeded || 5
    });
  });

  useWebSocket('player_joined', (data) => {
    const newCount = data.currentPlayerCount || 0;
    const minPlayers = data.minimumPlayers || 5;
    const isReady = newCount >= minPlayers;
    
    setGameStatus({
      gameReady: isReady,
      currentPlayerCount: newCount,
      minimumPlayers: minPlayers,
      playersNeeded: data.playersNeeded || Math.max(0, minPlayers - newCount)
    });
    
    if (data.username) {
      setMessage({ 
        type: 'success', 
        text: `${data.username} joined! (${newCount}/${minPlayers} players)` 
      });
      setTimeout(() => setMessage({ type: '', text: '' }), 3000);
    }
    
    // If game just became ready, show a special message
    if (isReady && newCount === minPlayers) {
      setTimeout(() => {
        setMessage({ 
          type: 'success', 
          text: `🎉 Game is ready! You can now start solving challenges!` 
        });
        setTimeout(() => setMessage({ type: '', text: '' }), 5000);
      }, 500);
    }
  });

  useEffect(() => {
    const fetchProblems = async () => {
      try {
        const data = await api.getProblems();
        setProblems(data);
      } catch (error) {
        console.error('Failed to fetch problems:', error);
      }
    };
    
    const fetchGameStatus = async () => {
      try {
        const status = await api.getGameStatus();
        setGameStatus(status);
      } catch (error) {
        console.error('Failed to fetch game status:', error);
      }
    };
    
    fetchProblems();
    fetchGameStatus();
    
    // Poll game status every 10 seconds as backup (WebSocket handles real-time updates)
    const statusInterval = setInterval(fetchGameStatus, 10000);
    
    return () => clearInterval(statusInterval);
  }, []);

  const handleSubmitAnswer = async (e) => {
    e.preventDefault();
    if (!answerInput.trim() || !selectedProblem) return;
    
    if (!gameStatus.gameReady) {
      setMessage({ 
        type: 'error', 
        text: `Waiting for more players. ${gameStatus.currentPlayerCount}/${gameStatus.minimumPlayers} players joined.` 
      });
      setTimeout(() => setMessage({ type: '', text: '' }), 5000);
      return;
    }

    setSubmitting(true);
    try {
      const result = await api.submitAnswer(selectedProblem.id, answerInput);
      if (result.correct) {
        setMessage({ type: 'success', text: `Correct! +${result.points} points` });
        setProblems(prev => prev.map(ch =>
          ch.id === selectedProblem.id
            ? { ...ch, solved: true }
            : ch
        ));
        setSelectedProblem(null);
        setAnswerInput('');
      } else {
        // Check if it's a game not ready message
        if (result.message && result.message.includes('Waiting for more players')) {
          setGameStatus(prev => ({
            ...prev,
            gameReady: result.gameReady || false,
            currentPlayerCount: result.currentPlayerCount || prev.currentPlayerCount,
            minimumPlayers: result.minimumPlayers || prev.minimumPlayers
          }));
        }
        setMessage({ type: 'error', text: result.message || 'Incorrect answer. Try again!' });
      }
    } catch (error) {
      setMessage({ type: 'error', text: 'Failed to submit answer' });
    } finally {
      setSubmitting(false);
      setTimeout(() => setMessage({ type: '', text: '' }), 5000);
    }
  };

  const getDifficultyColor = (difficulty) => {
    switch (difficulty?.toLowerCase()) {
      case 'easy': return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200';
      case 'medium': return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200';
      case 'hard': return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200';
      default: return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200';
    }
  };

  const filteredProblems = problems.filter(ch => {
    if (filter === 'solved') return ch.solved;
    if (filter === 'unsolved') return !ch.solved;
    return true;
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-2">
            Math Problems
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            Challenge your mathematical skills and solve problems
          </p>
        </div>

        <div className="flex items-center space-x-4">
          {/* Game Status Indicator */}
          <div className={`px-4 py-2 rounded-lg flex items-center space-x-2 ${
            gameStatus.gameReady 
              ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' 
              : 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
          }`}>
            <Users className="w-5 h-5" />
            <span className="font-medium">
              {gameStatus.gameReady ? (
                'Game Ready!'
              ) : (
                `${gameStatus.currentPlayerCount}/${gameStatus.minimumPlayers} Players`
              )}
            </span>
          </div>

          <div className="flex space-x-2">
            {['all', 'solved', 'unsolved'].map(f => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={`px-4 py-2 rounded-lg font-medium transition-all ${
                  filter === f
                    ? 'bg-purple-600 text-white'
                    : 'bg-gray-200 dark:bg-dark-200 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-dark-300'
                }`}
              >
                {f.charAt(0).toUpperCase() + f.slice(1)}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Waiting Screen */}
      {!gameStatus.gameReady && (
        <div className="card bg-yellow-50 dark:bg-yellow-900/20 border-2 border-yellow-200 dark:border-yellow-800">
          <div className="flex items-center space-x-4 p-6">
            <div className="flex-shrink-0">
              <Clock className="w-12 h-12 text-yellow-600 dark:text-yellow-400 animate-pulse" />
            </div>
            <div className="flex-1">
              <h3 className="text-xl font-bold text-yellow-900 dark:text-yellow-100 mb-2">
                Waiting for Players to Join
              </h3>
              <p className="text-yellow-800 dark:text-yellow-200 mb-2">
                A minimum of <strong>{gameStatus.minimumPlayers} players</strong> must join before you can start solving challenges.
              </p>
              <div className="flex items-center space-x-2 mt-3">
                <div className="flex-1 bg-yellow-200 dark:bg-yellow-800 rounded-full h-4 overflow-hidden">
                  <div 
                    className="bg-yellow-600 dark:bg-yellow-400 h-full transition-all duration-500"
                    style={{ width: `${(gameStatus.currentPlayerCount / gameStatus.minimumPlayers) * 100}%` }}
                  />
                </div>
                <span className="text-sm font-bold text-yellow-900 dark:text-yellow-100">
                  {gameStatus.currentPlayerCount} / {gameStatus.minimumPlayers}
                </span>
              </div>
              <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-2">
                {gameStatus.playersNeeded > 0 
                  ? `${gameStatus.playersNeeded} more player${gameStatus.playersNeeded > 1 ? 's' : ''} needed`
                  : 'Almost ready!'}
              </p>
            </div>
          </div>
        </div>
      )}

      {message.text && (
        <div className={`p-4 rounded-lg flex items-center space-x-2 ${
          message.type === 'success'
            ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
            : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
        }`}>
          <AlertCircle className="w-5 h-5" />
          <span>{message.text}</span>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredProblems.map((problem) => (
          <div
            key={problem.id}
            className={`card hover:shadow-xl transition-all cursor-pointer ${
              selectedProblem?.id === problem.id ? 'ring-2 ring-purple-500' : ''
            }`}
            onClick={() => setSelectedProblem(problem)}
          >
            <div className="flex items-start justify-between mb-3">
              <div className="flex items-center space-x-2">
                <Calculator className={`w-5 h-5 ${problem.solved ? 'text-green-500' : 'text-purple-400'}`} />
                <h3 className="font-bold text-gray-900 dark:text-gray-100">
                  {problem.name}
                </h3>
              </div>
              {problem.solved && (
                <CheckCircle className="w-5 h-5 text-green-500" />
              )}
            </div>

            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
              {problem.description}
            </p>

            <div className="flex items-center justify-between">
              <span className={`badge ${getDifficultyColor(problem.difficulty)}`}>
                {problem.difficulty}
              </span>
              <span className="text-lg font-bold text-purple-600">
                {problem.points} pts
              </span>
            </div>

            {problem.category && (
              <div className="mt-3 pt-3 border-t border-gray-200 dark:border-dark-200">
                <span className="text-xs text-gray-500 uppercase">{problem.category}</span>
              </div>
            )}
          </div>
        ))}
      </div>

      {filteredProblems.length === 0 && (
        <div className="card text-center py-12">
          <Lock className="w-16 h-16 mx-auto text-gray-400 mb-4" />
          <p className="text-gray-600 dark:text-gray-400">
            No problems available in this filter
          </p>
        </div>
      )}

      {selectedProblem && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50" onClick={() => setSelectedProblem(null)}>
          <div className="card max-w-2xl w-full" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-start justify-between mb-4">
              <div>
                <h2 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-2">
                  {selectedProblem.name}
                </h2>
                <div className="flex items-center space-x-3">
                  <span className={`badge ${getDifficultyColor(selectedProblem.difficulty)}`}>
                    {selectedProblem.difficulty}
                  </span>
                  <span className="text-purple-600 font-bold">
                    {selectedProblem.points} points
                  </span>
                </div>
              </div>
              <button
                onClick={() => setSelectedProblem(null)}
                className="text-gray-400 hover:text-gray-600"
              >
                ✕
              </button>
            </div>

            <div className="mb-6">
              <h3 className="font-semibold mb-2 text-gray-900 dark:text-gray-100">
                Problem
              </h3>
              <p className="text-gray-600 dark:text-gray-400 text-lg">
                {selectedProblem.description}
              </p>
            </div>

            {selectedProblem.hints && selectedProblem.hints.length > 0 && (
              <div className="mb-6 p-4 bg-purple-50 dark:bg-purple-900/20 rounded-lg">
                <h3 className="font-semibold mb-2 text-purple-900 dark:text-purple-200">
                  Hints
                </h3>
                <ul className="list-disc list-inside space-y-1 text-purple-800 dark:text-purple-300">
                  {selectedProblem.hints.map((hint, index) => (
                    <li key={index}>{hint}</li>
                  ))}
                </ul>
              </div>
            )}

            {!selectedProblem.solved && (
              <form onSubmit={handleSubmitAnswer} className="space-y-4">
                {!gameStatus.gameReady && (
                  <div className="p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800 mb-4">
                    <p className="text-sm text-yellow-800 dark:text-yellow-200">
                      ⏸️ Submissions are disabled until {gameStatus.minimumPlayers} players join ({gameStatus.currentPlayerCount}/{gameStatus.minimumPlayers})
                    </p>
                  </div>
                )}
                <div>
                  <label className="block text-sm font-medium mb-2 text-gray-700 dark:text-gray-300">
                    Your Answer
                  </label>
                  <div className="flex space-x-2">
                    <input
                      type="text"
                      value={answerInput}
                      onChange={(e) => setAnswerInput(e.target.value)}
                      placeholder="Enter your answer..."
                      className="input-field flex-1"
                      required
                      disabled={!gameStatus.gameReady}
                    />
                    <button
                      type="submit"
                      disabled={submitting || !gameStatus.gameReady}
                      className="bg-purple-600 hover:bg-purple-700 text-white px-6 py-2 rounded-lg font-medium transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center space-x-2"
                    >
                      <Send className="w-5 h-5" />
                      <span>Submit</span>
                    </button>
                  </div>
                </div>
              </form>
            )}

            {selectedProblem.solved && (
              <div className="p-4 bg-green-50 dark:bg-green-900/20 rounded-lg flex items-center space-x-3 text-green-800 dark:text-green-200">
                <CheckCircle className="w-6 h-6" />
                <span className="font-medium">Problem Solved!</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default Problems;


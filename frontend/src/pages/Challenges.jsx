import { useState, useEffect } from 'react';
import { Calculator, CheckCircle, Lock, Send, AlertCircle, Users, Clock, Play } from 'lucide-react';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAuth } from '../contexts/AuthContext';
import api from '../services/api';

const Problems = () => {
  const { user } = useAuth();
  const [problems, setProblems] = useState([]);
  const [selectedProblem, setSelectedProblem] = useState(null);
  const [answerInput, setAnswerInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });
  const [filter, setFilter] = useState('all');
  const [gameStatus, setGameStatus] = useState({
    gameReady: false,
    currentPlayerCount: 0,
    minimumPlayers: 3,
    playersNeeded: 3,
    sessionStarted: false,
    sessionStartTime: 0,
    sessionEndTime: 0,
    remainingTime: 0
  });
  const [isFirstPlayer, setIsFirstPlayer] = useState(false);
  const [startingSession, setStartingSession] = useState(false);

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
    setGameStatus(prev => ({
      ...prev,
      gameReady: data.gameReady || false,
      currentPlayerCount: data.currentPlayerCount || 0,
      minimumPlayers: data.minimumPlayers || 3,
      playersNeeded: data.playersNeeded || 3,
      sessionStarted: data.sessionStarted || prev.sessionStarted,
      sessionStartTime: data.sessionStartTime || prev.sessionStartTime,
      sessionEndTime: data.sessionEndTime || prev.sessionEndTime,
      remainingTime: data.remainingTime || prev.remainingTime
    }));
    
    // Update first player status if firstPlayerId is provided
    if (user && user.id && data.firstPlayerId) {
      setIsFirstPlayer(user.id === data.firstPlayerId);
    }
  });

  useWebSocket('session_started', (data) => {
    setGameStatus(prev => ({
      ...prev,
      sessionStarted: true,
      sessionStartTime: data.startTime,
      sessionEndTime: data.endTime,
      remainingTime: data.duration
    }));
    setMessage({ 
      type: 'success', 
      text: '🎮 Game session started! You have 10 minutes to solve challenges!' 
    });
    setTimeout(() => setMessage({ type: '', text: '' }), 5000);
  });

  useWebSocket('session_ended', () => {
    setGameStatus(prev => ({
      ...prev,
      sessionStarted: false,
      remainingTime: 0
    }));
    setMessage({ 
      type: 'info', 
      text: '⏰ Game session ended! Chat is now available again.' 
    });
    setTimeout(() => setMessage({ type: '', text: '' }), 7000);
  });

  useWebSocket('player_joined', (data) => {
    const newCount = data.currentPlayerCount || 0;
    const minPlayers = data.minimumPlayers || 3;
    const isReady = newCount >= minPlayers;
    
    setGameStatus(prev => ({
      ...prev,
      gameReady: isReady,
      currentPlayerCount: newCount,
      minimumPlayers: minPlayers,
      playersNeeded: data.playersNeeded || Math.max(0, minPlayers - newCount)
    }));
    
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
          text: `🎉 Minimum players reached! First player can start the session!` 
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
        
        // Check if current user is first player by comparing with firstPlayerId from server
        if (user && user.id && status.firstPlayerId) {
          setIsFirstPlayer(user.id === status.firstPlayerId);
        } else if (user && user.isFirstPlayer !== undefined) {
          // Fallback to isFirstPlayer from login response
          setIsFirstPlayer(user.isFirstPlayer);
        }
      } catch (error) {
        console.error('Failed to fetch game status:', error);
      }
    };
    
    fetchProblems();
    fetchGameStatus();
    
    // Poll game status every 10 seconds as backup (WebSocket handles real-time updates)
    const statusInterval = setInterval(fetchGameStatus, 10000);
    
    return () => clearInterval(statusInterval);
  }, [user]);

  // Timer for session countdown
  useEffect(() => {
    if (!gameStatus.sessionStarted) return;
    
    const timer = setInterval(() => {
      setGameStatus(prev => {
        const remaining = prev.sessionEndTime - Date.now();
        if (remaining <= 0) {
          clearInterval(timer);
          return { ...prev, remainingTime: 0, sessionStarted: false };
        }
        return { ...prev, remainingTime: remaining };
      });
    }, 1000);
    
    return () => clearInterval(timer);
  }, [gameStatus.sessionStarted, gameStatus.sessionEndTime]);

  const handleStartSession = async () => {
    setStartingSession(true);
    try {
      const result = await api.startSession();
      if (result.success) {
        setMessage({ 
          type: 'success', 
          text: '🎮 Game session started! Good luck!' 
        });
      } else {
        setMessage({ 
          type: 'error', 
          text: result.message || 'Failed to start session' 
        });
      }
      setTimeout(() => setMessage({ type: '', text: '' }), 5000);
    } catch (error) {
      setMessage({ 
        type: 'error', 
        text: error.message || 'Failed to start session' 
      });
      setTimeout(() => setMessage({ type: '', text: '' }), 5000);
    } finally {
      setStartingSession(false);
    }
  };

  const handleSubmitAnswer = async (e) => {
    e.preventDefault();
    if (!answerInput.trim() || !selectedProblem) return;
    
    if (!gameStatus.sessionStarted) {
      setMessage({ 
        type: 'error', 
        text: 'Session has not started yet. Waiting for first player to start the game.' 
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

  const formatTime = (milliseconds) => {
    const totalSeconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
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
            gameStatus.sessionStarted
              ? 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200' 
              : gameStatus.gameReady 
                ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' 
                : 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
          }`}>
            <Users className="w-5 h-5" />
            <span className="font-medium">
              {gameStatus.sessionStarted ? (
                <>
                  <Clock className="w-4 h-4 inline mr-1" />
                  {formatTime(gameStatus.remainingTime)}
                </>
              ) : gameStatus.gameReady ? (
                'Ready to Start!'
              ) : (
                `${gameStatus.currentPlayerCount}/${gameStatus.minimumPlayers} Players`
              )}
            </span>
          </div>

          {/* Start Button for First Player */}
          {gameStatus.gameReady && !gameStatus.sessionStarted && isFirstPlayer && (
            <button
              onClick={handleStartSession}
              disabled={startingSession}
              className="px-6 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg font-medium 
                       transition-all flex items-center space-x-2 disabled:opacity-50 disabled:cursor-not-allowed
                       shadow-lg hover:shadow-xl transform hover:scale-105"
            >
              <Play className="w-5 h-5" />
              <span>{startingSession ? 'Starting...' : 'Start Game'}</span>
            </button>
          )}

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
      {!gameStatus.sessionStarted && !gameStatus.gameReady && (
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
                A minimum of <strong>{gameStatus.minimumPlayers} players</strong> must join before the first player can start the session.
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
                  : 'Ready! Waiting for first player to start the session...'}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Session Ready - Waiting to Start */}
      {gameStatus.gameReady && !gameStatus.sessionStarted && (
        <div className="card bg-green-50 dark:bg-green-900/20 border-2 border-green-200 dark:border-green-800">
          <div className="flex items-center space-x-4 p-6">
            <div className="flex-shrink-0">
              <Users className="w-12 h-12 text-green-600 dark:text-green-400" />
            </div>
            <div className="flex-1">
              <h3 className="text-xl font-bold text-green-900 dark:text-green-100 mb-2">
                Ready to Start!
              </h3>
              <p className="text-green-800 dark:text-green-200">
                {isFirstPlayer 
                  ? '👑 You are the first player! Click the "Start Game" button above to begin the 10-minute session.'
                  : 'Waiting for the first player to start the 10-minute game session...'}
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


import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Chat from './Chat';
import { useState } from 'react';

const Layout = () => {
  const [chatOpen, setChatOpen] = useState(false);

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 dark:from-dark-50 dark:to-dark-100">
      <Navbar onToggleChat={() => setChatOpen(!chatOpen)} />
      <main className="container mx-auto px-4 py-6 max-w-7xl">
        <Outlet />
      </main>
      <Chat isOpen={chatOpen} onClose={() => setChatOpen(false)} />
    </div>
  );
};

export default Layout;


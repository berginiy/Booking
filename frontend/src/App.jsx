import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import BookPage from './pages/BookPage.jsx';
import SchedulePage from './pages/SchedulePage.jsx';
import MyBookingsPage from './pages/MyBookingsPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import ChangePasswordPage from './pages/ChangePasswordPage.jsx';
import AdminPage from './pages/admin/AdminPage.jsx';

export default function App() {
  const { user, logout, isAdmin } = useAuth();
  const ready = user && !user.mustChangePassword;

  return (
    <>
      <header className="header">
        <div className="container header-in">
          <div className="brand">
            <span className="brand-mark" />
            <span className="brand-text">Конференц-залы<small>Головной офис</small></span>
          </div>
          {ready && (
            <nav>
              <NavLink to="/" end>Забронировать</NavLink>
              <NavLink to="/schedule">Занятость</NavLink>
              {!isAdmin && <NavLink to="/my">Мои брони</NavLink>}
              {isAdmin && <NavLink to="/admin">Администратор</NavLink>}
            </nav>
          )}
          {user && (
            <div className="userbox">
              <span className="avatar">{user.fullName.charAt(0)}</span>
              <span>{user.fullName}</span>
              {isAdmin && <span className="role-tag">Админ</span>}
              <button className="btn btn-ghost" onClick={logout}>Выйти</button>
            </div>
          )}
        </div>
      </header>
      <main className="container">
        {!user ? <LoginPage /> : user.mustChangePassword ? <ChangePasswordPage /> : (
          <Routes>
            <Route path="/" element={<BookPage />} />
            <Route path="/schedule" element={<SchedulePage />} />
            <Route path="/my" element={isAdmin ? <Navigate to="/" replace /> : <MyBookingsPage />} />
            <Route path="/admin" element={isAdmin ? <AdminPage /> : <Navigate to="/" replace />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        )}
      </main>
    </>
  );
}

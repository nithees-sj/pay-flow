import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { api, setAccessToken } from "../../api/client";
import type { Profile, Token } from "../../api/types";
interface Auth {
  profile: Profile | null;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}
const AuthContext = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const cache = useQueryClient();
  const logout = useCallback(() => {
    setAccessToken(null);
    setProfile(null);
    cache.clear();
  }, [cache]);
  useEffect(() => {
    window.addEventListener("payflow:unauthorized", logout);
    return () => window.removeEventListener("payflow:unauthorized", logout);
  }, [logout]);
  useEffect(() => {
    if (profile?.wallet.status !== "PROVISIONING") return;
    const timer = window.setInterval(() => {
      api<Profile>("/me")
        .then(setProfile)
        .catch(() => undefined);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [profile?.wallet.status]);
  async function login(email: string, password: string) {
    const token = await api<Token>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    setAccessToken(token.accessToken);
    try {
      const next = await api<Profile>("/me");
      cache.clear();
      setProfile(next);
    } catch (e) {
      setAccessToken(null);
      throw e;
    }
  }
  async function register(name: string, email: string, password: string) {
    await api<Profile>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ name, email, password }),
    });
    await login(email, password);
  }
  return (
    <AuthContext.Provider value={{ profile, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error("AuthProvider is required");
  return auth;
}
export function RequireAuth() {
  const { profile } = useAuth();
  const location = useLocation();
  return profile ? (
    <Outlet />
  ) : (
    <Navigate to="/login" state={{ from: location.pathname }} replace />
  );
}

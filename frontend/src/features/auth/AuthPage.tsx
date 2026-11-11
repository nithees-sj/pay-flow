import { useState } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  ArrowRight,
  ArrowUpRight,
  ShieldCheck,
  Check,
  Eye,
  EyeOff,
} from "lucide-react";
import { useAuth } from "./AuthProvider";
import { Logo } from "../../components/Logo";
import { ErrorNotice } from "../../components/Feedback";
import { errorMessage } from "../../api/client";
const base = z.object({
  name: z.string().optional(),
  email: z.string().email("Enter a valid email address."),
  password: z.string().min(1, "Enter your password."),
});
const registration = base.extend({
  name: z.string().trim().min(1, "Enter your name.").max(100),
  password: z
    .string()
    .min(12, "Use at least 12 characters.")
    .refine(
      (value) => new TextEncoder().encode(value).length <= 72,
      "Use at most 72 UTF-8 bytes.",
    ),
});
type Fields = z.infer<typeof base>;
export function AuthPage({ mode }: { mode: "login" | "register" }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const isRegister = mode === "register";
  const [error, setError] = useState("");
  const [show, setShow] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Fields>({
    resolver: zodResolver(isRegister ? registration : base),
  });
  if (auth.profile) return <Navigate to="/" replace />;
  async function submit(values: Fields) {
    setError("");
    try {
      if (isRegister)
        await auth.register(values.name ?? "", values.email, values.password);
      else await auth.login(values.email, values.password);
      navigate("/");
    } catch (e) {
      setError(errorMessage(e));
    }
  }
  return (
    <main className="auth-layout" id="main-content">
      <section className="auth-story">
        <Logo light />
        <div className="auth-story-content">
          <span className="eyebrow">A CLEARER WAY TO MOVE MONEY</span>
          <h1>
            Good things
            <br />
            are in <em>flow.</em>
          </h1>
          <p>
            A simple wallet for the people, plans, and everyday moments that
            matter.
          </p>
          <div className="auth-illustration" aria-hidden="true">
            <div className="orbit orbit-one" />
            <div className="orbit orbit-two" />
            <div className="illustration-card">
              <span>YOUR PERSONAL WALLET</span>
              <div className="illustration-symbol">
                ₹<ArrowUpRight size={45} />
              </div>
              <div className="illustration-bottom">
                <span>Simple. Connected.</span>
                <span>payflow.</span>
              </div>
            </div>
            <div className="illustration-badge">
              <span>
                <Check size={16} />
              </span>
              Every transfer, accounted for.
            </div>
          </div>
        </div>
        <div className="auth-story-footer">
          <ShieldCheck size={18} />A simulated wallet. Real engineering.
        </div>
      </section>
      <section className="auth-form-side">
        <div className="auth-switch">
          {isRegister ? "Already have an account?" : "New to PayFlow?"}{" "}
          <Link to={isRegister ? "/login" : "/register"}>
            {isRegister ? "Sign in" : "Create an account"}{" "}
            <ArrowUpRight size={14} />
          </Link>
        </div>
        <div className="auth-form-wrap">
          <div className="auth-mobile-logo">
            <Logo />
          </div>
          <span className="section-kicker">LET’S GET YOU STARTED</span>
          <h2>{isRegister ? "Make room for possibility." : "Welcome back."}</h2>
          <p className="form-intro">
            {isRegister
              ? "Your personal INR wallet is just a few details away."
              : "Sign in to pick up right where you left off."}
          </p>
          {error && <ErrorNotice>{error}</ErrorNotice>}
          <form onSubmit={handleSubmit(submit)} noValidate>
            {isRegister && (
              <div className="field">
                <label htmlFor="name">Full name</label>
                <input
                  id="name"
                  autoComplete="name"
                  placeholder="Your name"
                  {...register("name")}
                  aria-invalid={!!errors.name}
                />
                {errors.name && (
                  <span className="field-error">{errors.name.message}</span>
                )}
              </div>
            )}
            <div className="field">
              <label htmlFor="email">Email address</label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                {...register("email")}
                aria-invalid={!!errors.email}
              />
              {errors.email && (
                <span className="field-error">{errors.email.message}</span>
              )}
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <div className="password-field">
                <input
                  id="password"
                  type={show ? "text" : "password"}
                  autoComplete={
                    isRegister ? "new-password" : "current-password"
                  }
                  placeholder={
                    isRegister
                      ? "At least 12 characters"
                      : "Enter your password"
                  }
                  {...register("password")}
                  aria-invalid={!!errors.password}
                />
                <button
                  type="button"
                  aria-label={show ? "Hide password" : "Show password"}
                  onClick={() => setShow(!show)}
                >
                  {show ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {errors.password && (
                <span className="field-error">{errors.password.message}</span>
              )}
            </div>
            <button
              className="button button-primary button-full"
              disabled={isSubmitting}
            >
              {isSubmitting
                ? "Just a moment…"
                : isRegister
                  ? "Create your wallet"
                  : "Sign in"}
              <ArrowRight size={18} />
            </button>
          </form>
          <p className="auth-disclaimer">
            <ShieldCheck size={16} />
            PayFlow is a portfolio demo. Please use a test password and never
            enter real financial details.
          </p>
        </div>
        <div className="auth-bottom">
          Money, made simple. <span>© {new Date().getFullYear()} PayFlow</span>
        </div>
      </section>
    </main>
  );
}

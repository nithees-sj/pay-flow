import { ArrowUpRight } from "lucide-react";
export function Logo({ light = false }: { light?: boolean }) {
  return (
    <span className={`logo ${light ? "logo-light" : ""}`}>
      <span className="logo-mark">
        <ArrowUpRight size={24} strokeWidth={3} />
      </span>
      payflow<span className="logo-dot">.</span>
    </span>
  );
}

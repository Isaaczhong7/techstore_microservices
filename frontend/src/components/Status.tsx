import type React from "react";
import { formatEnum } from "../lib/format";

export function Metric({ label, value, icon }: { label: string; value: string | number; icon: React.ReactNode }) {
  return (
    <div className="metric">
      {icon}
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

export function Status({ active }: { active: boolean }) {
  return <span className={active ? "status active" : "status inactive"}>{active ? "Active" : "Inactive"}</span>;
}

export function StatusText({ value }: { value: string }) {
  return <strong className="status-text">{formatEnum(value)}</strong>;
}

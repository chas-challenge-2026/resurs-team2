import type { ReactNode } from "react";

import "./Panel.css";

interface PanelProps {
  title: ReactNode;
  children: ReactNode;
  className?: string;
}

export function Panel({
  title,
  children,
  className = "",
}: PanelProps) {
  return (
    <section className={`panel ${className}`.trim()}>
      <div className="panel-heading">
        {title}
      </div>

      <div className="panel-body">
        {children}
      </div>
    </section>
  );
}

export default Panel;
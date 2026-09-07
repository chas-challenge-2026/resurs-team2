import type { ReactNode } from "react";

import "./Panel.css";

interface PanelProps {
  title: ReactNode;
  children: ReactNode;
  className?: string;
}

export const Panel = ({
  title,
  children,
  className = "",
}: PanelProps) => {
  return (
    <div
      className={`panel ${className}`.trim()}
    >
      <div className="panel-heading">
        {title}
      </div>

      <div className="panel-body">
        {children}
      </div>
    </div>
  );
};
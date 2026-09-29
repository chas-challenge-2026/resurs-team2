import "./MetricTooltips.css";

interface MetricTooltipProps {
  label: string;
  description: string;
  formula: string;
  calculation: string;
}

export const MetricTooltip = ({
  label,
  description,
  formula,
  calculation,
}: MetricTooltipProps) => {
  const tooltipId = `tooltip-${label
    .toLowerCase()
    .replace(/\s+/g, "-")}`;

  return (
    <span className="metric-tooltip">
      <span
        className="metric-tooltip-trigger"
        tabIndex={0}
        aria-describedby={tooltipId}
      >
        {label}

        <span
          className="metric-tooltip-icon"
          aria-hidden="true"
        >
          ?
        </span>
      </span>

      <span
        id={tooltipId}
        className="metric-tooltip-content"
        role="tooltip"
      >
        <strong className="metric-tooltip-title">
          {label}
        </strong>

        <span className="metric-tooltip-description">
          {description}
        </span>

        <span className="metric-tooltip-formula">
          {formula}
        </span>

        <code className="metric-tooltip-calculation">
          {calculation}
        </code>
      </span>
    </span>
  );
};
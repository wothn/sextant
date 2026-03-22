import Svg, { Circle } from "react-native-svg";

type DonutChartSegment = {
  value: number;
  color: string;
};

type DonutChartProps = {
  size?: number;
  strokeWidth?: number;
  segments: DonutChartSegment[];
  trackColor: string;
};

export function DonutChart({
  size = 164,
  strokeWidth = 22,
  segments,
  trackColor,
}: DonutChartProps) {
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const total = segments.reduce((sum, item) => sum + item.value, 0);
  let offset = 0;

  return (
    <Svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
      <Circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        stroke={trackColor}
        strokeWidth={strokeWidth}
        fill="none"
      />
      {segments.map((segment, index) => {
        const portion = total > 0 ? segment.value / total : 0;
        const dashArray = `${portion * circumference} ${circumference}`;
        const circle = (
          <Circle
            key={`${segment.color}-${index}`}
            cx={size / 2}
            cy={size / 2}
            r={radius}
            stroke={segment.color}
            strokeWidth={strokeWidth}
            strokeDasharray={dashArray}
            strokeDashoffset={-offset}
            strokeLinecap="round"
            fill="none"
            originX={size / 2}
            originY={size / 2}
            rotation={-90}
          />
        );
        offset += portion * circumference;
        return circle;
      })}
    </Svg>
  );
}

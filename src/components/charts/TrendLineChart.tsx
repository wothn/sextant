import Svg, { Defs, LinearGradient, Path, Stop, Text as SvgText } from "react-native-svg";

type TrendPoint = {
  label: string;
  value: number;
};

type TrendLineChartProps = {
  points: TrendPoint[];
  width?: number;
  height?: number;
  color: string;
  fillColor: string;
  axisColor: string;
  labelColor: string;
};

export function TrendLineChart({
  points,
  width = 320,
  height = 180,
  color,
  fillColor,
  axisColor,
  labelColor,
}: TrendLineChartProps) {
  const paddingX = 18;
  const paddingTop = 16;
  const labelHeight = 28;
  const chartHeight = height - paddingTop - labelHeight;
  const chartWidth = width - paddingX * 2;
  const max = Math.max(...points.map((point) => point.value), 1);
  const stepX = points.length > 1 ? chartWidth / (points.length - 1) : 0;

  const coordinates = points.map((point, index) => {
    const x = paddingX + stepX * index;
    const y = paddingTop + chartHeight - (point.value / max) * chartHeight;
    return { ...point, x, y };
  });

  const linePath = coordinates
    .map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`)
    .join(" ");
  const fillPath = `${linePath} L ${paddingX + chartWidth} ${paddingTop + chartHeight} L ${paddingX} ${paddingTop + chartHeight} Z`;

  return (
    <Svg width={width} height={height} viewBox={`0 0 ${width} ${height}`}>
      <Defs>
        <LinearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor={fillColor} stopOpacity={1} />
          <Stop offset="1" stopColor={fillColor} stopOpacity={0.1} />
        </LinearGradient>
      </Defs>
      <Path
        d={`M ${paddingX} ${paddingTop + chartHeight} H ${paddingX + chartWidth}`}
        stroke={axisColor}
        strokeWidth={1}
        fill="none"
      />
      <Path d={fillPath} fill="url(#trendFill)" />
      <Path d={linePath} stroke={color} strokeWidth={3} fill="none" />
      {coordinates.map((point) => (
        <SvgText
          key={point.label}
          x={point.x}
          y={height - 6}
          fontSize="11"
          fill={labelColor}
          textAnchor="middle"
        >
          {point.label}
        </SvgText>
      ))}
    </Svg>
  );
}

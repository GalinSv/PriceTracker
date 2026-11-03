import { useMemo } from "react";

import { Typography } from "@mui/material";
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from "recharts";

import type { Offer, PriceSnapshot } from "../../types/api";
import { formatDate, formatDateTime, formatPrice } from "../../utils/format";

const COLORS = ["#0f6e56", "#d85a30", "#185fa5", "#993556", "#854f0b", "#534ab7"];

type Props = {
    offers: Offer[];
    historyByOffer: Record<number, PriceSnapshot[]>;
};

type ChartPoint = { time: number } & Record<string, number>;

function PriceHistoryChart({ offers, historyByOffer }: Props) {
    // One point per price change. Each line ends at the last check with the current price,
    // because history only stores changes and the last price was still valid then.
    const points = useMemo(() => {
        const result: ChartPoint[] = [];
        for (const offer of offers) {
            const key = `offer${offer.id}`;
            for (const snapshot of historyByOffer[offer.id] ?? []) {
                result.push({ time: new Date(snapshot.capturedAt).getTime(), [key]: snapshot.price } as ChartPoint);
            }
            if (offer.currentPrice != null && offer.lastCheckedAt) {
                result.push({ time: new Date(offer.lastCheckedAt).getTime(), [key]: offer.currentPrice } as ChartPoint);
            }
        }
        return result.sort((a, b) => a.time - b.time);
    }, [offers, historyByOffer]);

    if (points.length === 0) {
        return <Typography color="text.secondary">Все още няма история на цената.</Typography>;
    }

    return (
        <ResponsiveContainer width="100%" height={320}>
            <LineChart data={points} margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis
                    dataKey="time"
                    type="number"
                    scale="time"
                    domain={["dataMin", "dataMax"]}
                    padding={{ left: 16, right: 16 }}
                    tickFormatter={formatDate}
                />
                <YAxis
                    domain={["auto", "auto"]}
                    tickFormatter={(value: number) => `${value} €`}
                    width={70}
                />
                <Tooltip
                    labelFormatter={(label) => formatDateTime(Number(label))}
                    formatter={(value) => formatPrice(Number(value))}
                />
                <Legend />
                {offers.map((offer, index) => (
                    <Line
                        key={offer.id}
                        type="stepAfter"
                        dataKey={`offer${offer.id}`}
                        name={offer.storeName}
                        stroke={COLORS[index % COLORS.length]}
                        strokeWidth={2}
                        connectNulls
                        isAnimationActive={false}
                    />
                ))}
            </LineChart>
        </ResponsiveContainer>
    );
}

export default PriceHistoryChart;

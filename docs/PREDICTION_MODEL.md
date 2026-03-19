# Gold Price Prediction Model Documentation

## Overview

The GoldPulse app includes a gold price prediction feature that calculates a composite score based on macroeconomic indicators. This document describes the scoring formula, data sources, and implementation details.

## Scoring Formula

The gold score is calculated as a weighted sum of normalized factor scores:

```
Gold Score = Σ (Factor Weight × Normalized Factor Score)
```

### Factors and Weights

| Factor | Weight | Correlation | Scoring Formula |
|--------|--------|-------------|-----------------|
| DXY (Dollar Index) | 35% | Inverse | Score = -0.5 × (% change) |
| US10Y (10Y Yield) | 30% | Inverse | Score = -0.4 × (% change) |
| Breakeven10Y (Inflation) | 20% | Positive | Score = +0.5 × (% change) |
| VIX (Risk Proxy) | 15% | Positive | Score = +0.3 × (% change) |

### Score Interpretation

- **Bullish** (Score > 0.5): Conditions favor higher gold prices
  - Rising factors: Breakeven10Y, VIX
  - Falling factors: DXY, US10Y
  
- **Neutral** (-0.5 ≤ Score ≤ 0.5): Mixed signals, no clear direction

- **Bearish** (Score < -0.5): Conditions favor lower gold prices
  - Rising factors: DXY, US10Y
  - Falling factors: Breakeven10Y, VIX

## Data Sources

### Primary Sources

| Indicator | FRED Code | Description |
|-----------|-----------|-------------|
| DXY | DTWEXBGS | Trade Weighted U.S. Dollar Index |
| US10Y | DGS10 | 10-Year Treasury Constant Maturity Rate |
| Breakeven10Y | T10YIE | 10-Year Breakeven Inflation Rate |
| VIX | VIXCLS | CBOE Volatility Index |

### Fallback Sources

| Indicator | Yahoo Finance Symbol | Description |
|-----------|---------------------|-------------|
| DXY | DX-Y.NYB | US Dollar Index |
| US10Y | ^TNX | Treasury Yield 10 Years |
| VIX | ^VIX | CBOE Volatility Index |

### Fallback Calculation

If Breakeven10Y is unavailable, it can be calculated as:
```
Breakeven10Y = 10Y Nominal Yield - 10Y TIPS Yield
```

## Implementation

### Key Files

- `IndicatorData.kt`: Data models for indicators and predictions
- `IndicatorApiService.kt`: API service for fetching indicator data
- `PredictionEngine.kt`: Core scoring logic
- `PredictionScreen.kt`: UI for prediction display
- `PredictionScoringTest.kt`: Unit tests for scoring logic

### API Endpoints

#### FRED CSV Endpoint
```
https://fred.stlouisfed.org/graph/fredgraph.csv?id={SERIES_ID}
```

#### Yahoo Finance Download
```
https://query1.finance.yahoo.com/v7/finance/download/{SYMBOL}?period1={START}&period2={END}&interval=1d&events=history
```

## Forecast Band Calculation

The forecast band is calculated using statistical analysis of historical gold prices:

### Method
1. Calculate Moving Average (MA) over the last N data points
2. Calculate Standard Deviation (σ) of the same period
3. Upper Band = MA + σ
4. Lower Band = MA - σ

### Requirements
- Minimum 30 data points for valid forecast band
- Uses last 30-60 points for calculation

## Arabic Labels and RTL Support

The prediction screen includes full Arabic (العربية) language support with RTL layout:

| English | Arabic |
|---------|--------|
| Prediction Score | درجة التنبؤ |
| Bullish | صاعد |
| Neutral | محايد |
| Bearish | هابط |
| Factor Distribution | توزيع العوامل |
| Current Indicators | المؤشرات الحالية |
| Upper Band | الحد الأعلى |
| Lower Band | الحد الأدنى |

## Economic Rationale

### DXY (Dollar Index)
Gold is priced in USD globally. A weaker dollar makes gold cheaper for foreign buyers, increasing demand and price.

### US10Y (10-Year Treasury Yield)
Gold pays no yield. When Treasury yields rise, the opportunity cost of holding gold increases, putting downward pressure on gold prices.

### Breakeven10Y (Inflation Expectations)
Gold is a traditional inflation hedge. Higher inflation expectations increase demand for gold as a store of value.

### VIX (Fear Index)
During market stress and uncertainty, investors seek safe-haven assets like gold, driving up prices.

## Testing

Run the unit tests to verify scoring logic:

```bash
./gradlew test --tests "com.goldpulse.PredictionScoringTest"
```

Key test cases:
- Factor weight sum equals 1.0
- Inverse correlation multipliers are negative
- Positive correlation multipliers are positive
- Badge thresholds work correctly
- Forecast band calculations are accurate
- Arabic labels are correct

## References

- [FRED API Documentation](https://fred.stlouisfed.org/docs/api/fred/)
- [Yahoo Finance API](https://finance.yahoo.com/)
- [Gold Price Correlation Studies](https://www.gold.org/goldhub/research)

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2024-03 | Initial implementation |
| 1.1 | 2024-03 | Added Arabic labels, RTL support |
| 1.2 | 2024-03 | Added forecast band chart |

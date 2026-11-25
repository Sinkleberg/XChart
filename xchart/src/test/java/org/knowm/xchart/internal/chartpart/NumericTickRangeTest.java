package org.knowm.xchart.internal.chartpart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.internal.chartpart.Axis_.Direction;
import org.knowm.xchart.style.XYStyler;

class NumericTickRangeTest {

  private static Function<Double, String> within(double min, double max) {
    return value -> {
      assertThat(value).isBetween(min, max);
      return Double.toString(value);
    };
  }

  @Test
  void formatsOnlyVisibleGridMultiplesIncludingExactBounds() {
    for (Direction direction : Direction.values()) {
      AxisTickCalculator ticks =
          new AxisTickCalculator_Callback(within(-2, 3), direction, 600, -2, 3, new XYStyler());
      assertThat(ticks.getTickLabels()).first().isEqualTo("-2.0");
      assertThat(ticks.getTickLabels()).last().isEqualTo("3.0");
    }
  }

  @Test
  void equallySpacedDataOutsideTheVisibleBandIsNeverFormatted() {
    AxisTickCalculator ticks =
        new AxisTickCalculator_Callback(
            within(1, 3), Direction.X, 600, 1, 3, List.of(1d, 2d, 3d, 4d), new XYStyler());
    assertThat(ticks.getTickLabels()).containsExactly("1.0", "2.0", "3.0");
  }

  @Test
  void builtInNumberLayoutKeepsTheGridForSevenDecimalPlaces() {
    XYStyler styler = new XYStyler();
    styler.setXAxisDecimalPattern("0.0000000");
    AxisTickCalculator ticks = new AxisTickCalculator_Number(Direction.X, 755, 0, 1e-5, styler);
    assertThat(ticks.getTickLabels())
        .containsExactly(
            "0.0000000", "0.0000020", "0.0000040", "0.0000060", "0.0000080", "0.0000100");
  }

  @Test
  void builtInFormatterHandlesFiniteLimitsAtBothEnds() {
    for (double[] limits :
        new double[][] {{1e308, Double.MAX_VALUE}, {-Double.MAX_VALUE, -1e308}}) {
      AxisTickCalculator ticks =
          new AxisTickCalculator_Number(Direction.X, 800, limits[0], limits[1], new XYStyler());
      assertThat(ticks.getTickLabels()).isNotEmpty();
      assertThat(ticks.getTickLocations()).allMatch(Double::isFinite);
    }
  }

  @Test
  void aNarrowBandUsesNiceGridMultiplesInsteadOfTheMidpoint() {
    double min = 2e-12;
    double max = 7e-12;
    XYStyler styler = new XYStyler();
    styler.setYAxisTickMarkSpacingHint(100);
    AxisTickCalculator ticks =
        new AxisTickCalculator_Callback(within(min, max), Direction.Y, 600, min, max, styler);
    assertThat(ticks.getTickLabels())
        .containsExactly("2.0E-12", "3.0E-12", "4.0E-12", "5.0E-12", "6.0E-12", "7.0E-12");
  }

  @Test
  void noisyEndpointsKeepTheirBoundaryLabels() {
    double sum = 0;
    for (int i = 0; i < 10; i++) {
      sum += 0.1;
    }
    double[][] ranges = {{0.1 + 0.2, 0.1 + 0.7}, {0, sum}, {-sum, -(0.1 + 0.2)}};
    String[][] expected = {
      {"0.3", "0.4", "0.5", "0.6", "0.7", "0.8"},
      {"0.0", "0.2", "0.4", "0.6", "0.8", "1.0"},
      {"-1.0", "-0.9", "-0.8", "-0.7", "-0.6", "-0.5", "-0.4", "-0.3"}
    };
    for (Direction direction : Direction.values()) {
      for (int i = 0; i < ranges.length; i++) {
        XYStyler styler = new XYStyler();
        styler.setPlotContentSize(0.9);
        styler.setDecimalPattern("0.0");
        // Explicit hints make the expected grid independent of the default X/Y density.
        styler.setXAxisTickMarkSpacingHint(100);
        styler.setYAxisTickMarkSpacingHint(100);
        AxisTickCalculator ticks =
            new AxisTickCalculator_Number(direction, 600, ranges[i][0], ranges[i][1], styler);
        assertThat(ticks.getTickLabels()).containsExactly(expected[i]);
        assertThat(ticks.getTickLocations())
            .allMatch(location -> location >= 30 && location <= 570);
      }
    }
  }

  @Test
  void noisyBoundaryCallbacksStayInsideTheActualDataRange() {
    double min = 0.1 + 0.2;
    double max = 0.1 + 0.7;
    for (Direction direction : Direction.values()) {
      List<Double> calls = new ArrayList<>();
      XYStyler styler = new XYStyler();
      styler.setPlotContentSize(0.9);
      AxisTickCalculator ticks =
          new AxisTickCalculator_Callback(
              value -> {
                calls.add(value);
                within(min, max).apply(value);
                return String.format(Locale.ROOT, "%.1f", value);
              },
              direction,
              600,
              min,
              max,
              styler);
      assertThat(calls).allMatch(value -> value >= min && value <= max);
      assertThat(calls).contains(min, max);
      assertThat(ticks.getTickLabels()).first().isEqualTo("0.3");
      assertThat(ticks.getTickLabels()).last().isEqualTo("0.8");
      assertThat(ticks.getTickLocations()).first().isEqualTo(30.0);
      assertThat(ticks.getTickLocations()).last().isEqualTo(570.0);
    }
  }

  @Test
  void genuinelyOutOfRangeGridMultiplesAreExcluded() {
    AxisTickCalculator ticks =
        new AxisTickCalculator_Callback(
            within(0.31, 0.79), Direction.Y, 600, 0.31, 0.79, new XYStyler());
    assertThat(ticks.getTickLabels())
        .containsExactly("0.35", "0.4", "0.45", "0.5", "0.55", "0.6", "0.65", "0.7", "0.75");
  }

  @Test
  void aSmallPlotRetriesAFinerStepWhenNoGridMultipleIsVisible() {
    XYStyler styler = new XYStyler();
    styler.setPlotContentSize(0.9);
    styler.setXAxisTickMarkSpacingHint(100);
    AxisTickCalculator ticks =
        new AxisTickCalculator_Callback(within(0.01, 0.42), Direction.X, 112, 0.01, 0.42, styler);
    assertThat(ticks.getTickLabels()).containsExactly("0.2", "0.4");
  }

  @Test
  void callbackLayoutDoesNotMeasureInvisibleNeighbourLabels() {
    Function<Double, String> formatter =
        value -> {
          assertThat(value).isBetween(0.0, 100.0);
          return value < 0
              ? "negative label longer than twenty characters " + value
              : Double.toString(value);
        };
    AxisTickCalculator plain =
        new AxisTickCalculator_Callback(
            value -> Double.toString(value), Direction.X, 600, 0, 100, new XYStyler());
    AxisTickCalculator prefixed =
        new AxisTickCalculator_Callback(formatter, Direction.X, 600, 0, 100, new XYStyler());
    assertThat(prefixed.getTickLabels()).containsExactlyElementsOf(plain.getTickLabels());
    assertThat(prefixed.getTickLocations()).containsExactlyElementsOf(plain.getTickLocations());
    assertThat(prefixed.getTickLabels()).hasSizeGreaterThan(2);
  }

  @Test
  void longVisibleCallbackLabelsStillControlFit() {
    AxisTickCalculator shortLabels =
        new AxisTickCalculator_Callback(within(0, 100), Direction.X, 600, 0, 100, new XYStyler());
    AxisTickCalculator longLabels =
        new AxisTickCalculator_Callback(
            value -> "visible measurement value " + within(0, 100).apply(value),
            Direction.X,
            600,
            0,
            100,
            new XYStyler());
    assertThat(longLabels.getTickLabels())
        .isNotEmpty()
        .hasSizeLessThan(shortLabels.getTickLabels().size());
  }

  @Test
  void theFirstVisibleLongLabelControlsVerticalFit() {
    AxisTickCalculator ticks =
        new AxisTickCalculator_Callback(
            value ->
                value == 1
                    ? "visible label longer than twenty characters"
                    : within(1, 9).apply(value),
            Direction.Y,
            600,
            1,
            9,
            new XYStyler());
    assertThat(ticks.getTickLabels()).containsExactly("2.0", "4.0", "6.0", "8.0");
  }

  @Test
  void aRenderedLogChartRespectsBothCustomFormatterRanges() {
    XYChart chart = new XYChartBuilder().width(600).height(400).build();
    XYStyler styler = chart.getStyler();
    styler.setXAxisLogarithmic(true);
    styler.setYAxisLogarithmic(true);
    styler.setXAxisLogarithmicDecadeOnly(false);
    styler.setYAxisLogarithmicDecadeOnly(false);
    styler.setXAxisMin(150.0);
    styler.setXAxisMax(895.0);
    styler.setYAxisMin(150.0);
    styler.setYAxisMax(895.0);
    styler.setXAxisTickLabelsFormattingFunction(within(150, 895));
    styler.setYAxisTickLabelsFormattingFunction(within(150, 895));
    chart.addSeries("bounded", new double[] {150, 895}, new double[] {150, 895});
    assertThat(BitmapEncoder.getBufferedImage(chart).getHeight()).isEqualTo(400);
    List<String> expected = List.of("200.0", "300.0", "400.0", "500.0", "600.0", "700.0", "800.0");
    assertThat(chart.axisPair.getXAxis().getAxisTickCalculator().getTickLabels())
        .containsExactlyElementsOf(expected);
    assertThat(chart.axisPair.getYAxis().getAxisTickCalculator().getTickLabels())
        .containsExactlyElementsOf(expected);
  }

  @Test
  void logarithmicFormattersNeverReceiveInvisibleValues() {
    for (Direction direction : Direction.values()) {
      for (boolean decadesOnly : new boolean[] {false, true}) {
        for (double max : new double[] {850, 895}) {
          XYStyler styler = new XYStyler();
          styler.setPlotContentSize(0.9);
          styler.setXAxisLogarithmicDecadeOnly(decadesOnly);
          styler.setYAxisLogarithmicDecadeOnly(decadesOnly);
          AxisTickCalculator ticks =
              new AxisTickCalculator_Logarithmic(
                  direction, 600, 150, max, styler, within(150, max));
          if (decadesOnly) {
            assertThat(ticks.getTickLabels()).containsOnlyNulls();
          } else {
            assertThat(ticks.getTickLabels())
                .containsExactly("200.0", "300.0", "400.0", "500.0", "600.0", "700.0", "800.0");
          }
          assertThat(ticks.getTickLocations())
              .allMatch(location -> location >= 30 && location <= 570);
        }
      }
    }
  }

  @Test
  void logarithmicNarrowBandFallsBackToDistinctValidEndpoints() {
    XYStyler styler = new XYStyler();
    styler.setPlotContentSize(0.9);
    AxisTickCalculator ticks =
        new AxisTickCalculator_Logarithmic(Direction.Y, 600, 310, 320, styler, within(310, 320));
    assertThat(ticks.getTickLabels()).containsExactly("310.0", "320.0");
    assertThat(ticks.getTickLocations()).containsExactly(30.0, 570.0);
  }

  @Test
  void logarithmicSingleCandidateDoesNotDuplicateTheEndpointFallback() {
    XYStyler styler = new XYStyler();
    styler.setYAxisLogarithmicDecadeOnly(false);
    AxisTickCalculator ticks =
        new AxisTickCalculator_Logarithmic(Direction.Y, 600, 350, 450, styler, within(350, 450));
    assertThat(ticks.getTickLabels()).containsExactly("350.0", "450.0");
    assertThat(ticks.getTickLocations()).hasSize(2);
  }

  @Test
  void logarithmicNoisyEndpointsClampBeforeFormatting() {
    double min = 0.1 + 0.2;
    double max = 0.1 + 0.7;
    XYStyler styler = new XYStyler();
    styler.setPlotContentSize(0.9);
    styler.setYAxisLogarithmicDecadeOnly(false);
    AxisTickCalculator ticks =
        new AxisTickCalculator_Logarithmic(Direction.Y, 600, min, max, styler, within(min, max));
    assertThat(ticks.getTickLabels()).first().isEqualTo(Double.toString(min));
    assertThat(ticks.getTickLabels()).last().isEqualTo(Double.toString(max));
    assertThat(ticks.getTickLocations()).first().isEqualTo(30.0);
    assertThat(ticks.getTickLocations()).last().isEqualTo(570.0);
  }
}

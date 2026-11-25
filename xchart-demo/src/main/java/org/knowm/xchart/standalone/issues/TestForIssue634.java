package org.knowm.xchart.standalone.issues;

import java.util.Arrays;
import java.util.List;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.SwingWrapper;
import org.knowm.xchart.style.CategoryStyler;

/**
 * Issue #634: "I found negative number label on y axis, but the data are all positive."
 *
 * <p>All Y values are positive (60 M .. 23 B). The numeric tick generator used to start below the
 * axis minimum, allowing a negative label to leak into the bottom plot margin. It now selects only
 * visible grid multiples before formatting them.
 *
 * <p>The custom formatting function below tags any negative value it is asked to format with a
 * "NEG:" prefix so the bogus label is obvious in the rendered chart / console.
 */
public class TestForIssue634 {

  public static CategoryChart getChart() {

    CategoryChart chart =
        new CategoryChartBuilder().width(1200).height(500).title("Issue #634").build();

    CategoryStyler styler = chart.getStyler();
    styler.setPlotContentSize(0.8);
    styler.setStacked(true);
    styler.setLabelsVisible(true);

    // Prints/marks any negative value the axis asks us to format -- data is all positive.
    styler.setYAxisTickLabelsFormattingFunction(
        value -> {
          if (value < 0) {
            System.out.println("BUG: formatting a NEGATIVE axis value: " + value.longValue());
            return "NEG:" + value.longValue();
          }
          return value.longValue() + "B";
        });

    List<String> x =
        Arrays.asList("others", "aaaaaaaa", "bbbbbbbb", "ccccccc", "ddddddd", "eeeeeee");
    List<Long> y =
        Arrays.asList(23073516369L, 1466500686L, 1248980380L, 1248980380L, 60126163L, 60126163L);

    // Leave the axis minimum at its default (0), preserving the bars' meaningful baseline tick.
    // Before the fix, even this zero minimum could produce a negative tick.

    chart.addSeries("1111111", x, y);

    return chart;
  }

  public static void main(String[] args) {

    new SwingWrapper<>(getChart()).displayChart();
  }
}

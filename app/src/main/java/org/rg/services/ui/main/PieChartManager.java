package org.rg.services.ui.main;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PieChartManager {
    PieChart pieChart;
    List<PieEntry> pieEntries;
    List<Integer> pieEntriesColors;

    public PieChartManager(PieChart pieChart, boolean percentage) {
        this.pieChart = pieChart;
        pieEntries = new ArrayList<>();
        pieEntriesColors = new ArrayList<>();
        String label = "type";
        PieDataSet pieDataSet = new PieDataSet(pieEntries,label);
        pieDataSet.setValueTextSize(12f);
        pieDataSet.setColors(pieEntriesColors);
        PieData pieData = new PieData(pieDataSet);
        pieDataSet.setValueTextColor(Color.WHITE);
        pieDataSet.setValueTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        pieData.setDrawValues(true);
        pieChart.setData(pieData);
        pieChart.setTransparentCircleColor(50);
        pieChart.setHoleColor(0);
        pieChart.getLegend().setEnabled(false);
        pieChart.setEntryLabelTextSize(16);
        pieChart.setEntryLabelTypeface(Typeface.defaultFromStyle(Typeface.BOLD_ITALIC));
        pieChart.setRotationEnabled(false);
        if (percentage) {
            pieChart.setUsePercentValues(true);
            pieData.setValueFormatter(new PercentFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return mFormat.format(value) + "%";
                }

                @Override
                public String getPieLabel(float value, PieEntry pieEntry) {
                    return mFormat.format(value) + "%";
                }
            });
        }
    }

    public void setup(Map<String, Integer> labelsAndColors) {
        pieEntries.clear();
        for (Map.Entry<String, Integer> labelAndColor : labelsAndColors.entrySet()) {
            pieEntries.add(new PieEntry(0F, labelAndColor.getKey()));
            pieEntriesColors.add(ResourcesCompat.getColor(pieChart.getResources(), labelAndColor.getValue(), null));
        }
    }

    public void setData(Collection<Float> values) {
        Iterator<Float> valuesIterator = values.iterator();
        for (PieEntry pieEntry : pieEntries) {
           pieEntry.setY(Math.abs(valuesIterator.next()));
        }
        pieChart.notifyDataSetChanged();
        pieChart.invalidate();
    }

    public void visible() {
        pieChart.setVisibility(View.VISIBLE);
    }

    public void invisible() {
        pieChart.setVisibility(View.INVISIBLE);
    }
}

package org.rg.services.ui.main;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PieChartManager {
    private final static Random random;
    PieChart pieChart;
    List<PieEntry> pieEntries;
    List<Integer> pieEntriesColors;
    List<Integer> pieValuesColors;
    ViewGroup parent;
    Map<String, Integer> colorForLabel;

    static {
        random = new Random();
        //Nice seeds: 244
        random.setSeed(LocalDateTime.now().getDayOfYear());
    }

    public PieChartManager(PieChart pieChart, boolean percentage) {
        this.pieChart = pieChart;
        pieEntries = new ArrayList<>();
        pieEntriesColors = new ArrayList<>();
        pieValuesColors = new ArrayList<>();
        PieDataSet pieDataSet = new PieDataSet(pieEntries,null);
        pieDataSet.setValueTextSize(12f);
        pieDataSet.setColors(pieEntriesColors);
        PieData pieData = new PieData(pieDataSet);
        //pieDataSet.setValueTextColor(Color.WHITE);
        pieDataSet.setValueTextColors(pieValuesColors);
        pieDataSet.setValueTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        pieDataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        pieDataSet.setUsingSliceColorAsValueLineColor(true);
        pieDataSet.setValueLinePart1Length(0.5f);
        pieDataSet.setSliceSpace(1f);
        pieData.setDrawValues(true);
        pieChart.setData(pieData);
        pieChart.setExtraLeftOffset(23f);
        pieChart.setExtraRightOffset(23f);
        //pieChart.setTransparentCircleAlpha(88);
        //pieChart.setTransparentCircleColor(ResourcesCompat.getColor(pieChart.getResources(), R.color.dark_red, null));
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setEntryLabelColor(Color.WHITE);
        pieChart.setEntryLabelTextSize(16);
        pieChart.setEntryLabelTypeface(Typeface.defaultFromStyle(Typeface.BOLD_ITALIC));
        pieChart.getLegend().setEnabled(false);
        pieChart.getDescription().setEnabled(false);
        //pieChart.setRotationEnabled(false);
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
        colorForLabel = new LinkedHashMap<>();
    }

    public void setup(Map<String, Integer> labelsAndColors) {
        pieEntries.clear();
        pieEntriesColors.clear();
        pieValuesColors.clear();
        for (Map.Entry<String, Integer> labelAndColor : labelsAndColors.entrySet()) {
            pieEntries.add(new PieEntry(0F, labelAndColor.getKey()));
            Integer color = labelAndColor.getValue();
            if (color == null) {
                color = randomColor();
            }
            pieEntriesColors.add((color & 0x00FFFFFF) | 0xE0000000);
            pieValuesColors.add(color);
            colorForLabel.put(labelAndColor.getKey(), color);
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
        if (pieChart.getVisibility() == View.INVISIBLE) {
            pieChart.animateXY(700, 700);
            pieChart.setVisibility(View.VISIBLE);
        }
    }

    public void invisible() {
        pieChart.setVisibility(View.INVISIBLE);
    }

    public int getOrGenerateColorFor(String label){
        Integer color = colorForLabel.get(label);
        if (color == null) {
            colorForLabel.put(label, color = randomColor());
        }
        return color;
    }

    public final static int randomColor() {
        return Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }
}

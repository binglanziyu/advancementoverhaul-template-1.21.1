/*
 * Decompiled with CFR 0.152.
 */
package com.example.advancementoverhaul.data.model;

import java.util.List;

public class MonologueCategory {
    private double weight = 1.0;
    private List<MonologueEntry> texts;

    public double getWeight() {
        return this.weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public List<MonologueEntry> getTexts() {
        return this.texts;
    }

    public void setTexts(List<MonologueEntry> texts) {
        this.texts = texts;
    }

    public static class MonologueEntry {
        private String text = "";
        private double weight = 1.0;

        public String getText() {
            return this.text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public double getWeight() {
            return this.weight;
        }

        public void setWeight(double weight) {
            this.weight = weight;
        }
    }
}


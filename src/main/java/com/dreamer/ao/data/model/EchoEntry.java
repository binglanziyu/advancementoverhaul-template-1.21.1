package com.dreamer.ao.data.model;

public class EchoEntry {
    private String id;
    private EchoCondition condition;
    private String[] texts;
    private double weight = 1.0;
    private int cooldownSeconds = 300;
    private boolean onceOnly = false;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public EchoCondition getCondition() {
        return this.condition;
    }

    public void setCondition(EchoCondition condition) {
        this.condition = condition;
    }

    public String[] getTexts() {
        return this.texts;
    }

    public void setTexts(String[] texts) {
        this.texts = texts;
    }

    public double getWeight() {
        return this.weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public int getCooldownSeconds() {
        return this.cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public boolean isOnceOnly() {
        return this.onceOnly;
    }

    public void setOnceOnly(boolean onceOnly) {
        this.onceOnly = onceOnly;
    }

    public static class EchoCondition {
        private String type;
        private String biome;
        private String dimension;
        private int y;
        private String event;
        private String structure;

        public String getType() {
            return this.type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBiome() {
            return this.biome;
        }

        public void setBiome(String biome) {
            this.biome = biome;
        }

        public String getDimension() {
            return this.dimension;
        }

        public void setDimension(String dimension) {
            this.dimension = dimension;
        }

        public int getY() {
            return this.y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public String getEvent() {
            return this.event;
        }

        public void setEvent(String event) {
            this.event = event;
        }

        public String getStructure() {
            return this.structure;
        }

        public void setStructure(String structure) {
            this.structure = structure;
        }
    }

    public enum EchoConditionType {
        BIOME,
        Y_BELOW,
        Y_ABOVE,
        FIRST_TIME,
        DIMENSION,
        STRUCTURE
    }
}

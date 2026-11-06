package io.jenkins.plugins.buildvalue.logic;

/** One headline number on the page: what it measures, the value, and an optional comparison. */
public final class Panel {
    private Panel() {}

    /** A metric tile. */
    public static final class Metric {
        private final String label;
        private final String value;
        private final String note;

        Metric(String label, String value, String note) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
            this.note = note == null ? "" : note;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        public String getNote() {
            return note;
        }
    }

    /** A chart card: its heading, the rendered SVG, and an optional explanation. */
    public static final class Chart {
        private final String title;
        private final String svg;
        private final String note;

        Chart(String title, String svg, String note) {
            this.title = title == null ? "" : title;
            this.svg = svg == null ? "" : svg;
            this.note = note == null ? "" : note;
        }

        public String getTitle() {
            return title;
        }

        public String getSvg() {
            return svg;
        }

        public String getNote() {
            return note;
        }
    }
}

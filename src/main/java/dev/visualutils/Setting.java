package dev.visualutils;

/** Ustawienia widoczne w zakladkach "Opcje" i "Wyglad". Klikniecie przelacza na nastepna wartosc. */
public enum Setting {
    // ----- zakladka Opcje -----
    TRACER_FROM(Module.Category.OPCJE, "Tracery: start", "Skąd wychodzą linie", 0, "Dół ekranu", "Środek ekranu"),
    TRACER_COLOR(Module.Category.OPCJE, "Tracery: kolor", "Kolor linii do celów", 0, "Wg typu celu", "Kolor motywu"),
    TRACER_WIDTH(Module.Category.OPCJE, "Tracery: grubość", "Cienkie albo grube linie", 0, "Cienkie", "Grube"),
    ESP_STYLE(Module.Category.OPCJE, "ESP: styl ramki", "Same rogi albo cała ramka", 0, "Rogi", "Pełna"),
    RANGE(Module.Category.OPCJE, "Zasięg ESP i tracerów", "Maksymalny dystans celów (bloki)", 2, "64", "96", "128"),
    RADAR_RANGE(Module.Category.OPCJE, "Zasięg radaru", "Ile bloków obejmuje radar", 1, "32", "48", "64", "96"),
    ZOOM_FOV(Module.Category.OPCJE, "Zoom: FOV", "Im mniej, tym większe przybliżenie", 1, "15", "30", "45"),

    // ----- zakladka Wyglad -----
    PANEL_OPACITY(Module.Category.WYGLAD, "Tło paneli HUD", "Jak mocno przyciemnione są panele", 1,
            "Przezroczyste", "Normalne", "Ciemne"),
    ESP_FILL(Module.Category.WYGLAD, "ESP: wypełnienie", "Kolor wewnątrz ramki", 1, "Brak", "Słabe", "Mocne"),
    HP_BAR(Module.Category.WYGLAD, "ESP: pasek HP", "Pasek zdrowia przy ramce", 0, "Wł.", "Wył."),
    TAG_COUNT(Module.Category.WYGLAD, "Nametagi: ile naraz", "Tylko najbliższe cele", 2, "6", "10", "14", "20"),
    TRACER_COUNT(Module.Category.WYGLAD, "Tracery: ile naraz", "Tylko najbliższe cele", 2, "8", "16", "24", "40"),
    RADAR_SIZE(Module.Category.WYGLAD, "Radar: rozmiar", "Wielkość radaru na ekranie", 1, "64", "84", "110"),
    NEARBY_RANGE(Module.Category.WYGLAD, "Gracze w pobliżu: zasięg", "Do ilu bloków (lista i alert)", 1,
            "50", "100", "200"),
    TAG_DETAILS(Module.Category.WYGLAD, "Nametagi: szczegóły", "Ikony pancerza i enchanty pod nickiem", 0,
            "Ikony + enchanty", "Tylko ikony", "Tylko set"),
    HUD_SIDE(Module.Category.WYGLAD, "Panele HUD: strona", "Po której stronie ekranu panele", 0,
            "Lewa", "Prawa"),
    MENU_OPACITY(Module.Category.WYGLAD, "Menu: tło", "Przezroczystość okna menu", 0,
            "Ciemne", "Średnie", "Jasne");

    private final Module.Category tab;
    private final String label;
    private final String description;
    private final int defaultIndex;
    private final String[] values;

    Setting(Module.Category tab, String label, String description, int defaultIndex, String... values) {
        this.tab = tab;
        this.label = label;
        this.description = description;
        this.defaultIndex = defaultIndex;
        this.values = values;
    }

    public Module.Category tab() {
        return tab;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public int defaultIndex() {
        return defaultIndex;
    }

    public int count() {
        return values.length;
    }

    public String valueAt(int index) {
        return values[Math.floorMod(index, values.length)];
    }
}

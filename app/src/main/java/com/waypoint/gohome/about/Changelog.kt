package com.waypoint.gohome.about

data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<String>
)

/** Hand-maintained release history, shown on the "Historia zmian" screen. Newest first. */
object Changelog {
    val entries: List<ChangelogEntry> = listOf(
        ChangelogEntry(
            version = "1.16.0",
            date = "2026-08-19",
            changes = listOf(
                "Ekran Słońce: animowana grafika pokazująca aktualną pozycję słońca względem horyzontu (obok istniejącego kompasu słonecznego)"
            )
        ),
        ChangelogEntry(
            version = "1.15.0",
            date = "2026-08-19",
            changes = listOf(
                "Ekran startowy przy uruchamianiu aplikacji z logiem, nazwą i numerem wersji"
            )
        ),
        ChangelogEntry(
            version = "1.14.0",
            date = "2026-08-19",
            changes = listOf(
                "Nowa ikona aplikacji dopasowana do kolorów appki (pineska z domkiem)",
                "Skrócona nazwa na ekranie głównym: „WayHome” zamiast „Waypoint Go Home”, żeby się mieściła"
            )
        ),
        ChangelogEntry(
            version = "1.13.0",
            date = "2026-08-19",
            changes = listOf(
                "Tracker bezpieczeństwa: cyklicznie wysyła SMS z pozycją do jednego, wcześniej ustawionego numeru (Menu → Narzędzia → Tracker bezpieczeństwa)",
                "Bez własnego serwera i bez wystawiania adresu IP — działa wyłącznie przez SMS, zawsze widoczny w powiadomieniach"
            )
        ),
        ChangelogEntry(
            version = "1.12.0",
            date = "2026-08-19",
            changes = listOf(
                "Szczegóły waypointu: współrzędne, wysokość i data zapisu (dialog waypointu → „Szczegóły”)"
            )
        ),
        ChangelogEntry(
            version = "1.11.0",
            date = "2026-08-19",
            changes = listOf(
                "Podgląd waypointu na żywym obrazie z kamery, z naniesionym kierunkiem i dystansem (dostępne z dialogu każdego waypointu → „Pokaż kierunek w kamerze”)"
            )
        ),
        ChangelogEntry(
            version = "1.10.0",
            date = "2026-08-19",
            changes = listOf(
                "Nawigacja do ręcznie wpisanych współrzędnych (Menu → Trasa → Prowadź do współrzędnych)"
            )
        ),
        ChangelogEntry(
            version = "1.9.0",
            date = "2026-08-19",
            changes = listOf(
                "Historia tras i zdjęcia wykluczone z automatycznego backupu w chmurze / adb backup",
                "Ostrzeżenie przed pierwszym włączeniem trasy po drogach o wysyłaniu pozycji do zewnętrznego serwera OSRM"
            )
        ),
        ChangelogEntry(
            version = "1.8.0",
            date = "2026-08-19",
            changes = listOf(
                "Pełny zestaw kolorów Material 3 (primary/secondary/tertiary + warianty container, error) w jasnym i ciemnym motywie",
                "Bez zmian w wyglądzie mapy — kafelki bez filtrów kolorów"
            )
        ),
        ChangelogEntry(
            version = "1.7.0",
            date = "2026-08-17",
            changes = listOf(
                "Zwykły kompas jako osobny ekran (Menu → Informacje → Kompas)",
                "Mały kompas na mapie, w lewym dolnym rogu, zawsze widoczny"
            )
        ),
        ChangelogEntry(
            version = "1.6.0",
            date = "2026-08-17",
            changes = listOf(
                "Menu podzielone na grupy: Trasa / Narzędzia / Informacje",
                "Spójny wygląd dialogów (Material 3) w całej aplikacji",
                "Komunikat przy ponownym naciśnięciu „Zaznacz pozycję”, gdy start już ustawiony",
                "Większe, czytelniejsze przyciski na dolnym pasku",
                "Rozróżnienie nazw: „Zaznacz pozycję” vs „Współrzędne GPS”",
                "Opisy przy wyborze trybu powrotu",
                "Wyjaśnienie odczytu kompasu słońca (odległość od środka = wysokość nad horyzontem)",
                "Powiadomienia (Snackbar) zamiast krótkich, łatwych do przeoczenia Toastów",
                "Podświetlenie aktualnie wczytanej trasy w historii tras",
                "Pola tekstowe w dialogach z etykietą (Material TextInputLayout)"
            )
        ),
        ChangelogEntry(
            version = "1.5.0",
            date = "2026-08-17",
            changes = listOf(
                "Eksport trasy ze zdjęciami: GPX + ZIP lub KMZ (Google Earth)"
            )
        ),
        ChangelogEntry(
            version = "1.4.0",
            date = "2026-08-17",
            changes = listOf(
                "Wersjonowanie aplikacji i ekran z historią zmian"
            )
        ),
        ChangelogEntry(
            version = "1.3.0",
            date = "2026-08-16",
            changes = listOf(
                "Przycisk „Zlokalizuj mnie” centrujący mapę na aktualnej pozycji",
                "Poprawiony crash przy próbie pobrania mapy offline",
                "Trafniejsze komunikaty błędów trasy po drogach (brak internetu / brak trasy / błąd serwera)",
                "Rozszerzony wybór trybu powrotu: przez waypointy, bezpośrednio do startu lub wybrany punkt",
                "Ekran „Słońce”: wschód/zachód, odliczanie do zmierzchu, pozycja słońca na żywym kompasie",
                "Dialog „Aktualna pozycja” ze współrzędnymi GPS i wysokością",
                "Większy panel i wskaźnik nawigacji powrotnej",
                "Automatyczny punkt końcowy dodawany po zatrzymaniu nagrywania",
                "Opis (etykieta) przy ręcznym dodawaniu waypointu",
                "Zdjęcia przypisane do waypointów"
            )
        ),
        ChangelogEntry(
            version = "1.2.0",
            date = "2026-08-11",
            changes = listOf(
                "Odświeżony, nowoczesny design: Material 3, tryb ciemny, zaokrąglone, unoszące się panele"
            )
        ),
        ChangelogEntry(
            version = "1.1.0",
            date = "2026-08-10",
            changes = listOf(
                "Historia tras — wiele zapisanych wypraw",
                "Eksport i import tras w formacie GPX",
                "Automatyczne dodawanie waypointów co zadany dystans",
                "Statystyki na żywo podczas nagrywania: dystans, czas, tempo",
                "Profil wysokości trasy",
                "Opcjonalna nakładka trasy po drogach w trybie powrotu",
                "Wibracja przy dotarciu do waypointu",
                "Usuwanie pojedynczego waypointu",
                "Przypomnienie o wyłączeniu optymalizacji baterii przed nagrywaniem",
                "Udostępnianie aktualnej lokalizacji przez WhatsApp, Signal i SMS"
            )
        ),
        ChangelogEntry(
            version = "1.0.0",
            date = "2026-08-10",
            changes = listOf(
                "Zaznaczanie aktualnej pozycji na mapie",
                "Dodawanie waypointów po drodze",
                "Rysowanie przebytej trasy na żywo podczas nagrywania",
                "Nawigacja powrotna do punktu startowego lub wybranego punktu"
            )
        )
    )
}

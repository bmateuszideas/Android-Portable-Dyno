# Jedna aplikacja hamowni drogowej

Zatwierdzony kierunek: polecenie użytkownika „wykonaj” po wymaganiu jednej aplikacji działającej w układzie masa, prędkość, czas rozpędzania i wybiegu.

## Przebieg
Ustawienia auta (opis, rzeczywista masa, bieg, opcjonalne RPM przy km/h) → START → zapis całego przejazdu → STOP → wynik automatyczny. Brak przycisków ręcznego oznaczania faz. STOP pozostaje decyzją użytkownika; aplikacja nie przerywa zapisu na podstawie własnej klasyfikacji ruchu.

## Dane i obliczenia
Surowe rekordy pozostają niezmienione. Room przechowuje parametry kalkulacji przy sesji. Migracja 1→2 zachowuje stare sesje, które wymagają podania masy. Historyczne ustawienia można zmienić i ponownie przeliczyć dane.

Jeden zapis obejmuje jeden ciąg rozpędzania i następującego wybiegu. Kolejne rozpędzenia zgłaszają niejednoznaczny zapis zamiast automatycznie wybierać największy wynik. Regresja energii kinetycznej względem monotonicznego czasu wyznacza moc. Okno ma promień 2 s lub dwóch medianowych odstępów próbkowania, jeśli są większe. Pierwsze 2 s po maksimum prędkości są pomijane w modelu wybiegu jako przejście. Dodatnia moc strat jest interpolowana wyłącznie w zmierzonym zakresie prędkości. Moc całkowita jest sumą mocy rozpędzania i strat dla tej samej prędkości; RPM pochodzi z kalibracji tego samego biegu, moment z P/omega.

Maksimum dotyczy tylko zakresu pokrytego wybiegiem. Z prędkości nie da się odróżnić hamowania od oporów, dlatego interpretacja zakłada wybieg z rozłączonym napędem. Nie deklarujemy zgodności liczbowej z niepublicznymi filtrami Dynomet ani certyfikowanej mocy silnika.

## Ekrany i eksport
POMIAR, HISTORIA, import CSV. Po STOP automatyczny wynik: moc, straty, moment, krzywe z osiami RPM lub km/h, zapisany przebieg prędkości. Osobny eksport wyliczonych punktów zawiera parametry i wersję algorytmu. Surowy eksport i replay dostępne z wyniku.

## Weryfikacja
Testy fizyki, zgodności 1/20 Hz, długiego rozpędzania, częściowego i brakującego wybiegu, powtarzających się przejazdów, duplikatów i przerw. CI buduje jeden pakiet com.roaddyno.app. Instalacja i pomiar na S25 wymagają fizycznego telefonu.

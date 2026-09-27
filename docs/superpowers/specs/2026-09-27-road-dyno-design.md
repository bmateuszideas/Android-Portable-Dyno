# Jedna aplikacja hamowni drogowej

Zatwierdzony kierunek: polecenie użytkownika „wykonaj” po wymaganiu jednej aplikacji działającej w układzie masa, prędkość, czas rozpędzania i wybiegu.

## Przebieg
Ustawienia auta (opis, rzeczywista masa, bieg, opcjonalne RPM przy km/h) → START → zapis całego przejazdu → STOP → wynik automatyczny. Brak przycisków ręcznego oznaczania faz. STOP pozostaje decyzją użytkownika; aplikacja nie przerywa zapisu na podstawie własnej klasyfikacji ruchu.

## Dane i obliczenia
Surowe rekordy pozostają niezmienione. Room przechowuje parametry kalkulacji przy sesji. Migracja 1→2 zachowuje stare sesje, które wymagają podania masy. Historyczne ustawienia można zmienić i ponownie przeliczyć dane.

Jeden START/STOP zapisuje cały przebieg. Do obliczenia jednego wyniku używamy rozpędzania od najniższej prędkości przed pierwszym maksimum do tego maksimum oraz następującego wybiegu do najniższej prędkości po maksimum. Dodatkowe manewry nie usuwają surowego zapisu; cały wykres prędkości pozostaje widoczny. Nie wymagamy arbitralnego przyrostu prędkości, czasu fazy ani liczby próbek ponad dwie dla samej mocy rozpędzania. Regresja energii kinetycznej względem monotonicznego czasu wyznacza moc. Okno ma promień 2 s lub dwóch medianowych odstępów próbkowania, jeśli są większe. Wybieg zaczyna się od maksimum prędkości, bez pomijania pierwszych 2 s. Dodatnia moc strat jest interpolowana wyłącznie w zmierzonym zakresie prędkości. Moc całkowita jest sumą mocy rozpędzania i strat dla tej samej prędkości; RPM pochodzi z kalibracji tego samego biegu, moment z P/omega.

Maksimum mocy z oporami dotyczy tylko zakresu pokrytego wybiegiem. Z prędkości nie da się odróżnić hamowania od oporów, dlatego interpretacja zakłada wybieg z rozłączonym napędem. Kryterium poprawności stanowią bilans energii, jednostki, znane przypadki analityczne i powtarzalność przejazdów. Nie dopasowujemy algorytmu do liczby podawanej przez określoną markę hamowni; porównania wymagają uzgodnienia masy, zakresu i tego, czy raportowana jest moc rozpędzania czy moc skorygowana o straty.

## Ekrany i eksport
POMIAR, HISTORIA, import CSV. Po STOP automatyczny wynik: moc, straty, moment, krzywe z osiami RPM lub km/h, zapisany przebieg prędkości. Osobny eksport wyliczonych punktów zawiera parametry i wersję algorytmu. Surowy eksport i replay dostępne z wyniku.

## Weryfikacja
Testy fizyki, zgodności 1/20 Hz, długiego rozpędzania, częściowego i brakującego wybiegu, powtarzających się przejazdów, duplikatów i przerw. CI buduje jeden pakiet com.roaddyno.app. Instalacja i pomiar na S25 wymagają fizycznego telefonu.

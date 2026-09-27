# Android Road Dyno

Jedna aplikacja hamowni drogowej na Androida. Telefon zapisuje bezpośrednią prędkość GNSS (`Location.speed`) z monotonicznym czasem każdej próbki. Ten sam zapis jest wejściem do obliczenia mocy rozpędzania, oporów z wybiegu, mocy z oporami i momentu obrotowego. Surowe próbki pozostają niezmienione i można je eksportować do CSV.

## Przebieg pomiaru

1. Podaj rzeczywistą masę pomiarową oraz bieg. Opcjonalnie skalibruj RPM kolejno przy 2000 i 3000 obrotów na tym biegu; można też podać rozmiar opony do pomocniczych obliczeń drogi i obrotów koła.
2. Naciśnij **START POMIARU**, rozpędź pojazd na wybranym biegu, rozłącz napęd i wykonaj swobodny wybieg, potem naciśnij **STOP · OBLICZ WYNIK**.
3. Na ekranie wyniku widać cały przebieg prędkości w czasie, moc rozpędzania i, gdy zmierzono wybieg przy tej samej prędkości, moc strat oraz moc z oporami. Po kalibracji RPM widoczny jest moment. Zapisany przejazd można otworzyć ponownie, zmienić masę/kalibrację i przeliczyć offline; dostępny jest także import i eksport CSV.

Wynik wybiegu zakłada rozłączony napęd i brak użycia hamulców. Sama prędkość nie rozróżnia hamowania od oporów ruchu. Krzywa strat nie jest ekstrapolowana poza zmierzony zakres prędkości. Rozmiar opony nie dostarcza niezależnego drugiego pomiaru prędkości: droga i obroty koła są pochodnymi tej samej prędkości GNSS.

## Budowa

Projekt: Kotlin, Jetpack Compose, Room; Android SDK 36, JDK 17, Gradle 8.13. `gradle :app:testDebugUnitTest :app:assembleDebug` uruchamia testy i buduje pakiet. GitHub Actions wykonuje te same kroki. Na telefonie wymagane są uprawnienie do dokładnej lokalizacji i włączony GPS; foreground service utrzymuje zapis po wygaszeniu ekranu.

Pomiar fizyczny na Galaxy S25 oraz porównanie wartości z urządzeniem referencyjnym wymagają jazdy na zamkniętym odcinku testowym. Nie deklarujemy numerycznej zgodności z własnościowym filtrowaniem Dynomet.

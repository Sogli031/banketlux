# BanketLux — primena redizajna (tamna tema, diskretno zlato)

Paket sadrži gotove fajlove na istim putanjama kao u projektu. Menja se samo UI sloj:
tema, komponente i pet ekrana. Baza, ViewModel-i, backup, kalendar i logika cena se ne diraju.

## 1. Fontovi (obavezno pre builda)

Napravi folder `app/src/main/res/font/` i ubaci šest fajlova, tačno ovako nazvanih
(mala slova, bez crtica):

| Fajl                     | Odakle                                                   |
|--------------------------|----------------------------------------------------------|
| `outfit_regular.ttf`     | https://fonts.google.com/specimen/Outfit → static/Outfit-Regular.ttf   |
| `outfit_medium.ttf`      | static/Outfit-Medium.ttf                                 |
| `outfit_semibold.ttf`    | static/Outfit-SemiBold.ttf                               |
| `outfit_bold.ttf`        | static/Outfit-Bold.ttf                                   |
| `geistmono_regular.ttf`  | https://fonts.google.com/specimen/Geist+Mono → static/GeistMono-Regular.ttf |
| `geistmono_medium.ttf`   | static/GeistMono-Medium.ttf                              |

Na Google Fonts klikni „Get font" → „Download all", pa iz ZIP-a uzmi fajlove iz `static/` foldera.

## 2. Prekopiraj fajlove

Iz `handoff/app/...` u `app/...` (pregazi postojeće):

```
app/src/main/res/values/styles.xml
app/src/main/java/com/banketlux/BanketLuxApp.kt
app/src/main/java/com/banketlux/ui/theme/Color.kt
app/src/main/java/com/banketlux/ui/theme/Theme.kt
app/src/main/java/com/banketlux/ui/theme/Type.kt
app/src/main/java/com/banketlux/ui/components/BanketScaffold.kt
app/src/main/java/com/banketlux/ui/bookings/BookingsScreen.kt
app/src/main/java/com/banketlux/ui/bookings/BookingEditorScreen.kt
app/src/main/java/com/banketlux/ui/equipment/EquipmentScreen.kt
app/src/main/java/com/banketlux/ui/earnings/EarningsScreen.kt
```

Nepromenjeni ostaju: `SettingsScreen`, `BookingLineEditor`, `EquipmentPickerSheet`,
`EquipmentEditorDialog`, `BanketFeedback`, `BanketInfo`, `BanketStatus` — oni tamnu temu
dobijaju automatski kroz `MaterialTheme` i nove `Banket*` boje.

## 3. Build

```
./gradlew assembleDebug
```

## Šta je promenjeno

- **Tema**: `darkColorScheme` sa svim ulogama eksplicitno zadatim — nestaju ljubičasti FAB i
  kartica zarade (Material podrazumevani `primaryContainer`). `primary` je sada zlato, pa su
  dugmad, aktivna navigacija, ikonica „nazad" i naslovi sekcija zlatni bez izmena po ekranima.
- **Tipografija**: Outfit za tekst, Geist Mono za datume i iznose (`BanketMono`).
- **Komponente** (`BanketScaffold.kt`): `BanketTopBar` dobio `showLogo`; `BanketSection` je ravna
  sekcija sa zlatnim naslovom umesto kartice; nove `BanketCard`, `BanketChip`, `BanketSectionLabel`.
- **Zakazivanja**: zaglavlje sa logom i brojem predstojećih; kartica — period zlatan monospace,
  čip trajanja, ime krupno, telefon/lokacija sa ikonicama, oprema, ukupno.
- **Editor**: početak i kraj najma jedan pored drugog kao pločice; „Dodaj stavku" preko cele
  širine; blok ukupne cene sa zlatnim okvirom; brisanje kao tekstualno dugme.
- **Oprema**: cela kategorija u jednoj kartici, stavke razdvojene linijom; cena zlatna monospace.
- **Zarada**: hero blok ukupne zarade; godine i meseci sa monospace iznosima.
- **Navigacija**: tamna traka, aktivna stavka zlatna, bez Material „pilule".
- `styles.xml`: tamna status i navigaciona traka.

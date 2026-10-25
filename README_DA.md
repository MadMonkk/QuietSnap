# QuietSnap – Android 9 / OnePlus 3T

Dette projekt bygger to APK-varianter via GitHub Actions, så du ikke behøver Android Studio på din egen computer.

## APK-varianter
- **QuietSnap Bagkamera**: bruger altid bagkameraet.
- **QuietSnap Kameravalg**: giver mulighed for at vælge bagkamera eller frontkamera.

Begge varianter kan planlægge ét eller to billeder om dagen, gemmer JPEG-billeder lokalt i `DCIM/QuietSnap` og forsøger at genskabe tidsplanen efter genstart.

## Byg APK-filer uden Android Studio
1. Log ind på GitHub og opret et nyt repository, fx `QuietSnap`.
2. Pak denne ZIP-fil ud på din computer.
3. Upload indholdet af mappen `QuietSnap` til roden af repository'et. Sørg for, at `.github/workflows/build-apk.yml` også uploades.
4. Gå til fanen **Actions** i repository'et. Hvis GitHub beder om det, skal du aktivere workflows.
5. Vælg **Build QuietSnap APKs** og tryk **Run workflow**. En push til `main` eller `master` starter også bygningen automatisk.
6. Vent, til jobbet bliver grønt. Åbn kørslen, og find **Artifacts** nederst.
7. Hent `QuietSnap-APK-filer` og pak den ZIP-fil ud. Den indeholder begge APK-filer.
8. Overfør den ønskede APK til telefonen, åbn den og tillad installation fra den app, du bruger til at åbne filen.

## Første opsætning på telefonen
1. Åbn appen og giv kamera- og lageradgang.
2. Vælg et eller to tidspunkter og tryk **Gem og aktivér**. I Kameravalg-udgaven vælges kameraet også her.
3. I telefonens batteriindstillinger skal QuietSnap så vidt muligt undtages fra batterioptimering. OxygenOS kan have yderligere baggrundsbegrænsninger.
4. Test først med et tidspunkt få minutter fremme, mens telefonen er låst og skærmen slukket.

## Vigtigt
Dette projekt er ikke testet på en fysisk OnePlus 3T. Android/OxygenOS kan begrænse kameraadgang fra baggrundstjenester; derfor er der ingen garanti for, at fotografering i standby virker konsekvent før praktisk test. Appen viser en vedvarende notifikation under fotograferingen. Den er målrettet Android 9 (target SDK 28).

GitHub Actions bygger debug-APK'er. De er beregnet til installation og test, ikke som signeret produktionsudgivelse.

## Fejlretning: sorte billeder
Denne udgave vælger en JPEG-størrelse, som kameraet faktisk understøtter, venter kort efter opstart og angiver automatisk eksponering, hvidbalance og fokus i stillbilledanmodningen. Det er en forbedring, ikke en garanti; test på den konkrete OnePlus 3T.

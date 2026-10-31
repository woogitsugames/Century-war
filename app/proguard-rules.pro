# Kod produkcyjny :app i :sim (stan zlecenia 66) nie uzywa Class.forName,
# getDeclaredField/getDeclaredMethod ani serializacji opartej na refleksji.
# Profile i powtorki sa kodowane jawnie, a komponenty z manifestu
# sa zachowywane przez reguly generowane przez Android Gradle Plugin.
# Nie dodajemy ogolnego -keep com.centurywar.**: zwiekszylby rozmiar APK.
# Po zmianie mechanizmu ladowania/serializacji sprawdz mapowanie R8 w CI.
